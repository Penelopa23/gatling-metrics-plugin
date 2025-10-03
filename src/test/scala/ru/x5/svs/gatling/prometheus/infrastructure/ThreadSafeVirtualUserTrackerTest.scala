package ru.x5.svs.gatling.prometheus.infrastructure

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Тесты для ThreadSafeVirtualUserTracker
 */
class ThreadSafeVirtualUserTrackerTest extends AnyFlatSpec with Matchers with ScalaFutures {
  
  "ThreadSafeVirtualUserTracker" should "start with zero users" in {
    val tracker = new ThreadSafeVirtualUserTracker()
    tracker.getCurrentCount() shouldBe 0
    tracker.getPeakCount() shouldBe 0
  }
  
  "ThreadSafeVirtualUserTracker" should "increment count when user starts" in {
    val tracker = new ThreadSafeVirtualUserTracker()
    tracker.startUser()
    tracker.getCurrentCount() shouldBe 1
    tracker.getPeakCount() shouldBe 1
  }
  
  "ThreadSafeVirtualUserTracker" should "decrement count when user ends" in {
    val tracker = new ThreadSafeVirtualUserTracker()
    tracker.startUser()
    tracker.startUser()
    tracker.getCurrentCount() shouldBe 2
    
    tracker.endUser()
    tracker.getCurrentCount() shouldBe 1
    tracker.getPeakCount() shouldBe 2
  }
  
  "ThreadSafeVirtualUserTracker" should "not go below zero" in {
    val tracker = new ThreadSafeVirtualUserTracker()
    tracker.endUser() // Попытка завершить пользователя, когда их нет
    tracker.getCurrentCount() shouldBe 0
  }
  
  "ThreadSafeVirtualUserTracker" should "track peak count correctly" in {
    val tracker = new ThreadSafeVirtualUserTracker()
    tracker.startUser() // 1
    tracker.startUser() // 2
    tracker.startUser() // 3
    tracker.getPeakCount() shouldBe 3
    
    tracker.endUser() // 2
    tracker.getPeakCount() shouldBe 3 // Пик остается 3
    
    tracker.endUser() // 1
    tracker.getPeakCount() shouldBe 3 // Пик остается 3
  }
  
  "ThreadSafeVirtualUserTracker" should "reset correctly" in {
    val tracker = new ThreadSafeVirtualUserTracker()
    tracker.startUser()
    tracker.startUser()
    tracker.getCurrentCount() shouldBe 2
    
    tracker.reset()
    tracker.getCurrentCount() shouldBe 0
    tracker.getPeakCount() shouldBe 0
  }
}
