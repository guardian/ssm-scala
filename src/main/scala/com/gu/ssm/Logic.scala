package com.gu.ssm

import com.gu.ssm.utils.attempt.{ArgumentsError, FailedAttempt, Failure}

import scala.io.Source


object Logic {
  def generateScript(toExecute: ToExecute): Either[FailedAttempt, String] = {
    val scriptSourceOpt = toExecute.scriptOpt.map(Source.fromFile(_, "UTF-8").mkString)
    toExecute.cmdOpt.orElse(scriptSourceOpt).toRight {
      Failure("No execution commands provided", "You must provide commands to execute (src-file or cmd)", ArgumentsError).attempt
    }
  }

  def extractSASTags(input: String): Either[String, AppStackStage] = {
    input.split(',').toList match {
      case app :: stack :: stage :: Nil =>
        Right(AppStackStage(app, stack, stage))
      case _ =>
        Left("You should provide Stack, App and Stage tags in the format \"stack,app,stage\"")
    }
  }
}
