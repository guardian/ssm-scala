package com.gu.ssm

import java.io.{ByteArrayOutputStream, PrintWriter}

import com.gu.ssm.utils.attempt.{ExitCode, FailedAttempt}

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
  def apply(result: Either[FailedAttempt, Seq[Output]]): ProgramResult = {
    result.fold[ProgramResult] (
      failedAttempt => ProgramResult.apply(UI.outputFailure(failedAttempt), Some(failedAttempt.exitCode)),
      output => ProgramResult.apply(output, None)
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

  def output(extendedResults: ResultsWithInstancesNotFound): Seq[Output] = {
    val buffer = mutable.Buffer.empty[Output]
    if(extendedResults.instancesNotFound.nonEmpty){
      buffer += Err(s"The following instance(s) could not be found: ${extendedResults.instancesNotFound.map(_.id).mkString(", ")}\n")
    }
    extendedResults.results.flatMap { case (instance, result) =>
      buffer += Metadata(s"========= ${instance.id} =========")
      if (result.isLeft) {
        buffer += Err(result.left.get.toString)
      } else {
        val output = result.right.get
        buffer ++= Seq(
          Metadata(s"STDOUT:"),
          Out(output.stdOut),
          Metadata(s"STDERR:"),
          Err(output.stdErr)
        )
      }
    }
    buffer.toList
  }

  def sshOutput(rawOutput: Boolean)(result: (InstanceId, Seq[Output])): Seq[Output] = {
    if (rawOutput){
      result._2
    } else {
      Metadata(s"========= ${result._1.id} =========") +: result._2
    }
  }

  def outputFailure(failedAttempt: FailedAttempt): Seq[Output] = {
    failedAttempt.failures.flatMap { failure =>
      Seq(Err(failure.friendlyMessage, failure.throwable)) ++ failure.context.map(Verbose.apply)
    }
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
