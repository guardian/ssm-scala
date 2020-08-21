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

  def extractSASTags(tags: Seq[String]): Either[String, List[String]] = {
    if (tags.length < 1 || tags.head.length == 0) Left("Please supply an app, stack and stage tag in any order. For example, -t grafana,PROD,deploy")
    else if (tags.length >= 10) Left("Please provide fewer than 10 tags")
    else Right(tags.toList)
  }

  def checkInstancesList(config: SSMConfig): Either[FailedAttempt, Unit] = config.targets match {
    case List() => Left(FailedAttempt(List(Failure("No instances found", "No instances found", ErrorCode))))
    case _ => Right(Unit)
  }

  def getSSHInstance(instances: List[Instance], sism: SingleInstanceSelectionMode): Either[FailedAttempt, Instance] = {
    instances.sortBy(_.launchInstant) match {
      case Nil => Left(FailedAttempt(Failure(s"Unable to identify a single instance", s"Could not find any instance", UnhandledError)))
      case instance :: Nil => Right(instance)
      case _ :: _ :: _ if sism == SismUnspecified => Left(FailedAttempt(Failure(s"Unable to identify a single instance", s"Error choosing single instance, found ${instances.map(_.id.id).mkString(", ")}.  Use --oldest or --newest to select single instance", UnhandledError)))
      case instances if sism == SismNewest => Right(instances.last) // we know that `instances` is not empty, otherwise first case would have applied, therefore calling `.last` is safe
      case instance :: _ if sism == SismOldest => Right(instance)
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

  def getAddress(instance: Instance, onlyUsePrivateIP: Boolean): Either[FailedAttempt, String] = {
    if (onlyUsePrivateIP) {
      Right(instance.privateIpAddress)
    } else {
      instance.publicIpAddressOpt match {
        case Some(ipAddress) => Right(ipAddress)
        case None => Right(instance.privateIpAddress)
      }
    }
  }

  def getHostKeyEntry(ssmResult: Either[CommandStatus, CommandResult], preferredAlgs: List[String]): Either[FailedAttempt, String] = {
    ssmResult match {
      case Right(result) =>
        val resultLines = result.stdOut.lines
        val preferredKeys = resultLines.filter(hostKey => preferredAlgs.exists(hostKey.startsWith))
        val preferenceOrderedKeys = preferredKeys.toList.sortBy(hostKey => preferredAlgs.indexWhere(hostKey.startsWith))
        preferenceOrderedKeys.headOption match {
          case Some(hostKey) => Right(hostKey)
          case None => Left(Failure(
            "host key with preferred algorithm not found",
            s"The remote instance did not return a host key with any preferred algorithm (preferred: $preferredAlgs)",
            NoHostKey,
            s"The result lines returned from the host:\n${resultLines.mkString("\n")}"
          ).attempt)
        }
      case Left(otherStatus) => Left(Failure("host keys not returned", s"The remote instance failed to return the host keys within the timeout window (status: $otherStatus)", AwsError).attempt)
    }
  }

}
