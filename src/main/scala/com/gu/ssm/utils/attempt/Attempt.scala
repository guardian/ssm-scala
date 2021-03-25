package com.gu.ssm.utils.attempt

import java.util.{Timer, TimerTask}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal


/**
  * Represents a value that will need to be calculated using an asynchronous
  * computation that may fail.
  */
case class Attempt[A] private (underlying: Future[Either[FailedAttempt, A]]) {
  /**
    * Change the value within an attempt
    */
  def map[B](f: A => B)(implicit ec: ExecutionContext): Attempt[B] =
    flatMap(a => Attempt.Right(f(a)))

  /**
    * Create an Attempt by combining this with the result of a dependant operation
    * that returns a new Attempt.
    */
  def flatMap[B](f: A => Attempt[B])(implicit ec: ExecutionContext): Attempt[B] = Attempt {
    asFuture.flatMap {
      case Right(a) => f(a).asFuture
      case Left(e) => Future.successful(Left(e))
    }
  }

  /**
    * Produce a value from an Attempt regardless of whether it failed or succeeded.
    *
    * Note that Attempts are asynchronous so this must return a Future.
    */
  def fold[B](failure: FailedAttempt => B, success: A => B)(implicit ec: ExecutionContext): Future[B] = {
    asFuture.map(_.fold(failure, success))
  }

  /**
    * Combine this Attempt with another attempt without dependencies (in parallel).
    */
  def map2[B, C](bAttempt: Attempt[B])(f: (A, B) => C)(implicit ec: ExecutionContext): Attempt[C] = {
    Attempt.map2(this, bAttempt)(f)
  }

  /**
    * If there is an error in the Future itself (e.g. a timeout) we convert it to a
    * Left so we have a consistent error representation. Unfortunately, this means
    * the error isn't being handled properly so we're left with just the information
    * provided by the exception.
    *
    * Try to avoid hitting this method's failure case by always handling Future errors
    * and creating a suitable failure instance for the problem.
    */
  def asFuture(implicit ec: ExecutionContext): Future[Either[FailedAttempt, A]] = {
    underlying recover { case err =>
      val apiErrors = FailedAttempt(Failure(err.getMessage, "Unexpected error", UnhandledError, err))
      scala.Left(apiErrors)
    }
  }

  def delay(delay: FiniteDuration)(implicit ec: ExecutionContext): Attempt[A] = {
    Attempt.delay(delay).flatMap(_ => this)
  }

  def onComplete[B](callback: Either[FailedAttempt, A] => B)(implicit ec: ExecutionContext): Unit = {
    this.asFuture.onComplete {
      case util.Failure(e) =>
        throw new IllegalStateException("Unexpected error handling was bypassed")
      case util.Success(either) =>
        callback(either)
    }
  }
}

object Attempt {
  def map2[A, B, C](aAttempt: Attempt[A], bAttempt: Attempt[B])(f: (A, B) => C)(implicit ec: ExecutionContext): Attempt[C] = {
    for {
      a <- aAttempt
      b <- bAttempt
    } yield f(a, b)
  }

  /**
    * Changes generated `List[Attempt[A]]` to `Attempt[List[A]]` via provided
    * traversal function (like `Future.traverse`).
    *
    * This implementation returns the first failure in the resulting list,
    * or the successful result.
    */
  def traverse[A, B](as: List[A])(f: A => Attempt[B])(implicit ec: ExecutionContext): Attempt[List[B]] = {
    as.foldRight[Attempt[List[B]]](Right(Nil))(f(_).map2(_)(_ :: _))
  }

  /**
    * Using the provided traversal function, sequence the resulting attempts
    * into a list that preserves failures.
    *
    * This is useful if failure is acceptable in part of the application.
    */
  def traverseWithFailures[A, B](as: List[A])(f: A => Attempt[B])(implicit ec: ExecutionContext): Attempt[List[Either[FailedAttempt, B]]] = {
    sequenceWithFailures(as.map(f))
  }

  /**
    * As with `Future.sequence`, changes `List[Attempt[A]]` to `Attempt[List[A]]`.
    *
    * This implementation returns the first failure in the list, or the successful result.
    */
  def sequence[A](responses: List[Attempt[A]])(implicit ec: ExecutionContext): Attempt[List[A]] = {
    traverse(responses)(identity)
  }

