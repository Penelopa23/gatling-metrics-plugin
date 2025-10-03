package ru.x5.svs.gatling.prometheus

import org.slf4j.LoggerFactory

/**
 * Загрузчик конфигурации для плагина
 */
object ConfigurationLoader {
  private val logger = LoggerFactory.getLogger(classOf[ConfigurationLoader.type])
  
  case class TestConfig(testId: String, pod: String, environment: String)
  case class RemoteWriteConfig(url: String, pushIntervalSeconds: Int, batchSize: Int, timeoutSeconds: Int)
  case class Config(testConfig: TestConfig, remoteWriteConfig: Option[RemoteWriteConfig])
  
  def loadFromSystem(): Config = {
    logger.info("Loading configuration from system properties and environment variables")
    
    // Загружаем конфигурацию теста
    val testConfig = loadTestConfig()
    
    // Загружаем конфигурацию remote write
    val remoteWriteConfig = loadRemoteWriteConfig()
    
    Config(testConfig, remoteWriteConfig)
  }
  
  private def loadTestConfig(): TestConfig = {
    logger.debug("Loading test configuration...")
    
    val testId = Option(System.getProperty("penelopa.testid"))
      .orElse(Option(System.getenv("PENELOPA_TESTID")))
      .getOrElse("PenelopaTestId")
    
    val pod = Option(System.getProperty("penelopa.pod"))
      .orElse(Option(System.getenv("PENELOPA_POD")))
      .orElse(Option(System.getenv("HOSTNAME")))  // Kubernetes pod name
      .orElse(Option(System.getenv("KUBERNETES_POD_NAME")))  // Alternative Kubernetes variable
      .getOrElse("PenelopaPod")
    
    val environment = Option(System.getProperty("penelopa.environment"))
      .orElse(Option(System.getenv("PENELOPA_ENVIRONMENT")))
      .getOrElse("default")
    
    logger.debug(s"🔍 Test configuration loaded: testId=$testId, pod=$pod, environment=$environment")
    
    TestConfig(testId, pod, environment)
  }
  
  private def loadRemoteWriteConfig(): Option[RemoteWriteConfig] = {
    logger.debug("🔍 Loading remote write configuration...")
    
    val enabled = Option(System.getProperty("penelopa.remote.write.enabled"))
      .orElse(Option(System.getenv("PENELOPA_REMOTE_WRITE_ENABLED")))
      .getOrElse("true")
      .toLowerCase == "true"
    
    if (!enabled) {
      logger.info("📋 Remote write DISABLED")
      return None
    }
    
    val url = Option(System.getProperty("penelopa.remote.write.url"))
      .orElse(Option(System.getenv("PENELOPA_REMOTE_WRITE_URL")))
      .getOrElse("http://vms-victoria-metrics-single-victoria-server.metricstest:8428/api/v1/import/prometheus")
    
    val pushInterval = Option(System.getProperty("penelopa.remote.write.interval"))
      .orElse(Option(System.getenv("PENELOPA_REMOTE_WRITE_INTERVAL")))
      .map(_.toInt)
      .getOrElse(5)
    
    val batchSize = Option(System.getProperty("penelopa.remote.write.batch.size"))
      .orElse(Option(System.getenv("PENELOPA_REMOTE_WRITE_BATCH_SIZE")))
      .map(_.toInt)
      .getOrElse(1000)
    
    val timeout = Option(System.getProperty("penelopa.remote.write.timeout"))
      .orElse(Option(System.getenv("PENELOPA_REMOTE_WRITE_TIMEOUT")))
      .map(_.toInt)
      .getOrElse(30)
    
    logger.info(s"✅ Remote write ENABLED with URL: $url (default)")
    logger.debug(s"🔍 Remote write config: interval=$pushInterval, batchSize=$batchSize, timeout=$timeout, headers=0")
    logger.debug(s"🔍 Default values: interval=5, batchSize=1000, timeout=30")
    
    Some(RemoteWriteConfig(url, pushInterval, batchSize, timeout))
  }
}
