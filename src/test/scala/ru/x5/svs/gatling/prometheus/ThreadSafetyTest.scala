package ru.x5.svs.gatling.prometheus

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import ru.x5.svs.gatling.prometheus.infrastructure.ThreadSafeExecutorPool
import ru.x5.svs.gatling.prometheus.monitoring.ThreadMonitor
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch

/**
 * Тесты для проверки thread safety и производительности
 */
class ThreadSafetyTest extends AnyFlatSpec with Matchers with ScalaFutures {
  
  "ThreadSafeExecutorPool" should "handle concurrent tasks without creating too many threads" in {
    val pool = new ThreadSafeExecutorPool(
      name = "TestPool",
      corePoolSize = 2,
      maxPoolSize = 4,
      queueCapacity = 100
    )
    
    val taskCount = 1000
    val completedTasks = new AtomicInteger(0)
    val latch = new CountDownLatch(taskCount)
    
    // Запускаем много задач одновременно
    (1 to taskCount).foreach { i =>
      pool.submit(new Runnable {
        override def run(): Unit = {
          try {
            // Имитируем работу
            Thread.sleep(10)
            completedTasks.incrementAndGet()
          } finally {
            latch.countDown()
          }
        }
      })
    }
    
    // Ждем завершения всех задач
    latch.await(30, java.util.concurrent.TimeUnit.SECONDS)
    
    // Проверяем, что все задачи выполнены
    completedTasks.get() shouldBe taskCount
    
    // Проверяем статистику пула
    val stats = pool.getPoolStats()
    stats.tasksSubmitted shouldBe taskCount
    stats.tasksCompleted shouldBe taskCount
    stats.tasksRejected shouldBe 0
    
    // Проверяем, что не создано слишком много потоков
    stats.maxPoolSize shouldBe 4
    stats.poolSize should be <= 4
    
    pool.shutdown()
  }
  
  "MetricsQueue" should "be thread-safe under high load" in {
    val threadCount = 50
    val operationsPerThread = 1000
    val completedOperations = new AtomicInteger(0)
    val latch = new CountDownLatch(threadCount)
    
    // Запускаем много потоков, которые одновременно работают с MetricsQueue
    val threads = (1 to threadCount).map { threadId =>
      new Thread(new Runnable {
        override def run(): Unit = {
          try {
            (1 to operationsPerThread).foreach { i =>
              val scenario = s"scenario-$threadId"
              val request = s"request-$i"
              val method = "GET"
              val status = if (i % 10 == 0) "KO" else "OK"
              val responseTime = 100 + (i % 500)
              
              MetricsQueue.addHttpRequest(scenario, request, method, status, responseTime)
              completedOperations.incrementAndGet()
            }
          } finally {
            latch.countDown()
          }
        }
      })
    }
    
    // Запускаем все потоки
    threads.foreach(_.start())
    
    // Ждем завершения
    latch.await(60, java.util.concurrent.TimeUnit.SECONDS)
    
    // Проверяем результаты
    completedOperations.get() shouldBe threadCount * operationsPerThread
    
    // Проверяем, что счетчики корректны
    val requestCounters = MetricsQueue.getHttpRequestCounters()
    requestCounters.values.sum shouldBe threadCount * operationsPerThread
    
    // Очищаем после теста
    MetricsQueue.clearAllMetrics()
  }
  
  "ThreadMonitor" should "detect thread leaks" in {
    // Запускаем мониторинг
    ThreadMonitor.startMonitoring()
    
    // Создаем много пулов потоков
    val pools = (1 to 10).map { i =>
      new ThreadSafeExecutorPool(
        name = s"TestPool-$i",
        corePoolSize = 2,
        maxPoolSize = 4,
        queueCapacity = 100
      )
    }
    
    // Проверяем статистику
    val stats = ThreadMonitor.getThreadStats()
    stats.totalThreadsCreated should be > 0
    stats.threadPools.length shouldBe 10
    
    // Останавливаем пулы
    pools.foreach(_.shutdown())
    
    // Останавливаем мониторинг
    ThreadMonitor.stopMonitoring()
  }
  
  "ConcurrentHashMap operations" should "be thread-safe" in {
    val map = new java.util.concurrent.ConcurrentHashMap[String, AtomicInteger]()
    val threadCount = 20
    val operationsPerThread = 1000
    val latch = new CountDownLatch(threadCount)
    
    // Запускаем потоки, которые одновременно работают с ConcurrentHashMap
    val threads = (1 to threadCount).map { threadId =>
      new Thread(new Runnable {
        override def run(): Unit = {
          try {
            (1 to operationsPerThread).foreach { i =>
              val key = s"key-${i % 100}" // Ограничиваем количество ключей
              map.computeIfAbsent(key, _ => new AtomicInteger(0)).incrementAndGet()
            }
          } finally {
            latch.countDown()
          }
        }
      })
    }
    
    threads.foreach(_.start())
    latch.await(30, java.util.concurrent.TimeUnit.SECONDS)
    
    // Проверяем, что все операции выполнены корректно
    val totalOperations = threadCount * operationsPerThread
    val sum = map.values().asScala.map(_.get()).sum
    sum shouldBe totalOperations
  }
}
