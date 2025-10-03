package ru.x5.svs.gatling.prometheus

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import ru.x5.svs.gatling.prometheus.di.MetricsContainer
import ru.x5.svs.gatling.prometheus.collector.EnhancedMetricsCollector
import ru.x5.svs.gatling.prometheus.processor.EnhancedMetricsProcessor
import ru.x5.svs.gatling.prometheus.exporter.EnhancedMetricsExporter
import ru.x5.svs.gatling.prometheus.config.ConfigurationLoader
import ru.x5.svs.gatling.prometheus.domain.{RequestMetrics, UserMetrics, SystemMetrics}
import io.gatling.javaapi.core.Session
import scala.concurrent.{ExecutionContext, Future}
import java.util.concurrent.Executors

/**
 * Интеграционный тест для улучшенной системы метрик
 * Применяет принципы тестирования в лучших традициях разработки
 * 
 * Принципы:
 * - Single Responsibility: тестирование интеграции компонентов
 * - Open/Closed: открыт для расширения новыми тестами
 * - Liskov Substitution: можно заменить реализации
 * - Interface Segregation: разделение тестовых интерфейсов
 * - Dependency Inversion: зависит от абстракций
 */
class EnhancedMetricsIntegrationTest extends AnyFlatSpec with Matchers with BeforeAndAfterEach {
  
  private var metricsContainer: MetricsContainer = _
  private var metricsCollector: EnhancedMetricsCollector = _
  private var metricsProcessor: EnhancedMetricsProcessor = _
  private var metricsExporter: EnhancedMetricsExporter = _
  private var executor: ExecutionContext = _
  
  override def beforeEach(): Unit = {
    // Создание executor для тестов
    val threadPool = Executors.newFixedThreadPool(4)
    executor = ExecutionContext.fromExecutor(threadPool)
    
    // Создание конфигурации для тестов
    val config = new ConfigurationLoader()
    config.setProperty("penelopa.metrics.async.collection", "true")
    config.setProperty("penelopa.metrics.async.processing", "true")
    config.setProperty("penelopa.metrics.async.export", "true")
    config.setProperty("penelopa.metrics.max.queue.size", "1000")
    config.setProperty("penelopa.remote.write.url", "file:///tmp/test-metrics.txt")
    
    // Создание контейнера
    metricsContainer = new MetricsContainer(config)
    metricsContainer.initialize()
    metricsContainer.start()
    
    // Получение компонентов
    metricsCollector = metricsContainer.getMetricsCollector()
    metricsProcessor = metricsContainer.getMetricsProcessor()
    metricsExporter = metricsContainer.getMetricsExporter()
  }
  
  override def afterEach(): Unit = {
    // Очистка ресурсов
    if (metricsContainer != null) {
      metricsContainer.shutdown()
    }
  }
  
  "EnhancedMetricsCollector" should "collect request metrics correctly" in {
    // Given
    val session = createTestSession()
    val requestName = "test-request"
    val status = "OK"
    val responseTime = 100L
    
    // When
    metricsCollector.collectRequestMetrics(session, requestName, status, responseTime)
    
    // Then
    val stats = metricsCollector.getCollectorStats()
    stats.totalRequests shouldBe 1
    stats.successfulRequests shouldBe 1
    stats.failedRequests shouldBe 0
    stats.averageResponseTime shouldBe responseTime
  }
  
  it should "collect user metrics correctly" in {
    // Given
    val session = createTestSession()
    val userState = "START"
    
    // When
    metricsCollector.collectUserMetrics(session, userState)
    
    // Then
    val stats = metricsCollector.getCollectorStats()
    stats.activeUsers shouldBe 1
    stats.peakUsers shouldBe 1
  }
  
  it should "collect system metrics correctly" in {
    // When
    metricsCollector.collectSystemMetrics()
    
    // Then
    val stats = metricsCollector.getCollectorStats()
    stats.totalRequests shouldBe 0 // Системные метрики не влияют на запросы
  }
  
  "EnhancedMetricsProcessor" should "process request metrics correctly" in {
    // Given
    val metrics = RequestMetrics(
      requestName = "test-request",
      status = "OK",
      responseTime = 100L,
      timestamp = System.currentTimeMillis(),
      sessionId = 1,
      scenario = "test-scenario"
    )
    
    // When
    metricsProcessor.processRequestMetrics(metrics)
    
    // Then
    val aggregatedData = metricsProcessor.getAggregatedData()
    aggregatedData.requestAggregates should have size 1
    aggregatedData.requestAggregates.head.requestName shouldBe "test-request"
    aggregatedData.requestAggregates.head.status shouldBe "OK"
    aggregatedData.requestAggregates.head.count shouldBe 1
  }
  
  it should "process user metrics correctly" in {
    // Given
    val metrics = UserMetrics(
      userId = 1,
      scenario = "test-scenario",
      state = "START",
      timestamp = System.currentTimeMillis()
    )
    
    // When
    metricsProcessor.processUserMetrics(metrics)
    
    // Then
    val aggregatedData = metricsProcessor.getAggregatedData()
    aggregatedData.userAggregates should have size 1
    aggregatedData.userAggregates.head.userId shouldBe 1
    aggregatedData.userAggregates.head.scenario shouldBe "test-scenario"
    aggregatedData.userAggregates.head.state shouldBe "START"
  }
  
