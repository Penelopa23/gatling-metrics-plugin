# Конфигурация плагина Penelopa

## Обзор

Плагин Penelopa поддерживает гибкую конфигурацию через:
- **Системные свойства** (System Properties)
- **Переменные окружения** (Environment Variables)
- **Дефолтные значения** (Default Values)

## Приоритет конфигурации

1. **Системные свойства** (наивысший приоритет)
2. **Переменные окружения** (средний приоритет)
3. **Дефолтные значения** (низший приоритет)

### Особенности дефолтных значений

- **POD** - автоматически берется из переменной окружения `HOSTNAME`, если не задано явно
- **TEST_ID** - фиксированное значение `PenelopaTestId`
- **ENVIRONMENT** - фиксированное значение `default`
- **REGION** - фиксированное значение `default`

## Основные флаги

| Свойство | Переменная окружения | Дефолт | Описание |
|----------|---------------------|--------|----------|
| `penelopa.metrics.enabled` | `PENELOPA_METRICS_ENABLED` | `true` | Включить/выключить метрики |
| `penelopa.remote.write.enabled` | `PENELOPA_REMOTE_WRITE_ENABLED` | `true` | Включить/выключить remote write |
| `penelopa.persistence.enabled` | `PENELOPA_PERSISTENCE_ENABLED` | `true` | Включить/выключить persistence |
| `penelopa.monitoring.enabled` | `PENELOPA_MONITORING_ENABLED` | `true` | Включить/выключить мониторинг |
| `penelopa.debug.mode` | `PENELOPA_DEBUG_MODE` | `false` | Включить/выключить debug режим |

## Тест конфигурация

| Свойство | Переменная окружения | Дефолт | Описание |
|----------|---------------------|--------|----------|
| `penelopa.test.id` | `PENELOPA_TEST_ID` | `PenelopaTestId` | ID теста |
| `penelopa.test.pod` | `PENELOPA_POD` | `HOSTNAME` или `PenelopaPod` | Имя пода (автоматически берется из HOSTNAME) |
| `penelopa.test.environment` | `PENELOPA_ENVIRONMENT` | `default` | Окружение |
| `penelopa.test.region` | `PENELOPA_REGION` | `default` | Регион |

## Remote Write конфигурация

| Свойство | Переменная окружения | Дефолт | Описание |
|----------|---------------------|--------|----------|
| `penelopa.remote.write.url` | `PENELOPA_REMOTE_WRITE_URL` | `http://vms-victoria-metrics-single-victoria-server.metricstest:8428/prometheus` | URL для отправки метрик (Prometheus endpoint для Victoria Metrics) |
| `penelopa.remote.write.interval` | `PENELOPA_REMOTE_WRITE_INTERVAL` | `5` | Интервал отправки в секундах |
| `penelopa.remote.write.batch.size` | `PENELOPA_REMOTE_WRITE_BATCH_SIZE` | `1000` | Размер батча |
| `penelopa.remote.write.timeout` | `PENELOPA_REMOTE_WRITE_TIMEOUT` | `30` | Таймаут в секундах |
| `penelopa.remote.write.retry.attempts` | `PENELOPA_REMOTE_WRITE_RETRY_ATTEMPTS` | `3` | Количество попыток повтора |
| `penelopa.remote.write.retry.delay` | `PENELOPA_REMOTE_WRITE_RETRY_DELAY` | `1000` | Задержка между попытками в миллисекундах |

## Метрики конфигурация

| Свойство | Переменная окружения | Дефолт | Описание |
|----------|---------------------|--------|----------|
| `penelopa.metrics.collection.interval` | `PENELOPA_METRICS_COLLECTION_INTERVAL` | `1000` | Интервал сбора метрик в миллисекундах |
| `penelopa.metrics.max.queue.size` | `PENELOPA_METRICS_MAX_QUEUE_SIZE` | `10000` | Максимальный размер очереди |
| `penelopa.metrics.max.durations.per.key` | `PENELOPA_METRICS_MAX_DURATIONS_PER_KEY` | `1000` | Максимальное количество длительностей на ключ |
| `penelopa.metrics.max.keys` | `PENELOPA_METRICS_MAX_KEYS` | `10000` | Максимальное количество ключей |

## Persistence конфигурация

| Свойство | Переменная окружения | Дефолт | Описание |
|----------|---------------------|--------|----------|
| `penelopa.persistence.file.path` | `PENELOPA_PERSISTENCE_FILE_PATH` | `/tmp/gatling-metrics-backup.json` | Путь к файлу persistence |
| `penelopa.persistence.max.file.size` | `PENELOPA_PERSISTENCE_MAX_FILE_SIZE` | `100` | Максимальный размер файла в MB |
| `penelopa.persistence.max.files` | `PENELOPA_PERSISTENCE_MAX_FILES` | `10` | Максимальное количество файлов |
| `penelopa.persistence.compression.enabled` | `PENELOPA_PERSISTENCE_COMPRESSION_ENABLED` | `true` | Включить сжатие |

