# Gatling Prometheus Plugin

Плагин для Gatling, который обеспечивает точное отслеживание виртуальных пользователей (VU) и экспорт метрик в Prometheus с использованием принципов чистой архитектуры и SOLID.

## 🚀 Быстрый старт

1. **Подключите JAR файл** к вашему проекту Gatling
2. **Оберните цепочку** в `AutoChains.withAutoMetrics()`
3. **Запустите тест** - метрики собираются автоматически!

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

// Метрики автоматически отправляются в Prometheus/VictoriaMetrics!
```

## 🏗️ Архитектура

Проект построен согласно принципам **Clean Architecture** и **SOLID**:

### Структура пакетов

```
src/main/scala/ru/x5/svs/gatling/prometheus/
├── domain/                          # Доменный слой
│   ├── Metric.scala                 # Доменные модели метрик
│   ├── Configuration.scala          # Доменные модели конфигурации
│   ├── repository/                  # Интерфейсы репозиториев
│   │   └── MetricRepository.scala
│   └── service/                     # Доменные сервисы
│       ├── MetricCollector.scala
│       ├── MetricExporter.scala
│       └── VirtualUserTracker.scala
├── application/                     # Слой приложения
│   ├── service/                     # Сервисы приложения
│   │   └── MetricsApplicationService.scala
│   └── factory/                     # Фабрики
│       └── MetricsServiceFactory.scala
├── infrastructure/                  # Инфраструктурный слой
│   ├── prometheus/                  # Prometheus реализации
│   │   ├── PrometheusMetricRepository.scala
│   │   └── PrometheusMetricCollector.scala
│   ├── remote/                      # Удаленный экспорт
│   │   ├── PrometheusRemoteExporter.scala
│   │   └── protobuf/
│   │       └── ProtobufSerializer.scala
│   ├── tracking/                    # Отслеживание VU
│   │   └── PrometheusVirtualUserTracker.scala
│   └── config/                      # Конфигурация
│       └── ConfigurationLoader.scala
├── presentation/                    # Слой представления
│   └── adapter/                     # Адаптеры для совместимости
│       ├── LegacyMetricsManager.scala
│       ├── LegacyHttpMetricsCollector.scala
│       └── LegacyGlobalVUTracker.scala
├── AutoChains.scala                 # 🚀 Автоматические цепочки (пользовательский API)
├── AutoHttpProtocol.scala           # 🌐 HTTP протокол (пользовательский API)
└── AutoHttpUtils.scala              # 🔧 HTTP утилиты (пользовательский API)
```

### Принципы SOLID

#### 1. Single Responsibility Principle (SRP)
- **MetricRepository** - только хранение и получение метрик
- **MetricCollector** - только сбор метрик
- **MetricExporter** - только экспорт метрик
- **VirtualUserTracker** - только отслеживание VU

#### 2. Open/Closed Principle (OCP)
- Легко добавлять новые типы метрик через наследование от `Metric`
- Новые экспортеры реализуют интерфейс `MetricExporter`
- Новые коллекторы реализуют интерфейс `MetricCollector`

#### 3. Liskov Substitution Principle (LSP)
- Все реализации интерфейсов полностью заменяемы
- `PrometheusMetricRepository` может быть заменен на любую другую реализацию

#### 4. Interface Segregation Principle (ISP)
- Интерфейсы разделены по функциональности
- Клиенты зависят только от нужных им методов

#### 5. Dependency Inversion Principle (DIP)
- Высокоуровневые модули не зависят от низкоуровневых
- Все зависимости инвертированы через интерфейсы

### Clean Architecture

#### Доменный слой (Domain Layer)
- **Сущности**: `Metric`, `TestConfiguration`, `RemoteWriteConfiguration`
- **Интерфейсы**: `MetricRepository`, `MetricCollector`, `MetricExporter`, `VirtualUserTracker`
- **Бизнес-логика**: Определение типов метрик и их поведения

#### Слой приложения (Application Layer)
- **Сервисы**: `MetricsApplicationService` - оркестрирует доменные сервисы
- **Фабрики**: `MetricsServiceFactory` - создает и настраивает зависимости
- **Use Cases**: Управление жизненным циклом метрик

#### Инфраструктурный слой (Infrastructure Layer)
- **Репозитории**: `PrometheusMetricRepository` - хранение в Prometheus
- **Экспортеры**: `PrometheusRemoteExporter` - отправка в VictoriaMetrics
- **Коллекторы**: `PrometheusMetricCollector` - сбор метрик
- **Конфигурация**: `ConfigurationLoader` - загрузка настроек

#### Слой представления (Presentation Layer)
- **Адаптеры**: Обеспечивают обратную совместимость
- **Legacy API**: Старые интерфейсы делегируют к новой архитектуре

## 🚀 Особенности

- ✅ **Супер простое использование** - просто оберните цепочку в `AutoChains.withAutoMetrics()`
- ✅ **Автоматический сбор метрик** - никаких ручных вызовов, все работает автоматически
- ✅ **Чистая архитектура** - разделение на слои с четкими границами
- ✅ **SOLID принципы** - легко тестируемый и расширяемый код
- ✅ **Обратная совместимость** - существующий код работает без изменений
- ✅ **Точное отслеживание VU** через события UserStart/UserEnd
- ✅ **Экспорт метрик в Prometheus** с правильными именами (gatling_*)
- ✅ **Совместимость с k6** - метрики называются аналогично k6_*
- ✅ **Поддержка всех метрик Gatling** (HTTP запросы, итерации, ошибки)
- ✅ **Простая интеграция** - просто добавьте JAR в classpath

## 📦 Установка

1. Соберите плагин:
```bash
sbt package
```

2. Скопируйте JAR в ваш проект Gatling:
```bash
cp target/scala-2.13/gatling-prometheus-plugin_2.13-1.0.0.jar /path/to/your/gatling/project/lib/
```

3. Настройте отправку в VictoriaMetrics (опционально):
```bash
export PROMETHEUS_REMOTE_WRITE_URL="http://victoriametrics:8428/api/v1/write"
export PROMETHEUS_PUSH_INTERVAL="10"  # интервал отправки в секундах
export PROMETHEUS_AUTH_TOKEN="your-token"  # если нужна аутентификация
```

## 💻 Использование

### Простое использование (Рекомендуемый способ)

Просто подключите JAR файл и используйте автоматические методы:

```scala
import ru.x5.svs.gatling.prometheus.{AutoChains, AutoHttpUtils}
import io.gatling.javaapi.core.CoreDsl._
import io.gatling.javaapi.http.HttpDsl._