  it should "process system metrics correctly" in {
    // Given
    val metrics = Map(
      "memory_used" -> 1024L,
      "cpu_usage" -> 50.0
    )
    
    // When
    metricsProcessor.processSystemMetrics(metrics)
    
    // Then
    val aggregatedData = metricsProcessor.getAggregatedData()
    aggregatedData.systemAggregates should have size 2
  }
  
  "EnhancedMetricsExporter" should "export to Prometheus format correctly" in {
    // Given
    val aggregatedData = createTestAggregatedData()
    
    // When
    val result = metricsExporter.exportToPrometheus(aggregatedData)
    
    // Then
    result.isSuccess shouldBe true
    result.data shouldBe defined
    result.data.get should include("gatling_http_reqs_total")
  }
  
  it should "export to file format correctly" in {
    // Given
    val aggregatedData = createTestAggregatedData()
    val filePath = "/tmp/test-metrics.txt"
    
    // When
    val result = metricsExporter.exportToFile(aggregatedData, filePath)
    
    // Then
    result.isSuccess shouldBe true
    result.data shouldBe defined
    result.data.get should include("Gatling Metrics Export")
  }
  
  "MetricsContainer" should "initialize correctly" in {
    // Then
    metricsContainer should not be null
    val stats = metricsContainer.getSystemStats()
    stats.isInitialized shouldBe true
    stats.isRunning shouldBe true
  }
  
  it should "handle lifecycle correctly" in {
    // Given
    val stats = metricsContainer.getSystemStats()
    stats.isInitialized shouldBe true
    stats.isRunning shouldBe true
    
    // When
    metricsContainer.stop()
    
    // Then
    val stoppedStats = metricsContainer.getSystemStats()
    stoppedStats.isRunning shouldBe false
  }
  
  "EnhancedAutoChains" should "work with enhanced metrics system" in {
    // Given
    val session = createTestSession()
    
    // When
    EnhancedAutoChains.collectSystemMetrics()
    
    // Then
    val stats = EnhancedAutoChains.getSystemStats()
    stats.isInitialized shouldBe true
    stats.isStarted shouldBe true
  }
  
  it should "handle shutdown correctly" in {
    // When
    EnhancedAutoChains.shutdown()
    
    // Then
    val stats = EnhancedAutoChains.getSystemStats()
    stats.isInitialized shouldBe false
    stats.isStarted shouldBe false
  }
  
  "Integration" should "work end-to-end" in {
    // Given
    val session = createTestSession()
    val requestName = "integration-test"
    val status = "OK"
    val responseTime = 150L
    
    // When - сбор метрик
    metricsCollector.collectRequestMetrics(session, requestName, status, responseTime)
    metricsCollector.collectUserMetrics(session, "START")
    metricsCollector.collectSystemMetrics()
    
    // When - обработка метрик
    val requestMetrics = RequestMetrics(
      requestName = requestName,
      status = status,
      responseTime = responseTime,
      timestamp = System.currentTimeMillis(),
      sessionId = session.userId,
      scenario = session.scenario
    )
    metricsProcessor.processRequestMetrics(requestMetrics)
    
    // When - экспорт метрик
    val aggregatedData = metricsProcessor.getAggregatedData()
    val exportResult = metricsExporter.exportToPrometheus(aggregatedData)
    
    // Then
    exportResult.isSuccess shouldBe true
    exportResult.data shouldBe defined
    exportResult.data.get should include("gatling_http_reqs_total")
    exportResult.data.get should include("integration-test")
  }
  
  // Вспомогательные методы
  
  private def createTestSession(): Session = {
    Session(
      scenario = "test-scenario",
      userId = 1,
      attributes = Map.empty
    )
  }
  
  private def createTestAggregatedData(): ru.x5.svs.gatling.prometheus.processor.AggregatedMetricsData = {
    import ru.x5.svs.gatling.prometheus.processor._
    
    val requestAggregates = Seq(
      RequestAggregate(
        requestName = "test-request",
        status = "OK",
        count = 1,
        totalResponseTime = 100L,
        minResponseTime = 100L,
        maxResponseTime = 100L,
        lastUpdate = System.currentTimeMillis()
      )
    )
    
    val userAggregates = Seq(
      UserAggregate(
        userId = 1,
        scenario = "test-scenario",
        state = "START",
        sessionCount = 1,
        lastActivity = System.currentTimeMillis(),
        sessionData = Map.empty
      )
    )
    
    val systemAggregates = Seq(
      SystemAggregate(
        metricName = "memory_used",
        value = 1024L,
        count = 1,
        lastUpdate = System.currentTimeMillis()
      )
    )
    
    val processingStats = ProcessingStats(
      processedRequests = 1,
      processedUsers = 1,
      processingErrors = 0,
      processingDurationMs = 1000L,
      processingRate = 1.0
    )
    
    AggregatedMetricsData(
      requestAggregates = requestAggregates,
      userAggregates = userAggregates,
      systemAggregates = systemAggregates,
      processingStats = processingStats
    )
  }
}
