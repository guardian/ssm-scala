package com.gu.ssm.aws

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Region
import com.amazonaws.services.ec2.model._
import com.amazonaws.services.ec2.{AmazonEC2Async, AmazonEC2AsyncClientBuilder}
import com.gu.ssm.aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import com.gu.ssm.model.{AppStackStage, Instance, InstanceId}
import com.gu.ssm.utils.attempt.Attempt

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext


object EC2 {

  def client(profileName: String, region: Region): AmazonEC2Async = {
    AmazonEC2AsyncClientBuilder.standard()
      .withCredentials(new ProfileCredentialsProvider(profileName))
      .withRegion(region.getName)
      .build()
  }

  def resolveASSInstances(ass: AppStackStage, client: AmazonEC2Async)(implicit ec: ExecutionContext): Attempt[List[Instance]] = {
    val request = new DescribeInstancesRequest()
      .withFilters(
        new Filter("instance-state-name", List("running").asJava),
        new Filter("tag:App", List(ass.app).asJava),
        new Filter("tag:Stack", List(ass.stack).asJava),
        new Filter("tag:Stage", List(ass.stage).asJava)
      )
    handleAWSErrs(awsToScala(client.describeInstancesAsync)(request).map(extractInstances))
  }

  def resolveInstanceIds(ids: List[InstanceId], client: AmazonEC2Async)(implicit ec: ExecutionContext): Attempt[List[Instance]] = {
    val request = new DescribeInstancesRequest()
      .withFilters(
        new Filter("instance-state-name", List("running").asJava),
        new Filter("instance-id", ids.map(i => i.id).asJava)
      )
    handleAWSErrs(awsToScala(client.describeInstancesAsync)(request).map(extractInstances))
  }

  private def extractInstances(describeInstancesResult: DescribeInstancesResult): List[Instance] = {
    (for {
      reservation <- describeInstancesResult.getReservations.asScala
      awsInstance <- reservation.getInstances.asScala
      instanceId = awsInstance.getInstanceId
      launchDateTime = awsInstance.getLaunchTime.toInstant
    } yield Instance(InstanceId(instanceId), Option(awsInstance.getPublicIpAddress), launchDateTime)).toList
  }

  def tagInstance(id: InstanceId, key: String, value: String, client: AmazonEC2Async)(implicit ec: ExecutionContext): Attempt[Unit] = {
    val request = new CreateTagsRequest()
      .withTags(new Tag(key, value))
      .withResources(id.id)
    handleAWSErrs(awsToScala(client.createTagsAsync)(request)).map(_ => Unit)
  }

}


