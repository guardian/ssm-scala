package com.gu.ssm

import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsync
import com.gu.ssm.aws.{EC2, SSM}

import scala.concurrent.{ExecutionContext, Future}


object IO {
  def resolveInstances(executionTarget: ExecutionTarget, ec2Client: AmazonEC2Async)(implicit ec: ExecutionContext): Future[List[Instance]] = {
    executionTarget.instances.map(Future.successful).orElse {
      executionTarget.ass.map { ass =>
        EC2.resolveSASInstances(ass, ec2Client)
      }
    }.getOrElse(throw new RuntimeException("No execution target provided"))
  }

  def executeOnInstances(instances: List[Instance], username: String, script: String, client: AWSSimpleSystemsManagementAsync)(implicit ec: ExecutionContext): Future[List[(Instance, Either[CommandStatus, CommandResult])]] = {
    for {
      cmdId <- SSM.sendCommand(instances, script, username, client)
      results <- SSM.getCmdOutputs(instances, cmdId, client)
    } yield results
  }
}
