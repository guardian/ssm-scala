package com.gu.ssm.aws

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{Future, Promise}


object AWS extends LazyLogging {
  private class AwsAsyncPromiseHandler[R <: AmazonWebServiceRequest, T](promise: Promise[T]) extends AsyncHandler[R, T] {
    def onError(e: Exception): Unit = {
      logger.error("AWS call failed", e)
      promise failure e
    }
    def onSuccess(r: R, t: T): Unit = promise success t
  }

  def asFuture[R <: AmazonWebServiceRequest, T]
  (awsClientMethod: Function2[R, AsyncHandler[R, T], java.util.concurrent.Future[T]])
  : Function1[R, Future[T]] = { awsRequest =>

    val p = Promise[T]()
    awsClientMethod(awsRequest, new AwsAsyncPromiseHandler(p))
    p.future
  }
}
