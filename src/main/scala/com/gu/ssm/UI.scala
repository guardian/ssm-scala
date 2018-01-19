package com.gu.ssm

import com.gu.ssm.utils.attempt.FailedAttempt


object UI {
  def output(results: List[(InstanceId, scala.Either[CommandStatus, CommandResult])]): Unit = {
    results.foreach { case (instance, result) =>
      UI.printMetadata(s"========= ${instance.id} =========")
      if (result.isLeft) {
        UI.printMetadata(result.left.get.toString)
      } else {
        val output = result.right.get
        UI.printMetadata(s"STDOUT:")
        println(output.stdOut)
        UI.printMetadata(s"STDERR:")
        UI.printErr(output.stdErr)
      }
    }
  }

  def outputFailure(failedAttempt: FailedAttempt): Unit = {
    failedAttempt.failures.foreach { failure =>
      printErr(failure.friendlyMessage)
    }
  }

  def printMetadata(text: String): Unit = {
    System.err.println(text.colour(Console.CYAN))
  }

  def printErr(text: String): Unit = {
    System.err.println(text.colour(Console.YELLOW))
  }

  implicit class RichString(val s: String) extends AnyVal {
    def colour(colour: String): String = {
      colour + s + Console.RESET
    }
  }
}
