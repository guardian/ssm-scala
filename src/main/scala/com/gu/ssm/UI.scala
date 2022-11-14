package com.gu.ssm

import java.io.{ByteArrayOutputStream, PrintWriter}
import com.gu.ssm.utils.attempt.{ErrorCode, ExitCode, FailedAttempt}

import scala.collection.mutable

sealed trait Output {
  def text: String
  def newline: Boolean = true
}
case class Out(text: String, override val newline: Boolean = true) extends Output
case class Metadata(text: String) extends Output
case class Err(text: String, throwable: Option[Throwable] = None) extends Output
case class Verbose(text: String) extends Output

case class ProgramResult(output: Seq[Output], nonZeroExitCode: Option[ExitCode] = None)
object ProgramResult {
  def convertErrorToResult(programResult: Either[FailedAttempt, ProgramResult]): ProgramResult = {
    programResult.fold (
      failedAttempt => ProgramResult(UI.outputFailure(failedAttempt), Some(failedAttempt.exitCode)),
      identity
    )
  }
}

object UI {
  implicit class RichString(val s: String) extends AnyVal {
    def colour(colour: String): String = {
      colour + s + Console.RESET
    }
  }
  implicit class RichThrowable(val t: Throwable) extends AnyVal {
    def getAsString: String = {
      val baos = new ByteArrayOutputStream()
      val pw = new PrintWriter(baos)
      t.printStackTrace(pw)
      pw.close()
      baos.toString
    }
  }

  def output(extendedResults: ResultsWithInstancesNotFound): ProgramResult = {
    val buffer = mutable.Buffer.empty[Output]
    if(extendedResults.instancesNotFound.nonEmpty){
      buffer += Err(s"The following instance(s) could not be found: ${extendedResults.instancesNotFound.map(_.id).mkString(", ")}\n")
    }
    extendedResults.results.flatMap { case (instance, result) =>
      buffer += Metadata(s"========= ${instance.id} =========")
      result match {
        case Left(commandStatus) =>
          buffer += Err(commandStatus.toString)
        case Right(commandStatus) =>
          buffer ++= Seq(
            Metadata(s"STDOUT:"),
            Out(commandStatus.stdOut),
            Metadata(s"STDERR:"),
            Err(commandStatus.stdErr)
          )
      }
    }

    val nonZeroExitCode = if (hasAnyCommandFailed(extendedResults.results)) Some(ErrorCode) else None
    ProgramResult(buffer.toList, nonZeroExitCode)
  }

  def sshOutput(rawOutput: Boolean)(result: (InstanceId, Seq[Output])): ProgramResult = ProgramResult(
    if (rawOutput){
      result._2
    } else {
      Metadata(s"========= ${result._1.id} =========") +: result._2
    }
  )

  def outputFailure(failedAttempt: FailedAttempt): Seq[Output] = {
    failedAttempt.failures.flatMap { failure =>
      Seq(Err(failure.friendlyMessage, failure.throwable)) ++ failure.context.map(Verbose.apply)
    }
  }

  def hasAnyCommandFailed(ssmResults: List[(InstanceId, Either[CommandStatus, CommandResult])]): Boolean = {
    ssmResults.exists { case(_, result) => result.exists(_.commandFailed) }
  }
}

class UI(verbose: Boolean) {
  import UI._

  def printAll(output: Seq[Output]): Unit = print(output: _*)

  def print(output: Output*): Unit = {
    output.foreach {
      case Out(text, true) => System.out.println(text)
      case Out(text, false) => System.out.print(text)
      case Metadata(text) => printMetadata(text)
      case Err(text, maybeThrowable) =>
        printErr(text)
        maybeThrowable.foreach { t => printVerbose(t.getAsString) }
      case Verbose(text) => printVerbose(text)
    }
  }

  def printVerbose(text: String): Unit = {
    if (verbose) System.err.println(text.colour(Console.BLUE))
  }

  def printMetadata(text: String): Unit = {
    System.err.println(text.colour(Console.CYAN))
  }

  def printErr(text: String): Unit = {
    System.err.println(text.colour(Console.YELLOW))
  }

}
