package ru.x5.svs.gatling.prometheus

import org.slf4j.LoggerFactory
import java.util.concurrent.{ConcurrentHashMap, ScheduledExecutorService, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

/**
 * ОРИГИНАЛЬНЫЙ PrometheusMetricsManager - рабочая система метрик
 * Отправляет метрики в Victoria Metrics при остановке теста
 */
class PrometheusMetricsManager(
  private val victoriaMetricsUrl: String,
  private val testId: String,
  private val pod: String
)(implicit private val ec: ExecutionContext) {

  private val logger = LoggerFactory.getLogger(classOf[PrometheusMetricsManager])
  
  // Хранилище метрик с thread-safe коллекциями
  private val httpErrors = new ConcurrentHashMap[String, Int]()
  private val httpRequests = new ConcurrentHashMap[String, Int]()
  private val httpDurations = new ConcurrentHashMap[String, java.util.concurrent.CopyOnWriteArrayList[Long]]()
  private val virtualUsers = new java.util.concurrent.atomic.AtomicInteger(0)  // Изменено на AtomicInteger для подсчета VU
  
  // Хранилище длительности итераций сценариев с thread-safe коллекциями
  private val iterationDurations = new ConcurrentHashMap[String, java.util.concurrent.CopyOnWriteArrayList[Long]]()
  
  // Флаг для отправки метрик при остановке
  private val metricsSent = new AtomicBoolean(false)
  
  /**
   * Получить testId
   */
  def getTestId: String = testId
  
  /**
   * Получить pod
   */
  def getPod: String = pod
  
  /**
   * Собрать системные метрики (память, GC)
   * Возвращает Map[metricName, value]
   */
  def collectSystemMetrics(): Map[String, Double] = {
    import java.lang.management.ManagementFactory
    import scala.jdk.CollectionConverters._
    
    val runtime = Runtime.getRuntime
    val totalMemory = runtime.totalMemory()
    val freeMemory = runtime.freeMemory()
    val usedMemory = totalMemory - freeMemory
    val maxMemory = runtime.maxMemory()
    
    // Get GC metrics
    val memoryMXBean = ManagementFactory.getMemoryMXBean
    val heapMemoryUsage = memoryMXBean.getHeapMemoryUsage
    val nonHeapMemoryUsage = memoryMXBean.getNonHeapMemoryUsage
    
    // Get GC MXBean for GC statistics
    val gcMXBeans = ManagementFactory.getGarbageCollectorMXBeans
    val totalGcTime = gcMXBeans.asScala.map(_.getCollectionTime).sum
    val totalGcCount = gcMXBeans.asScala.map(_.getCollectionCount).sum
    
    // Get memory pool MXBeans for stack metrics
    val memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans
    val stackInuse = memoryPoolMXBeans.asScala
      .filter(pool => pool.getName.contains("Code Cache") || pool.getName.contains("Metaspace"))
      .map(_.getUsage.getUsed)
      .sum
    
    Map(
      "gatling_memory_alloc_bytes" -> usedMemory.toDouble,
      "gatling_memory_heap_alloc_bytes" -> heapMemoryUsage.getUsed.toDouble,
      "gatling_memory_heap_sys_bytes" -> heapMemoryUsage.getCommitted.toDouble,
      "gatling_memory_heap_idle_bytes" -> (heapMemoryUsage.getCommitted - heapMemoryUsage.getUsed).toDouble,
      "gatling_memory_heap_inuse_bytes" -> heapMemoryUsage.getUsed.toDouble,
      "gatling_memory_stack_inuse_bytes" -> stackInuse.toDouble,
      "gatling_memory_stack_sys_bytes" -> nonHeapMemoryUsage.getCommitted.toDouble,
      "gatling_gc_count" -> totalGcCount.toDouble,
      "gatling_gc_pause_ms" -> totalGcTime.toDouble  // В миллисекундах
    )
  }
  
  /**
   * Записать HTTP ошибку - С ДЕТАЛЬНЫМИ СООБЩЕНИЯМИ!
   */
  def recordHttpError(scenario: String, request: String, method: String, status: String, errorMessage: String): Unit = {
    val key = s"$scenario|$request|$method|$status|$errorMessage"
    httpErrors.compute(key, (_, count) => if (count == null) 1 else count + 1)
    logger.warn(s"HTTP_ERROR: $key error=$errorMessage")
  }
  
  /**
   * Записать HTTP запрос
   */
  def recordHttpRequest(scenario: String, request: String, method: String, status: String): Unit = {
    val key = s"$scenario|$request|$method|$status"
    httpRequests.compute(key, (_, count) => if (count == null) 1 else count + 1)
    logger.debug(s"HTTP_REQUEST: $key")
  }
  
  /**
   * Записать HTTP запрос с сообщением об ошибке (БЕЗ errorMessage в ключе для избежания дублирования)
   */
  def recordHttpRequest(scenario: String, request: String, method: String, status: String, _errorMessage: String): Unit = {
    val key = s"$scenario|$request|$method|$status"
    httpRequests.compute(key, (_, count) => if (count == null) 1 else count + 1)
    logger.debug(s"HTTP_REQUEST: $key")
  }
  
  /**
   * Записать HTTP запрос с duration (время ответа)
   */
  def recordHttpRequestWithDuration(scenario: String, request: String, method: String, status: String, responseTime: Long): Unit = {
    val key = s"$scenario|$request|$method|$status"
    
    // Записываем count
    httpRequests.compute(key, (_, count) => if (count == null) 1 else count + 1)
    
    // Записываем duration
    httpDurations.compute(key, (_, durations) => {
      val list = if (durations == null) new java.util.concurrent.CopyOnWriteArrayList[Long]() else durations
      list.add(responseTime)
      list
    })
    
    logger.info(s"🔥 ORIGINAL: Recorded HTTP request with duration: $key, responseTime=${responseTime}ms")
  }
  
  /**
   * Обновить количество виртуальных пользователей (вызывается из DataWriter)
   */
  def updateVirtualUsersCount(count: Int): Unit = {
    virtualUsers.set(count)
    logger.info(s"📊 VU COUNT UPDATED: $count active virtual users")
  }
  
  /**
   * Обновить накопительные HTTP метрики из очереди
   */
  def updateHttpMetricsFromQueue(requestCounters: Map[String, Long], errorCounters: Map[String, Long]): Unit = {
    try {
      // ЗАМЕНЯЕМ счетчики HTTP запросов (НЕ ДОБАВЛЯЕМ!)
      requestCounters.foreach { case (key, count) =>
        httpRequests.put(key, count.toInt)
      }
      
      // ЗАМЕНЯЕМ счетчики HTTP ошибок (НЕ ДОБАВЛЯЕМ!)
      errorCounters.foreach { case (key, count) =>
        httpErrors.put(key, count.toInt)
      }
      
      logger.info(s"📊 QUEUE: Updated HTTP metrics - requests: ${requestCounters.size}, errors: ${errorCounters.size}")
    } catch {
      case e: Exception =>
        logger.error(s"📊 QUEUE: Error updating HTTP metrics from queue: ${e.getMessage}", e)
    }
  }
  
  /**
   * Получить текущее количество виртуальных пользователей
   */
  def getCurrentVirtualUsersCount(): Int = {
    virtualUsers.get()
  }
  
  /**
   * Получить пиковое количество виртуальных пользователей
   */
  def getPeakVirtualUsersCount(): Int = {
    // Получаем пиковое значение из MetricsQueue
    MetricsQueue.getPeakVirtualUsersCount()
  }
  
  /**
   * Записать длительность итерации сценария
   */
  def recordIterationDuration(scenario: String, duration: Long): Unit = {
    iterationDurations.compute(scenario, (_, durations) => {
      val list = if (durations == null) new java.util.concurrent.CopyOnWriteArrayList[Long]() else durations
      list.add(duration)
      list
    })
    logger.info(s"🔥 ORIGINAL: Recorded iteration duration: scenario=$scenario, duration=${duration}ms")
  }
  
  /**
   * Всегда генерировать и логировать системные метрики (независимо от настроек отправки)
   */
  def logSystemMetrics(): Unit = {
    val systemMetrics = collectSystemMetrics()
    systemMetrics.foreach { case (metricName, value) =>
      val labels = s"""testid="$testId",pod="$pod""""
      val metricLine = s"$metricName{$labels} $value"
      logger.error(s"📊 SYSTEM METRIC: $metricLine")
    }
    
    // Логируем метрики виртуальных пользователей (текущее и пиковое)
    // ИСПРАВЛЕНИЕ: Читаем актуальное значение из MetricsQueue вместо локального поля
    val vuValue = MetricsQueue.getVirtualUsersCount()
    val vuLabels = s"""testid="$testId",pod="$pod""""
    val vuMetricLine = s"gatling_vus{$vuLabels} $vuValue"
    logger.error(s"📊 VU METRIC: $vuMetricLine")
    
    // Логируем пиковое количество VU
    val peakVuValue = getPeakVirtualUsersCount()
    logger.error(s"📊 VU PEAK DEBUG: peakVuValue=$peakVuValue, currentVuValue=$vuValue")
    val peakVuMetricLine = s"gatling_vus_peak{$vuLabels} $peakVuValue"
    logger.error(s"📊 VU PEAK METRIC: $peakVuMetricLine")
  }
  
  /**
   * Отправить ВСЕ метрики в Victoria Metrics при остановке теста
   */
  def sendMetricsOnTestStop(): Unit = {
    logger.error("🚨🚨🚨 PrometheusMetricsManager.sendMetricsOnTestStop() CALLED!")
    
    if (metricsSent.compareAndSet(false, true)) {
      logger.error(s"🔥 ORIGINAL: Sending ALL metrics to Victoria Metrics on test stop!")
      
      try {
        val allMetrics = createPrometheusFormat()
        logger.error(s"🔥 ORIGINAL: Created Prometheus format (${allMetrics.length} chars)")
        logger.error(s"🔥 ORIGINAL: First 500 chars:\n${allMetrics.take(500)}")
        
        sendToVictoriaMetrics(allMetrics)
        logger.error(s"🔥 ORIGINAL: Successfully sent metrics to Victoria Metrics!")
        
      } catch {
        case e: Exception =>
          logger.error(s"🔥 ORIGINAL: Error sending metrics: ${e.getMessage}", e)
      }
    } else {
      logger.error(s"🔥 ORIGINAL: Metrics already sent, skipping sendMetricsOnTestStop")
    }
  }
  
  /**
   * ВЫВОД ВСЕХ ЗАРЕГИСТРИРОВАННЫХ МЕТРИК В КОНСОЛЬ ПРИ СТАРТЕ!
   */
  def printAllRegisteredMetrics(): Unit = {
    logger.info(s"🔥 ORIGINAL: ===== ВСЕ ЗАРЕГИСТРИРОВАННЫЕ МЕТРИКИ =====")
    logger.info(s"🔥 ORIGINAL: Test ID: $testId")
    logger.info(s"🔥 ORIGINAL: Pod: $pod")
    logger.info(s"🔥 ORIGINAL: Victoria Metrics URL: $victoriaMetricsUrl")
    
    logger.info(s"🔥 ORIGINAL: ===== HTTP ОШИБКИ =====")
    if (httpErrors.isEmpty) {
      logger.info(s"🔥 ORIGINAL: HTTP ошибок пока нет")
    } else {
      httpErrors.forEach { (key, count) =>
        val parts = key.split("\\|")
        if (parts.length >= 5) {
          val scenario = parts(0)
          val request = parts(1)
          val method = parts(2)
          val status = parts(3)
          val errorMessage = parts(4)
          logger.info(s"🔥 ORIGINAL: HTTP ERROR: scenario=$scenario, request=$request, method=$method, status=$status, error=$errorMessage, count=$count")
        }
      }
    }
    
    logger.info(s"🔥 ORIGINAL: ===== HTTP ЗАПРОСЫ =====")
    if (httpRequests.isEmpty) {
      logger.info(s"🔥 ORIGINAL: HTTP запросов пока нет")
    } else {
      httpRequests.forEach { (key, count) =>
        val parts = key.split("\\|")
        if (parts.length >= 4) {
          val scenario = parts(0)
          val request = parts(1)
          val method = parts(2)
          val status = parts(3)
          logger.info(s"🔥 ORIGINAL: HTTP REQUEST: scenario=$scenario, request=$request, method=$method, status=$status, count=$count")
        }
      }
    }
    
    logger.info(s"🔥 ORIGINAL: ===== ВИРТУАЛЬНЫЕ ПОЛЬЗОВАТЕЛИ =====")
    logger.info(s"🔥 ORIGINAL: Active VUs: ${virtualUsers.get()}")
    
    logger.info(s"🔥 ORIGINAL: ===== КОНЕЦ МЕТРИК =====")
  }
  
  /**
   * Создать Prometheus формат из всех накопленных метрик
   */
  def createPrometheusFormat(): String = {
    val timestamp = System.currentTimeMillis() / 1000
    val lines = mutable.ListBuffer[String]()
    
    // HELP и TYPE заголовки - k6/Penelopa совместимые названия!
    lines += "# HELP gatling_http_req_failed Total number of failed HTTP requests"
    lines += "# TYPE gatling_http_req_failed counter"
    lines += "# HELP gatling_http_reqs_total Total number of HTTP requests"
    lines += "# TYPE gatling_http_reqs_total counter"
    lines += "# HELP gatling_http_req_duration HTTP request duration in milliseconds"
    lines += "# TYPE gatling_http_req_duration summary"
    lines += "# HELP gatling_vus Current number of virtual users"
    lines += "# TYPE gatling_vus gauge"
    lines += "# HELP gatling_vus_peak Peak number of virtual users during test"
    lines += "# TYPE gatling_vus_peak gauge"
    
    // HTTP ошибки (failed requests) - переименовано в k6-compatible формат
    httpErrors.forEach { (key, count) =>
      val parts = key.split("\\|")
      if (parts.length >= 5) {
        val scenario = parts(0)
        val request = parts(1)
        val method = parts(2)
        val status = parts(3)
        val errorMessage = parts(4)
        
        val labels = s"""testid="$testId",pod="$pod",scenario="$scenario",name="$request",method="$method",status="$status",error_message="$errorMessage""""
        val metricLine = s"gatling_http_req_failed{$labels} $count $timestamp"
        lines += metricLine
        logger.error(s"📊 METRIC: $metricLine")
      }
    }
    
    // HTTP запросы
    httpRequests.forEach { (key, count) =>
      val parts = key.split("\\|")
      if (parts.length >= 4) {
        val scenario = parts(0)
        val request = parts(1)
        val method = parts(2)
        val status = parts(3)
        
        val labels = s"""testid="$testId",pod="$pod",scenario="$scenario",name="$request",method="$method",status="$status""""
        val metricLine = s"gatling_http_reqs_total{$labels} $count $timestamp"
        lines += metricLine
        logger.error(s"📊 METRIC: $metricLine")
      }
    }
    
    // HTTP request duration (summary metric)
    httpDurations.forEach { (key, durations) =>
      val parts = key.split("\\|")
      if (parts.length >= 4 && durations != null && !durations.isEmpty) {
        val scenario = parts(0)
        val request = parts(1)
        val method = parts(2)
        val status = parts(3)
        
        val labels = s"""testid="$testId",pod="$pod",scenario="$scenario",name="$request",method="$method",status="$status""""
        
        // Вычисляем среднее значение
        import scala.jdk.CollectionConverters._
        val durationsList = durations.asScala.map(_.toLong).toList
        val avg = if (durationsList.nonEmpty) durationsList.sum / durationsList.size else 0
        
        // Простая метрика без квантилей
        val metric_avg = s"gatling_http_req_duration{$labels} $avg $timestamp"
        
        lines += metric_avg
        
        logger.error(s"📊 DURATION METRIC: $metric_avg")
      }
    }
    
    // Виртуальные пользователи - текущее количество
    // ИСПРАВЛЕНИЕ: Читаем актуальное значение из MetricsQueue вместо локального поля
    val vuValue = MetricsQueue.getVirtualUsersCount()
    val vuLabels = s"""testid="$testId",pod="$pod""""
    val metricVus = s"gatling_vus{$vuLabels} $vuValue $timestamp"
    lines += metricVus
    logger.error(s"📊 METRIC: $metricVus")
    
    // Пиковое количество виртуальных пользователей
    val peakVuValue = getPeakVirtualUsersCount()
    logger.error(s"📊 VU PEAK DEBUG: peakVuValue=$peakVuValue, currentVuValue=$vuValue")
    val metricVusPeak = s"gatling_vus_peak{$vuLabels} $peakVuValue $timestamp"
    lines += metricVusPeak
    logger.error(s"📊 METRIC: $metricVusPeak")
    
    // СИСТЕМНЫЕ МЕТРИКИ (память, GC)
    lines += "# HELP gatling_memory_alloc_bytes Total allocated memory in bytes"
    lines += "# TYPE gatling_memory_alloc_bytes gauge"
    lines += "# HELP gatling_memory_heap_alloc_bytes Heap allocated memory in bytes"
    lines += "# TYPE gatling_memory_heap_alloc_bytes gauge"
    lines += "# HELP gatling_memory_heap_sys_bytes Heap system memory in bytes"
    lines += "# TYPE gatling_memory_heap_sys_bytes gauge"
    lines += "# HELP gatling_gc_count Total number of GC collections"
    lines += "# TYPE gatling_gc_count counter"
    lines += "# HELP gatling_gc_pause_ms Total GC pause time in milliseconds"
    lines += "# TYPE gatling_gc_pause_ms counter"
    
    // ИТЕРАЦИИ СЦЕНАРИЕВ (длительность выполнения полных сценариев)
    lines += "# HELP gatling_iteration_duration Iteration duration in milliseconds"
    lines += "# TYPE gatling_iteration_duration gauge"
    
    iterationDurations.forEach { (scenario, durations) =>
      if (durations != null && !durations.isEmpty) {
        import scala.jdk.CollectionConverters._
        val durationsList = durations.asScala.map(_.toLong).toList.sorted
        
        // Берем последнее значение (текущая длительность итерации)
        val currentDuration = durationsList.last
        
        val labels = s"""testid="$testId",pod="$pod",scenario="$scenario""""
        val metricLine = s"gatling_iteration_duration{$labels} $currentDuration $timestamp"
        lines += metricLine
        logger.error(s"📊 ITERATION METRIC: $metricLine")
      }
    }
    
    val systemMetrics = collectSystemMetrics()
    systemMetrics.foreach { case (metricName, value) =>
      val labels = s"""testid="$testId",pod="$pod""""
      val metricLine = s"$metricName{$labels} $value $timestamp"
      lines += metricLine
      logger.error(s"📊 SYSTEM METRIC: $metricLine")
    }
    
    logger.error(s"📊 TOTAL METRICS GENERATED: ${lines.size} lines")
    lines.mkString("\n")
  }
  
  /**
   * Отправить метрики в Victoria Metrics через Remote Write API (Protobuf + Snappy)
   * FALLBACK: Если Protobuf не работает, пробуем текстовый формат
   */
  def sendToVictoriaMetrics(metricsData: String): Unit = {
    import org.apache.hc.client5.http.classic.methods.HttpPost
    import org.apache.hc.client5.http.impl.classic.{HttpClients, CloseableHttpClient, CloseableHttpResponse}
    import org.apache.hc.core5.http.ContentType
    import org.apache.hc.core5.http.io.entity.{ByteArrayEntity, StringEntity}
    
    // СНАЧАЛА ПРОБУЕМ ТЕКСТОВЫЙ ФОРМАТ (проще и надежнее для отладки)
    try {
      val textFormatUrl = victoriaMetricsUrl.replace("/api/v1/write", "/api/v1/import/prometheus")
      logger.error(s"🔥 VICTORIA METRICS TEXT FORMAT: Sending metrics to $textFormatUrl")
      logger.error(s"🔥 VICTORIA METRICS TEXT FORMAT: Metrics data:\n${metricsData.take(1000)}")
      
      // ПРОВЕРЯЕМ: если URL начинается с file://, записываем в файл
      if (textFormatUrl.startsWith("file://")) {
        val filePath = textFormatUrl.substring(7) // убираем "file://"
        val file = new java.io.File(filePath)
        val writer = new java.io.FileWriter(file, false) // OVERWRITE mode - не дублируем!
        try {
          writer.write(metricsData)
          logger.error(s"✅ Successfully wrote ${metricsData.length} characters to file: $filePath")
        } finally {
          writer.close()
        }
        return
      }
      
      // Используем try-with-resources для автоматического закрытия ресурсов
      val httpClient: CloseableHttpClient = HttpClients.createDefault()
      try {
        val request = new HttpPost(textFormatUrl)
        request.setEntity(new StringEntity(metricsData, ContentType.TEXT_PLAIN))
        request.setHeader("Content-Type", "text/plain")
        
        logger.error(s"🔥 VICTORIA METRICS TEXT FORMAT: Sending ${metricsData.length} characters")
        
        val response: CloseableHttpResponse = httpClient.execute(request)
        
        val statusCode = response.getCode
        
        val responseBody = try {
          val entity = response.getEntity
          if (entity != null) {
            val content = new String(entity.getContent.readAllBytes(), "UTF-8")
            if (content.nonEmpty) content else "empty"
          } else {
            "no entity"
          }
        } catch {
          case e: Exception => s"error reading response: ${e.getMessage}"
        }
        
        logger.error(s"🔥 VICTORIA METRICS TEXT FORMAT: HTTP Response Status: $statusCode")
        logger.error(s"🔥 VICTORIA METRICS TEXT FORMAT: HTTP Response Body: $responseBody")
        
        if (statusCode >= 200 && statusCode < 300) {
          logger.error(s"✅ Successfully sent metrics to Victoria Metrics in TEXT format (HTTP $statusCode)")
        } else {
          logger.error(s"❌ Failed to send metrics in TEXT format (HTTP $statusCode)")
          logger.error(s"❌ Response: $responseBody")
        }
        
        response.close()
        httpClient.close()
      }
    } catch {
      case e: Exception =>
        logger.error(s"💥 Error sending metrics in TEXT format to Victoria Metrics: ${e.getClass.getSimpleName}: ${e.getMessage}", e)
        // Перебрасываем исключение, чтобы вызывающий код знал об ошибке
        throw e
    }
  }
  
  /**
   * Обновить HTTP durations из очереди (вызывается из PrometheusRemoteWriter)
   */
  def updateHttpDurationsFromQueue(httpDurations: Map[String, Seq[Long]]): Unit = {
    // Очищаем старые durations
    this.httpDurations.clear()
    
    // Добавляем новые durations
    httpDurations.foreach { case (key, durations) =>
      val durationsList = new java.util.concurrent.CopyOnWriteArrayList[Long]()
      durations.foreach(durationsList.add)
      this.httpDurations.put(key, durationsList)
    }
    
    logger.info(s"📊 QUEUE: Updated HTTP durations from queue - ${httpDurations.size} duration groups")
  }
}

object PrometheusMetricsManager {
  private var instance: Option[PrometheusMetricsManager] = None
  
  def initialize(victoriaMetricsUrl: String, testId: String, pod: String)(implicit ec: ExecutionContext): PrometheusMetricsManager = {
    instance = Some(new PrometheusMetricsManager(victoriaMetricsUrl, testId, pod))
    instance.get
  }
  
  def getInstance: Option[PrometheusMetricsManager] = instance
  
  def sendMetricsOnTestStop(): Unit = {
    instance.foreach(_.sendMetricsOnTestStop())
  }
}
