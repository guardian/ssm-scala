package com.gu.ssm

import java.io.File

import com.amazonaws.regions.{Region, Regions}


case class Instance(id: String)
case class AppStackStage(app: String, stack: String, stage: String)
case class ExecutionTarget(instances: Option[List[Instance]] = None, ass: Option[AppStackStage] = None)

case class ToExecute(cmdOpt: Option[String] = None, scriptOpt: Option[File] = None)

case class Arguments(executionTarget: Option[ExecutionTarget], toExecute: Option[ToExecute], profile: Option[String], region: Region, interactive: Boolean)
object Arguments {
  def empty(): Arguments = Arguments(None, None, None, Region.getRegion(Regions.EU_WEST_1), interactive = false)
}

sealed trait CommandStatus
case object Pending extends CommandStatus
case object InProgress extends CommandStatus
case object Delayed extends CommandStatus
case object Success extends CommandStatus
case object DeliveryTimedOut extends CommandStatus
case object ExecutionTimedOut extends CommandStatus
case object Failed extends CommandStatus
case object Canceled extends CommandStatus
case object Undeliverable extends CommandStatus
case object Terminated extends CommandStatus

case class CommandResult(stdOut: String, stdErr: String)
