package com.gu.ssm

import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceAsync
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsync
import java.time.Instant
import com.amazonaws.services.rds.AmazonRDSAsync

case class InstanceId(id: String) extends AnyVal
case class Instance(id: InstanceId, publicDomainNameOpt: Option[String], publicIpAddressOpt: Option[String], privateIpAddress: String, launchInstant: Instant)
case class AppStackStage(app: String, stack: String, stage: String)
case class ExecutionTarget(instances: Option[List[InstanceId]] = None, tagValues: Option[List[String]] = None)
case class RDSInstanceId(id: String) extends AnyVal
case class RDSInstance(id: RDSInstanceId, hostname: String, port: Int)

case class Arguments(
  verbose: Boolean,
  executionTarget: Option[ExecutionTarget],
  toExecute: Option[String],
  profile: Option[String],
  region: Region,
  mode: Option[SsmMode],
  targetInstanceUser: Option[String],
  singleInstanceSelectionMode: SingleInstanceSelectionMode,
  isSelectionModeNewest: Boolean,
  isSelectionModeOldest: Boolean,
  usePrivateIpAddress: Boolean,
  rawOutput: Boolean,
  bastionInstance: Option[ExecutionTarget],
  bastionPortNumber: Option[Int],
  bastionUser: Option[String],
  targetInstancePortNumber: Option[Int],
  useAgent: Option[Boolean],
  hostKeyAlgPreference: List[String],
  sourceFile: Option[String],
  targetFile: Option[String],
  tunnelThroughSystemsManager: Boolean,
  useDefaultCredentialsProvider: Boolean,
  tunnelTarget: Option[TunnelTargetWithHostName],
  rdsTunnelTarget: Option[TunnelTargetWithRDSTags]
)

object Arguments {
  val targetInstanceDefaultUser = "ubuntu"
  val bastionDefaultUser = "ubuntu"
  val defaultHostKeyAlgPreference = List("ecdsa-sha2-nistp256", "ssh-rsa")

  def empty(): Arguments = Arguments(
    verbose = false,
    executionTarget = None,
    toExecute = None,
    profile = None,
    region = Region.getRegion(Regions.EU_WEST_1),
    mode = None,
    targetInstanceUser = Some(targetInstanceDefaultUser),
    singleInstanceSelectionMode = SismUnspecified,
    isSelectionModeNewest = false,
    isSelectionModeOldest = false,
    usePrivateIpAddress = false,
    rawOutput = true,
    bastionInstance = None,
    bastionPortNumber = None,
    bastionUser = Some(bastionDefaultUser),
    targetInstancePortNumber = None,
    useAgent = None,
    hostKeyAlgPreference = defaultHostKeyAlgPreference,
    sourceFile = None,
    targetFile = None,
    tunnelThroughSystemsManager = true,
    useDefaultCredentialsProvider = false,
    tunnelTarget = None,
    rdsTunnelTarget = None
  )
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
case object SsmScp extends SsmMode

case class CommandResult(stdOut: String, stdErr: String, commandFailed: Boolean)

case class SSMConfig (
  targets: List[Instance],
  name: String
)

case class AWSClients (
  ssmClient: AWSSimpleSystemsManagementAsync,
  stsClient: AWSSecurityTokenServiceAsync,
  ec2Client: AmazonEC2Async,
  rdsClient: AmazonRDSAsync
)

case class ResultsWithInstancesNotFound(
  results: List[(InstanceId, scala.Either[CommandStatus, CommandResult])],
  instancesNotFound: List[InstanceId]
)

sealed trait SingleInstanceSelectionMode
case object SismNewest extends SingleInstanceSelectionMode
case object SismOldest extends SingleInstanceSelectionMode
case object SismUnspecified extends SingleInstanceSelectionMode

sealed trait TunnelTarget
case class TunnelTargetWithRDSTags(localPort: Int, remoteTags: Seq[String]) extends TunnelTarget
case class TunnelTargetWithHostName(localPort: Int, remoteHostName: String, remotePort: Int, remoteTags: Seq[String] = Seq.empty) extends TunnelTarget
