# Changelog

Все значимые изменения в этом проекте будут документированы в этом файле.

Формат основан на [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
и этот проект придерживается [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.3.8] - 2025-01-03

### Changed
- **BREAKING**: Упрощена метрика `gatling_http_req_duration` - убраны квантили (p50, p95, p99)
- Теперь генерируется только среднее значение времени ответа
- Квантили можно рассчитывать в Grafana используя `quantile_over_time()`

### Fixed
- Исправлены предупреждения компиляции
- Убраны неиспользуемые параметры и импорты

## [1.3.7] - 2025-01-03

### Added
- Детальное логирование response time для отладки
- Логирование статуса сессии при сборе метрик
- Улучшенная отладочная информация для диагностики

### Changed
- Улучшен расчет response time с использованием реального времени выполнения
- Заменен фиксированный fallback (1000ms) на расчетное время

## [1.3.6] - 2025-01-03

### Added
- SIGTERM handler для Kubernetes graceful shutdown
- Безопасная отправка финальных метрик при остановке теста
- Улучшенное логирование процесса отправки метрик

### Fixed
- Исправлена пропавшая метрика `gatling_http_req_duration`
- Добавлена передача HTTP durations из MetricsQueue в PrometheusMetricsManager

## [1.3.5] - 2025-01-03

### Fixed
- Исправлено дублирование метрик (было в 6 раз больше)
- Заменена логика ADD на SET в `updateHttpMetricsFromQueue`
- Исправлен URL Victoria Metrics с `/api/v1/write` на `/api/v1/import/prometheus`

## [1.3.4] - 2025-01-03

### Added
- Системные метрики (память, GC) в PrometheusMetricsManager
- Метрика `gatling_iteration_duration` для длительности итераций сценариев
- Поддержка файлового экспорта метрик (URL начинается с `file://`)

### Changed
- Переключение с Protobuf на Prometheus TEXT format для Victoria Metrics
- Улучшена совместимость с Victoria Metrics

## [1.3.3] - 2025-01-03

### Added
- Метрика `gatling_vus_peak` для пикового количества виртуальных пользователей
- Обновление VU счетчика из MetricsQueue
- Логирование системных метрик

### Changed
- Изменен `virtualUsers` с `AtomicBoolean` на `AtomicInteger` для точного подсчета VU
- Улучшена интеграция с MetricsQueue

## [1.3.2] - 2025-01-03

### Added
- Метрика `gatling_http_req_duration` с квантилями (p50, p95, p99)
- Хранение response time в `httpDurations`
- Метод `recordHttpRequestWithDuration` для записи времени ответа

### Changed
- Переименована метрика `gatling_http_errors` в `gatling_http_req_failed` для k6 совместимости
- Улучшена интеграция с AutoChains для автоматического сбора метрик

## [1.3.1] - 2025-01-03

### Added
- Автоматический сбор метрик через `AutoChains.withAutoMetrics()`
- Поддержка k6-совместимых названий метрик
- Интеграция с MetricsQueue для батчевой отправки

### Changed
- Упрощен API для пользователей - достаточно обернуть цепочку в `AutoChains.withAutoMetrics()`
- Улучшена производительность за счет батчевой отправки метрик

## [1.3.0] - 2025-01-03

### Added
- Первая стабильная версия плагина
- Поддержка отправки метрик в Victoria Metrics
- Автоматический сбор HTTP метрик, VU, системных метрик
- Kubernetes graceful shutdown
- Thread-safe операции

### Features
- **Автоматический сбор метрик** - просто оберните цепочку в `AutoChains.withAutoMetrics()`
- **k6 совместимость** - метрики называются как в k6 (`gatling_*`)
- **Victoria Metrics интеграция** - отправка в Prometheus text format
- **Kubernetes ready** - SIGTERM handler для graceful shutdown
- **Thread-safe** - безопасная работа в многопоточной среде

## [1.0.0] - 2025-01-03

### Added
- Первоначальная версия плагина
- Базовая функциональность сбора метрик
- Интеграция с Gatling 3.10+
- Экспорт в Prometheus format

---

## Типы изменений

- **Added** - для новых функций
- **Changed** - для изменений в существующей функциональности  
- **Deprecated** - для функций, которые скоро будут удалены
- **Removed** - для удаленных функций
- **Fixed** - для исправления ошибок
- **Security** - для исправлений уязвимостей
