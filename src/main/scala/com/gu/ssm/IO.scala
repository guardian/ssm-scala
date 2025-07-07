package com.gu.ssm

import com.gu.ssm.aws.{EC2, RDS, SSM, STS}
import com.gu.ssm.utils.attempt.{ArgumentsError, Attempt, Failure}
import software.amazon.awssdk.services.ec2.Ec2AsyncClient
import software.amazon.awssdk.services.rds.RdsAsyncClient
import software.amazon.awssdk.services.ssm.SsmAsyncClient
import software.amazon.awssdk.services.sts.StsAsyncClient

import scala.concurrent.ExecutionContext

object IO {
  def resolveInstances(executionTarget: ExecutionTarget, ec2Client: Ec2AsyncClient)(implicit ec: ExecutionContext): Attempt[List[Instance]] = {
    executionTarget.instances.map( instances =>
      EC2.resolveInstanceIds(instances, ec2Client)
    ).orElse {
      executionTarget.tagValues.map(EC2.resolveByTags(_, ec2Client))
    }.getOrElse(Attempt.Left(Failure("Unable to resolve execution target", "You must provide an execution target (instance(s) or tags)", ArgumentsError)))
  }

  def resolveRDSTunnelTarget(target: TunnelTargetWithRDSTags, rdsClient: RdsAsyncClient)(implicit ec: ExecutionContext): Attempt[TunnelTargetWithHostName] = {
    RDS.resolveByTags(target.remoteTags.toList, rdsClient).flatMap {
      case rdsInstance :: Nil => Attempt.Right(TunnelTargetWithHostName(target.localPort, rdsInstance.hostname, rdsInstance.port, target.remoteTags))
      case Nil => Attempt.Left(Failure("Could not find target from tags", s"We could not find an RDS instance with the tags: ${target.remoteTags.mkString(", ")}", ArgumentsError))
      case tooManyInstances =>
        Attempt.Left(Failure("More than one tunnel target resolved from tags", s"We expected to find a single target, but there was more than one tunnel target resolved from the tags: ${target.remoteTags.mkString(", ")}", ArgumentsError))
    }
  }

  def executeOnInstances(instanceIds: List[InstanceId], username: String, cmd: String, client: SsmAsyncClient)(implicit ec: ExecutionContext): Attempt[List[(InstanceId, Either[CommandStatus, CommandResult])]] = {
    for {
      cmdId <- SSM.sendCommand(instanceIds, cmd, username, client)
      results <- SSM.getCmdOutputs(instanceIds, cmdId, client)
    } yield results
  }

  def executeOnInstance(instanceId: InstanceId, username: String, script: String, client: SsmAsyncClient)(implicit ec: ExecutionContext): Attempt[Either[CommandStatus, CommandResult]] = {
    for {
      cmdId <- SSM.sendCommand(List(instanceId), script, username, client)
      result <- SSM.getCmdOutput(instanceId, cmdId, client).map{ case (_, result) => result }
    } yield result
  }

  def executeOnInstanceAsync(instanceId: InstanceId, username: String, script: String, client: SsmAsyncClient)(implicit ec: ExecutionContext): Attempt[String] = {
    for {
      cmdId <- SSM.sendCommand(List(instanceId), script, username, client)
    } yield cmdId
  }

  def tagAsTainted(instanceId: InstanceId, username: String,ec2Client: Ec2AsyncClient)(implicit ec: ExecutionContext): Attempt[Unit] =
    EC2.tagInstance(instanceId, "taintedBy", username, ec2Client)

  def getSSMConfig(ec2Client: Ec2AsyncClient, stsClient: StsAsyncClient, executionTarget: ExecutionTarget)(implicit ec: ExecutionContext): Attempt[SSMConfig] = {
    for {
      instances <- IO.resolveInstances(executionTarget, ec2Client)
      name <- STS.getCallerIdentity(stsClient)
    } yield SSMConfig(instances, name)
  }
}
