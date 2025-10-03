#!/bin/bash
# Шаблон переменных окружения для плагина Penelopa
# Скопируйте этот файл в penelopa-env.sh и настройте под ваши нужды

# =============================================================================
# ОСНОВНЫЕ ФЛАГИ (с дефолтными значениями)
# =============================================================================

# Включить/выключить метрики (по умолчанию: true)
export PENELOPA_METRICS_ENABLED=true

# Включить/выключить remote write (по умолчанию: true)
export PENELOPA_REMOTE_WRITE_ENABLED=true

# Включить/выключить persistence (по умолчанию: true)
export PENELOPA_PERSISTENCE_ENABLED=true

# Включить/выключить мониторинг (по умолчанию: true)
export PENELOPA_MONITORING_ENABLED=true

# Включить/выключить debug режим (по умолчанию: false)
export PENELOPA_DEBUG_MODE=false

# =============================================================================
# ТЕСТ КОНФИГУРАЦИЯ (с дефолтными значениями)
# =============================================================================

# ID теста (по умолчанию: PenelopaTestId)
export PENELOPA_TEST_ID="PenelopaTestId"

# Имя пода (по умолчанию: HOSTNAME или PenelopaPod)
export PENELOPA_POD="PenelopaPod"

# Окружение (по умолчанию: default)
export PENELOPA_ENVIRONMENT="default"

# Регион (по умолчанию: default)
export PENELOPA_REGION="default"

# =============================================================================
# REMOTE WRITE КОНФИГУРАЦИЯ (с дефолтными значениями)
# =============================================================================

# URL для отправки метрик (по умолчанию: Prometheus endpoint для Victoria Metrics)
export PENELOPA_REMOTE_WRITE_URL="http://vms-victoria-metrics-single-victoria-server.metricstest:8428/prometheus"

# Интервал отправки в секундах (по умолчанию: 5)
export PENELOPA_REMOTE_WRITE_INTERVAL="5"

# Размер батча (по умолчанию: 1000)
export PENELOPA_REMOTE_WRITE_BATCH_SIZE="1000"

# Таймаут в секундах (по умолчанию: 30)
export PENELOPA_REMOTE_WRITE_TIMEOUT="30"

# Количество попыток повтора (по умолчанию: 3)
export PENELOPA_REMOTE_WRITE_RETRY_ATTEMPTS="3"

# Задержка между попытками в миллисекундах (по умолчанию: 1000)
export PENELOPA_REMOTE_WRITE_RETRY_DELAY="1000"

# =============================================================================
# МЕТРИКИ КОНФИГУРАЦИЯ (с дефолтными значениями)
# =============================================================================

# Интервал сбора метрик в миллисекундах (по умолчанию: 1000)
export PENELOPA_METRICS_COLLECTION_INTERVAL="1000"

# Максимальный размер очереди (по умолчанию: 10000)
export PENELOPA_METRICS_MAX_QUEUE_SIZE="10000"

# Максимальное количество длительностей на ключ (по умолчанию: 1000)
export PENELOPA_METRICS_MAX_DURATIONS_PER_KEY="1000"

# Максимальное количество ключей (по умолчанию: 10000)
export PENELOPA_METRICS_MAX_KEYS="10000"

# =============================================================================
# PERSISTENCE КОНФИГУРАЦИЯ (с дефолтными значениями)
# =============================================================================

# Путь к файлу persistence (по умолчанию: /tmp/gatling-metrics-backup.json)
export PENELOPA_PERSISTENCE_FILE_PATH="/tmp/gatling-metrics-backup.json"

# Максимальный размер файла в MB (по умолчанию: 100)
export PENELOPA_PERSISTENCE_MAX_FILE_SIZE="100"

# Максимальное количество файлов (по умолчанию: 10)
export PENELOPA_PERSISTENCE_MAX_FILES="10"

# Включить сжатие (по умолчанию: true)
export PENELOPA_PERSISTENCE_COMPRESSION_ENABLED="true"

# =============================================================================
# МОНИТОРИНГ КОНФИГУРАЦИЯ (с дефолтными значениями)
# =============================================================================

# Интервал проверки здоровья в миллисекундах (по умолчанию: 30000)
export PENELOPA_MONITORING_HEALTH_CHECK_INTERVAL="30000"

# Интервал сбора метрик мониторинга в миллисекундах (по умолчанию: 10000)
export PENELOPA_MONITORING_METRICS_INTERVAL="10000"

# Порог ошибок для алертов (по умолчанию: 0.05 = 5%)
export PENELOPA_MONITORING_ALERT_ERROR_RATE="0.05"

# Порог медленных логов для алертов (по умолчанию: 0.1 = 10%)
export PENELOPA_MONITORING_ALERT_SLOW_LOG_RATE="0.1"

# Порог использования памяти для алертов (по умолчанию: 0.8 = 80%)
export PENELOPA_MONITORING_ALERT_MEMORY_USAGE="0.8"

# =============================================================================
# ПРИМЕРЫ ИСПОЛЬЗОВАНИЯ
# =============================================================================

# Для отладки:
# export PENELOPA_DEBUG_MODE=true
# export PENELOPA_METRICS_ENABLED=true
# export PENELOPA_REMOTE_WRITE_URL="file:///tmp/gatling-metrics.txt"

# Для продакшена:
# export PENELOPA_DEBUG_MODE=false
# export PENELOPA_METRICS_ENABLED=true
# export PENELOPA_REMOTE_WRITE_ENABLED=true
# export PENELOPA_PERSISTENCE_ENABLED=true
# export PENELOPA_MONITORING_ENABLED=true

# Для отключения метрик:
# export PENELOPA_METRICS_ENABLED=false

# Для отправки в файл:
# export PENELOPA_REMOTE_WRITE_URL="file:///tmp/gatling-metrics.txt"

# Для отправки в Victoria Metrics:
# export PENELOPA_REMOTE_WRITE_URL="http://victoria-metrics:8428/prometheus"

# Для отправки в Prometheus:
# export PENELOPA_REMOTE_WRITE_URL="http://prometheus:9090/api/v1/write"
