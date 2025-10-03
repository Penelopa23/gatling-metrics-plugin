# Руководство по вкладу в проект

Спасибо за интерес к проекту Gatling Prometheus Metrics Plugin!

## Быстрый старт

### 1. Fork и клонирование
```bash
# Fork репозитория на GitHub
# Затем клонируйте ваш fork
git clone https://github.com/YOUR_USERNAME/gatling-metrics-plugin.git
cd gatling-metrics-plugin

# Добавьте upstream remote
git remote add upstream https://github.com/Penelopa23/gatling-metrics-plugin.git
```

### 2. Настройка среды разработки
```bash
# Убедитесь, что у вас установлены:
# - Java 11+
# - Scala 2.13
# - SBT 1.8+

# Соберите проект
sbt compile

# Запустите тесты
sbt test
```

## Процесс разработки

### 1. Создание feature branch
```bash
# Создайте новую ветку для вашей функции
git checkout -b feature/amazing-feature

# Или для исправления бага
git checkout -b fix/bug-description
```

### 2. Разработка
- Следуйте принципам **SOLID**
- Покрывайте код **тестами**
- Документируйте **изменения**
- Используйте **адаптеры** для обратной совместимости

### 3. Тестирование
```bash
# Запустите все тесты
sbt test

# Запустите конкретный тест
sbt "testOnly *ThreadSafetyTest"

# Проверьте покрытие тестами
sbt coverage test coverageReport
```

### 4. Сборка
```bash
# Соберите JAR
sbt assembly

# Проверьте, что JAR создался
ls -la target/scala-2.13/gatling-prometheus-plugin-fat.jar
```

## Стандарты кода

### Scala стиль
```scala
// Хорошо
class MetricsManager {
  def recordMetric(name: String, value: Double): Unit = {
    // Реализация
  }
}

// Плохо  
class metricsmanager {
  def recordmetric(name:String,value:Double):Unit={
    // реализация
  }
}
```

### Принципы SOLID

#### 1. Single Responsibility Principle (SRP)
```scala
// Хорошо - один класс, одна ответственность
class HttpMetricsCollector {
  def collectHttpRequest(...): Unit = { /* только сбор HTTP метрик */ }
}

// Плохо - много ответственностей
class MetricsManager {
  def collectHttpRequest(...): Unit = { /* сбор метрик */ }
  def sendToDatabase(...): Unit = { /* отправка в БД */ }
  def generateReport(...): Unit = { /* генерация отчета */ }
}
```

#### 2. Open/Closed Principle (OCP)
```scala
// Хорошо - легко расширяется
trait MetricExporter {
  def export(metrics: Seq[Metric]): Unit
}

class PrometheusExporter extends MetricExporter { /* реализация */ }
class InfluxDBExporter extends MetricExporter { /* новая реализация */ }
```

#### 3. Liskov Substitution Principle (LSP)
```scala
// Хорошо - все реализации взаимозаменяемы
trait MetricRepository {
  def save(metric: Metric): Unit
  def findByTestId(testId: String): Seq[Metric]
}

class PrometheusRepository extends MetricRepository { /* реализация */ }
class InMemoryRepository extends MetricRepository { /* другая реализация */ }
```

### Thread Safety
```scala
// Хорошо - thread-safe
class ThreadSafeMetricsManager {
  private val metrics = new ConcurrentHashMap[String, AtomicLong]()
  
  def incrementCounter(key: String): Unit = {
    metrics.computeIfAbsent(key, _ => new AtomicLong(0)).incrementAndGet()
  }
}

// Плохо - не thread-safe
class UnsafeMetricsManager {
  private var metrics = Map[String, Long]()
  
  def incrementCounter(key: String): Unit = {
    metrics = metrics + (key -> (metrics.getOrElse(key, 0L) + 1))
  }
}
```

## Тестирование

### Unit тесты
```scala
class MetricsManagerTest extends AnyFunSuite {
  test("should record HTTP request metric") {
    val manager = new PrometheusMetricsManager()
    manager.recordHttpRequest("test", "api", "GET", "OK", 150L)
    
    val metrics = manager.getAllMetrics()
    assert(metrics.exists(_.name == "gatling_http_reqs_total"))
  }
}
```

### Integration тесты
```scala
class IntegrationTest extends AnyFunSuite {
  test("should send metrics to Victoria Metrics") {
    val manager = new PrometheusMetricsManager()
    manager.initialize("http://localhost:8428/api/v1/import/prometheus", "test", "pod")
    
    manager.recordHttpRequest("test", "api", "GET", "OK", 150L)
    manager.sendToVictoriaMetrics()
    
    // Проверяем, что метрики отправлены
    assert(manager.isMetricsSent())
  }
}
```

