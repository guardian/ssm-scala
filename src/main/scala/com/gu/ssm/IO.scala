package com.gu.ssm

import com.amazonaws.regions.Region
import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceAsync
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceAsync
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsync
import com.gu.ssm.aws.{EC2, EMR, SSM, STS}
import com.gu.ssm.model._
import com.gu.ssm.utils.attempt.{ArgumentsError, Attempt, Failure}

import scala.concurrent.ExecutionContext


object IO {

  def resolveInstances(
    executionTarget: ExecutionTarget,
    ec2Client: AmazonEC2Async,
    emrClient: AmazonElasticMapReduceAsync
  )(implicit ec: ExecutionContext): Attempt[List[Instance]] = {

    executionTarget match {
      case InstanceIds(ids) =>
        EC2.resolveInstanceIds(ids, ec2Client)

      case ass: AppStackStage =>
        EC2.resolveASSInstances(ass, ec2Client)

      case EMRClusterId(id) =>
        new EMR(emrClient).resolveMasterInstances(id)

      case _ =>
        Attempt.Left(Failure("Unable to resolve execution target", "You must provide an execution target (instance(s), tags or EMR cluster ID)", ArgumentsError))
    }
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

  def tagAsTainted(instanceId: InstanceId, username: String,ec2Client: AmazonEC2Async)(implicit ec: ExecutionContext): Attempt[Unit] =
    EC2.tagInstance(instanceId, "taintedBy", username, ec2Client)

  def getSSMConfig(clients: AWSClients, profile: String, region: Region, executionTarget: ExecutionTarget)(implicit ec: ExecutionContext): Attempt[SSMConfig] = {
    for {
      instances <- IO.resolveInstances(executionTarget, clients.ec2, clients.emr)
      name <- STS.getCallerIdentity(clients.sts)
    } yield SSMConfig(instances, name)
  }
}