  /**
    * Sequence these attempts into a list that preserves failures.
    *
    * This is useful if failure is acceptable in part of the application.
    */
  def sequenceWithFailures[A](attempts: List[Attempt[A]])(implicit ec: ExecutionContext): Attempt[List[Either[FailedAttempt, A]]] = {
    Async.Right(Future.traverse(attempts)(_.asFuture))
  }

  def fromEither[A](e: Either[FailedAttempt, A]): Attempt[A] =
    Attempt(Future.successful(e))

  def fromOption[A](optA: Option[A], ifNone: FailedAttempt): Attempt[A] =
    fromEither(optA.toRight(ifNone))

  /**
    * Convert a plain `Future` value to an attempt by providing a recovery handler.
    */
  def fromFuture[A](future: Future[A])(recovery: PartialFunction[Throwable, FailedAttempt])(implicit ec: ExecutionContext): Attempt[A] = {
    Attempt {
      future
        .map(scala.Right(_))
        .recover { case t =>
          scala.Left(recovery(t))
        }
    }
  }

  /**
    * Discard failures from a list of attempts.
    *
    * **Use with caution**.
    */
  def successfulAttempts[A](attempts: List[Attempt[A]])(implicit ec: ExecutionContext): Attempt[List[A]] = {
    Attempt.Async.Right {
      Future.traverse(attempts)(_.asFuture).map(_.collect { case Right(a) => a })
    }
  }

  /**
    * Returns a successful attempt after a delay. Can be chained with other Attempts to delay those.
    */
  def delay(delay: FiniteDuration)(implicit ctx: ExecutionContext): Attempt[Unit] = {
    val timer = new Timer()
    val prom = Promise[Unit]()
    val unitTask = new TimerTask {
      def run(): Unit = {
        ctx.execute(() => prom.complete(util.Success(())))
      }
    }
    timer.schedule(unitTask, delay.toMillis)
    Attempt.fromFuture(prom.future) {
      case NonFatal(e) => Failure("failed to run delay task", "Internal error while delaying operations", ErrorCode, e).attempt
    }
  }

  /**
    * Retry an attempt until the condition is met.
    *
    * Note that this will fail immediately with the failure if a FailedAttempt is returned,
    * this function is for testing the successful value.
    */
  def retryUntil[A](delayBetweenRetries: FiniteDuration, attemptA: () => Attempt[A])(condition: A => Boolean)
              (implicit ec: ExecutionContext): Attempt[A] = {
    def loop(a: A, attemptCount: Int): Attempt[A] = {
      if (condition(a)) {
        Attempt.Right(a)
      } else {
        for {
          _ <- delay(delayBetweenRetries)
          nextA <- attemptA()
          result <- loop(nextA, attemptCount + 1)
        } yield result
      }
    }

    for {
      initialA <- attemptA()
      result <- loop(initialA, 1)
    } yield result
  }

  /**
    * Create an Attempt instance from a "good" value.
    */
  def Right[A](a: A): Attempt[A] =
    Attempt(Future.successful(scala.Right(a)))

  /**
    * Create an Attempt failure from an Failure instance, representing the possibility of multiple failures.
    */
  def Left[A](errs: FailedAttempt): Attempt[A] =
    Attempt(Future.successful(scala.Left(errs)))
  /**
    * Syntax sugar to create an Attempt failure if there's only a single error.
    */
  def Left[A](err: Failure): Attempt[A] =
    Attempt(Future.successful(scala.Left(FailedAttempt(err))))

  /**
    * Asyncronous versions of the Attempt Right/Left helpers for when you have
    * a Future that returns a good/bad value directly.
    */
  object Async {
    /**
      * Create an Attempt from a Future of a good value.
      */
    def Right[A](fa: Future[A])(implicit ec: ExecutionContext): Attempt[A] =
      Attempt(fa.map(scala.Right(_)))

    /**
      * Create an Attempt from a known failure in the future. For example,
      * if a piece of logic fails but you need to make a Database/API call to
      * get the failure information.
      */
    def Left[A](ferr: Future[FailedAttempt])(implicit ec: ExecutionContext): Attempt[A] =
      Attempt(ferr.map(scala.Left(_)))
  }
}
