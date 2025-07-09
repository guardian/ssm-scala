package com.gu.ssm.aws

import com.gu.ssm.aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import com.gu.ssm.utils.attempt.Attempt
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sts.StsAsyncClient
import software.amazon.awssdk.services.sts.model.{GetCallerIdentityRequest, GetCallerIdentityResponse}

import scala.concurrent.ExecutionContext


object STS {
  def client(credentialsProvider: AwsCredentialsProvider, region: Region): StsAsyncClient = {
    StsAsyncClient.builder()
      .credentialsProvider(credentialsProvider)
      .region(region)
      .build()
  }

  def getCallerIdentity(client: StsAsyncClient)(implicit ec: ExecutionContext): Attempt[String] = {
    val request = GetCallerIdentityRequest.builder().build()
    handleAWSErrs(awsToScala(client.getCallerIdentity(request))).map(extractUserId)
  }

  def extractUserId(getCallerIdentityResult: GetCallerIdentityResponse): String = {
    getCallerIdentityResult.userId()
  }
}
