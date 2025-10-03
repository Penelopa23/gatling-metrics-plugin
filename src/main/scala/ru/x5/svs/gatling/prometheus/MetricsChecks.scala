package ru.x5.svs.gatling.prometheus

import io.gatling.javaapi.http.HttpRequestActionBuilder
import io.gatling.javaapi.core.CheckBuilder
import io.gatling.javaapi.http.HttpDsl._
import io.gatling.javaapi.core.CoreDsl._

/**
 * Утилитный класс для сбора метрик HTTP запросов
 * Содержит готовые проверки для извлечения данных из ответа
 */
object MetricsChecks {

  /**
   * Добавляет проверки для сбора метрик к HTTP запросу
   * 
   * @param requestBuilder HTTP запрос к которому добавляем проверки
   * @return HTTP запрос с добавленными проверками для сбора метрик
   */
  def sendMetrics(requestBuilder: HttpRequestActionBuilder): HttpRequestActionBuilder = {
    requestBuilder
      // СБИРАЕМ ДАННЫЕ ДЛЯ МЕТРИК - ВСЕГДА, ДАЖЕ ПРИ ОШИБКАХ!
      .check(responseTimeInMillis().saveAs("responseTime"))
      .check(bodyBytes().saveAs("responseSize"))
      .check(bodyString().saveAs("Response")) // Сохраняем ответ ПЕРЕД проверками
      .check(status().saveAs("statusCode"))
  }
}
