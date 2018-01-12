package com.gu.ssm.utils.attempt

import java.io.{ByteArrayOutputStream, PrintWriter}

import org.scalatest.Matchers
import org.scalatest.exceptions.TestFailedException

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}


trait AttemptValues extends Matchers {
  implicit class RichAttempt[A](attempt: Attempt[A]) {
    private def stackTrace(failure: Failure): String = {
      failure.throwable.map { t =>
        val baos = new ByteArrayOutputStream()
        val pw = new PrintWriter(baos)
        t.printStackTrace(pw)
        pw.close()
        baos.toString
      }.getOrElse("")
    }

    def value()(implicit ec: ExecutionContext): A = {
      val result = Await.result(attempt.asFuture, 5.seconds)
      withClue {
        result.fold(
          fa => s"${fa.failures.map(_.message).mkString(", ")} - ${fa.failures.map(stackTrace).mkString("\n\n")}",
          _ => ""
        )
      } {
        result.fold[A](
          _ => throw new TestFailedException("Could not extract value from failed Attempt", 10),
          identity
        )
      }
    }

    def leftValue()(implicit ec: ExecutionContext): FailedAttempt = {
      val result = Await.result(attempt.asFuture, 5.seconds)
      withClue {
        result.fold(
          _ => "",
          a => s"$a"
        )
      } {
        result.fold[FailedAttempt](
          identity,
          failed => throw new TestFailedException("Cannot extract failure from successful Attempt", 10)
        )
      }
    }

    def isSuccessfulAttempt()(implicit ec: ExecutionContext): Boolean = {
      Await.result(attempt.asFuture, 5.seconds).fold (
        _ => false,
        _ => true
      )
    }

    def isFailedAttempt()(implicit ec: ExecutionContext): Boolean = {
      !isSuccessfulAttempt()
    }
  }
}
