package ru.x5.svs.gatling.prometheus.domain

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Тесты для типов результатов
 */
class ResultTypesTest extends AnyFlatSpec with Matchers {
  
  "Success" should "return true for isSuccess" in {
    val result = Success("test")
    result.isSuccess shouldBe true
    result.isFailure shouldBe false
  }
  
  "Success" should "return the value" in {
    val result = Success("test")
    result.get shouldBe "test"
    result.getOrElse("default") shouldBe "test"
  }
  
  "Success" should "map correctly" in {
    val result = Success("test")
    val mapped = result.map(_.toUpperCase)
    mapped.get shouldBe "TEST"
  }
  
  "Failure" should "return true for isFailure" in {
    val result = Failure(new Exception("test"))
    result.isSuccess shouldBe false
    result.isFailure shouldBe true
  }
  
  "Failure" should "return default value" in {
    val result = Failure(new Exception("test"))
    result.getOrElse("default") shouldBe "default"
  }
  
  "Result.sequence" should "return Success for all successes" in {
    val results = Seq(Success("a"), Success("b"), Success("c"))
    val sequenced = Result.sequence(results)
    sequenced.isSuccess shouldBe true
    sequenced.get shouldBe Seq("a", "b", "c")
  }
  
  "Result.sequence" should "return Failure for any failure" in {
    val results = Seq(Success("a"), Failure(new Exception("error")), Success("c"))
    val sequenced = Result.sequence(results)
    sequenced.isFailure shouldBe true
  }
}
