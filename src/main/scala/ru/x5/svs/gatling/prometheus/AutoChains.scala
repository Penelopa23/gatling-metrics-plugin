package ru.x5.svs.gatling.prometheus

import io.gatling.javaapi.core.ChainBuilder
import io.gatling.javaapi.core.Session
import org.slf4j.LoggerFactory
// REMOVED: import ru.x5.svs.gatling.prometheus.application.factory.MetricsServiceFactory
import ru.x5.svs.gatling.prometheus.ConfigurationLoader
import ru.x5.svs.gatling.prometheus.{PrometheusMetricsManager, HttpMetricsCollector, PrometheusRemoteWriter, MetricsQueue}

import java.util.function.Function
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

/**
 * УНИВЕРСАЛЬНЫЕ автоматические Chains для сбора метрик
 * Работает с ЛЮБЫМИ цепочками - автоматически собирает метрики!
 * 
 * Этот класс входит в плагин и доступен автоматически в любом проекте!
 * Просто подключите JAR и используйте AutoChains.withAutoMetrics()
 */
object AutoChains {
  private val logger = LoggerFactory.getLogger(classOf[AutoChains])
  @volatile private var systemStartupLogged = false
  private val systemStarted = new java.util.concurrent.atomic.AtomicBoolean(false)
  
  // ИНИЦИАЛИЗИРУЕМ СИСТЕМУ МЕТРИК ЛЕНИВО ПРИ ПЕРВОМ ИСПОЛЬЗОВАНИИ
  // ensureMetricsSystemStarted() // УБРАНО - инициализация при загрузке класса

  /**
   * УНИВЕРСАЛЬНЫЙ метод для автоматического сбора метрик
   * Обертывает ЛЮБУЮ цепочку и автоматически собирает метрики
   * 
   * @param chainBuilder - любая цепочка Gatling
   * @param scenarioName - имя сценария для метрик
   * @param requestName - имя запроса для метрик
   * @return цепочка с автоматическими метриками
   */
  def withAutoMetrics(chainBuilder: ChainBuilder, scenarioName: String, requestName: String): ChainBuilder = {
    import io.gatling.javaapi.core.CoreDsl._
    // import io.gatling.javaapi.http.HttpDsl._ // REMOVED: unused import
    
    exec(new Function[Session, Session] {
      override def apply(session: Session): Session = {
        // Автоматически запускаем VU отслеживание
        startVirtualUser()
        // Сохраняем время начала для расчета response time
        session.set("startTime", System.currentTimeMillis())
        // Счетчик HTTP запросов в цепочке
        session.set("httpRequestCount", 0)
      }
    })
    .exec(chainBuilder)
    // АВТОМАТИЧЕСКИ СОБИРАЕМ МЕТРИКИ ПОСЛЕ КАЖДОГО HTTP ЗАПРОСА!
    .exec(new Function[Session, Session] {
      override def apply(session: Session): Session = {
        try {
          // Автоматически собираем метрики для каждого HTTP запроса
          collectMetricsAutomatically(session, scenarioName, requestName)
        } catch {
          case e: Exception =>
            logger.error(s"Error collecting metrics for $requestName: ${e.getMessage}", e)
        }
        
        // Автоматически завершаем VU отслеживание
        endVirtualUser()
        session
      }
    })
  }

