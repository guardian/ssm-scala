package com.gu.ssm.aws

import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.regions.Region
import com.amazonaws.services.securitytoken.model.{GetCallerIdentityRequest, GetCallerIdentityResult}
import com.amazonaws.services.securitytoken.{AWSSecurityTokenServiceAsync, AWSSecurityTokenServiceAsyncClientBuilder}
import com.gu.ssm.aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import com.gu.ssm.utils.attempt.Attempt

import scala.concurrent.ExecutionContext


object STS {
  def client(credentialsProvider: AWSCredentialsProvider, region: Region): AWSSecurityTokenServiceAsync = {
    AWSSecurityTokenServiceAsyncClientBuilder.standard()
      .withCredentials(credentialsProvider)
      // STS is a global service but you need to access the regional endpoint if using it through an endpoint in VPCs
      // that have no outbound internet access. https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_temp_enable-regions.html
      .withEndpointConfiguration(new EndpointConfiguration(region.getServiceEndpoint("sts"), region.getName))
      .build()
  }

  def getCallerIdentity(client: AWSSecurityTokenServiceAsync)(implicit ec: ExecutionContext): Attempt[String] = {
    val request = new GetCallerIdentityRequest()
    handleAWSErrs(awsToScala(client.getCallerIdentityAsync)(request).map(extractUserId))
  }

  def extractUserId(getCallerIdentityResult: GetCallerIdentityResult): String = {
    getCallerIdentityResult.getUserId
  }
}
