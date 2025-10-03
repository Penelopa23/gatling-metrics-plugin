package ru.x5.svs.gatling.prometheus

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import org.slf4j.LoggerFactory
import scala.collection.mutable

/**
 * Очередь для накопления метрик перед батчевой отправкой
 * Все метрики собираются здесь и отправляются каждые 5 секунд
 */
object MetricsQueue {
  private val logger = LoggerFactory.getLogger(classOf[MetricsQueue.type])
  
  // Очередь для HTTP метрик (запросы, ошибки, durations)
  private val httpMetricsQueue = new ConcurrentLinkedQueue[HttpMetric]()
  
  // THREAD-SAFE счетчики для накопительных метрик
  private val httpRequestCounters = new java.util.concurrent.ConcurrentHashMap[String, AtomicLong]()
  private val httpErrorCounters = new java.util.concurrent.ConcurrentHashMap[String, AtomicLong]()
  private val httpDurations = new java.util.concurrent.ConcurrentHashMap[String, java.util.concurrent.CopyOnWriteArrayList[Long]]()
  
  // THREAD-SAFE счетчики виртуальных пользователей
  private val virtualUsersCount = new java.util.concurrent.atomic.AtomicInteger(0)
  private val peakVirtualUsersCount = new java.util.concurrent.atomic.AtomicInteger(0)
  
  // THREAD-SAFE метрики итераций
  private val iterationDurations = new java.util.concurrent.ConcurrentHashMap[String, java.util.concurrent.CopyOnWriteArrayList[Long]]()
  
  // Конфигурация для предотвращения memory leaks
  private val MAX_DURATIONS_PER_KEY = 1000
  private val MAX_KEYS = 10000
  
  /**
   * Добавить HTTP запрос в очередь
   */
  def addHttpRequest(scenario: String, requestName: String, method: String, status: String, responseTime: Long): Unit = {
    val key = s"$scenario|$requestName|$method|$status"
    
    // Добавляем в очередь для отправки
    httpMetricsQueue.offer(HttpMetric("request", scenario, requestName, method, status, responseTime))
    
    // THREAD-SAFE обновление накопительных счетчиков
    httpRequestCounters.computeIfAbsent(key, _ => new AtomicLong(0)).incrementAndGet()
    
    // THREAD-SAFE добавление duration с ограничением размера
    val durations = httpDurations.computeIfAbsent(key, _ => new java.util.concurrent.CopyOnWriteArrayList[Long]())
    if (durations.size() < MAX_DURATIONS_PER_KEY) {
      durations.add(responseTime)
    } else {
      // Удаляем старые значения, добавляем новые (THREAD-SAFE)
      if (durations.size() > 0) {
        durations.remove(0)
      }
      durations.add(responseTime)
    }
    
    logger.debug(s"📊 Added HTTP request to queue: $key, responseTime=${responseTime}ms")
  }
  
  /**
   * Добавить HTTP ошибку в очередь
   */
  def addHttpError(scenario: String, requestName: String, method: String, status: String, errorMessage: String): Unit = {
    val key = s"$scenario|$requestName|$method|$status|$errorMessage"
    
    // Добавляем в очередь для отправки
    httpMetricsQueue.offer(HttpMetric("error", scenario, requestName, method, status, 0L, errorMessage))
    
    // THREAD-SAFE обновление накопительных счетчиков ошибок
    httpErrorCounters.computeIfAbsent(key, _ => new AtomicLong(0)).incrementAndGet()
    
    logger.debug(s"📊 Added HTTP error to queue: $key, error=$errorMessage")
  }
  
  /**
   * Добавить метрику итерации с ограничением размера
   */
  def addIterationDuration(scenario: String, duration: Long): Unit = {
    // THREAD-SAFE добавление в накопительную коллекцию с ограничением размера
    val durations = iterationDurations.computeIfAbsent(scenario, _ => new java.util.concurrent.CopyOnWriteArrayList[Long]())
    if (durations.size() < MAX_DURATIONS_PER_KEY) {
      durations.add(duration)
    } else {
      // Удаляем старые значения, добавляем новые (THREAD-SAFE)
      if (durations.size() > 0) {
        durations.remove(0)
      }
      durations.add(duration)
    }
    
    logger.debug(s"📊 Added iteration duration: $scenario, duration=${duration}ms")
  }
  
  /**
   * Обновить счетчик виртуальных пользователей
   */
  def updateVirtualUsersCount(count: Int): Unit = {
    virtualUsersCount.set(count)
    // THREAD-SAFE обновление пикового значения
    var currentPeak = peakVirtualUsersCount.get()
    while (count > currentPeak && !peakVirtualUsersCount.compareAndSet(currentPeak, count)) {
      currentPeak = peakVirtualUsersCount.get()
    }
    logger.info(s"VU_COUNT_UPDATED: current=$count peak=${peakVirtualUsersCount.get()}")
  }
  
