package com.gu.ssm.aws

import com.gu.ssm.aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import com.gu.ssm.utils.attempt.Attempt
import com.gu.ssm.*
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ssm.SsmAsyncClient
import software.amazon.awssdk.services.ssm.model.{GetCommandInvocationRequest, GetCommandInvocationResponse, InvocationDoesNotExistException, SendCommandRequest, SendCommandResponse}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

object SSM {
  def client(credentialsProvider: AwsCredentialsProvider, region: Region): SsmAsyncClient = {
    SsmAsyncClient.builder()
      .credentialsProvider(credentialsProvider)
      .region(region)
      .build()
  }

  def sendCommand(instanceIds: List[InstanceId], cmd: String, username: String, client: SsmAsyncClient)(implicit ec: ExecutionContext): Attempt[String] = {
    val parameters = Map("commands" -> List(cmd).asJava).asJava
    val sendCommandRequest = SendCommandRequest.builder()
      .comment(s"Command submitted by $username")
      .instanceIds(instanceIds.map(_.id).asJava)
      .documentName("AWS-RunShellScript")
      .parameters(parameters)
      .build()
    handleAWSErrs(awsToScala(client.sendCommand(sendCommandRequest))).map(extractCommandId)
  }

  def extractCommandId(sendCommandResult: SendCommandResponse): String = {
    sendCommandResult.command().commandId()
  }

  def getCommandInvocation(instance: InstanceId, commandId: String, client: SsmAsyncClient)(implicit ec: ExecutionContext): Attempt[Either[CommandStatus, CommandResult]] = {
    val request = GetCommandInvocationRequest.builder()
      .commandId(commandId)
      .instanceId(instance.id)
      .build()
    handleAWSErrs(
      awsToScala(client.getCommandInvocation(request))
        .map(extractCommandResult)
        .recover { case _: InvocationDoesNotExistException =>
          Left(InvocationDoesNotExist) }
    )
  }

  def extractCommandResult(getCommandInvocationResult: GetCommandInvocationResponse): Either[CommandStatus, CommandResult] = {
    commandStatus(getCommandInvocationResult.statusDetails()) match {
      case Success =>
        Right(CommandResult(getCommandInvocationResult.standardOutputContent(), getCommandInvocationResult.standardErrorContent(), commandFailed = false))
      case Failed =>
        Right(CommandResult(getCommandInvocationResult.standardOutputContent(), getCommandInvocationResult.standardErrorContent(), commandFailed = true))
      case status =>
        Left(status)
    }
  }

  def getCmdOutput(instance: InstanceId, commandId: String, client: SsmAsyncClient)(implicit ec: ExecutionContext): Attempt[(InstanceId, Either[CommandStatus, CommandResult])] = {
    for {
      cmdResult <- Attempt.retryUntil(delayBetweenRetries = 500.millis, () => getCommandInvocation(instance, commandId, client))(_.isRight)
    } yield instance -> cmdResult
  }

  def getCmdOutputs(instanceIds: List[InstanceId], commandId: String, client: SsmAsyncClient)(implicit ec: ExecutionContext): Attempt[List[(InstanceId, Either[CommandStatus, CommandResult])]] = {
    Attempt.traverse(instanceIds)(getCmdOutput(_, commandId, client))
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
