package com.gu.ssm

import scala.util.{Failure, Success, Try}

object Ssm {

  /** The actual ssh subprogram for our SSM CLI.
    *
    * While Main takes care of CLI details, this method is responsible for the core logic of
    * resolving an instance to connect to based on the provided parameters, and then handing off to
    * the InstanceResolver to start the session.
    */
  def ssh(
      profile: String,
      region: String,
      instance: Option[String],
      tags: Option[String],
      newest: Boolean,
      oldest: Boolean,
      instanceResolve: InstanceManager
  ): Try[Int] =
    for {
      resolutionStrategy <- resolveInstanceStrategy(instance, tags, newest, oldest)
      _ = println(Output.displayResolutionPlan(resolutionStrategy))
      resolutionResult <- resolveInstance(resolutionStrategy, instanceResolve)
      _ = println(Output.displayResolutionResult(resolutionResult, profile, region))
      exitCode <- instanceResolve.handoffSession(resolutionResult)
    } yield exitCode

  /** Turns the raw CLI parameters into a structured resolution strategy, validating that the
    * combination of parameters is correct and parsing the tags strings, if provided.
    */
  private[ssm] def resolveInstanceStrategy(
      instance: Option[String],
      tags: Option[String],
      newest: Boolean,
      oldest: Boolean
  ): Try[InstanceResolutionStrategy] =
    (instance, tags, newest, oldest) match {
      case (Some(instanceId), None, false, false) =>
        Success(InstanceResolutionStrategy.InstanceId(instanceId))
      case (None, Some(tagsStr), newestFlag, oldestFlag) if !(newestFlag && oldestFlag) =>
        parseTags(tagsStr)
          .map { instanceTags =>
            val tagDiscoveryStrategy =
              if (newestFlag) TagDiscoveryStrategy.Newest
              else if (oldestFlag) TagDiscoveryStrategy.Oldest
              else TagDiscoveryStrategy.Single
            InstanceResolutionStrategy.TagDiscovery(instanceTags, tagDiscoveryStrategy)
          }
      case _ =>
        Failure(
          new InstanceResolutionException(
            "Invalid instance parameters. Please specify either just an instance ID or tags with an optional oldest/newest."
          )
        )
    }

  /** Resolves the instance to connect to based on the provided resolution strategy.
    *
    * If the strategy is to resolve by tags, this will query the InstanceResolver for matching
    * instances and apply the tag discovery strategy to determine which instance to connect to.
    */
  private[ssm] def resolveInstance(
      strategy: InstanceResolutionStrategy,
      instanceResolver: InstanceManager
  ): Try[InstanceResolutionResult] =
    strategy match {
      case InstanceResolutionStrategy.InstanceId(instanceId) =>
        // if we're already given an instance ID, resolution is can complete immediately with that instance ID
        Success(InstanceResolutionResult.ResolvedInstance(instanceId))
      case InstanceResolutionStrategy.TagDiscovery(tags, tagDiscoveryStrategy) =>
        // otherwise we need to ask the resolver for matching instances and apply the tag discovery strategy
        instanceResolver.findMatchingInstances(tags).map { instancesOrError =>
          instancesOrError.map { instances =>
            tagDiscoveryStrategy match {
              case TagDiscoveryStrategy.Single =>
                instances match {
                  case Nil =>
                    InstanceResolutionResult.NoInstancesFound(tags)
                  case instance :: Nil =>
                    InstanceResolutionResult.ResolvedInstance(instance.id)
                  case _ =>
                    InstanceResolutionResult.MultipleInstancesFound(tags, instances)
                }
              case TagDiscoveryStrategy.Newest =>
                instances.maxByOption(_.launchTime) match {
                  case Some(instance) =>
                    InstanceResolutionResult.ResolvedInstance(instance.id)
                  case None =>
                    InstanceResolutionResult.NoInstancesFound(tags)
                }
              case TagDiscoveryStrategy.Oldest =>
                instances.minByOption(_.launchTime) match {
                  case Some(instance) =>
                    InstanceResolutionResult.ResolvedInstance(instance.id)
                  case None =>
                    InstanceResolutionResult.NoInstancesFound(tags)
                }
            }
          }.merge
        }
    }

  /** Parses a tags string in the format "App(,Stack(,Stage))" into an InstanceTags object. The App
    * tag is required, while Stack and Stage are optional.
    */
  private[ssm] def parseTags(str: String): Try[InstanceTags] =
    str.split(',').toList match {
      case app :: Nil if app.nonEmpty =>
        Success(InstanceTags(app, None))
      case app :: stack :: Nil if app.nonEmpty && stack.nonEmpty =>
        Success(InstanceTags(app, Some((stack, None))))
      case app :: stack :: stage :: Nil if app.nonEmpty && stack.nonEmpty && stage.nonEmpty =>
        Success(InstanceTags(app, Some((stack, Some(stage)))))
      case Nil =>
        Failure(
          new InstanceResolutionException(
            "Tags string cannot be empty. Expected format is 'App[,Stack[,Stage]]'."
          )
        )
      case _ =>
        Failure(
          new InstanceResolutionException(
            s"Invalid tags format: '$str'. Expected format is 'App[,Stack[,Stage]]'."
          )
        )
    }
}
