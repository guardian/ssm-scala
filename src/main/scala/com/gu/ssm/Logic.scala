package com.gu.ssm

import java.io.File

import com.amazonaws.regions.Region
import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceAsync
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsync
import com.gu.ssm.aws.{EC2, SSM, STS}
import com.gu.ssm.utils.attempt._

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
        Left("You should provide app, stack and stage tags in the format \"app,stack,stage\"")
    }
  }

  def checkInstancesList(config: SSMConfig): Either[FailedAttempt, Unit] = config.targets match {
    case List() => Left(FailedAttempt(List(Failure("No instances found", "No instances found", ErrorCode, None, None))))
    case _ => Right(Unit)
  }

  def getSSHInstance(instances: List[Instance], sism: SingleInstanceSelectionMode, usePrivate: Boolean): Either[FailedAttempt, Instance] = {
    if (instances.isEmpty) {
      Left(FailedAttempt(Failure(s"Unable to identify a single instance", s"Could not find any instance", UnhandledError, None, None)))
    } else {
      val validInstancesWithOrder = instances
        .filter(i => usePrivate || i.publicIpAddressOpt.isDefined)
        .sortBy(_.launchInstant)
      validInstancesWithOrder match {
        case Nil => Left(FailedAttempt(Failure(s"Instances with no IPs matching filter", s"Found ${instances.map(_.id.id).mkString(", ")} but no instances have public IPs (use '--private' if internal address is routable)", UnhandledError, None, None)))
        case instance :: Nil => Right(instance)
        case _ :: _ :: _ if sism == SismUnspecified => Left(FailedAttempt(Failure(s"Unable to identify a single instance", s"Error choosing single instance, found ${validInstancesWithOrder.map(_.id.id).mkString(", ")}.  Use --oldest or --newest to select single instance", UnhandledError, None, None)))
        case instances if sism == SismNewest && instances.nonEmpty => Right(instances.last)
        case instance :: _ if sism == SismOldest => Right(instance)
      }
    }
  }

  def getClients(profile: Option[String], region: Region): AWSClients = {
    val ssmClient: AWSSimpleSystemsManagementAsync = SSM.client(profile, region)
    val stsClient: AWSSecurityTokenServiceAsync = STS.client(profile, region)
    val ec2Client: AmazonEC2Async = EC2.client(profile, region)
    AWSClients(ssmClient, stsClient, ec2Client)
  }

  def computeIncorrectInstances(executionTarget: ExecutionTarget, instanceIds: List[InstanceId]): List[InstanceId] =
    executionTarget.instances.getOrElse(List()).filterNot(instanceIds.toSet)

  def getAddress(instance: Instance, usePrivate: Boolean): Either[FailedAttempt, String] = {
    if (usePrivate) {
      Right(instance.privateIpAddress)
    } else {
      instance.publicDomainNameOpt match {
        case Some(domainName) => Right(domainName)
        case None =>
          instance.publicIpAddressOpt.toRight(FailedAttempt(Failure("No public IP address", "No public IP address", NoIpAddress, None, None)))
      }
    }
  }

}
