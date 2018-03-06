package com.gu.ssm.aws

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Region
import com.amazonaws.services.securitytoken.model.{GetCallerIdentityRequest, GetCallerIdentityResult}
import com.amazonaws.services.securitytoken.{AWSSecurityTokenServiceAsync, AWSSecurityTokenServiceAsyncClientBuilder}
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsyncClientBuilder
import com.gu.ssm.aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import com.gu.ssm.utils.attempt.Attempt

import scala.concurrent.ExecutionContext


object STS {
  def client(profileName: Option[String], region: Region): AWSSecurityTokenServiceAsync = {

    val profileCredentialsProvider = profileName match {
      case Some(profile) => new ProfileCredentialsProvider(profile)
      case _ => new ProfileCredentialsProvider()
    }

  // STS is a global service so no region required
    AWSSecurityTokenServiceAsyncClientBuilder.standard()
      .withCredentials(profileCredentialsProvider)
      .withRegion(region.getName)
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
