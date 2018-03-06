package com.gu.ssm

import com.gu.ssm.utils.attempt.FailedAttempt


object UI {
  def output(extendedResults: ResultsWithInstancesNotFound): Unit = {
    if(extendedResults.instancesNotFound.nonEmpty){
      UI.printErr(s"The following instance(s) could not be found: ${extendedResults.instancesNotFound.map(_.id).mkString(", ")}\n")
    }
    extendedResults.results.foreach { case (instance, result) =>
      UI.printMetadata(s"========= ${instance.id} =========")
      if (result.isLeft) {
        UI.printErr(result.left.get.toString)
      } else {
        val output = result.right.get
        UI.printMetadata(s"STDOUT:")
        println(output.stdOut)
        UI.printMetadata(s"STDERR:")
        UI.printErr(output.stdErr)
      }
    }
  }

  def sshOutput(rawOutput: Boolean)(result: (InstanceId, String)): Unit = {
    if (rawOutput){
      print(result._2)
    } else {
      UI.printMetadata(s"========= ${result._1.id} =========")
      UI.printMetadata(s"STDOUT:")
      println(result._2)
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
