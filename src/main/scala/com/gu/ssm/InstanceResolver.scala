package com.gu.ssm

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

trait InstanceResolver {
  def describeMatchingInstances(instanceTags: InstanceTags): Try[List[InstanceInfo]]
  def handoffToAwsCli(resolutionResult: InstanceResolutionResult): Try[Int]
}

class AwsInstanceResolver(profile: String, region: String) extends InstanceResolver {
  private lazy val ec2Client = Ec2Client
    .builder()
    .credentialsProvider(ProfileCredentialsProvider.create(profile))
    .region(Region.of(region))
    .httpClient(UrlConnectionHttpClient.create())
    .build()

  override def describeMatchingInstances(instanceTags: InstanceTags): Try[List[InstanceInfo]] = {
    val filters = AwsInstanceResolver.instancesFilters(instanceTags)
    val request = DescribeInstancesRequest
      .builder()
      .filters(filters: _*)
      .build()
    // TODO: handle expected errors from AWS API, e.g. expired credentials, no matching AWS profile
    Try(ec2Client.describeInstances(request))
      .map(AwsInstanceResolver.handleDescribeInstancesResponse)
  }

  /** Handoff to the AWS CLI to start the Main session. The AWS CLI can then handle the interactive
    * session in this terminal.
    */
  override def handoffToAwsCli(resolutionResult: InstanceResolutionResult): Try[Int] =
    resolutionResult match {
      case InstanceResolutionResult.ResolvedInstance(instanceId) =>
        Try {
          // we have a valid instance ID, and can start the Main session
          val pb = new ProcessBuilder(
            // format: off
            "instanceResolve", "ssm", "start-session",
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
object AwsInstanceResolver {
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
  ): List[InstanceInfo] = {
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
}
