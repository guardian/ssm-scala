package com.gu.ssm

import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsync
import com.gu.ssm.aws.{EC2, SSM}
import com.gu.ssm.utils.attempt.{ArgumentsError, Attempt, FailedAttempt, Failure}

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

  def executeOnInstances(instances: List[InstanceId], username: String, toExecute: ToExecute, client: AWSSimpleSystemsManagementAsync)(implicit ec: ExecutionContext): Attempt[List[(InstanceId, Either[CommandStatus, CommandResult])]] = {
    for {
      script <- Attempt.fromEither(Logic.generateScript(toExecute))
      cmdId <- SSM.sendCommand(instances, script, username, client)
      results <- SSM.getCmdOutputs(instances, cmdId, client)
    } yield results
  }

  def executeOnInstances(instances: List[InstanceId], username: String, cmd: String, client: AWSSimpleSystemsManagementAsync)(implicit ec: ExecutionContext): Attempt[List[(InstanceId, Either[CommandStatus, CommandResult])]] = {
    executeOnInstances(instances, username, ToExecute(cmdOpt = Some(cmd)), client)
  }

  def installSshKey(instances: List[InstanceId], username: String, cmd: String, client: AWSSimpleSystemsManagementAsync)(implicit ec: ExecutionContext): Attempt[String] = {
    for {
      // Get the script first, so that we only tag if we are ready to go
      script <- Attempt.fromEither(Logic.generateScript(ToExecute(cmdOpt = Some(cmd))))
      cmdId <- SSM.sendCommand(instances, script, username, client)
    } yield cmdId
  }

  def tagAsTainted(instances: List[InstanceId], username: String,ec2Client: AmazonEC2Async)(implicit ec: ExecutionContext): Attempt[String] = {
    for {
      // Get the script first, so that we only tag if we are ready to go
      _ <- EC2.tagInstances(instances, "taintedBy", username, ec2Client)
    } yield Unit
  }

}
