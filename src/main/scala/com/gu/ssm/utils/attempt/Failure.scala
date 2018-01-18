package com.gu.ssm.utils.attempt


case class FailedAttempt(failures: List[Failure]) {
  def exitCode: Int = failures.map(_.exitCode.code).max
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
  exitCode: ExitCode,
  context: Option[String] = None,
  throwable: Option[Throwable] = None
) {
  def attempt = FailedAttempt(this)
}

sealed abstract class ExitCode(val code: Int)
case object ErrorCode extends ExitCode(1)
case object ArgumentsError extends ExitCode(2)
case object AwsPermissionsError extends ExitCode(3)
case object AwsError extends ExitCode(4)
case object UnhandledError extends ExitCode(255)
