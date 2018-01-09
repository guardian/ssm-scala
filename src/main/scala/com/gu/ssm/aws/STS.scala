package com.gu.ssm.aws

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Region
import com.amazonaws.services.securitytoken.model.{GetCallerIdentityRequest, GetCallerIdentityResult}
import com.amazonaws.services.securitytoken.{AWSSecurityTokenServiceAsync, AWSSecurityTokenServiceAsyncClientBuilder}
import com.gu.ssm.aws.AWS.asFuture

import scala.concurrent.{ExecutionContext, Future}


object STS {
  def client(profileName: String, region: Region): AWSSecurityTokenServiceAsync = {
    // STS is a global service so no region required
    AWSSecurityTokenServiceAsyncClientBuilder.standard()
      .withCredentials(new ProfileCredentialsProvider(profileName))
      .withRegion(region.getName)
      .build()
  }

  def getCallerIdentity(client: AWSSecurityTokenServiceAsync)(implicit ec: ExecutionContext): Future[String] = {
    val request = new GetCallerIdentityRequest()
    asFuture(client.getCallerIdentityAsync)(request).map(extractUserId)
  }

  def extractUserId(getCallerIdentityResult: GetCallerIdentityResult): String = {
    getCallerIdentityResult.getUserId
  }
}
