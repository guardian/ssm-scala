package com.gu.ssm.aws

import com.gu.ssm.{RDSInstance, RDSInstanceId}
import com.gu.ssm.aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import com.gu.ssm.utils.attempt.Attempt
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.rds.RdsAsyncClient
import software.amazon.awssdk.services.rds.model.{DBInstance, DescribeDbInstancesRequest}

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.*

object RDS {
  def client(credentialsProvider: AwsCredentialsProvider, region: Region): RdsAsyncClient = {
    RdsAsyncClient.builder().credentialsProvider(credentialsProvider).region(region).build()
  }

  def resolveByTags(tagValues: List[String], client: RdsAsyncClient)(implicit ec: ExecutionContext): Attempt[List[RDSInstance]] = {
    val request = DescribeDbInstancesRequest.builder().build()

    handleAWSErrs(awsToScala(client.describeDBInstances(request)).map { result =>
      result.dbInstances().asScala.toList
        .filter(hasTagList(tagValues))
        .map(toInstance)
    })
  }

  private def hasTagList(tagValues: List[String])(awsInstance: DBInstance): Boolean = {
    val instanceTags = awsInstance.tagList().asScala.toList.map(_.value())
    tagValues.forall(requiredTag => instanceTags.contains(requiredTag))
  }

  private def toInstance(awsInstance: DBInstance): RDSInstance = {
    val endpoint = awsInstance.endpoint()
    RDSInstance(RDSInstanceId(awsInstance.dbInstanceIdentifier()), endpoint.address(), endpoint.port())
  }
}
