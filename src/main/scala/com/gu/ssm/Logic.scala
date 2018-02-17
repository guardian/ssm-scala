package com.gu.ssm

import java.io.File

import com.amazonaws.regions.Region
import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceAsync
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsync
import com.gu.ssm.aws.{EC2, SSM, STS}
import com.gu.ssm.utils.attempt.{ErrorCode, FailedAttempt, Failure, UnhandledError}

import scala.io.Source

object Logic {
  def generateScript(toExecute: Either[String, File]): String = {
    toExecute match {
      case Right(script) => Source.fromFile(script, "UTF-8").mkString
      case Left(cmd) => cmd
    }
  }

  def extractSASTags(input: String): Either[String, AppStackStage] = {
    input.split(',').toList match {
      case app :: stack :: stage :: Nil =>
        Right(AppStackStage(app, stack, stage))
      case _ =>
        Left("You should provide Stack, App and Stage tags in the format \"stack,app,stage\"")
    }
  }

  def checkInstancesList(config: SSMConfig): Either[FailedAttempt, Unit] = config.targets match {
    case List() => Left(FailedAttempt(List(Failure("No instances found", "No instances found", ErrorCode, None, None))))
    case _ => Right(Unit)
  }

  def getSSHInstance(instances: List[Instance], singleInstanceSelectionModeOpt: Option[String]): Either[FailedAttempt, Instance] = {
    val validInstances = instances
      .filter(_.publicIpAddressOpt.isDefined)
    if (instances.isEmpty) {
      Left(FailedAttempt(Failure(s"Unable to identify a single instance", s"Could not find any instance", UnhandledError, None, None)))
    } else {
      validInstances match {
        case Nil => Left(FailedAttempt(Failure(s"Instances with no IPs", s"Found ${instances.map(_.id.id).mkString(", ")} but none are valid targets (instances need public IP addresses)", UnhandledError, None, None)))
        case instance :: Nil => Right(instance)
        case _ :: _ :: _  => {
          singleInstanceSelectionModeOpt match {
            case None => Left(FailedAttempt(Failure(s"Unable to identify a single instance", s"Error choosing single instance, found ${validInstances.map(_.id.id).mkString(", ")}", UnhandledError, None, None)))
            case Some("any") => Right(validInstances.sortBy(_.id.id).head)             // safe .head
            case Some("newest") => Right(validInstances.sortBy(_.launchDateTime).last) // safe .last
            case Some("oldest") => Right(validInstances.sortBy(_.launchDateTime).head) // safe .head
            case Some(thing) => Left(FailedAttempt(Failure(s"Unable to identify a single instance", s"The value of selection is incorrect, given ${thing}, expected: 'any', 'newest' or 'oldest'", UnhandledError, None, None)))
          }
        }
      }
    }
  }

  def getClients(profile: String, region: Region): AWSClients = {
    val ssmClient: AWSSimpleSystemsManagementAsync = SSM.client(profile, region)
    val stsClient: AWSSecurityTokenServiceAsync = STS.client(profile, region)
    val ec2Client: AmazonEC2Async = EC2.client(profile, region)
    AWSClients(ssmClient, stsClient, ec2Client)
  }

  def computeIncorrectInstances(executionTarget: ExecutionTarget, instanceIds: List[InstanceId]): List[InstanceId] =
    executionTarget.instances.getOrElse(List()).filterNot(instanceIds.toSet)

}
