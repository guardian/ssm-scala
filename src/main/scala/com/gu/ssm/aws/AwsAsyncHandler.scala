package com.gu.ssm.aws

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import com.gu.ssm.utils.attempt.{Attempt, AwsError, AwsPermissionsError, Failure}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future, Promise}


object AwsAsyncHandler {
  private val ServiceName = ".*Service: ([^;]+);.*".r
  def awsToScala[R <: AmazonWebServiceRequest, T](sdkMethod: ( (R, AsyncHandler[R, T]) => java.util.concurrent.Future[T])): (R => Future[T]) = { req =>
    val p = Promise[T]
    sdkMethod(req, new AwsAsyncPromiseHandler(p))
    p.future
  }

  /**
    * Handles expected AWS errors in a nice way
    */
  def handleAWSErrs[T](f: Future[T])(implicit ec: ExecutionContext): Attempt[T] = {
    Attempt.fromFuture(f) { case e =>
      val serviceNameOpt = e.getMessage match {
        case ServiceName(serviceName) => Some(serviceName)
        case _ => None
      }
      if (e.getMessage.contains("The security token included in the request is expired")) {
        Failure("expired AWS credentials", "Failed to request data from AWS, the temporary credentials have expired", AwsPermissionsError).attempt
      } else if (e.getMessage.contains("Unable to load AWS credentials from any provider in the chain")) {
        Failure("Invalid AWS profile name (no credentials)", "No credentials found for the specified AWS profile", AwsPermissionsError).attempt
      } else if (e.getMessage.contains("No AWS profile named")) {
        Failure("Invalid AWS profile name (does not exist)", "The specified AWS profile does not exist", AwsPermissionsError).attempt
      } else if (e.getMessage.contains("is not authorized to perform")) {
        val message = serviceNameOpt.fold("You do not have sufficient AWS privileges")(serviceName => s"You do not have sufficient privileges to perform actions on $serviceName")
        Failure("insuficient permissions", message, AwsPermissionsError).attempt
      } else if (e.getMessage.contains("InvalidInstanceId")) {
        Failure("InvalidInstanceId from AWS", "The specified instance(s) are not eligible targets (AWS said InvalidInstanceId)", AwsError).attempt
      } else {
        val details = serviceNameOpt.fold("AWS unknown error, unknown service (check logs for stacktrace)") { serviceName =>
          s"AWS unknown error, service: $serviceName (check logs for stacktrace), ${e.getMessage}"
        }
        val friendlyMessage = serviceNameOpt.fold("Unknown error while making API calls to AWS.") { serviceName =>
          s"Unknown error while making an API call to AWS' $serviceName service, ${e.getMessage}"
        }
        Failure(details, friendlyMessage, AwsError).attempt
      }
    }
  }

  class AwsAsyncPromiseHandler[R <: AmazonWebServiceRequest, T](promise: Promise[T]) extends AsyncHandler[R, T] with LazyLogging {
    def onError(e: Exception): Unit = {
      logger.warn("Failed to execute AWS SDK operation", e)
      promise failure e
    }
    def onSuccess(r: R, t: T): Unit = {
      promise success t
    }
  }
}