  /**
   * УНИВЕРСАЛЬНЫЙ метод для автоматического сбора метрик (упрощенная версия)
   * Автоматически определяет имена из сессии Gatling
   * 
   * @param chainBuilder - любая цепочка Gatling
   * @return цепочка с автоматическими метриками
   */
  def withAutoMetrics(chainBuilder: ChainBuilder): ChainBuilder = {
    import io.gatling.javaapi.core.CoreDsl._
    
    exec(new Function[Session, Session] {
      override def apply(session: Session): Session = {
        // 🚀 АВТОМАТИЧЕСКИ ЗАПУСКАЕМ СИСТЕМУ МЕТРИК ПРИ ПЕРВОМ ИСПОЛЬЗОВАНИИ
        ensureMetricsSystemStarted()
        
        // Автоматически запускаем VU отслеживание
        startVirtualUser()
        session
      }
    })
    .exec(chainBuilder)
    .exec(new Function[Session, Session] {
      override def apply(session: Session): Session = {
        try {
          // АВТОМАТИЧЕСКИ определяем имена из сессии Gatling
          // scenarioName = имя сценария Gatling
          val scenarioName = try {
            val scenario = session.scenario()
            if (scenario != null && scenario.nonEmpty) {
              scenario
            } else {
              "UnknownScenario"
            }
          } catch {
            case _: Exception => "UnknownScenario"
          }
          
          // requestName = пытаемся определить из контекста или используем fallback
          val requestName = "UnknownRequest"
          
          logger.info(s"🔥 AUTO-DETECTED: scenario=$scenarioName, request=$requestName")
          logger.warn(s"⚠️ AutoChains.withAutoMetrics() без параметров использует общие имена метрик. " +
            s"Для точных имен используйте AutoChains.withAutoMetrics(chainBuilder, scenarioName, requestName)")
          
          // Автоматически собираем метрики из сессии
          collectMetricsAutomatically(session, scenarioName, requestName)
        } catch {
          case e: Exception =>
            logger.error(s"ERROR in auto metrics collection: ${e.getMessage}", e)
        }
        
        // Автоматически завершаем VU отслеживание
        endVirtualUser()
        session
      }
    })
  }

  /**
   * Специфичный метод для SVS проекта (для обратной совместимости)
   * @deprecated Используйте withAutoMetrics() для универсальности
   */
  def autoVerifySignature(signatureType: String): ChainBuilder = {
    import io.gatling.javaapi.core.CoreDsl._
    import io.gatling.javaapi.http.HttpDsl._
    
    withAutoMetrics(
      feed(jsonFile(signatureType + ".json").circular())
        .exec(http("TC" + signatureType)
          .post("/v1/svs-fk/signatures")
          .body(StringBody("${body.jsonStringify()}"))
          .check(status().is(200))
          .check(jsonPath("$..Result").exists())
          .check(bodyString().saveAs("Response"))
          .check(responseTimeInMillis().saveAs("responseTime"))
          .check(bodyBytes().saveAs("responseSize"))),
      "SVS-Signature-Verification",
      "TC" + signatureType
    )
  }

