package com.gu.ssm.aws

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import com.gu.ssm.utils.attempt.{
  Attempt,
  AwsError,
  AwsPermissionsError,
  Failure
}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future, Promise}

object AwsAsyncHandler {
  private val ServiceName = ".*Service: ([^;]+);.*".r
  def awsToScala[R <: AmazonWebServiceRequest, T](
      sdkMethod: ((R, AsyncHandler[R, T]) => java.util.concurrent.Future[T])
  ): R => Future[T] = { req =>
    val p = Promise[T]()
    sdkMethod(req, new AwsAsyncPromiseHandler(p))
    p.future
  }

  /** Handles expected AWS errors in a nice way
    */
  def handleAWSErrs[T](
      f: Future[T]
  )(implicit ec: ExecutionContext): Attempt[T] = {
    Attempt.fromFuture(f) { case e =>
      val serviceNameOpt = e.getMessage match {
        case ServiceName(serviceName) => Some(serviceName)
        case _                        => None
      }
      if (e.getMessage.contains("Request has expired")) {
        Failure(
          "expired AWS credentials",
          "Failed to request data from AWS, the temporary credentials have expired",
          AwsPermissionsError,
          e
        ).attempt
      } else if (
        e.getMessage.contains(
          "Unable to load AWS credentials from any provider in the chain"
        )
      ) {
        Failure(
          "No AWS credentials found",
          "No AWS credentials found. Did you mean to set --profile?",
          AwsPermissionsError,
          e
        ).attempt
      } else if (e.getMessage.contains("No AWS profile named")) {
        Failure(
          "Invalid AWS profile name (does not exist)",
          "The specified AWS profile does not exist",
          AwsPermissionsError,
          e
        ).attempt
      } else if (e.getMessage.contains("is not authorized to perform")) {
        val message = serviceNameOpt.fold(
          "You do not have sufficient AWS privileges"
        )(serviceName =>
          s"You do not have sufficient privileges to perform actions on $serviceName"
        )
        Failure(
          "insufficient permissions",
          message,
          AwsPermissionsError,
          e
        ).attempt
      } else if (e.getMessage.contains("InvalidInstanceId")) {
        Failure(
          "InvalidInstanceId from AWS",
          "The specified instance(s) are not eligible targets (AWS said InvalidInstanceId)",
          AwsError,
          e
        ).attempt
      } else {
        val details = serviceNameOpt.fold(
          s"AWS unknown error, unknown service (check logs for stacktrace). $e"
        ) { serviceName =>
          s"AWS unknown error, service: $serviceName (check logs for stacktrace), $e"
        }
        val friendlyMessage = serviceNameOpt.fold(
          s"Unknown error while making API calls to AWS. $e"
        ) { serviceName =>
          s"Unknown error while making an API call to AWS' $serviceName service, $e"
        }
        Failure(details, friendlyMessage, AwsError, e).attempt
      }
    }
  }

  class AwsAsyncPromiseHandler[R <: AmazonWebServiceRequest, T](
      promise: Promise[T]
  ) extends AsyncHandler[R, T]
      with LazyLogging {
    def onError(e: Exception): Unit = {
      logger.warn("Failed to execute AWS SDK operation", e)
      promise failure e
    }
    def onSuccess(r: R, t: T): Unit = {
      promise success t
    }
  }
}