// 1. Создайте HTTP запрос с автоматическими метриками
val httpRequest = http("My API Request")
  .get("/api/endpoint")
  .check(status().is(200))
  .check(jsonPath("$.result").exists())

// 2. Добавьте автоматические метрики к запросу
val httpRequestWithMetrics = AutoHttpUtils.withAutoMetrics(httpRequest)

// 3. Оберните цепочку в AutoChains для автоматического сбора метрик
val scenario = scenario("My Test Scenario")
  .exec(
    AutoChains.withAutoMetrics(
      httpRequestWithMetrics,
      scenarioName = "MyTestScenario",
      requestName = "MyAPIRequest"
    )
  )

// 4. Запустите тест - метрики будут собираться автоматически!
setUp(scenario.injectOpen(atOnceUsers(10)))
  .protocols(http.baseUrl("http://localhost:8080"))
```

### Еще более простой способ

```scala
import ru.x5.svs.gatling.prometheus.AutoChains
import io.gatling.javaapi.core.CoreDsl._
import io.gatling.javaapi.http.HttpDsl._

// Просто оберните любую цепочку в AutoChains.withAutoMetrics()
val scenario = scenario("Simple Test")
  .exec(
    AutoChains.withAutoMetrics(
      http("Simple Request")
        .get("/api/simple")
        .check(status().is(200))
    )
  )

