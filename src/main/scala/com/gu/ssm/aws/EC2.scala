package com.gu.ssm.aws

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.regions.Region
import com.amazonaws.services.ec2.model._
import com.amazonaws.services.ec2.{AmazonEC2Async, AmazonEC2AsyncClientBuilder}
import com.gu.ssm.aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import com.gu.ssm.utils.attempt.Attempt
import com.gu.ssm.{Instance, InstanceId}

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

object EC2 {
  def client(
      credentialsProvider: AWSCredentialsProvider,
      region: Region
  ): AmazonEC2Async = {
    AmazonEC2AsyncClientBuilder
      .standard()
      .withCredentials(credentialsProvider)
      .withRegion(region.getName)
      .build()
  }

  def makeFilter(tagName: String, values: List[String]) =
    new Filter(s"tag:$tagName", values.asJava)

  def resolveByTags(tagValues: List[String], client: AmazonEC2Async)(implicit
      ec: ExecutionContext
  ): Attempt[List[Instance]] = {
    val allTags =
      tagValues ++ tagValues.map(_.toUpperCase) ++ tagValues.map(_.toLowerCase)

    // if user has provided fewer than 3 tags then assume order app,stage,stack
    val tagOrder = List("App", "Stage", "Stack")
    val filters = new Filter("instance-state-name", List("running").asJava) ::
      tagOrder.take(tagValues.length).map(makeFilter(_, allTags))

    val request = new DescribeInstancesRequest()
      .withFilters(filters*)
    handleAWSErrs(
      awsToScala(client.describeInstancesAsync)(request).map(extractInstances)
    )
  }

  def resolveInstanceIds(ids: List[InstanceId], client: AmazonEC2Async)(implicit
      ec: ExecutionContext
  ): Attempt[List[Instance]] = {
    val request = new DescribeInstancesRequest()
      .withFilters(
        new Filter("instance-state-name", List("running").asJava),
        new Filter("instance-id", ids.map(i => i.id).asJava)
      )
    handleAWSErrs(
      awsToScala(client.describeInstancesAsync)(request).map(extractInstances)
    )
  }

  private def extractInstances(
      describeInstancesResult: DescribeInstancesResult
  ): List[Instance] = {
    (for {
      reservation <- describeInstancesResult.getReservations.asScala
      awsInstance <- reservation.getInstances.asScala
      instanceId = awsInstance.getInstanceId
      launchDateTime = awsInstance.getLaunchTime.toInstant
    } yield Instance(
      InstanceId(instanceId),
      Option(awsInstance.getPublicDnsName),
      Option(awsInstance.getPublicIpAddress),
      awsInstance.getPrivateIpAddress,
      launchDateTime
    )).toList
  }

  def tagInstance(
      id: InstanceId,
      key: String,
      value: String,
      client: AmazonEC2Async
  )(implicit ec: ExecutionContext): Attempt[Unit] = {
    val request = new CreateTagsRequest()
      .withTags(new Tag(key, value))
      .withResources(id.id)
    handleAWSErrs(awsToScala(client.createTagsAsync)(request)).map(_ => ())
  }

}