  /**
   * Автоматический сбор метрик из сессии Gatling
   */
  private def collectMetricsAutomatically(session: Session, scenarioName: String, requestName: String): Unit = {
    try {
      // Получаем реальные данные из сессии (если есть check для response time)
      val responseTime = try {
        val rt = session.getLong("responseTime")
        logger.info(s"✅ [$requestName] SUCCESS: Got response time from session: ${rt}ms")
        rt
      } catch {
        case e: Exception => 
          // Если response time не найден, вычисляем из времени выполнения цепочки
          logger.warn(s"⚠️ [$requestName] responseTime not found in session (${e.getMessage}), calculating from execution time")
          
          // Логируем все доступные ключи в сессии для отладки
          try {
            // В Gatling Java API нет прямого доступа к attributes, используем альтернативный способ
            logger.info(s"🔍 [$requestName] Session debug: isFailed=${session.isFailed}")
          } catch {
            case _: Exception => logger.warn(s"🔍 [$requestName] Could not get session info")
          }
          
          val startTime = try {
            val st = session.getLong("startTime")
            logger.debug(s"[$requestName] Found startTime in session: ${st}ms")
            st
          } catch {
            case e: Exception => 
              // Если startTime тоже не найден, используем текущее время как fallback
              logger.warn(s"⚠️ [$requestName] startTime not found in session (${e.getMessage}), using current time")
              System.currentTimeMillis()
          }
          val currentTime = System.currentTimeMillis()
          val estimatedTime = (currentTime - startTime).toLong
          
          logger.info(s"📊 [$requestName] CALCULATED: startTime=${startTime}ms, currentTime=${currentTime}ms, estimatedTime=${estimatedTime}ms")
          
          if (estimatedTime > 0 && estimatedTime < 30000) {
            logger.info(s"✅ [$requestName] Using calculated response time: ${estimatedTime}ms")
            estimatedTime
          } else {
            // ИСПРАВЛЕНИЕ: Используем реальное время выполнения вместо фиксированного fallback
            val realTime = Math.max(estimatedTime, 1L) // Минимум 1ms, но реальное время
            logger.warn(s"⚠️ [$requestName] Using estimated response time: ${realTime}ms (calculated from execution)")
            realTime
          }
      }
      
      // Получаем размеры данных (с fallback значениями)
      val responseSize = try {
        val responseBytes = session.get("responseSize").asInstanceOf[Array[Byte]]
        if (responseBytes != null) responseBytes.length else 0
      } catch {
        case _: Exception => 0 // Fallback если ключ не найден
      }
      
      val requestSize = try {
        val responseBody = session.getString("Response")
        if (responseBody != null) responseBody.length else 0
      } catch {
        case _: Exception => 0 // Fallback если ключ не найден
      }
      
      // Определяем статус запроса
      val status = if (session.isFailed) "KO" else "OK"
      
      // ДОБАВЛЯЕМ МЕТРИКИ В ОЧЕРЕДЬ ДЛЯ БАТЧЕВОЙ ОТПРАВКИ!
      logger.info(s"[$requestName] Adding HTTP metrics to queue for batch sending")
      MetricsQueue.addHttpRequest(
        scenarioName,
        requestName,
        "HTTP",
        status,
        responseTime
      )
      logger.debug(s"📊 QUEUE: Added HTTP request to queue, responseTime=${responseTime}ms")
      
      // Автоматически записываем детальные метрики ошибок
      logger.debug(s"[$requestName] Status: $status, Session failed: ${session.isFailed}")
      if (status == "KO") {
        // Создаем детальное сообщение об ошибке на основе доступной информации
        val errorMessage = try {
          // Сначала попробуем получить из HTTP ответа (если есть)
          val response = session.getString("Response")
          logger.info(s"[$requestName] Response from session: $response")
          if (response != null && response.nonEmpty) {
            // Попробуем извлечь сообщение об ошибке из JSON
            try {
              import com.fasterxml.jackson.databind.ObjectMapper
              val mapper = new ObjectMapper()
              val jsonNode = mapper.readTree(response)
              val message = jsonNode.get("message")
              if (message != null && !message.isNull) {
                message.asText()
              } else {
                // Если нет поля message, используем весь ответ (обрезанный)
                if (response.length > 500) {
                  response.substring(0, 500) + "..."
                } else {
                  response
                }
              }
            } catch {
              case _: Exception => 
                // Если не JSON или ошибка парсинга, используем весь ответ
                if (response.length > 500) {
                  response.substring(0, 500) + "..."
                } else {
                  response
                }
            }
          } else {
            // Если нет HTTP ответа, попробуем получить информацию об ошибке из сессии
            logger.info(s"[$requestName] No HTTP response, trying session fields...")
            // Попробуем получить информацию об ошибке из сессии
            val errorMsg = try {
              val msg = session.getString("errorMessage")
              logger.info(s"[$requestName] errorMessage: $msg")
              if (msg != null && msg.nonEmpty) msg else null
            } catch {
              case _: Exception => null
            }
            
            if (errorMsg != null) {
              errorMsg
            } else {
              val error = try {
                val err = session.getString("error")
                logger.info(s"[$requestName] error: $err")
                if (err != null && err.nonEmpty) err else null
              } catch {
                case _: Exception => null
              }
              
              if (error != null) {
                error
              } else {
                val exception = try {
                  val exc = session.getString("exception")
                  logger.info(s"[$requestName] exception: $exc")
                  if (exc != null && exc.nonEmpty) exc else null
                } catch {
                  case _: Exception => null
                }
                
                if (exception != null) {
                  exception
                } else {
                  val message = try {
                    val msg = session.getString("message")
                    logger.info(s"[$requestName] message: $msg")
                    if (msg != null && msg.nonEmpty) msg else null
                  } catch {
                    case _: Exception => null
                  }
                  
                  if (message != null) {
                    message
                  } else {
                    // Если ничего не найдено, создаем информативное сообщение
                    logger.info(s"[$requestName] No session fields found, creating fallback message")
                    if (session.isFailed) {
                      "Connection timeout or network error"
                    } else {
                      s"HTTP request failed with status: $status"
                    }
                  }
                }
              }
            }
          }
        } catch {
          case _: Exception => 
            // Если все попытки не удались, используем общую информацию
            if (session.isFailed) {
              "Connection timeout or network error"
            } else {
              "HTTP request failed"
            }
        }
        logger.info(s"[$requestName] Adding HTTP error to queue: $errorMessage")
        MetricsQueue.addHttpError(scenarioName, requestName, "HTTP", status, errorMessage)
      }
      
      // Добавляем метрики итерации в очередь
      MetricsQueue.addIterationDuration(scenarioName, responseTime)
      
      // ПРИНУДИТЕЛЬНО ГЕНЕРИРУЕМ СИСТЕМНЫЕ МЕТРИКИ ПРИ КАЖДОМ ЗАПРОСЕ!
      PrometheusMetricsManager.getInstance.foreach { manager =>
        manager.logSystemMetrics()
      }
      
      // Метрики проверки теперь не нужны - все собирается через очередь
      
      // Note: Detailed HTTP timing is not available in Gatling
      // We only have total response time
      
      logger.info(s"[$requestName] Auto-collected metrics: " +
        s"responseTime=${responseTime}ms, " +
        s"requestSize=${requestSize} bytes, " +
        s"responseSize=${responseSize} bytes, " +
        s"status=$status, " +
        s"sessionFailed=${session.isFailed}")
        
    } catch {
      case e: Exception =>
        logger.error(s"Error collecting metrics for $requestName: ${e.getMessage}", e)
    }
  }

