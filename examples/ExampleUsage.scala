import ru.x5.svs.gatling.prometheus.{PrometheusMetricsManager, SimpleVUTracker, HttpMetricsCollector, PrometheusRemoteWriter}

/**
 * Пример использования плагина для отслеживания всех метрик Gatling
 * с отправкой в VictoriaMetrics
 */
object ExampleUsage {
  
  def main(args: Array[String]): Unit = {
    println("=== Пример использования Gatling Prometheus Plugin ===")
    
    // Настройка отправки в VictoriaMetrics (опционально)
    // Установите переменную окружения PROMETHEUS_REMOTE_WRITE_URL
    // Например: export PROMETHEUS_REMOTE_WRITE_URL="http://victoriametrics:8428/api/v1/write"
    
    // Создаем VU tracker
    val tracker = new SimpleVUTracker()
    val manager = PrometheusMetricsManager.getInstance()
    
    // Запускаем систему метрик (включая remote writer если настроен)
    manager.start()
    
    // Проверяем конфигурацию remote writer
    if (manager.isRemoteWriterConfigured) {
      println(s"✅ Remote writer настроен: ${manager.getRemoteWriterUrl.getOrElse("unknown")}")
      println("   Метрики будут автоматически отправляться в VictoriaMetrics")
    } else {
      println("⚠️  Remote writer не настроен")
      println("   Для отправки в VictoriaMetrics установите переменную окружения:")
      println("   export PROMETHEUS_REMOTE_WRITE_URL=\"http://victoriametrics:8428/api/v1/write\"")
    }
    
    // Симулируем запуск пользователей
    println("\n1. Запускаем 5 пользователей...")
    for (i <- 1 to 5) {
      tracker.startUser()
      println(s"   Пользователь $i запущен. Текущих VU: ${tracker.getCurrentVU}")
    }
    
    // Проверяем метрики Prometheus
    println(s"\n2. Prometheus метрики:")
    println(s"   Текущих VU: ${manager.getCurrentVUCount}")
    println(s"   Максимум VU: ${manager.getMaxVUCount}")
    
    // Симулируем HTTP запросы
    println("\n3. Симулируем HTTP запросы...")
    for (i <- 1 to 10) {
      HttpMetricsCollector.recordHttpRequest(
        scenario = "example-scenario",
        requestName = s"request-$i",
        method = if (i % 2 == 0) "GET" else "POST",
        status = if (i % 10 == 0) "ERROR" else "OK",
        expectedBody = "true",
        responseTime = 100 + (i * 10),
        requestLength = 50 + (i * 5),
        responseLength = 100 + (i * 10)
      )
      
      // Симулируем итерацию
      HttpMetricsCollector.recordIteration("example-scenario", 1000 + (i * 100))
      
      // Симулируем проверку
      HttpMetricsCollector.recordCheck("example-scenario")
    }
    
    // Симулируем HTTP ошибки
    println("\n4. Симулируем HTTP ошибки...")
    HttpMetricsCollector.recordHttpError(
      scenario = "example-scenario",
      requestName = "error-request",
      method = "GET",
      status = "500",
      errorMessage = "Internal Server Error"
    )
    
    // Симулируем завершение некоторых пользователей
    println("\n5. Завершаем 2 пользователей...")
    tracker.endUser()
    tracker.endUser()
    println(s"   Текущих VU: ${tracker.getCurrentVU}")
    println(s"   Prometheus VU: ${manager.getCurrentVUCount}")
    
    // Завершаем всех пользователей
    println("\n6. Завершаем всех пользователей...")
    while (tracker.getCurrentVU > 0) {
      tracker.endUser()
    }
    println(s"   Текущих VU: ${tracker.getCurrentVU}")
    println(s"   Prometheus VU: ${manager.getCurrentVUCount}")
    println(s"   Максимум VU: ${manager.getMaxVUCount}")
    
    // Показываем финальные метрики
    println(s"\n7. Финальные метрики:")
    println(s"   Последнее обновление: ${manager.getLastUpdateTime}")
    println(s"   Внутренний счетчик VU: ${manager.getCurrentVirtualUsers}")
    println(s"   Максимальный VU: ${manager.getMaxVirtualUsers}")
    
    println("\n=== Пример завершен ===")
    println("Все метрики записаны в Prometheus:")
    println("- gatling_vus (текущие VU)")
    println("- gatling_vus_max (максимальные VU)")
    println("- gatling_http_reqs_total (HTTP запросы)")
    println("- gatling_http_req_failed (неудачные запросы)")
    println("- gatling_http_errors (HTTP ошибки)")
    println("- gatling_iterations_total (итерации)")
    println("- gatling_checks (проверки)")
    println("- gatling_data_sent/received (данные)")
    println("- gatling_memory_* (метрики памяти)")
    
    // Останавливаем систему метрик
    manager.stop()
    println("\n🛑 Система метрик остановлена")
  }
}
