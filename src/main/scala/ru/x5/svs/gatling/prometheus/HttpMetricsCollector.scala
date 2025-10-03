package ru.x5.svs.gatling.prometheus

import org.slf4j.LoggerFactory
import scala.concurrent.ExecutionContext

/**
 * ОРИГИНАЛЬНЫЙ HttpMetricsCollector - собирает HTTP метрики
 */
class HttpMetricsCollector(implicit private val ec: ExecutionContext) {
  
  private val logger = LoggerFactory.getLogger(classOf[HttpMetricsCollector])
  
  /**
   * Собрать HTTP ошибку - С ДЕТАЛЬНЫМИ СООБЩЕНИЯМИ!
   */
  def collectHttpError(scenario: String, request: String, method: String, status: String, errorMessage: String): Unit = {
    logger.info(s"🔥 ORIGINAL WITH DETAILED ERRORS HttpMetricsCollector: Collecting HTTP error: scenario=$scenario, request=$request, method=$method, status=$status, error=$errorMessage")
    
    // Отправляем в оригинальный PrometheusMetricsManager
    PrometheusMetricsManager.getInstance.foreach { manager =>
      manager.recordHttpError(scenario, request, method, status, errorMessage)
    }
  }
  
  /**
   * Собрать HTTP запрос
   */
  def collectHttpRequest(scenario: String, request: String, method: String, status: String): Unit = {
    logger.info(s"🔥 ORIGINAL HttpMetricsCollector: Collecting HTTP request: scenario=$scenario, request=$request, method=$method, status=$status")
    
    // Отправляем в оригинальный PrometheusMetricsManager
    PrometheusMetricsManager.getInstance.foreach { manager =>
      manager.recordHttpRequest(scenario, request, method, status)
    }
  }
}

object HttpMetricsCollector {
  private var instance: Option[HttpMetricsCollector] = None
  
  def initialize()(implicit ec: ExecutionContext): HttpMetricsCollector = {
    instance = Some(new HttpMetricsCollector())
    instance.get
  }
  
  def getInstance: Option[HttpMetricsCollector] = instance
}
