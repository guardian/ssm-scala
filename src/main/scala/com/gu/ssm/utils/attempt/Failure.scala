package com.gu.ssm.utils.attempt


case class FailedAttempt(failures: List[Failure]) {
  def exitCode: ExitCode = failures.map(_.exitCode).maxBy(_.code)
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
object Failure {
  def apply(message: String,
            friendlyMessage: String,
            exitCode: ExitCode): Failure = apply(message, friendlyMessage, exitCode, None, None)
  def apply(message: String,
            friendlyMessage: String,
            exitCode: ExitCode,
            context: String): Failure = apply(message, friendlyMessage, exitCode, Some(context), None)
  def apply(message: String,
            friendlyMessage: String,
            exitCode: ExitCode,
            throwable: Throwable): Failure = apply(message, friendlyMessage, exitCode, None, Some(throwable))
  def apply(message: String,
            friendlyMessage: String,
            exitCode: ExitCode,
            context: String,
            throwable: Throwable): Failure = apply(message, friendlyMessage, exitCode, Some(context), Some(throwable))
}

sealed abstract class ExitCode(val code: Int)
case object ErrorCode extends ExitCode(1)
case object ArgumentsError extends ExitCode(2)
case object AwsPermissionsError extends ExitCode(3)
case object AwsError extends ExitCode(4)
case object NoIpAddress extends ExitCode(5)
case object NoHostKey extends ExitCode(6)
case object UnhandledError extends ExitCode(255)