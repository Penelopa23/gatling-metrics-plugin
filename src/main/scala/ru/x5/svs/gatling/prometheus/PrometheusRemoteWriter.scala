package ru.x5.svs.gatling.prometheus

import org.slf4j.LoggerFactory
import java.util.concurrent.{ScheduledExecutorService, Executors, TimeUnit}
import scala.concurrent.ExecutionContext
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.core5.util.Timeout
import org.apache.hc.core5.http.io.entity.StringEntity
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.core5.http.ContentType
// import ru.x5.svs.gatling.prometheus.infrastructure.ThreadSafeExecutorPool
// import ru.x5.svs.gatling.prometheus.monitoring.ThreadMonitor

/**
 * ОРИГИНАЛЬНЫЙ PrometheusRemoteWriter - отправляет метрики в Victoria Metrics
 */
class PrometheusRemoteWriter(
  private val victoriaMetricsUrl: String,
  private val testId: String,
  private val pod: String,
  private val pushIntervalSeconds: Int = 5
)(implicit private val ec: ExecutionContext) {

  private val logger = LoggerFactory.getLogger(classOf[PrometheusRemoteWriter])
  
  // Состояния для корректной остановки
  sealed trait State
  case object RUNNING extends State
  case object FLUSHING extends State
  case object STOPPED extends State
  
  private val state = new java.util.concurrent.atomic.AtomicReference[State](RUNNING)
  private val finalized = new java.util.concurrent.atomic.AtomicBoolean(false)
  
  // ОПТИМИЗИРОВАННЫЙ планировщик для высокой нагрузки
  private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
  
  // HTTP клиент с таймаутами
  private val httpClient = HttpClients.custom()
    .setDefaultRequestConfig(RequestConfig.custom()
      .setConnectTimeout(Timeout.ofSeconds(3))
      .setResponseTimeout(Timeout.ofSeconds(5))
      .build())
    .build()
  
  // Конфигурация для батчинга
  private val maxCharsPerBatch = 1_000_000  // ~1MB текста безопасно
  
  // THREAD-SAFE пул потоков для обработки метрик с мониторингом
  // private val metricsProcessor = new ThreadSafeExecutorPool(
  //   name = "PrometheusMetricsProcessor",
  //   corePoolSize = Math.max(2, Runtime.getRuntime.availableProcessors() / 2),
  //   maxPoolSize = Math.max(4, Runtime.getRuntime.availableProcessors()),
  //   queueCapacity = 1000
  // )
  
  /**
   * Запустить периодическую отправку метрик
   */
  def start(): Unit = {
    if (state.get() == RUNNING) {
      logger.info(s"ORIGINAL PrometheusRemoteWriter: Starting periodic export to $victoriaMetricsUrl")
      
      // Запускаем мониторинг потоков
      // ThreadMonitor.startMonitoring()
      
      try {
        // Используем конфигурируемый интервал отправки метрик
        val intervalSeconds = pushIntervalSeconds
        val scheduledFuture = scheduler.scheduleAtFixedRate(
          new Runnable {
            override def run(): Unit = {
              // АСИНХРОННАЯ обработка метрик
              try {
                logger.info(s"🔥 ORIGINAL PrometheusRemoteWriter: SCHEDULER TRIGGERED! Thread: ${Thread.currentThread().getName}")
                sendMetrics()
                logger.info(s"🔥 ORIGINAL PrometheusRemoteWriter: SCHEDULER COMPLETED successfully!")
              } catch {
                case e: Exception =>
                  logger.error(s"🔥 ORIGINAL PrometheusRemoteWriter: SCHEDULER ERROR: ${e.getMessage}", e)
              }
            }
          },
          intervalSeconds, intervalSeconds, TimeUnit.SECONDS
        )
        
        logger.info(s"🔥 ORIGINAL PrometheusRemoteWriter: Started periodic export - scheduled task: $scheduledFuture")
        logger.info(s"🔥 ORIGINAL PrometheusRemoteWriter: Scheduler is running: ${!scheduler.isShutdown}")
        
      } catch {
        case e: Exception =>
          logger.error(s"ORIGINAL PrometheusRemoteWriter: ERROR starting scheduler: ${e.getMessage}", e)
          state.set(STOPPED)
      }
    } else {
      logger.warn(s"ORIGINAL PrometheusRemoteWriter: Not in RUNNING state, skipping start")
    }
  }
  
  /**
   * Остановить отправку метрик
   */
  def stop(): Unit = {
    // Идемпотентность - предотвращаем двойной вызов
    if (!finalized.compareAndSet(false, true)) {
      logger.warn("ORIGINAL PrometheusRemoteWriter: Already finalized")
      return
    }
    
    state.get match {
      case STOPPED => 
        logger.warn("ORIGINAL PrometheusRemoteWriter: Already stopped")
        return
      case _ =>
    }
    
    logger.info("ORIGINAL PrometheusRemoteWriter: Stopping periodic export")
    
    // 1) Больше не планируем НОВЫЕ задачи, но даём завершиться текущим
    scheduler.shutdown()
    try {
      if (!scheduler.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
        logger.warn("ORIGINAL PrometheusRemoteWriter: Scheduler did not terminate gracefully, forcing shutdown")
        scheduler.shutdownNow()
      }
    } catch {
      case e: InterruptedException =>
        logger.warn("ORIGINAL PrometheusRemoteWriter: Interrupted while waiting for scheduler termination")
        scheduler.shutdownNow()
        Thread.currentThread().interrupt()
    }
    
    // 2) Переходим в FLUSHING (enqueue() ещё может принимать, если нужно)
    if (!state.compareAndSet(RUNNING, FLUSHING)) {
      logger.warn("ORIGINAL PrometheusRemoteWriter: Stop called not from RUNNING state")
    }
    
    // 3) БЛОКИРУЮЩИЙ финальный флаш с таймаутами и кусованием
    logger.info("ORIGINAL PrometheusRemoteWriter: Flushing final metrics")
    flushBlocking()
    
    // 4) Теперь уже STOPPED – дальше enqueue() должно отказывать
    state.set(STOPPED)
    logger.info("ORIGINAL PrometheusRemoteWriter: Stopped and sent final metrics")
  }
  
  /**
   * Блокирующий финальный флаш с таймаутами и кусованием
   */
  private def flushBlocking(): Unit = {
    val url = victoriaMetricsUrl.replace("/api/v1/write", "/api/v1/import/prometheus")
    var chunk = pullChunk(maxCharsPerBatch) // достаёт строку из очереди до лимита
    while (chunk.nonEmpty) {
      sendChunk(url, chunk)
      chunk = pullChunk(maxCharsPerBatch)
    }
  }
  
  /**
   * Извлечь чанк метрик из очереди
   */
  private def pullChunk(maxChars: Int): String = {
    // Получаем все метрики из PrometheusMetricsManager
    PrometheusMetricsManager.getInstance match {
      case Some(manager) =>
        val allMetrics = manager.createPrometheusFormat()
        if (allMetrics.length > maxChars) {
          allMetrics.take(maxChars)
        } else {
          allMetrics
        }
      case None => ""
    }
  }
  
  /**
   * Отправить чанк метрик с таймаутами (без gzip для совместимости)
   */
  private def sendChunk(url: String, data: String): Unit = {
    if (data.isEmpty) return
    
    try {
      val req = new HttpPost(url)
      val entity = new StringEntity(data, ContentType.TEXT_PLAIN)
      req.setEntity(entity)
      req.setHeader("Content-Type", "text/plain")

      val resp = httpClient.execute(req)
      val code = resp.getCode
      val body = Option(resp.getEntity).map(e => new String(e.getContent.readAllBytes(), "UTF-8")).getOrElse("")
      
      if (code != 204) {
        logger.error(s"VM import failed: $code body=${body.take(200)}")
      } else {
        logger.info(s"Successfully sent chunk of ${data.length} characters to Victoria Metrics")
      }
      
    } catch {
      case e: Exception =>
        logger.error(s"Error sending chunk to Victoria Metrics: ${e.getMessage}", e)
    }
  }

  /**
   * Отправить метрики в Victoria Metrics из очереди
   */
  private def sendMetrics(): Unit = {
    // Проверяем, что writer еще работает
    if (state.get() == STOPPED) {
      logger.warn("QUEUE: Writer is stopped, skipping metrics sending")
      return
    }
    
    try {
      logger.info(s"📊 QUEUE: Starting batch metrics sending from queue")
      
      // НЕ ИСПОЛЬЗУЕМ очередь! Используем накопительные метрики!
      val queueStats = MetricsQueue.getQueueStats()
      
      logger.info(s"📊 QUEUE: Queue stats: $queueStats")
      
      // Всегда отправляем накопительные метрики (не зависим от очереди)
      if (queueStats.httpRequestCount > 0 || queueStats.httpErrorCount > 0) {
        val managerOpt = PrometheusMetricsManager.getInstance
        
        managerOpt.foreach { manager =>
          logger.info(s"📊 QUEUE: Processing metrics with manager...")
          
          // Получаем накопительные метрики из очереди
          val httpRequestCounters = MetricsQueue.getHttpRequestCounters()
          val httpErrorCounters = MetricsQueue.getHttpErrorCounters()
          val httpDurations = MetricsQueue.getHttpDurations()
          
          // Обновляем накопительные метрики в PrometheusMetricsManager
          manager.updateHttpMetricsFromQueue(httpRequestCounters, httpErrorCounters)
          
          // Обновляем HTTP durations
          manager.updateHttpDurationsFromQueue(httpDurations)
          
          // Логируем системные метрики
          manager.logSystemMetrics()
          
          // Создаем Prometheus формат со всеми метриками
          val allMetrics = manager.createPrometheusFormat()
          logger.info(s"📊 QUEUE: Created Prometheus format (${allMetrics.length} chars)")
          
          // Отправляем в Victoria Metrics
          manager.sendToVictoriaMetrics(allMetrics)
          logger.info(s"📊 QUEUE: Successfully sent batch metrics to Victoria Metrics!")
          
          // НЕ ОЧИЩАЕМ очередь! Используем накопительные метрики!
          // MetricsQueue.clearQueue() // УБРАНО - не очищаем очередь!
        }
      } else {
        logger.info(s"📊 QUEUE: No metrics in queue, skipping batch send")
      }
      
    } catch {
      case e: Exception =>
        logger.error(s"📊 QUEUE: Error sending batch metrics: ${e.getMessage}", e)
    }
  }
  
}

object PrometheusRemoteWriter {
  private var instance: Option[PrometheusRemoteWriter] = None
  
  def initialize(victoriaMetricsUrl: String, testId: String, pod: String)(implicit ec: ExecutionContext): PrometheusRemoteWriter = {
    instance = Some(new PrometheusRemoteWriter(victoriaMetricsUrl, testId, pod))
    instance.get
  }
  
  def getInstance: Option[PrometheusRemoteWriter] = instance
  
  def start(): Unit = {
    instance.foreach(_.start())
  }
  
  def stop(): Unit = {
    instance.foreach(_.stop())
  }
}
