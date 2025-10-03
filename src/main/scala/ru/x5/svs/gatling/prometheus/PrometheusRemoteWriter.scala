package ru.x5.svs.gatling.prometheus

import org.slf4j.LoggerFactory
import java.util.concurrent.{ScheduledExecutorService, Executors, TimeUnit}
import scala.concurrent.ExecutionContext
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
  
  // ОПТИМИЗИРОВАННЫЙ планировщик для высокой нагрузки
  private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
  private val running = new java.util.concurrent.atomic.AtomicBoolean(false)
  
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
    if (running.compareAndSet(false, true)) {
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
          logger.error(s"🔥 ORIGINAL PrometheusRemoteWriter: ERROR starting scheduler: ${e.getMessage}", e)
          running.set(false)
      }
    } else {
      logger.warn(s"🔥 ORIGINAL PrometheusRemoteWriter: Already running, skipping start")
    }
  }
  
  /**
   * Остановить отправку метрик
   */
  def stop(): Unit = {
    if (running.compareAndSet(true, false)) {
      logger.info(s"🔥 ORIGINAL PrometheusRemoteWriter: Stopping periodic export")
      
      // GRACEFUL SHUTDOWN для всех пулов потоков
      logger.info("🔥 ORIGINAL PrometheusRemoteWriter: Shutting down thread pools...")
      
      // Останавливаем планировщик
      scheduler.shutdown()
      try {
        if (!scheduler.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS)) {
          logger.warn("🔥 ORIGINAL PrometheusRemoteWriter: Scheduler did not terminate gracefully, forcing shutdown")
          scheduler.shutdownNow()
        }
      } catch {
        case e: InterruptedException =>
          logger.warn("🔥 ORIGINAL PrometheusRemoteWriter: Interrupted while waiting for scheduler termination")
          scheduler.shutdownNow()
          Thread.currentThread().interrupt()
      }
      
      // Останавливаем THREAD-SAFE процессор метрик
      // metricsProcessor.shutdown()
      
      // Останавливаем мониторинг потоков
      // ThreadMonitor.stopMonitoring()
      
      // Отправляем финальные метрики при остановке
      logger.info(s"🔥 ORIGINAL PrometheusRemoteWriter: Sending final metrics")
      sendMetrics()
      
      logger.info(s"🔥 ORIGINAL PrometheusRemoteWriter: Stopped and sent final metrics")
    } else {
      logger.warn(s"🔥 ORIGINAL PrometheusRemoteWriter: Already stopped or not running")
    }
  }
  
  /**
   * Отправить метрики в Victoria Metrics из очереди
   */
  private def sendMetrics(): Unit = {
    // Проверяем, что writer еще работает
    if (!running.get()) {
      logger.warn(s"📊 QUEUE: Writer is stopped, skipping metrics sending")
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