  // REMOVED: serviceLock - not used in original system

  /**
   * 🚀 ОБЕСПЕЧИВАЕМ АВТОМАТИЧЕСКИЙ ЗАПУСК СИСТЕМЫ МЕТРИК
   * Вызывается при первом использовании AutoChains
   */
  def ensureMetricsSystemStarted(): Unit = {
    // ВОЗВРАЩАЕМ ЗАЩИТУ ОТ МНОЖЕСТВЕННОЙ ИНИЦИАЛИЗАЦИИ!
    if (systemStarted.get()) {
      logger.info("🚀 Metrics system already started, skipping initialization")
      return
    }
    
    if (systemStarted.compareAndSet(false, true)) {
      logger.info("🚀 Initializing Gatling Prometheus Plugin...")
      try {
        val config = ConfigurationLoader.loadFromSystem()
        logger.info(s"🚀 Configuration loaded: remoteWriteConfig=${config.remoteWriteConfig}")
        implicit val ec: ExecutionContext = ExecutionContext.global
        
        // ОЧИЩАЕМ ВСЕ МЕТРИКИ ПЕРЕД НАЧАЛОМ НОВОГО ТЕСТА
        MetricsQueue.clearAllMetrics()
        logger.info("🧹 Cleared all metrics before starting new test")
        
        // Инициализируем PrometheusMetricsManager
        val manager = config.remoteWriteConfig match {
          case Some(remoteConfig) =>
            PrometheusMetricsManager.initialize(
              remoteConfig.url,
              config.testConfig.testId,
              config.testConfig.pod
            )
          case None =>
            // Если remote write отключен, используем заглушку
            PrometheusMetricsManager.initialize(
              "http://localhost:8428/api/v1/write", // заглушка
              config.testConfig.testId,
              config.testConfig.pod
            )
        }
        
        // Инициализируем HttpMetricsCollector
        HttpMetricsCollector.initialize()
        
        // Инициализируем PrometheusRemoteWriter только если remote write включен
        val remoteWriter = config.remoteWriteConfig match {
          case Some(remoteConfig) =>
            logger.info(s"🚀 Initializing PrometheusRemoteWriter with URL: ${remoteConfig.url}")
            val writer = new PrometheusRemoteWriter(
              remoteConfig.url,
              config.testConfig.testId,
              config.testConfig.pod,
              remoteConfig.pushIntervalSeconds
            )
            
            // Включаем периодическую отправку метрик
            writer.start()
            logger.info(s"✅ Periodic metrics sending enabled (interval: ${remoteConfig.pushIntervalSeconds}s)")
            Some(writer)
          case None =>
            logger.info("🚀 Remote write disabled - PrometheusRemoteWriter not initialized")
            None
        }
        
        // 1) SIGTERM handler - для Kubernetes Cancel Job (основной путь)
        try {
          import sun.misc.Signal
          Signal.handle(new Signal("TERM"), _ => {
            logger.error("🚨🚨🚨 SIGTERM RECEIVED! Kubernetes Cancel Job detected!")
            logger.error("📤 SIGTERM: Flushing metrics immediately...")
            safeFlushMetrics(remoteWriter)
            logger.error("✅ SIGTERM: Metrics flushed successfully!")
          })
          logger.info("📤 SIGTERM handler registered successfully")
        } catch {
          case e: Exception =>
            logger.warn(s"⚠️ Could not register SIGTERM handler: ${e.getMessage}")
        }
        
        // 2) Shutdown hook - запасной путь для других случаев остановки
        val shutdownHook = new Thread(() => {
          logger.error("🚨🚨🚨 SHUTDOWN HOOK TRIGGERED! Test is stopping...")
          logger.error("📤 ShutdownHook: Sending final metrics...")
          safeFlushMetrics(remoteWriter)
          logger.error("✅ ShutdownHook: Final metrics sent!")
        })
        
        Runtime.getRuntime.addShutdownHook(shutdownHook)
        logger.info("📤 Shutdown hook registered successfully")
        
        // Генерируем системные метрики при старте
        manager.logSystemMetrics()
        
        // Проверяем, настроен ли remote writer (логируем только один раз)
        if (!systemStartupLogged) {
          config.remoteWriteConfig match {
            case Some(remoteConfig) =>
              logger.info(s"✅ Remote writer configured: ${remoteConfig.url}")
            case None =>
              logger.info("📋 Remote write disabled - metrics will be logged only")
          }
          logger.info("🎯 Gatling Prometheus Plugin initialized successfully!")
          systemStartupLogged = true
        }
        
      } catch {
        case e: Exception =>
          logger.error(s"💥 Error initializing metrics system: ${e.getMessage}", e)
      }
    }
  }

