package com.gu.ssm

import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceAsync
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsync
import java.time.Instant

import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceAsync
import com.gu.ssm.model.{ExecutionTarget, Instance, InstanceId}


case class Arguments(
  executionTarget: Option[ExecutionTarget],
  toExecute: Option[String],
  profile: Option[String],
  region: Region,
  mode: Option[SsmMode],
  singleInstanceSelectionMode: SingleInstanceSelectionMode,
  isSelectionModeNewest: Boolean,
  isSelectionModeOldest: Boolean
)

object Arguments {
  def empty(): Arguments = Arguments(None, None, None, Region.getRegion(Regions.EU_WEST_1), None, SismUnspecified, false, false)
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
case object InvocationDoesNotExist extends CommandStatus

sealed trait SsmMode
case object SsmCmd extends SsmMode
case object SsmRepl extends SsmMode
case object SsmSsh extends SsmMode

case class CommandResult(stdOut: String, stdErr: String)

case class SSMConfig (
  targets: List[Instance],
  name: String
)

case class AWSClients (
  ssm: AWSSimpleSystemsManagementAsync,
  sts: AWSSecurityTokenServiceAsync,
  ec2: AmazonEC2Async,
  emr: AmazonElasticMapReduceAsync
)

case class ResultsWithInstancesNotFound(results: List[(InstanceId, scala.Either[CommandStatus, CommandResult])], instancesNotFound: List[InstanceId])

sealed trait SingleInstanceSelectionMode
case object SismNewest extends SingleInstanceSelectionMode
case object SismOldest extends SingleInstanceSelectionMode
case object SismUnspecified extends SingleInstanceSelectionMode
