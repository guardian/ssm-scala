package com.gu.ssm.utils

import java.util.{Timer, TimerTask}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

object RichFuture {
  def retryUntil[A](maxRetries: Int, delayDuration: FiniteDuration, errMsg: String)(block: () => Future[A])(condition: A => Boolean)(implicit ec: ExecutionContext): Future[A] = {
    def loop(remainingRetries: Int): Future[A] = {
      if (remainingRetries > 0) {
        for {
          _ <- delay(delayDuration.toMillis)(())
          a <- block()
          retried <-
            if (condition(a)) Future.successful(a)
            else {
              loop(remainingRetries - 1)
            }
        } yield retried
      } else {
        throw new RuntimeException(s"Exceeded retries: $errMsg")
      }
    }
    loop(maxRetries)
  }

  def delay[T](delayMs: Long)(block: => T)(implicit ec: ExecutionContext): Future[T] = {
    val promise = Promise[T]()
    val t = new Timer()
    t.schedule(new TimerTask {
      override def run(): Unit = {
        promise.complete(Try(block))
      }
    }, delayMs)
    promise.future
  }
}
