package com.gu.ssm.aws

import com.gu.ssm.utils.attempt.{Attempt, AwsError, AwsPermissionsError, Failure}

import java.util.concurrent.{CompletableFuture, CompletionException}
import scala.concurrent.{ExecutionContext, Future}


object AwsAsyncHandler {
  private val ServiceName = ".*Service: ([^;]+);.*".r

  def awsToScala[T](cf: CompletableFuture[T])(using ExecutionContext): Future[T] = {
    import scala.jdk.FutureConverters.*
    cf.asScala.recoverWith {
      case ex: CompletionException if ex.getCause != null => Future.failed(ex.getCause)
    }
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
      if (e.getMessage.contains("Request has expired")) {
        Failure("expired AWS credentials", "Failed to request data from AWS, the temporary credentials have expired", AwsPermissionsError, e).attempt
      } else if (e.getMessage.contains("Unable to load AWS credentials from any provider in the chain")) {
        Failure("No AWS credentials found", "No AWS credentials found. Did you mean to set --profile?", AwsPermissionsError, e).attempt
      } else if (e.getMessage.contains("No AWS profile named")) {
        Failure("Invalid AWS profile name (does not exist)", "The specified AWS profile does not exist", AwsPermissionsError, e).attempt
      } else if (e.getMessage.contains("is not authorized to perform")) {
        val message = serviceNameOpt.fold("You do not have sufficient AWS privileges")(serviceName => s"You do not have sufficient privileges to perform actions on $serviceName")
        Failure("insufficient permissions", message, AwsPermissionsError, e).attempt
      } else if (e.getMessage.contains("InvalidInstanceId")) {
        Failure("InvalidInstanceId from AWS", "The specified instance(s) are not eligible targets (AWS said InvalidInstanceId)", AwsError, e).attempt
      } else {
        val details = serviceNameOpt.fold(s"AWS unknown error, unknown service (check logs for stacktrace). $e") { serviceName =>
          s"AWS unknown error, service: $serviceName (check logs for stacktrace), $e"
        }
        val friendlyMessage = serviceNameOpt.fold(s"Unknown error while making API calls to AWS. $e") { serviceName =>
          s"Unknown error while making an API call to AWS' $serviceName service, $e"
        }
        Failure(details, friendlyMessage, AwsError, e).attempt
      }
    }
  }
}
