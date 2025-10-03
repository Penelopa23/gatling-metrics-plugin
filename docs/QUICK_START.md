# Быстрый старт с Gatling Prometheus Plugin

## Что изменилось

После рефакторинга использование плагина стало **намного проще**! Теперь не нужно вручную вызывать методы сбора метрик.

## Новый способ использования

### 1. Подключите JAR файл
```bash
cp target/scala-2.13/gatling-prometheus-plugin_2.13-1.0.0.jar /path/to/your/gatling/project/lib/
```

### 2. Оберните цепочку в AutoChains
```scala
import ru.x5.svs.gatling.prometheus.AutoChains
import io.gatling.javaapi.core.CoreDsl._
import io.gatling.javaapi.http.HttpDsl._

val scenario = scenario("My Test")
  .exec(
    AutoChains.withAutoMetrics(
      http("API Request")
        .get("/api/endpoint")
        .check(status().is(200))
    )
  )
```

### 3. Запустите тест
Метрики автоматически собираются и отправляются в Prometheus/VictoriaMetrics!

## Что происходит автоматически

- ✅ **VU отслеживание** - автоматически считает виртуальных пользователей
- ✅ **HTTP метрики** - время ответа, размеры данных, статусы
- ✅ **Итерации** - количество и длительность итераций
- ✅ **Проверки** - количество успешных/неуспешных проверок
- ✅ **Память** - использование памяти JVM
- ✅ **Экспорт** - автоматическая отправка в VictoriaMetrics

## Настройка экспорта (опционально)

```bash
export PROMETHEUS_REMOTE_WRITE_URL="http://victoriametrics:8428/api/v1/write"
export PROMETHEUS_PUSH_INTERVAL="10"
```

## Преимущества нового способа

- **Меньше кода** - не нужно вручную вызывать методы
- **Без ошибок** - нельзя забыть добавить метрики
- **Автоматически** - все работает из коробки
- **Чище** - код тестов более читаемый

## Обратная совместимость

Старый код продолжает работать без изменений:
```scala
val manager = PrometheusMetricsManager.getInstance()
manager.start()
// ... старый код работает как раньше
```

## Миграция

Просто замените ваши цепочки на:
```scala
// Было:
.exec(http("Request").get("/api"))

// Стало:
.exec(AutoChains.withAutoMetrics(http("Request").get("/api")))
```

**Не забудьте добавить импорт:**
```scala
import ru.x5.svs.gatling.prometheus.AutoChains
```

Вот и все! 🎉