  /**
   * Безопасная отправка финальных метрик (используется в SIGTERM и shutdown hook)
   */
  private def safeFlushMetrics(remoteWriter: Option[PrometheusRemoteWriter]): Unit = {
    try {
      // ПРИНУДИТЕЛЬНО останавливаем PrometheusRemoteWriter
      remoteWriter.foreach { writer =>
        logger.error("📤 SAFE FLUSH: Stopping PrometheusRemoteWriter...")
        writer.stop()
        // Даем время на завершение
        Thread.sleep(1000)
        logger.error("📤 SAFE FLUSH: PrometheusRemoteWriter stopped!")
      }
      
      // ДОПОЛНИТЕЛЬНО: принудительно отправляем метрики через PrometheusMetricsManager
      logger.error("📤 SAFE FLUSH: Sending final metrics via PrometheusMetricsManager...")
      PrometheusMetricsManager.sendMetricsOnTestStop()
      logger.error("✅ SAFE FLUSH: Final metrics sent via BOTH PrometheusRemoteWriter AND PrometheusMetricsManager")
      
    } catch {
      case e: Exception =>
        logger.error(s"💥 Error in safeFlushMetrics: ${e.getMessage}", e)
    }
  }

  // Методы для работы с метриками
  private def startVirtualUser(): Unit = {
    // Увеличиваем счетчик активных виртуальных пользователей в очереди
    val currentCount = MetricsQueue.getVirtualUsersCount()
    MetricsQueue.updateVirtualUsersCount(currentCount + 1)
    logger.debug(s"📊 QUEUE VU START: Current VUs: ${currentCount + 1}")
  }

