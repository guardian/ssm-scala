package com.gu.ssm

import com.amazonaws.regions.{Region, Regions}


case class InstanceId(id: String) extends AnyVal
case class Instance(id: InstanceId, publicIpAddressOpt: Option[String])
case class AppStackStage(app: String, stack: String, stage: String)
case class ExecutionTarget(instances: Option[List[InstanceId]] = None, ass: Option[AppStackStage] = None)

case class Arguments(executionTarget: Option[ExecutionTarget], toExecute: Option[String], profile: Option[String], region: Region, mode: Option[SsmMode])
object Arguments {
  def empty(): Arguments = Arguments(None, None, None, Region.getRegion(Regions.EU_WEST_1), None)
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

sealed trait SsmMode
case object SsmCmd extends SsmMode
case object SsmRepl extends SsmMode
case object SsmSsh extends SsmMode

case class CommandResult(stdOut: String, stdErr: String)