// Метрики будут собираться автоматически с именами "AutoDetectedScenario" и "AutoDetected"
```

### Базовое использование (Legacy API)

Если нужен более детальный контроль:

```scala
import ru.x5.svs.gatling.prometheus.{PrometheusMetricsManager, SimpleVUTracker, HttpMetricsCollector}

// Запустите систему метрик
val manager = PrometheusMetricsManager.getInstance()
manager.start()

// Создайте VU tracker
val tracker = new SimpleVUTracker()

// Отслеживайте пользователей
tracker.startUser()  // Увеличивает счетчик VU
tracker.endUser()    // Уменьшает счетчик VU

// Записывайте HTTP метрики
HttpMetricsCollector.recordHttpRequest(
  scenario = "test", requestName = "api", method = "GET",
  status = "OK", expectedBody = "true", responseTime = 150L,
  requestLength = 100L, responseLength = 200L
)

// Получайте метрики
println(s"Текущих VU: ${manager.getCurrentVUCount}")
println(s"Максимум VU: ${manager.getMaxVUCount}")

// Остановите систему метрик
manager.stop()
```

### Использование новой архитектуры

```scala
import ru.x5.svs.gatling.prometheus.application.factory.MetricsServiceFactory
import ru.x5.svs.gatling.prometheus.infrastructure.config.ConfigurationLoader
import scala.concurrent.ExecutionContext.Implicits.global

// Загрузите конфигурацию
val config = ConfigurationLoader.loadFromSystem()

// Создайте сервис приложения
val metricsService = MetricsServiceFactory.createApplicationService(config)

// Инициализируйте и запустите
metricsService.start(config.testConfig)

// Используйте сервис
metricsService.recordHttpRequest(
  scenario = "test", requestName = "api", method = "GET",
  status = "OK", expectedBody = "true", responseTime = 150L,
  requestLength = 100L, responseLength = 200L
)

metricsService.startVirtualUser()
metricsService.endVirtualUser()

// Остановите сервис
metricsService.stop()
```

### Создание кастомных метрик

```scala
import ru.x5.svs.gatling.prometheus.domain.{CounterMetric, GaugeMetric}
import java.time.Instant

// Создайте кастомную метрику
val customMetric = CounterMetric(
  name = "custom_requests_total",
  labels = Map("service" -> "my-service", "version" -> "1.0"),
  value = 1.0,
  timestamp = Instant.now()
)