  private def endVirtualUser(): Unit = {
    // Уменьшаем счетчик активных виртуальных пользователей в очереди
    val currentCount = MetricsQueue.getVirtualUsersCount()
    val newCount = Math.max(0, currentCount - 1) // Не даем уйти в минус
    MetricsQueue.updateVirtualUsersCount(newCount)
    logger.debug(s"📊 QUEUE VU END: Current VUs: $newCount")
  }

  // УДАЛЕНЫ: старые методы recordHttpRequest, recordIteration, recordCheck, recordHttpError
  // Теперь все метрики добавляются в MetricsQueue для батчевой отправки
  
  /**
   * Принудительная отправка всех метрик (для использования в конце теста)
   */
  def sendFinalMetrics(): Unit = {
    logger.info("📤 FORCING final metrics send...")
    
    try {
      // Получаем все метрики из очереди
      val queuedMetrics = MetricsQueue.pollAllMetrics()
      val queueStats = MetricsQueue.getQueueStats()
      
      logger.info(s"📤 FORCE: Polled ${queuedMetrics.length} metrics from queue")
      logger.info(s"📤 FORCE: Queue stats: $queueStats")
      
      // Если есть метрики в очереди, отправляем их
      if (queuedMetrics.nonEmpty || queueStats.queueSize > 0) {
        val managerOpt = PrometheusMetricsManager.getInstance
        
        managerOpt.foreach { manager =>
          logger.info(s"📤 FORCE: Processing metrics with manager...")
          
          // Сначала обновляем накопительные метрики в PrometheusMetricsManager
          updateAccumulativeMetrics(manager, queuedMetrics)
          
          // Логируем системные метрики
          manager.logSystemMetrics()
          
          // Создаем Prometheus формат со всеми метриками
          val allMetrics = manager.createPrometheusFormat()
          logger.info(s"📤 FORCE: Created Prometheus format (${allMetrics.length} chars)")
          
          // Отправляем в Victoria Metrics
          manager.sendToVictoriaMetrics(allMetrics)
          logger.info(s"📤 FORCE: Successfully sent batch metrics to Victoria Metrics!")
          
          // Очищаем очередь после успешной отправки
          MetricsQueue.clearQueue()
        }
      } else {
        logger.info(s"📤 FORCE: No metrics in queue, skipping batch send")
      }
      
    } catch {
      case e: Exception =>
        logger.error(s"📤 FORCE: Error sending batch metrics: ${e.getMessage}", e)
    }
  }
  
  /**
   * Обновить накопительные метрики из очереди
   */
  private def updateAccumulativeMetrics(manager: PrometheusMetricsManager, queuedMetrics: Seq[HttpMetric]): Unit = {
    try {
      // Обновляем VU счетчик (используем текущее значение для финальной отправки)
      val vuCount = MetricsQueue.getVirtualUsersCount()
      manager.updateVirtualUsersCount(vuCount)
      
      // Получаем накопительные счетчики
      val httpRequestCounters = MetricsQueue.getHttpRequestCounters()
      val httpErrorCounters = MetricsQueue.getHttpErrorCounters()
      val iterationDurations = MetricsQueue.getIterationDurations()
      
      logger.info(s"📤 FORCE: Updated accumulative metrics - VUs: $vuCount, HTTP requests: ${httpRequestCounters.size}, HTTP errors: ${httpErrorCounters.size}, queued metrics: ${queuedMetrics.length}")
      
      // Обновляем накопительные HTTP метрики в PrometheusMetricsManager
      manager.updateHttpMetricsFromQueue(httpRequestCounters, httpErrorCounters)
      
      // Обновляем итерационные durations
      iterationDurations.foreach { case (scenario, durations) =>
        durations.foreach { duration =>
          manager.recordIterationDuration(scenario, duration)
        }
      }
      
    } catch {
      case e: Exception =>
        logger.error(s"📤 FORCE: Error updating accumulative metrics: ${e.getMessage}", e)
    }
  }

  // Note: Detailed HTTP timing methods are not available in Gatling
  // as we don't have the detailed timing breakdown that k6 provides
}

/**
 * Java-совместимый класс для удобства использования
 */
class AutoChains {
  // Этот класс нужен только для Java совместимости
  // Все методы делегируются к объекту AutoChains
}