  /**
   * Увеличить счетчик виртуальных пользователей (при старте VU)
   */
  def incrementVirtualUsers(): Unit = {
    val newCount = virtualUsersCount.incrementAndGet()
    // THREAD-SAFE обновление пикового значения
    var currentPeak = peakVirtualUsersCount.get()
    while (newCount > currentPeak && !peakVirtualUsersCount.compareAndSet(currentPeak, newCount)) {
      currentPeak = peakVirtualUsersCount.get()
    }
    logger.debug(s"VU_START: current=$newCount peak=${peakVirtualUsersCount.get()}")
  }
  
  /**
   * Уменьшить счетчик виртуальных пользователей (при завершении VU)
   */
  def decrementVirtualUsers(): Unit = {
    val newCount = virtualUsersCount.decrementAndGet()
    if (newCount < 0) {
      virtualUsersCount.set(0) // Предотвращаем отрицательные значения
    }
    logger.debug(s"VU_END: current=${virtualUsersCount.get()} peak=${peakVirtualUsersCount.get()}")
  }
  
  /**
   * Получить текущий счетчик виртуальных пользователей
   */
  def getVirtualUsersCount(): Int = virtualUsersCount.get()
  
  /**
   * Получить пиковое количество виртуальных пользователей
   */
  def getPeakVirtualUsersCount(): Int = peakVirtualUsersCount.get()
  
  /**
   * Получить все метрики из очереди для отправки
   */
  def pollAllMetrics(): Seq[HttpMetric] = {
    val metrics = mutable.ListBuffer[HttpMetric]()
    var metric = httpMetricsQueue.poll()
    while (metric != null) {
      metrics += metric
      metric = httpMetricsQueue.poll()
    }
    logger.debug(s"📊 Polled ${metrics.length} metrics from queue")
    metrics.toSeq
  }
  
  /**
   * Получить накопительные счетчики HTTP запросов
   */
  def getHttpRequestCounters(): Map[String, Long] = {
    import scala.jdk.CollectionConverters._
    httpRequestCounters.asScala.map { case (key, counter) => key -> counter.get() }.toMap
  }
  
  /**
   * Получить накопительные счетчики HTTP ошибок
   */
  def getHttpErrorCounters(): Map[String, Long] = {
    import scala.jdk.CollectionConverters._
    httpErrorCounters.asScala.map { case (key, counter) => key -> counter.get() }.toMap
  }
  
  /**
   * Получить durations для статистики
   */
  def getHttpDurations(): Map[String, Seq[Long]] = {
    import scala.jdk.CollectionConverters._
    httpDurations.asScala.map { case (key, durations) => key -> durations.asScala.toSeq }.toMap
  }
  
  /**
   * Получить durations итераций
   */
  def getIterationDurations(): Map[String, Seq[Long]] = {
    import scala.jdk.CollectionConverters._
    iterationDurations.asScala.map { case (key, durations) => key -> durations.asScala.toSeq }.toMap
  }
  
  /**
   * Очистить очередь (но сохранить накопительные метрики)
   */
  def clearQueue(): Unit = {
    val cleared = httpMetricsQueue.size()
    httpMetricsQueue.clear()
    logger.debug(s"📊 Cleared $cleared metrics from queue (accumulative counters preserved)")
  }
  
  /**
   * Очистить все метрики (для предотвращения memory leaks)
   */
  def clearAllMetrics(): Unit = {
    httpMetricsQueue.clear()
    httpRequestCounters.clear()
    httpErrorCounters.clear()
    httpDurations.clear()
    iterationDurations.clear()
    virtualUsersCount.set(0)
    peakVirtualUsersCount.set(0)
    logger.info(s"🧹 Cleared all metrics to prevent memory leaks")
  }
  
  /**
   * СТАТИЧЕСКИЙ метод для очистки метрик (доступен из Java)
   */
  def clearAllMetricsStatic(): Unit = {
    clearAllMetrics()
  }
  
  /**
   * Получить размер очереди
   */
  def queueSize(): Int = httpMetricsQueue.size()
  
  /**
   * Получить статистику очереди
   */
  def getQueueStats(): QueueStats = {
    QueueStats(
      queueSize = httpMetricsQueue.size(),
      httpRequestCount = httpRequestCounters.size,
      httpErrorCount = httpErrorCounters.size,
      virtualUsersCount = virtualUsersCount.get(),
      iterationScenarios = iterationDurations.size
    )
  }
}

/**
 * Модель HTTP метрики для очереди
 */
case class HttpMetric(
  metricType: String, // "request" или "error"
  scenario: String,
  requestName: String,
  method: String,
  status: String,
  responseTime: Long,
  errorMessage: String = ""
)

/**
 * Статистика очереди
 */
case class QueueStats(
  queueSize: Int,
  httpRequestCount: Int,
  httpErrorCount: Int,
  virtualUsersCount: Int,
  iterationScenarios: Int
)
