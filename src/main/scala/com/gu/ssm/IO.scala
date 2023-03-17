package com.gu.ssm

import com.amazonaws.regions.Region
import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceAsync
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsync
import com.gu.ssm.aws.{EC2, SSM, STS, RDS}
import com.gu.ssm.utils.attempt.{ArgumentsError, Attempt, Failure}

import scala.concurrent.ExecutionContext
import com.amazonaws.services.rds.AmazonRDSAsync


object IO {
  def resolveInstances(executionTarget: ExecutionTarget, ec2Client: AmazonEC2Async)(implicit ec: ExecutionContext): Attempt[List[Instance]] = {
    executionTarget.instances.map( instances =>
      EC2.resolveInstanceIds(instances, ec2Client)
    ).orElse {
      executionTarget.tagValues.map(EC2.resolveByTags(_, ec2Client))
    }.getOrElse(Attempt.Left(Failure("Unable to resolve execution target", "You must provide an execution target (instance(s) or tags)", ArgumentsError)))
  }

  def resolveRDSInstances(tunnelTarget: TunnelTarget, rdsClient: AmazonRDSAsync)(implicit ec: ExecutionContext): Attempt[List[RDSInstance]] = tunnelTarget match {
    case TunnelTargetWithTags(_, remoteTags, _) =>
      RDS.resolveByTags(remoteTags.toList, rdsClient)
  }

  def executeOnInstances(instanceIds: List[InstanceId], username: String, cmd: String, client: AWSSimpleSystemsManagementAsync)(implicit ec: ExecutionContext): Attempt[List[(InstanceId, Either[CommandStatus, CommandResult])]] = {
    for {
      cmdId <- SSM.sendCommand(instanceIds, cmd, username, client)
      results <- SSM.getCmdOutputs(instanceIds, cmdId, client)
    } yield results
  }

  def executeOnInstance(instanceId: InstanceId, username: String, script: String, client: AWSSimpleSystemsManagementAsync)(implicit ec: ExecutionContext): Attempt[Either[CommandStatus, CommandResult]] = {
    for {
      cmdId <- SSM.sendCommand(List(instanceId), script, username, client)
      result <- SSM.getCmdOutput(instanceId, cmdId, client).map{ case (_, result) => result }
    } yield result
  }

  def executeOnInstanceAsync(instanceId: InstanceId, username: String, script: String, client: AWSSimpleSystemsManagementAsync)(implicit ec: ExecutionContext): Attempt[String] = {
    for {
      cmdId <- SSM.sendCommand(List(instanceId), script, username, client)
    } yield cmdId
  }

  def tagAsTainted(instanceId: InstanceId, username: String,ec2Client: AmazonEC2Async)(implicit ec: ExecutionContext): Attempt[Unit] =
    EC2.tagInstance(instanceId, "taintedBy", username, ec2Client)

  def getSSMConfig(ec2Client: AmazonEC2Async, stsClient: AWSSecurityTokenServiceAsync, executionTarget: ExecutionTarget)(implicit ec: ExecutionContext): Attempt[SSMConfig] = {
    for {
      instances <- IO.resolveInstances(executionTarget, ec2Client)
      name <- STS.getCallerIdentity(stsClient)
    } yield SSMConfig(instances, name)
  }
}
