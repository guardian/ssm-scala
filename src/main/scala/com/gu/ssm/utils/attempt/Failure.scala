package com.gu.ssm.utils.attempt


case class FailedAttempt(failures: List[Failure]) {
  def statusCode: Int = failures.map(_.exitCode).max
}

object FailedAttempt {
  def apply(error: Failure): FailedAttempt = {
    FailedAttempt(List(error))
  }
  def apply(errors: Seq[Failure]): FailedAttempt = {
    FailedAttempt(errors.toList)
  }
}

case class Failure(
  message: String,
  friendlyMessage: String,
  exitCode: Int,
  context: Option[String] = None,
  throwable: Option[Throwable] = None
) {
  def attempt = FailedAttempt(this)
}
