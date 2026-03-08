package com.gu.ssm

import scala.util.Try

object Ssm {
  def ssh(
      profile: String,
      region: String,
      instance: Option[String],
      tags: Option[String],
      newest: Boolean,
      oldest: Boolean,
      instanceResolve: InstanceResolver
  ): Try[Int] =
    for {
      resolutionStrategy <-
        InstanceResolution.resolveInstanceStrategy(instance, tags, newest, oldest)
      _ = println(Output.displayResolutionPlan(resolutionStrategy))
      resolutionResult <-
        InstanceResolution.resolveInstance(resolutionStrategy, instanceResolve)
      _ = println(Output.displayResolutionResult(resolutionResult, profile, region))
      exitCode <- instanceResolve.handoffToAwsCli(resolutionResult)
    } yield exitCode
}
