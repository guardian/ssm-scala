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

  def getSSHInstance(instances: List[Instance], sism: Option[SingleInstanceSelectionMode]): Either[FailedAttempt, Instance] = {
    if (instances.isEmpty) {
      Left(FailedAttempt(Failure(s"Unable to identify a single instance", s"Could not find any instance", UnhandledError, None, None)))
    } else {
      val validInstances = instances
        .filter(_.publicIpAddressOpt.isDefined)
      val validInstanceOrdered = sism match {
        case Some(SismAny) => validInstances.sortBy(_.id.id)
        case Some(SismNewest) => validInstances.sortBy(_.launchDateTime).reverse
        case Some(SismOldest) => validInstances.sortBy(_.launchDateTime)
        case None => validInstances
      }
      validInstanceOrdered match {
        case Nil => Left(FailedAttempt(Failure(s"Instances with no IPs", s"Found ${instances.map(_.id.id).mkString(", ")} but none are valid targets (instances need public IP addresses)", UnhandledError, None, None)))
        case instance :: Nil => Right(instance)
        case _ :: _ :: _ if sism == None => Left(FailedAttempt(Failure(s"Unable to identify a single instance", s"Error choosing single instance, found ${validInstances.map(_.id.id).mkString(", ")}.  Use --selection [any|oldest|newest]?", UnhandledError, None, None)))
        case instance :: _ :: _ => Right(instance)
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

  def singleInstanceSelectionModeConversion(mode: String): Option[SingleInstanceSelectionMode] = {
    mode match {
      case "any" => Some(SismAny)
      case "newest" => Some(SismNewest)
      case "oldest" => Some(SismOldest)
      case _ => None
    }
  }

}
