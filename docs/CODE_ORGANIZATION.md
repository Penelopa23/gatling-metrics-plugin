# Организация кода Gatling Prometheus Plugin

## Обзор

Код проекта был реорганизован для лучшей структуры и соответствия принципам Clean Architecture. Все файлы теперь логически сгруппированы по слоям архитектуры.

## Финальная структура кода

```
src/main/scala/ru/x5/svs/gatling/prometheus/
├── domain/                          # 🏛️ Доменный слой
│   ├── Metric.scala                 # Доменные модели метрик
│   ├── Configuration.scala          # Доменные модели конфигурации
│   ├── repository/                  # Интерфейсы репозиториев
│   │   └── MetricRepository.scala
│   └── service/                     # Доменные сервисы
│       ├── MetricCollector.scala
│       ├── MetricExporter.scala
│       └── VirtualUserTracker.scala
├── application/                     # 🚀 Слой приложения
│   ├── service/                     # Сервисы приложения
│   │   └── MetricsApplicationService.scala
│   └── factory/                     # Фабрики
│       └── MetricsServiceFactory.scala
├── infrastructure/                  # 🔧 Инфраструктурный слой
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
├── presentation/                    # 🎨 Слой представления
│   └── adapter/                     # Адаптеры для совместимости
│       ├── LegacyMetricsManager.scala
│       ├── LegacyHttpMetricsCollector.scala
│       └── LegacyGlobalVUTracker.scala
└── legacy/                          # 📦 Legacy файлы для обратной совместимости
    ├── AutoChains.scala             # Автоматические цепочки
    ├── AutoHttpProtocol.scala       # HTTP протокол
    ├── AutoHttpUtils.scala          # HTTP утилиты
    ├── AutoPrometheusDataWriterFactory.scala # Фабрика DataWriter
    ├── GatlingMetricsConfig.scala   # Конфигурация метрик
    ├── GlobalVUTracker.scala        # Глобальный трекер VU
    ├── HttpMetricsCollector.scala   # Сборщик HTTP метрик
    ├── MetricsLogger.scala          # Логгер метрик
    ├── PrometheusMetricsManager.scala # Менеджер метрик
    ├── PrometheusRemoteWriter.scala # Удаленный писатель
    └── SimpleVUTracker.scala        # Простой трекер VU
```

## Принципы организации

### 1. Clean Architecture Layers

#### Domain Layer (Доменный слой)
- **Назначение**: Содержит бизнес-логику и доменные модели
- **Содержит**: Модели, интерфейсы, доменные сервисы
- **Не зависит от**: Внешних слоев
- **Файлы**:
  - `Metric.scala` - модели метрик
  - `Configuration.scala` - модели конфигурации
  - `repository/MetricRepository.scala` - интерфейс репозитория
  - `service/` - интерфейсы доменных сервисов

#### Application Layer (Слой приложения)
- **Назначение**: Оркестрирует доменные сервисы
- **Содержит**: Сервисы приложения, фабрики
- **Зависит от**: Domain Layer
- **Файлы**:
  - `service/MetricsApplicationService.scala` - основной сервис
  - `factory/MetricsServiceFactory.scala` - фабрика зависимостей

#### Infrastructure Layer (Инфраструктурный слой)
- **Назначение**: Реализует технические детали
- **Содержит**: Реализации для внешних систем
- **Зависит от**: Domain Layer
- **Файлы**:
  - `prometheus/` - реализации для Prometheus
  - `remote/` - удаленный экспорт метрик
  - `tracking/` - отслеживание VU
  - `config/` - загрузка конфигурации

#### Presentation Layer (Слой представления)
- **Назначение**: Адаптеры для внешних интерфейсов
- **Содержит**: Адаптеры для совместимости
- **Зависит от**: Application Layer
- **Файлы**:
  - `adapter/` - адаптеры для legacy API

#### Legacy Layer (Legacy слой)
- **Назначение**: Обеспечивает обратную совместимость
- **Содержит**: Старые API и утилиты
- **Зависит от**: Presentation Layer
- **Файлы**: Все legacy файлы для совместимости

### 2. Принципы именования

#### Папки
- **domain/** - доменный слой
- **application/** - слой приложения
- **infrastructure/** - инфраструктурный слой
- **presentation/** - слой представления
- **legacy/** - legacy файлы

#### Файлы
- **Модели**: `Metric.scala`, `Configuration.scala`
- **Интерфейсы**: `MetricRepository.scala`, `MetricCollector.scala`
- **Реализации**: `PrometheusMetricRepository.scala`
- **Сервисы**: `MetricsApplicationService.scala`
- **Фабрики**: `MetricsServiceFactory.scala`
- **Адаптеры**: `LegacyMetricsManager.scala`

## Преимущества новой организации

### ✅ Четкое разделение ответственности
- Каждый слой имеет свою четко определенную роль
- Легко понять, где что находится
- Соблюдение принципов Clean Architecture

### ✅ Лучшая навигация
- Файлы логически сгруппированы
- Легко найти нужный компонент
- Понятная структура для новых разработчиков

### ✅ Масштабируемость
- Легко добавлять новые компоненты в нужный слой
- Четкие границы между слоями
- Возможность замены реализаций

### ✅ Тестируемость
- Каждый слой можно тестировать изолированно
- Легко создавать моки и стабы
- Четкие зависимости

### ✅ Обратная совместимость
- Legacy файлы выделены в отдельную папку
- Старые API продолжают работать
- Плавная миграция

## Миграция импортов

### Старые импорты:
```scala
import ru.x5.svs.gatling.prometheus.AutoChains
import ru.x5.svs.gatling.prometheus.PrometheusMetricsManager
import ru.x5.svs.gatling.prometheus.HttpMetricsCollector
```

### Новые импорты:
```scala
import ru.x5.svs.gatling.prometheus.legacy.AutoChains
import ru.x5.svs.gatling.prometheus.legacy.PrometheusMetricsManager
import ru.x5.svs.gatling.prometheus.legacy.HttpMetricsCollector
```

### Импорты новой архитектуры:
```scala
import ru.x5.svs.gatling.prometheus.application.service.MetricsApplicationService
import ru.x5.svs.gatling.prometheus.application.factory.MetricsServiceFactory
import ru.x5.svs.gatling.prometheus.domain.Metric
import ru.x5.svs.gatling.prometheus.domain.Configuration
```

## Рекомендации

### Для разработчиков:
1. **Новые компоненты** добавляйте в соответствующий слой
2. **Следуйте** принципам Clean Architecture
3. **Не нарушайте** границы между слоями
4. **Используйте** интерфейсы для зависимостей

### Для пользователей:
1. **Legacy API** используйте через `legacy.*` импорты
2. **Новая архитектура** доступна через соответствующие пакеты
3. **Документация** обновлена с новыми импортами

## Заключение

Новая организация кода обеспечивает:
- **Четкую структуру** по принципам Clean Architecture
- **Лучшую навигацию** и понимание кода
- **Масштабируемость** для будущего развития
- **Обратную совместимость** для существующих пользователей

Код теперь соответствует лучшим практикам организации и может служить примером правильной архитектуры для других проектов.
