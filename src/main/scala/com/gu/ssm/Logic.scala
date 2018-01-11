package com.gu.ssm

import scala.io.Source

object Logic {
  def generateScript(toExecute: ToExecute): String = {
    toExecute.cmdOpt.orElse(toExecute.scriptOpt.map(Source.fromFile(_, "UTF-8").mkString)).get
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