// Запишите метрику
metricsService.collectCustomMetric(customMetric)
```

## 📊 Метрики

Плагин экспортирует все метрики из основного проекта Gatling, точно совпадающие с k6:

### Виртуальные пользователи (точные значения)
- `gatling_vus` - текущее количество активных VU
- `gatling_vus_max` - максимальное количество VU за время теста

### HTTP метрики
- `gatling_http_reqs_total` - общее количество HTTP запросов
- `gatling_http_req_failed` - количество неудачных запросов
- `gatling_http_errors` - HTTP ошибки с деталями
- `gatling_http_req_duration` - длительность HTTP запросов (мс)
- `gatling_http_req_waiting` - время ожидания HTTP запросов (мс)
- `gatling_http_req_blocked` - время блокировки HTTP запросов (мс)
- `gatling_http_req_sending` - время отправки HTTP запросов (мс)
- `gatling_http_req_receiving` - время получения HTTP запросов (мс)
- `gatling_http_req_tls_handshaking` - время TLS handshake (мс)

### Итерации и проверки
- `gatling_iterations_total` - общее количество итераций
- `gatling_iteration_duration` - длительность итераций (мс)
- `gatling_dropped_duration` - длительность отброшенных итераций (мс)
- `gatling_checks` - количество проверок

### Данные
- `gatling_data_sent` - отправленные данные (байты)
- `gatling_data_received` - полученные данные (байты)

### Метрики памяти
- `gatling_memory_alloc_bytes` - выделенная память (байты)
- `gatling_memory_heap_alloc_bytes` - выделенная heap память (байты)
- `gatling_memory_heap_sys_bytes` - системная heap память (байты)
- `gatling_memory_heap_idle_bytes` - неиспользуемая heap память (байты)
- `gatling_memory_heap_inuse_bytes` - используемая heap память (байты)
- `gatling_memory_stack_inuse_bytes` - используемая stack память (байты)
- `gatling_memory_stack_sys_bytes` - системная stack память (байты)
- `gatling_memory_gc_cpu_fraction` - доля CPU для GC
- `gatling_memory_gc_pause_ns` - время паузы GC (наносекунды)
- `gatling_memory_gc_count` - количество циклов GC
- `gatling_memory_objects` - количество объектов в памяти

## 🔧 Отправка в VictoriaMetrics

Плагин автоматически отправляет метрики в VictoriaMetrics в формате Prometheus text format:

### Настройка

1. **Установите переменную окружения**:
```bash
export PROMETHEUS_REMOTE_WRITE_URL="http://victoriametrics:8428/api/v1/write"
```

2. **Дополнительные настройки** (опционально):
```bash
export PROMETHEUS_PUSH_INTERVAL="10"  # интервал отправки в секундах
export PROMETHEUS_AUTH_TOKEN="your-token"  # токен аутентификации
export PROMETHEUS_USERNAME="user"  # имя пользователя
export PROMETHEUS_PASSWORD="pass"  # пароль
```

3. **Плагин автоматически**:
   - Генерирует метрики в Prometheus text format
   - Отправляет в VictoriaMetrics через HTTP POST
   - Поддерживает graceful shutdown с отправкой последних метрик

### Формат отправки

Метрики отправляются в стандартном Prometheus text format:
- **Content-Type**: `text/plain; charset=utf-8`
- **X-Prometheus-Remote-Write-Version**: `0.1.0`

### VictoriaMetrics Endpoint

VictoriaMetrics принимает Prometheus text format на endpoint `/api/v1/import/prometheus`. Это стандартный способ импорта метрик Prometheus в VictoriaMetrics.

## 📈 Использование в Grafana

После установки плагина и настройки VictoriaMetrics вы сможете использовать точные метрики VU:

```promql
# Текущее количество VU
sum(gatling_vus{testid=~"$testid"})

# Максимальное количество VU
sum(gatling_vus_max{testid=~"$testid"})

# HTTP запросы в секунду
sum(irate(gatling_http_reqs_total{testid=~"$testid"}[$__interval]))

# Ошибки HTTP запросов
sum(rate(gatling_http_req_failed{testid=~"$testid"}[5m]))

# Использование памяти
sum(gatling_memory_heap_inuse_bytes{testid=~"$testid"}) / 1024 / 1024
```

## 🧪 Разработка

### Сборка
```bash
sbt compile
```

### Тестирование
```bash
sbt test
```

### Создание JAR
```bash
sbt assembly
```

### Архитектурные принципы

#### Расширение функциональности

1. **Добавление новых типов метрик**:
```scala
case class CustomMetric(
  name: String,
  labels: Map[String, String],
  value: Double,
  timestamp: Instant = Instant.now()
) extends Metric
```

2. **Создание новых экспортеров**:
```scala
class CustomExporter extends MetricExporter {
  // Реализация интерфейса
}
```

3. **Добавление новых коллекторов**:
```scala
class CustomCollector extends MetricCollector {
  // Реализация интерфейса
}
```

#### Тестирование

Архитектура позволяет легко тестировать каждый слой:

```scala
// Тестирование доменного слоя
class MetricTest extends AnyFunSuite {
  test("should create HTTP request metric") {
    val metric = HttpRequestMetric(
      scenario = "test", requestName = "api", method = "GET",
      status = "OK", expectedBody = "true", responseTime = 150L,
      requestLength = 100L, responseLength = 200L
    )
    assert(metric.name == "gatling_http_reqs_total")
  }
}

