package com.gu.ssm.utils.attempt

import java.util.concurrent.TimeoutException

import org.scalatest.{EitherValues, FreeSpec, Matchers}
import Attempt.{Left, Right}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


class AttemptTest extends FreeSpec with Matchers with EitherValues with AttemptValues {
  "traverse" - {
    "returns the first failure" in {
      def failOnFourAndSix(i: Int): Attempt[Int] = {
        i match {
          case 4 => expectedFailure("fails on four")
          case 6 => expectedFailure("fails on six")
          case n => Right(n)
        }
      }
      val errors = Attempt.traverse(List(1, 2, 3, 4, 5, 6))(failOnFourAndSix).leftValue()
      checkError(errors, "fails on four")
    }

    "returns the successful result if there were no failures" in {
      Attempt.traverse(List(1, 2, 3, 4))(Right).value() shouldEqual List(1, 2, 3, 4)
    }
  }

  "successfulAttempts" - {
    "returns the list if all were successful" in {
      val attempts = List(Right(1), Right(2))

      Attempt.successfulAttempts(attempts).value() shouldEqual List(1, 2)
    }

    "returns only the successful attempts if there were failures" in {
      val attempts: List[Attempt[Int]] = List(Right(1), Right(2), expectedFailure("failed"), Right(4))

      Attempt.successfulAttempts(attempts).value() shouldEqual List(1, 2, 4)
    }
  }

  "delay" - {
    "will cause timeout in shorter time" ignore {
      val future = Attempt.delay(10.millis).asFuture
      intercept[TimeoutException] {
        Await.result(future, 5.millis)
      }
    }

    "will not cause timeout with longer delay" in {
      val future = Attempt.delay(5.millis).asFuture
      noException should be thrownBy Await.result(future, 10.millis)
    }
  }

  "retry" - {
    "returns failure if the number of retries is exceeded" in {
      Attempt.retryUntil(5, Duration.Zero, () => Attempt.Right(false))(_ == true).isFailedAttempt() shouldEqual true
    }

    "returns success if the attempt returns successfully" in {
      Attempt.retryUntil(5, Duration.Zero, () => Attempt.Right(true))(_ == true).isSuccessfulAttempt() shouldEqual true
    }

    "returns true if the attempt returns before the retries are exhausted" in {
      var counter = 0
      def incr(): Attempt[Int] = {
        counter += 1
        Attempt.Right(counter)
      }

      Attempt.retryUntil(5, Duration.Zero, () => incr())(_ > 3).value() shouldEqual 4
    }

    "returns false if the attempt does not return before the retries are exhausted" in {
      var counter = 0
      def incr(): Attempt[Int] = {
        counter += 1
        Attempt.Right(counter)
      }

      Attempt.retryUntil(5, Duration.Zero, () => incr())(_ > 8).isFailedAttempt() shouldEqual true
    }

    "returns failure if the attempt fails" in {
      val failure = Failure("test failure", "Test failure", ErrorCode).attempt
      Attempt.retryUntil(5, Duration.Zero, () => Attempt.Left[Boolean](failure))(_ => true).leftValue() shouldEqual failure
    }

    "delays between retrying" - {
      "and thus will time out in this test" in {
        val future = Attempt.retryUntil(5, 10.millis, () => Attempt.Right(false))(_ == true).asFuture
        intercept[TimeoutException] {
          Await.result(future, 25.millis)
        }
      }
    }
  }

  /**
    * Utilities for checking the failure state of attempts
    */
  def checkError(errors: FailedAttempt, expected: String): Unit = {
    errors.failures.head.message shouldEqual expected
  }
  def expectedFailure[A](message: String): Attempt[A] = Left[A](Failure(message, "this will fail", ErrorCode))
}
