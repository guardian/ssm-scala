package com.gu.ssm

import com.amazonaws.regions.{Region, Regions}


case class Instance(id: String)

case class Arguments(instances: List[Instance], command: Option[String], profile: Option[String], region: Region)
object Arguments {
  def empty(): Arguments = Arguments(Nil, None, None, Region.getRegion(Regions.EU_WEST_1))
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