### Thread Safety тесты
```scala
class ThreadSafetyTest extends AnyFunSuite {
  test("should be thread-safe under concurrent access") {
    val manager = new PrometheusMetricsManager()
    val threads = (1 to 100).map { i =>
      new Thread(() => {
        manager.recordHttpRequest("test", s"api$i", "GET", "OK", 150L)
      })
    }
    
    threads.foreach(_.start())
    threads.foreach(_.join())
    
    // Проверяем, что все метрики записаны
    assert(manager.getHttpRequestCount() == 100)
  }
}
```

## Документация

### Обновление README
- Обновляйте README при добавлении новых функций
- Добавляйте примеры использования
- Обновляйте таблицы совместимости

### Комментарии в коде
```scala
/**
 * Управляет метриками Prometheus для Gatling тестов
 * 
 * @param victoriaMetricsUrl URL для отправки метрик в Victoria Metrics
 * @param testId Идентификатор теста
 * @param pod Имя пода (для Kubernetes)
 */
class PrometheusMetricsManager(
  victoriaMetricsUrl: String,
  testId: String, 
  pod: String
) {
  /**
   * Записывает HTTP запрос в метрики
   * 
   * @param scenario Имя сценария
   * @param requestName Имя запроса
   * @param method HTTP метод
   * @param status Статус ответа (OK/KO)
   * @param responseTime Время ответа в миллисекундах
   */
  def recordHttpRequest(
    scenario: String,
    requestName: String, 
    method: String,
    status: String,
    responseTime: Long
  ): Unit = {
    // Реализация
  }
}
```

## Процесс Pull Request

### 1. Подготовка
```bash
# Убедитесь, что ваша ветка актуальна
git fetch upstream
git rebase upstream/main

# Запустите тесты
sbt test

# Соберите проект
sbt assembly
```

### 2. Создание PR
- **Заголовок**: Краткое описание изменения
- **Описание**: Подробное описание того, что изменилось
- **Тесты**: Убедитесь, что все тесты проходят
- **Документация**: Обновите README если нужно

### 3. Шаблон PR
```markdown
## Описание
Краткое описание изменений

## Тип изменения
- [ ] Bug fix
- [ ] New feature  
- [ ] Breaking change
- [ ] Documentation update

## Что изменилось
- Добавлена новая функция X
- Исправлен баг Y
- Обновлена документация Z

## Тестирование
- [ ] Все тесты проходят
- [ ] Добавлены новые тесты
- [ ] Проверена обратная совместимость

## Документация
- [ ] Обновлен README
- [ ] Добавлены примеры
- [ ] Обновлен CHANGELOG
```

## Сообщение об ошибках

### Шаблон Issue
```markdown
**Описание бага**
Краткое описание проблемы

**Шаги для воспроизведения**
1. Запустите тест с параметрами X
2. Выполните действие Y
3. Получите ошибку Z

**Ожидаемое поведение**
Что должно происходить

**Фактическое поведение**
Что происходит на самом деле

**Окружение**
- Gatling версия: 3.10.3
- Scala версия: 2.13.12
- Java версия: 11
- OS: macOS 12.0

**Дополнительная информация**
Логи, скриншоты, конфигурация
```

## Предложения функций

### Шаблон Feature Request
```markdown
**Описание функции**
Краткое описание предлагаемой функции

**Проблема, которую решает**
Какую проблему решает эта функция

**Предлагаемое решение**
Как вы видите реализацию

**Альтернативы**
Другие способы решения проблемы

**Дополнительная информация**
Примеры использования, ссылки на похожие функции
```

## Версионирование

Проект использует [Semantic Versioning](https://semver.org/):

- **MAJOR** (1.0.0 → 2.0.0): Breaking changes
- **MINOR** (1.0.0 → 1.1.0): Новые функции, обратно совместимые
- **PATCH** (1.0.0 → 1.0.1): Исправления багов

### Обновление версии
```bash
# В build.sbt
version := "1.4.0"

# В CHANGELOG.md
## [1.4.0] - 2025-01-03
### Added
- Новая функция X
```

## Получение помощи

- **GitHub Issues**: Для багов и предложений
- **GitHub Discussions**: Для вопросов и обсуждений
- **Email**: support@penelopa.dev

## Лицензия

Внося вклад в проект, вы соглашаетесь с тем, что ваш вклад будет лицензирован под MIT License.

---

**Спасибо за ваш вклад!**
