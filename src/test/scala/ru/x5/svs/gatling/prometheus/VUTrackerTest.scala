package ru.x5.svs.gatling.prometheus

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class VUTrackerTest extends AnyFlatSpec with Matchers {
  
  "SimpleVUTracker" should "track virtual users correctly" in {
    val tracker = new SimpleVUTracker()
    
    // Initially should be 0
    tracker.getCurrentVU shouldBe 0
    tracker.getMaxVU shouldBe 0
    
    // Start some users
    tracker.startUser()
    tracker.startUser()
    tracker.startUser()
    
    tracker.getCurrentVU shouldBe 3
    tracker.getMaxVU shouldBe 3
    
    // End one user
    tracker.endUser()
    
    tracker.getCurrentVU shouldBe 2
    tracker.getMaxVU shouldBe 3 // Max should remain 3
    
    // End remaining users
    tracker.endUser()
    tracker.endUser()
    
    tracker.getCurrentVU shouldBe 0
    tracker.getMaxVU shouldBe 3 // Max should still be 3
    
    // Test reset
    tracker.reset()
    tracker.getCurrentVU shouldBe 0
    tracker.getMaxVU shouldBe 0
  }
  
  "PrometheusMetricsManager" should "initialize correctly" in {
    val manager = PrometheusMetricsManager.getInstance()
    
    // Should be able to get instance
    manager should not be null
    
    // Should be able to get VU count (may be 0 or 1 depending on previous tests)
    val currentVU = manager.getCurrentVUCount
    val maxVU = manager.getMaxVUCount
    currentVU should be >= 0
    maxVU should be >= 0
  }
  
  "PrometheusMetricsManager" should "update VU count correctly" in {
    val manager = PrometheusMetricsManager.getInstance()
    
    // Update VU count
    manager.updateVirtualUsersCount(5)
    manager.getCurrentVUCount shouldBe 5
    manager.getMaxVUCount shouldBe 5
    
    // Update to higher count
    manager.updateVirtualUsersCount(10)
    manager.getCurrentVUCount shouldBe 10
    manager.getMaxVUCount shouldBe 10
    
    // Update to lower count (max should remain)
    manager.updateVirtualUsersCount(3)
    manager.getCurrentVUCount shouldBe 3
    manager.getMaxVUCount shouldBe 10 // Max should remain 10
  }
  
  "PrometheusMetricsManager" should "record HTTP requests correctly" in {
    val manager = PrometheusMetricsManager.getInstance()
    
    // Record HTTP request
    manager.recordHttpRequest(
      scenario = "test-scenario",
      requestName = "test-request",
      method = "GET",
      status = "OK",
      expectedBody = "true",
      responseTime = 150L,
      requestLength = 100L,
      responseLength = 200L
    )
    
    // Should not throw exceptions
    manager.getLastUpdateTime should be > 0L
  }
  
  "PrometheusMetricsManager" should "record HTTP errors correctly" in {
    val manager = PrometheusMetricsManager.getInstance()
    
    // Record HTTP error
    manager.recordHttpError(
      scenario = "test-scenario",
      requestName = "test-request",
      method = "GET",
      status = "500",
      errorMessage = "Internal Server Error"
    )
    
    // Should not throw exceptions
    manager.getLastUpdateTime should be > 0L
  }
  
  "PrometheusMetricsManager" should "record iterations correctly" in {
    val manager = PrometheusMetricsManager.getInstance()
    
    // Record iteration
    manager.recordIteration("test-scenario", 1000L)
    
    // Should not throw exceptions
    manager.getLastUpdateTime should be > 0L
  }
  
  "PrometheusMetricsManager" should "record checks correctly" in {
    val manager = PrometheusMetricsManager.getInstance()
    
    // Record check
    manager.recordCheck("test-scenario")
    
    // Should not throw exceptions
    manager.getLastUpdateTime should be > 0L
  }
  
  "HttpMetricsCollector" should "record HTTP metrics correctly" in {
    // Record HTTP request
    HttpMetricsCollector.recordHttpRequest(
      scenario = "test-scenario",
      requestName = "test-request",
      method = "POST",
      status = "OK",
      expectedBody = "true",
      responseTime = 200L,
      requestLength = 150L,
      responseLength = 300L
    )
    
    // Record HTTP error
    HttpMetricsCollector.recordHttpError(
      scenario = "test-scenario",
      requestName = "test-request",
      method = "POST",
      status = "400",
      errorMessage = "Bad Request"
    )
    
    // Record iteration
    HttpMetricsCollector.recordIteration("test-scenario", 1500L)
    
    // Record check
    HttpMetricsCollector.recordCheck("test-scenario")
    
    // Should not throw exceptions
    val manager = PrometheusMetricsManager.getInstance()
    manager.getLastUpdateTime should be > 0L
  }
  
  "PrometheusMetricsManager" should "handle remote writer configuration" in {
    val manager = PrometheusMetricsManager.getInstance()
    
    // Should be able to check remote writer status
    val isConfigured = manager.isRemoteWriterConfigured
    isConfigured shouldBe a[Boolean]
    
    // Should be able to get remote writer URL
    val url = manager.getRemoteWriterUrl
    url shouldBe a[Option[String]]
    
    // Should be able to start and stop
    manager.start()
    manager.stop()
    
    // Should not throw exceptions
    manager.getLastUpdateTime should be > 0L
  }
  
  "PrometheusRemoteWriteProtocol" should "convert metrics to protobuf" in {
    val protocol = new PrometheusRemoteWriteProtocol()
    
    val testMetrics = """
      # HELP gatling_vus Current number of virtual users
      # TYPE gatling_vus gauge
      gatling_vus{testid="test",pod="test-pod"} 5.0
      gatling_http_reqs_total{testid="test",pod="test-pod",scenario="test",name="api",method="GET",status="OK",expected_body="true"} 10.0
    """.trim
    
    val protobufData = protocol.convertTextToProtobuf(testMetrics)
    
    // Should not be empty
    protobufData should not be empty
    
    // Should be a valid byte array
    protobufData shouldBe a[Array[Byte]]
  }
}
