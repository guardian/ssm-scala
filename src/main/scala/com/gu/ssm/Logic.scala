package com.gu.ssm

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.auth.profile.ProfileCredentialsProvider

import java.time.Instant
import java.io.File
import com.amazonaws.regions.Region
import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceAsync
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsync
import com.gu.ssm.aws.{EC2, SSM, STS}
import com.gu.ssm.utils.attempt._

import scala.io.Source
import com.amazonaws.services.rds.AmazonRDSClient
import com.amazonaws.services.rds.AmazonRDSAsync
import com.gu.ssm.aws.RDS

object Logic {
  def generateScript(toExecute: Either[String, File]): String = {
    toExecute match {
      case Right(script) => Source.fromFile(script, "UTF-8").mkString
      case Left(cmd)     => cmd
    }
  }

  def extractSASTags(tags: Seq[String]): Either[String, List[String]] = {
    if (tags.length > 3 || tags.isEmpty || tags.head.length == 0)
      Left(
        "Please supply at least one and no more than 3 tags. " +
          "If you specify less than 3 tags order assumed is app,stage,stack"
      )
    else Right(tags.toList)
  }

  val tunnelValidationErrorMsg =
    "Please specify a tunnel target in the format localPort:host:remotePort."

  def extractTunnelConfig(
      tunnelStr: String
  ): Either[String, TunnelTargetWithHostName] = {
    tunnelStr.split(":").toList match {
      case localPortStr :: targetStr :: remotePortStr :: Nil =>
        (localPortStr.toIntOption, targetStr, remotePortStr.toIntOption) match {
          case (Some(localPort), targetStr, Some(remotePort)) =>
            Right(TunnelTargetWithHostName(localPort, targetStr, remotePort))
          case _ => Left(s"$tunnelValidationErrorMsg Ports must be integers.")
        }
      case _ => Left(tunnelValidationErrorMsg)
    }
  }

  val rdsTunnelValidationErrorMsg =
    "Please specify a tunnel target in the format localPort:tags, where tags is a comma-separated list of tag values."

  def extractRDSTunnelConfig(
      tunnelStr: String
  ): Either[String, TunnelTargetWithRDSTags] = {
    tunnelStr.split(":").toList match {
      case localPortStr :: tagsStr :: Nil =>
        localPortStr.toIntOption match {
          case Some(localPort) =>
            extractSASTags(tagsStr.split(",").toSeq).flatMap { tags =>
              Right(TunnelTargetWithRDSTags(localPort, tags))
            }
          case None => Left(rdsTunnelValidationErrorMsg)
        }
      case _ => Left(rdsTunnelValidationErrorMsg)
    }
  }

  def checkInstancesList(config: SSMConfig): Either[FailedAttempt, Unit] =
    config.targets match {
      case List() =>
        Left(
          FailedAttempt(
            List(Failure("No instances found", "No instances found", ErrorCode))
          )
        )
      case _ => Right(())
    }

  def getSSHInstance(
      instances: List[Instance],
      sism: SingleInstanceSelectionMode
  ): Either[FailedAttempt, Instance] = {
    instances match {
      case Nil =>
        // no instances is always a failure
        Left(
          FailedAttempt(
            Failure(
              s"Unable to identify a single instance",
              s"Could not find any instance",
              UnhandledError
            )
          )
        )
      case instance :: Nil =>
        // if there's a single instance there's no ambiguity, we can go right ahead with that one
        Right(instance)
      case _ =>
        // we have multiple instances, need to consider the selection mode to pick the right one
        sism match {
          case SismNewest =>
            Right(instances.maxBy(_.launchInstant))
          case SismOldest =>
            Right(instances.minBy(_.launchInstant))
          case SismUnspecified =>
            Left(
              FailedAttempt(
                Failure(
                  s"Unable to identify a single instance",
                  s"Error choosing single instance, found ${instances.map(_.id.id).mkString(", ")}.  Use --oldest or --newest to select single instance",
                  UnhandledError
                )
              )
            )
        }
    }
  }

  def getClients(
      profile: Option[String],
      region: Region,
      useDefaultCredentialsProvider: Boolean
  ): AWSClients = {
    val credentialsProvider = profile match {
      case _ if useDefaultCredentialsProvider =>
        DefaultAWSCredentialsProviderChain.getInstance()
      case Some(profile) => new ProfileCredentialsProvider(profile)
      // In this case it's set using the AWS_PROFILE environment variable
      case _ => new ProfileCredentialsProvider()
    }

    val ssmClient: AWSSimpleSystemsManagementAsync =
      SSM.client(credentialsProvider, region)
    val stsClient: AWSSecurityTokenServiceAsync =
      STS.client(credentialsProvider, region)
    val ec2Client: AmazonEC2Async = EC2.client(credentialsProvider, region)
    val rdsClient: AmazonRDSAsync = RDS.client(credentialsProvider, region)
    AWSClients(ssmClient, stsClient, ec2Client, rdsClient)
  }

  def computeIncorrectInstances(
      executionTarget: ExecutionTarget,
      instanceIds: List[InstanceId]
  ): List[InstanceId] =
    executionTarget.instances.getOrElse(List()).filterNot(instanceIds.toSet)

  def getAddress(
      instance: Instance,
      onlyUsePrivateIP: Boolean
  ): Either[FailedAttempt, String] = {
    if (onlyUsePrivateIP) {
      Right(instance.privateIpAddress)
    } else {
      instance.publicIpAddressOpt match {
        case Some(ipAddress) => Right(ipAddress)
        case None            => Right(instance.privateIpAddress)
      }
    }
  }

  def getHostKeyEntry(
      ssmResult: Either[CommandStatus, CommandResult],
      preferredAlgs: List[String]
  ): Either[FailedAttempt, String] = {
    ssmResult match {
      case Right(result) =>
        val resultLines = result.stdOut.linesIterator
        val preferredKeys = resultLines.filter(hostKey =>
          preferredAlgs.exists(hostKey.startsWith)
        )
        val preferenceOrderedKeys: Seq[String] = preferredKeys.toList.sortBy(
          hostKey => preferredAlgs.indexWhere(hostKey.startsWith)
        )

        preferenceOrderedKeys.headOption match {
          case Some(hostKey) => Right(hostKey)
          case None =>
            Left(
              Failure(
                "host key with preferred algorithm not found",
                s"The remote instance did not return a host key with any preferred algorithm (preferred: $preferredAlgs)",
                NoHostKey,
                s"The result lines returned from the host:\n${resultLines.mkString("\n")}"
              ).attempt
            )
        }
      case Left(otherStatus) =>
        Left(
          Failure(
            "host keys not returned",
            s"The remote instance failed to return the host keys within the timeout window (status: $otherStatus)",
            AwsError
          ).attempt
        )
    }
  }

}
