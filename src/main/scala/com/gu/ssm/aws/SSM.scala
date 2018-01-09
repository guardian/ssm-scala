package com.gu.ssm.aws

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Region
import com.amazonaws.services.simplesystemsmanagement.model._
import com.amazonaws.services.simplesystemsmanagement.{AWSSimpleSystemsManagementAsync, AWSSimpleSystemsManagementAsyncClientBuilder}
import com.gu.ssm.{CommandStatus, _}
import com.gu.ssm.aws.AWS.asFuture

import collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._


object SSM {
  def client(profileName: String, region: Region): AWSSimpleSystemsManagementAsync = {
    AWSSimpleSystemsManagementAsyncClientBuilder.standard()
      .withCredentials(new ProfileCredentialsProvider(profileName))
      .withRegion(region.getName)
      .build()
  }

  def sendCommand(instances: List[Instance], cmd: String, username: String, client: AWSSimpleSystemsManagementAsync)(implicit ec: ExecutionContext): Future[String] = {
    val parameters = Map("commands" -> List(cmd).asJava).asJava
    val sendCommandRequest = new SendCommandRequest()
      .withComment(s"Command submitted by $username")
      .withInstanceIds(instances.map(_.id).asJava)
      .withDocumentName("AWS-RunShellScript")
      .withParameters(parameters)
    asFuture(client.sendCommandAsync)(sendCommandRequest).map(extractCommandId)
  }

  def extractCommandId(sendCommandResult: SendCommandResult): String = {
    sendCommandResult.getCommand.getCommandId
  }

  def getCommandInvocation(instance: Instance, commandId: String, client: AWSSimpleSystemsManagementAsync)(implicit ec: ExecutionContext): Future[Either[CommandStatus, CommandResult]] = {
    val request = new GetCommandInvocationRequest()
      .withCommandId(commandId)
      .withInstanceId(instance.id)
    asFuture(client.getCommandInvocationAsync)(request).map(extractCommandResult)
  }

  def extractCommandResult(getCommandInvocationResult: GetCommandInvocationResult): Either[CommandStatus, CommandResult] = {
    commandStatus(getCommandInvocationResult.getStatusDetails) match {
      case Success =>
        Right(CommandResult(getCommandInvocationResult.getStandardOutputContent, getCommandInvocationResult.getStandardErrorContent))
      case Failed =>
        Right(CommandResult(getCommandInvocationResult.getStandardOutputContent, getCommandInvocationResult.getStandardErrorContent))
      case status =>
        Left(status)
    }
  }

  def getCmdOutput(instance: Instance, commandId: String, client: AWSSimpleSystemsManagementAsync)(implicit ec: ExecutionContext): Future[(Instance, Either[CommandStatus, CommandResult])] = {
    Util.retryUntil(30, 500.millis, "Attempting to get command output")(() => getCommandInvocation(instance, commandId, client))(_.isRight).map(instance -> _)
  }

  def getCmdOutputs(instances: List[Instance], commandId: String, client: AWSSimpleSystemsManagementAsync)(implicit ec: ExecutionContext): Future[List[(Instance, Either[CommandStatus, CommandResult])]] = {
    Future.traverse(instances)(getCmdOutput(_, commandId, client))
  }

  def commandStatus(statusDetail: String): CommandStatus = {
    statusDetail match {
      case "Pending" =>
        Pending
      case "InProgress" =>
        InProgress
      case "Delayed" =>
        Delayed
      case "Success" =>
        Success
      case "DeliveryTimedOut" =>
        DeliveryTimedOut
      case "ExecutionTimedOut" =>
        ExecutionTimedOut
      case "Failed" =>
        Failed
      case "Canceled" =>
        Canceled
      case "Undeliverable" =>
        Undeliverable
      case "Terminated" =>
        Terminated
      case _ =>
        throw new RuntimeException(s"Unexpected command status $statusDetail")
    }
  }
}
