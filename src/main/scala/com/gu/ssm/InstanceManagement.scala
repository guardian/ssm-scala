package com.gu.ssm

import com.gu.ssm.InstanceResolutionResult.ResolutionError
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.{
  DescribeInstancesRequest,
  DescribeInstancesResponse,
  Filter
}

import scala.util.{Success, Try}
import scala.jdk.CollectionConverters.*

trait InstanceManager {
  def findMatchingInstances(
      instanceTags: InstanceTags
  ): Try[Either[ResolutionError, List[InstanceInfo]]]

  def handoffSession(resolutionResult: InstanceResolutionResult): Try[Int]
}

class AwsInstanceManager(profile: String, region: String) extends InstanceManager {
  private lazy val ec2Client = Ec2Client
    .builder()
    .credentialsProvider(ProfileCredentialsProvider.create(profile))
    .region(Region.of(region))
    .httpClient(UrlConnectionHttpClient.create())
    .build()

  override def findMatchingInstances(
      instanceTags: InstanceTags
  ): Try[Either[ResolutionError, List[InstanceInfo]]] = {
    val filters = AwsInstanceManager.instancesFilters(instanceTags)
    val request = DescribeInstancesRequest
      .builder()
      .filters(filters: _*)
      .build()
    Try(ec2Client.describeInstances(request))
      .map(r => Right(AwsInstanceManager.handleDescribeInstancesResponse(r)))
      // helpfully handle common / expected AWS SDK exceptions
      .recover {
        // no matching AWS profile
        case e: software.amazon.awssdk.core.exception.SdkClientException
            if e.getMessage.contains("Profile file contained no credentials for profile") =>
          Left(
            ResolutionError(
              s"Failed to load AWS credentials for profile '$profile'. Check that the profile exists and contains valid credentials.",
              instanceTags
            )
          )
        // profile contains expired credentials
        case e: software.amazon.awssdk.services.ec2.model.Ec2Exception if e.statusCode() == 401 =>
          Left(
            ResolutionError(
              s"Failed to authenticate with AWS using profile '$profile'. Check that the credentials are valid and not expired.",
              instanceTags
            )
          )
        // IAM role lacks ec2:DescribeInstances permission
        case e: software.amazon.awssdk.services.ec2.model.Ec2Exception
            if e.awsErrorDetails().errorCode() == "UnauthorizedOperation" =>
          Left(
            ResolutionError(
              s"The profile '$profile' does not have permission to call ec2:DescribeInstances. Check the IAM policy attached to this profile.",
              instanceTags
            )
          )
        // credentials are present but invalid (e.g. wrong key/secret)
        case e: software.amazon.awssdk.services.ec2.model.Ec2Exception
            if e.awsErrorDetails().errorCode() == "AuthFailure" =>
          Left(
            ResolutionError(
              s"AWS rejected the credentials for profile '$profile'. Check that the access key and secret are correct.",
              instanceTags
            )
          )
        // no network connectivity / cannot reach AWS endpoint
        case e: software.amazon.awssdk.core.exception.SdkClientException
            if e.getMessage.contains("Unable to execute HTTP request") =>
          Left(
            ResolutionError(
              s"Could not connect to AWS. Check your network connection and VPN status.",
              instanceTags
            )
          )
        // invalid or unrecognised region
        case e: software.amazon.awssdk.core.exception.SdkClientException
            if e.getMessage.contains("endpoint") =>
          Left(
            ResolutionError(
              s"Could not resolve an endpoint for region '$region'. Check that the region is valid.",
              instanceTags
            )
          )
      }
  }

  /** Handoff to the AWS CLI to start the Main session. The AWS CLI will then handle the interactive
    * session in this terminal.
    */
  override def handoffSession(resolutionResult: InstanceResolutionResult): Try[Int] =
    resolutionResult match {
      case InstanceResolutionResult.ResolvedInstance(instanceId) =>
        Try {
          // we have a valid instance ID, and can start the Main session
          val pb = new ProcessBuilder(
            // format: off
            "aws", "ssm", "start-session",
            "--target", instanceId,
            "--profile", profile,
            "--region", region
            // format: on
          )
          pb.inheritIO()
          pb.start().waitFor()
        }
      case _ =>
        // otherwise we failed to resolve a single instance to connect to, so we should exit with an error code
        Success(1)
    }
}
object AwsInstanceManager {
  private def instancesFilters(instanceTags: InstanceTags): List[Filter] = {
    val stateFilter = Filter
      .builder()
      .name("instance-state-name")
      .values("running")
      .build()
    val tagFilters = List(
      Some(Filter.builder().name("tag:App").values(instanceTags.app).build()),
      instanceTags.stack.map(stack => Filter.builder().name("tag:Stack").values(stack).build()),
      instanceTags.stage.map(stage => Filter.builder().name("tag:Stage").values(stage).build())
    ).flatten
    stateFilter :: tagFilters
  }

  private def handleDescribeInstancesResponse(
      response: DescribeInstancesResponse
  ): List[InstanceInfo] =
    response
      .reservations()
      .asScala
      .flatMap(_.instances().asScala)
      .toList
      .map { instance =>
        val tags = instance.tags().asScala.map(tag => tag.key() -> tag.value()).toMap
        InstanceInfo(
          instance.instanceId(),
          tags.getOrElse("Name", "-"),
          tags,
          instance.launchTime()
        )
      }
}
