package com.gu.ssm.aws

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Region
import com.amazonaws.services.ec2.model.{DescribeInstancesRequest, DescribeInstancesResult, Filter}
import com.amazonaws.services.ec2.{AmazonEC2Async, AmazonEC2AsyncClientBuilder}
import com.gu.ssm.{Instance, AppStackStage}
import com.gu.ssm.aws.AWS.asFuture

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}


object EC2 {
  def client(profileName: String, region: Region): AmazonEC2Async = {
    // STS is a global service so no region required
    AmazonEC2AsyncClientBuilder.standard()
      .withCredentials(new ProfileCredentialsProvider(profileName))
      .withRegion(region.getName)
      .build()
  }

  def resolveSASInstances(ass: AppStackStage, client: AmazonEC2Async)(implicit ec: ExecutionContext): Future[List[Instance]] = {
    val request = new DescribeInstancesRequest()
      .withFilters(
        new Filter("tag:App", List(ass.app).asJava),
        new Filter("tag:Stack", List(ass.stack).asJava),
        new Filter("tag:Stage", List(ass.stage).asJava)
      )
    asFuture(client.describeInstancesAsync)(request).map(extractInstances)
  }

  private def extractInstances(describeInstancesResult: DescribeInstancesResult): List[Instance] = {
    (for {
      reservation <- describeInstancesResult.getReservations.asScala
      awsInstance <- reservation.getInstances.asScala
    } yield Instance(awsInstance.getInstanceId)).toList
  }
}
