package com.gu.ssm.aws

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Region
import com.amazonaws.services.elasticmapreduce.model._
import com.amazonaws.services.elasticmapreduce.{AmazonElasticMapReduceAsync, AmazonElasticMapReduceAsyncClientBuilder}
import com.gu.ssm.aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import com.gu.ssm.model.{Instance, InstanceId}
import com.gu.ssm.utils.attempt.Attempt

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class EMR(client: AmazonElasticMapReduceAsync) {

  def resolveMasterInstances(clusterId: String)(implicit ec: ExecutionContext): Attempt[List[Instance]] = {
    val describeClusterReq = new DescribeClusterRequest()
      .withClusterId(clusterId)

    val listInstancesReq = new ListInstancesRequest()
      .withClusterId(clusterId)
      .withInstanceStates(InstanceState.RUNNING)
      .withInstanceGroupTypes(InstanceGroupType.MASTER)

    for {
      cluster <- handleAWSErrs(awsToScala(client.describeClusterAsync)(describeClusterReq))
      instances <- handleAWSErrs(awsToScala(client.listInstancesAsync)(listInstancesReq))
    } yield extractInstances(cluster, instances)
  }

  private def extractInstances(cluster: DescribeClusterResult, instances: ListInstancesResult): List[Instance] = {
    instances.getInstances.asScala.toList.map { awsInstance =>
      Instance(
        InstanceId(awsInstance.getId),
        Option(awsInstance.getPublicIpAddress),
        cluster.getCluster.getStatus.getTimeline.getCreationDateTime.toInstant
      )
    }
  }
}

object EMR {

  def client(profileName: String, region: Region): AmazonElasticMapReduceAsync = {
    AmazonElasticMapReduceAsyncClientBuilder.standard()
      .withCredentials(new ProfileCredentialsProvider(profileName))
      .withRegion(region.getName)
      .build()
  }
}