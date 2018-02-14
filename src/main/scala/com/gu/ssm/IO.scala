package com.gu.ssm

import com.amazonaws.regions.Region
import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceAsync
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsync
import com.gu.ssm.aws.{EC2, SSM, STS}
import com.gu.ssm.utils.attempt.{ArgumentsError, Attempt, Failure}

import scala.concurrent.ExecutionContext


object IO {
  def resolveInstances(executionTarget: ExecutionTarget, ec2Client: AmazonEC2Async)(implicit ec: ExecutionContext): Attempt[List[Instance]] = {
    executionTarget.instances.map( instances =>
      EC2.resolveInstanceIds(instances, ec2Client)
    ).orElse {
      executionTarget.ass.map { ass =>
        EC2.resolveASSInstances(ass, ec2Client)
      }
    }.getOrElse(Attempt.Left(Failure("Unable to resolve execution target", "You must provide an execution target (instance(s) or tags)", ArgumentsError)))
  }

  def executeOnInstances(instanceIds: List[InstanceId], username: String, cmd: String, client: AWSSimpleSystemsManagementAsync)(implicit ec: ExecutionContext): Attempt[List[(InstanceId, Either[CommandStatus, CommandResult])]] = {
    for {
      cmdId <- SSM.sendCommand(instanceIds, cmd, username, client)
      results <- SSM.getCmdOutputs(instanceIds, cmdId, client)
    } yield results
  }

  def installSshKey(instanceId: InstanceId, username: String, script: String, client: AWSSimpleSystemsManagementAsync)(implicit ec: ExecutionContext): Attempt[String] = {
    for {
      cmdId <- SSM.sendCommand(List(instanceId), script, username, client)
    } yield cmdId
  }

  def tagAsTainted(instance: InstanceId, username: String,ec2Client: AmazonEC2Async)(implicit ec: ExecutionContext): Attempt[Unit] =
    EC2.tagInstance(instance, "taintedBy", username, ec2Client)

  def getSSMConfig(ec2Client: AmazonEC2Async, stsClient: AWSSecurityTokenServiceAsync, profile: String, region: Region, executionTarget: ExecutionTarget)(implicit ec: ExecutionContext): Attempt[SSMConfig] = {
    for {
      instances <- IO.resolveInstances(executionTarget, ec2Client)
      name <- STS.getCallerIdentity(stsClient)
    } yield SSMConfig(instances, name)
  }

}
