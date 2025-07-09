package com.gu.ssm.aws

import com.gu.ssm.aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import com.gu.ssm.utils.attempt.Attempt
import com.gu.ssm.{Instance, InstanceId}
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ec2.Ec2AsyncClient
import software.amazon.awssdk.services.ec2.model.*

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.*

object EC2 {
  def client(credentialsProvider: AwsCredentialsProvider, region: Region): Ec2AsyncClient = {
    Ec2AsyncClient.builder().credentialsProvider(credentialsProvider).region(region).build()
  }

  private def makeFilter(name: String, value: String): Filter = makeFilter(name, List(value))
  private def makeFilter(name: String, values: List[String]): Filter = Filter.builder().name(name).values(values.asJava).build()
  private def makeTagFilter(tagName: String, values: List[String]): Filter = Filter.builder().name(s"tag:$tagName").values(values.asJava).build()

  def resolveByTags(tagValues: List[String], client: Ec2AsyncClient)(implicit ec: ExecutionContext): Attempt[List[Instance]] = {
    val allTags = tagValues ++ tagValues.map(_.toUpperCase) ++ tagValues.map(_.toLowerCase)

    // if user has provided fewer than 3 tags then assume order app,stage,stack
    val tagOrder = List("App", "Stage", "Stack")

    val filters = makeFilter("instance-state-name", "running") ::
        tagOrder.take(tagValues.length).map(makeTagFilter(_, allTags))

    val request: DescribeInstancesRequest = DescribeInstancesRequest.builder().filters(filters.asJava).build()

    handleAWSErrs(awsToScala(client.describeInstances(request))).map(extractInstances)
  }

  def resolveInstanceIds(ids: List[InstanceId], client: Ec2AsyncClient)(implicit ec: ExecutionContext): Attempt[List[Instance]] = {
    val request = DescribeInstancesRequest.builder()
      .filters(
        makeFilter("instance-state-name", "running"),
        makeFilter("instance-id", ids.map(_.id)),
      )
      .build()
    handleAWSErrs(awsToScala(client.describeInstances(request))).map(extractInstances)
  }

  private def extractInstances(describeInstancesResult: DescribeInstancesResponse): List[Instance] = {
    (for {
      reservation <- describeInstancesResult.reservations().asScala
      awsInstance <- reservation.instances().asScala
      instanceId = awsInstance.instanceId()
      launchDateTime = awsInstance.launchTime()
    } yield Instance(InstanceId(instanceId), Option(awsInstance.publicDnsName()), Option(awsInstance.publicIpAddress()), awsInstance.privateIpAddress(), launchDateTime)).toList
  }

  def tagInstance(id: InstanceId, key: String, value: String, client: Ec2AsyncClient)(implicit ec: ExecutionContext): Attempt[Unit] = {
    val request = CreateTagsRequest.builder()
      .tags(Tag.builder().key(key).value(value).build())
      .resources(id.id)
      .build()
    handleAWSErrs(awsToScala(client.createTags(request))).map(_ => ())
  }

}