## Мониторинг конфигурация

| Свойство | Переменная окружения | Дефолт | Описание |
|----------|---------------------|--------|----------|
| `penelopa.monitoring.health.check.interval` | `PENELOPA_MONITORING_HEALTH_CHECK_INTERVAL` | `30000` | Интервал проверки здоровья в миллисекундах |
| `penelopa.monitoring.metrics.interval` | `PENELOPA_MONITORING_METRICS_INTERVAL` | `10000` | Интервал сбора метрик мониторинга в миллисекундах |
| `penelopa.monitoring.alert.error.rate` | `PENELOPA_MONITORING_ALERT_ERROR_RATE` | `0.05` | Порог ошибок для алертов (5%) |
| `penelopa.monitoring.alert.slow.log.rate` | `PENELOPA_MONITORING_ALERT_SLOW_LOG_RATE` | `0.1` | Порог медленных логов для алертов (10%) |
| `penelopa.monitoring.alert.memory.usage` | `PENELOPA_MONITORING_ALERT_MEMORY_USAGE` | `0.8` | Порог использования памяти для алертов (80%) |

## Примеры использования

### 1. Через системные свойства (Maven)

```bash
# Для отладки
mvn gatling:test -Dpenelopa.debug.mode=true -Dpenelopa.remote.write.url=file:///tmp/gatling-metrics.txt

# Для продакшена
mvn gatling:test -Dpenelopa.debug.mode=false -Dpenelopa.metrics.enabled=true

# Для отключения метрик
mvn gatling:test -Dpenelopa.metrics.enabled=false

# Для отправки в файл
mvn gatling:test -Dpenelopa.remote.write.url=file:///tmp/gatling-metrics.txt

# Для отправки в Victoria Metrics
mvn gatling:test -Dpenelopa.remote.write.url=http://victoria-metrics:8428/prometheus

# Для отправки в Prometheus
mvn gatling:test -Dpenelopa.remote.write.url=http://prometheus:9090/api/v1/write
```

### 2. Через переменные окружения

```bash
# Установить переменные окружения
export PENELOPA_DEBUG_MODE=true
export PENELOPA_METRICS_ENABLED=true
export PENELOPA_REMOTE_WRITE_URL="file:///tmp/gatling-metrics.txt"

# POD автоматически берется из HOSTNAME
echo "HOSTNAME: $HOSTNAME"  # Будет использован как POD

# Или задать POD явно
export PENELOPA_POD="my-custom-pod"

# Запустить тест
mvn gatling:test
```

### 3. Через файл переменных окружения

```bash
# Создать файл penelopa-env.sh
cat > penelopa-env.sh << 'EOF'
#!/bin/bash
export PENELOPA_DEBUG_MODE=true
export PENELOPA_METRICS_ENABLED=true
export PENELOPA_REMOTE_WRITE_URL="file:///tmp/gatling-metrics.txt"
EOF

# Загрузить переменные и запустить тест
source penelopa-env.sh
mvn gatling:test
```

### 4. Через Maven профили

```xml
<profiles>
  <profile>
    <id>debug</id>
    <properties>
      <penelopa.debug.mode>true</penelopa.debug.mode>
      <penelopa.remote.write.url>file:///tmp/gatling-metrics.txt</penelopa.remote.write.url>
    </properties>
  </profile>
  
  <profile>
    <id>production</id>
    <properties>
      <penelopa.debug.mode>false</penelopa.debug.mode>
      <penelopa.metrics.enabled>true</penelopa.metrics.enabled>
      <penelopa.remote.write.enabled>true</penelopa.remote.write.enabled>
    </properties>
  </profile>
</profiles>
```

## Валидация конфигурации

Плагин автоматически валидирует конфигурацию при запуске:

- **Проверка типов** - все значения проверяются на корректность типов
- **Проверка диапазонов** - числовые значения проверяются на допустимые диапазоны
- **Проверка зависимостей** - проверяется совместимость флагов
- **Проверка URL** - проверяется корректность URL для remote write

## Логирование конфигурации

При запуске плагин логирует:
- Загруженные значения конфигурации
- Источник каждого значения (системное свойство, переменная окружения, дефолт)
- Предупреждения о некорректных значениях
- Сводку конфигурации

## Рекомендации

### Для разработки:
- Используйте `PENELOPA_DEBUG_MODE=true`
- Используйте `PENELOPA_REMOTE_WRITE_URL=file:///tmp/gatling-metrics.txt`
- Включите все флаги для полного тестирования

### Для продакшена:
- Используйте `PENELOPA_DEBUG_MODE=false`
- Настройте правильный URL для Victoria Metrics
- Включите мониторинг и persistence
- Настройте алерты

### Для отладки:
- Используйте файловый вывод для анализа метрик
- Включите debug режим для детального логирования
- Настройте короткие интервалы для быстрого тестирования
