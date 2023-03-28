package com.gu.ssm.aws

import com.amazonaws.services.rds.model.{DescribeDBInstancesRequest, Filter}
import com.amazonaws.services.rds.AmazonRDSAsync
import com.gu.ssm.utils.attempt.Attempt
import com.gu.ssm.aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import com.amazonaws.services.rds.AmazonRDSAsyncClientBuilder
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.regions.Region
import com.amazonaws.services.rds.model.DescribeDBInstancesResult
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import com.gu.ssm.RDSInstance
import com.gu.ssm.RDSInstanceId
import com.amazonaws.services.rds.model.DBInstance

object RDS {
  def client(credentialsProvider: AWSCredentialsProvider, region: Region): AmazonRDSAsync = {
    AmazonRDSAsyncClientBuilder.standard()
      .withCredentials(credentialsProvider)
      .withRegion(region.getName)
      .build()
  }

  def resolveByTags(tagValues: List[String], client: AmazonRDSAsync)(implicit ec: ExecutionContext): Attempt[List[RDSInstance]] = {
    val request = new DescribeDBInstancesRequest()

    handleAWSErrs(awsToScala(client.describeDBInstancesAsync)(request).map { result =>
      result.getDBInstances.asScala.toList
        .filter(hasTagList(tagValues))
        .map(toInstance)
    })
  }

  private def hasTagList(tagValues: List[String])(awsInstance: DBInstance): Boolean = {
    val instanceTags = awsInstance.getTagList().asScala.toList.map(_.getValue())
    tagValues.forall(requiredTag => instanceTags.contains(requiredTag))
  }

  private def toInstance(awsInstance: DBInstance): RDSInstance = {
    val endpoint = awsInstance.getEndpoint()
    RDSInstance(RDSInstanceId(awsInstance.getDBInstanceIdentifier()), endpoint.getAddress(), endpoint.getPort())
  }
}
