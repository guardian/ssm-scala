package com.gu.ssm.aws

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.regions.Region
import com.amazonaws.services.simplesystemsmanagement.model._
import com.amazonaws.services.simplesystemsmanagement.{
  AWSSimpleSystemsManagementAsync,
  AWSSimpleSystemsManagementAsyncClientBuilder
}
import com.gu.ssm.aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import com.gu.ssm.utils.attempt.Attempt
import com.gu.ssm.{CommandStatus, _}

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object SSM {
  def client(
      credentialsProvider: AWSCredentialsProvider,
      region: Region
  ): AWSSimpleSystemsManagementAsync = {
    AWSSimpleSystemsManagementAsyncClientBuilder
      .standard()
      .withCredentials(credentialsProvider)
      .withRegion(region.getName)
      .build()
  }

  def sendCommand(
      instanceIds: List[InstanceId],
      cmd: String,
      username: String,
      client: AWSSimpleSystemsManagementAsync
  )(implicit ec: ExecutionContext): Attempt[String] = {
    val parameters = Map("commands" -> List(cmd).asJava).asJava
    val sendCommandRequest = new SendCommandRequest()
      .withComment(s"Command submitted by $username")
      .withInstanceIds(instanceIds.map(_.id).asJava)
      .withDocumentName("AWS-RunShellScript")
      .withParameters(parameters)
    handleAWSErrs(
      awsToScala(client.sendCommandAsync)(sendCommandRequest)
        .map(extractCommandId)
    )
  }

  def extractCommandId(sendCommandResult: SendCommandResult): String = {
    sendCommandResult.getCommand.getCommandId
  }

  def getCommandInvocation(
      instance: InstanceId,
      commandId: String,
      client: AWSSimpleSystemsManagementAsync
  )(implicit
      ec: ExecutionContext
  ): Attempt[Either[CommandStatus, CommandResult]] = {
    val request = new GetCommandInvocationRequest()
      .withCommandId(commandId)
      .withInstanceId(instance.id)
    handleAWSErrs(
      awsToScala(client.getCommandInvocationAsync)(request)
        .map(extractCommandResult)
        .recover { case _: InvocationDoesNotExistException =>
          Left(InvocationDoesNotExist)
        }
    )
  }

  def extractCommandResult(
      getCommandInvocationResult: GetCommandInvocationResult
  ): Either[CommandStatus, CommandResult] = {
    commandStatus(getCommandInvocationResult.getStatusDetails) match {
      case Success =>
        Right(
          CommandResult(
            getCommandInvocationResult.getStandardOutputContent,
            getCommandInvocationResult.getStandardErrorContent,
            commandFailed = false
          )
        )
      case Failed =>
        Right(
          CommandResult(
            getCommandInvocationResult.getStandardOutputContent,
            getCommandInvocationResult.getStandardErrorContent,
            commandFailed = true
          )
        )
      case status =>
        Left(status)
    }
  }

  def getCmdOutput(
      instance: InstanceId,
      commandId: String,
      client: AWSSimpleSystemsManagementAsync
  )(implicit
      ec: ExecutionContext
  ): Attempt[(InstanceId, Either[CommandStatus, CommandResult])] = {
    for {
      cmdResult <- Attempt.retryUntil(
        delayBetweenRetries = 500.millis,
        () => getCommandInvocation(instance, commandId, client)
      )(_.isRight)
    } yield instance -> cmdResult
  }

  def getCmdOutputs(
      instanceIds: List[InstanceId],
      commandId: String,
      client: AWSSimpleSystemsManagementAsync
  )(implicit
      ec: ExecutionContext
  ): Attempt[List[(InstanceId, Either[CommandStatus, CommandResult])]] = {
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