// Тестирование с моками
class MetricsApplicationServiceTest extends AnyFunSuite {
  test("should record HTTP request") {
    val mockRepository = mock[MetricRepository]
    val mockCollector = mock[MetricCollector]
    val service = new MetricsApplicationService(mockRepository, mockCollector, ...)
    
    service.recordHttpRequest("test", "api", "GET", "OK", "true", 150L, 100L, 200L)
    
    verify(mockCollector).collectHttpRequest(...)
  }
}
```

## 🔄 Миграция

### С существующего кода

Существующий код продолжает работать без изменений благодаря адаптерам:

```scala
// Старый код - работает как раньше
val manager = PrometheusMetricsManager.getInstance()
manager.start()
manager.recordHttpRequest(...)

// НОВЫЙ ПРОСТОЙ СПОСОБ - просто оберните цепочку!
val scenario = scenario("My Test")
  .exec(
    AutoChains.withAutoMetrics(
      http("API Request")
        .get("/api/endpoint")
        .check(status().is(200))
    )
  )
```

### Постепенная миграция

1. **Фаза 1**: Используйте Legacy API (текущее состояние)
2. **Фаза 2**: Переходите на `AutoChains.withAutoMetrics()` - намного проще!
3. **Фаза 3**: При необходимости используйте новую архитектуру напрямую

### Преимущества нового способа

- **Меньше кода** - не нужно вручную вызывать методы сбора метрик
- **Автоматически** - все метрики собираются автоматически
- **Без ошибок** - нельзя забыть добавить метрики
- **Чище** - код тестов становится более читаемым

## 🏷️ Совместимость

- Gatling 3.10.3+
- Scala 2.13
- Java 11+
- Prometheus 2.0+
- VictoriaMetrics 1.0+

## 📄 Лицензия

MIT License

## 🤝 Вклад в проект

1. Следуйте принципам Clean Architecture
2. Соблюдайте SOLID принципы
3. Покрывайте код тестами
4. Документируйте изменения
5. Используйте адаптеры для обратной совместимости

## 📁 Структура проекта

```
gatling-prometheus-plugin/
├── README.md                    # Основная документация
├── LICENSE                      # Лицензия MIT
├── .gitignore                   # Игнорируемые файлы
├── build.sbt                    # Конфигурация сборки
├── docs/                        # Документация
│   ├── ARCHITECTURE.md          # Описание архитектуры
│   ├── QUICK_START.md           # Быстрый старт
│   ├── REFACTORING_REPORT.md    # Отчет о рефакторинге
│   └── PROJECT_STRUCTURE.md     # Структура проекта
├── examples/                    # Примеры использования
│   └── ExampleUsage.scala       # Примеры кода
├── src/                         # Исходный код
│   ├── main/scala/              # Основной код
│   └── test/scala/              # Тесты
└── target/                      # Собранные артефакты
```

## 📚 Дополнительные ресурсы

- [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [SOLID Principles](https://en.wikipedia.org/wiki/SOLID)
- [Gatling Documentation](https://gatling.io/docs/)
- [Prometheus Documentation](https://prometheus.io/docs/)
- [VictoriaMetrics Documentation](https://docs.victoriametrics.com/)

## 📖 Документация

- **[README.md](README.md)** - Основная документация (этот файл)
- **[docs/QUICK_START.md](docs/QUICK_START.md)** - Быстрый старт
- **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** - Подробное описание архитектуры
- **[docs/REFACTORING_REPORT.md](docs/REFACTORING_REPORT.md)** - Отчет о рефакторинге
- **[docs/PROJECT_STRUCTURE.md](docs/PROJECT_STRUCTURE.md)** - Структура проекта
- **[examples/ExampleUsage.scala](examples/ExampleUsage.scala)** - Примеры использования# Force update JAR Fri Sep 26 15:10:58 MSK 2025
