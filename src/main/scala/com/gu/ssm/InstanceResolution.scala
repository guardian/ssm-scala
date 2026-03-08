package com.gu.ssm

import java.time.Instant
import scala.util.{Failure, Success, Try}

object InstanceResolution {
  def resolveInstanceStrategy(
      instance: Option[String],
      tags: Option[String],
      newest: Boolean,
      oldest: Boolean
  ): Try[InstanceResolutionStrategy] =
    (instance, tags, newest, oldest) match {
      case (Some(instanceId), None, _, _) =>
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
            "Invalid combination of instance resolution parameters. Please specify either an instance ID or tags with a selection strategy."
          )
        )
    }

  def resolveInstance(
      strategy: InstanceResolutionStrategy,
      instanceResolver: InstanceResolver
  ): Try[InstanceResolutionResult] =
    strategy match {
      case InstanceResolutionStrategy.InstanceId(instanceId) =>
        Success(InstanceResolutionResult.ResolvedInstance(instanceId))
      case InstanceResolutionStrategy.TagDiscovery(tags, tagDiscoveryStrategy) =>
        instanceResolver.describeMatchingInstances(tags).map { instances =>
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
              instances.sortBy(_.launchTime)(Ordering[Instant].reverse).headOption match {
                case Some(instance) =>
                  InstanceResolutionResult.ResolvedInstance(instance.id)
                case None =>
                  InstanceResolutionResult.NoInstancesFound(tags)
              }
            case TagDiscoveryStrategy.Oldest =>
              instances.sortBy(_.launchTime).headOption match {
                case Some(instance) =>
                  InstanceResolutionResult.ResolvedInstance(instance.id)
                case None =>
                  InstanceResolutionResult.NoInstancesFound(tags)
              }
          }
        }
    }

  private def parseTags(str: String): Try[InstanceTags] =
    str.split(',').toList match {
      case app :: Nil =>
        Success(InstanceTags(app, None))
      case app :: stack :: Nil =>
        Success(InstanceTags(app, Some((stack, None))))
      case app :: stack :: stage :: Nil =>
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

enum InstanceResolutionStrategy {
  case InstanceId(instanceId: String)
  case TagDiscovery(tags: InstanceTags, TagDiscoveryStrategy: TagDiscoveryStrategy)
}

enum TagDiscoveryStrategy {
  case Single
  case Newest
  case Oldest
}

// App is required, then optionally Stack and then optionally Stage
case class InstanceTags(app: String, stackStage: Option[(String, Option[String])]) {
  val stack: Option[String] = stackStage.map(_._1)
  val stage: Option[String] = stackStage.flatMap(_._2)

  val asPairs: List[(String, String)] =
    List(
      Some("App" -> app),
      stack.map("Stack" -> _),
      stage.map("Stage" -> _)
    ).flatten
}

class InstanceResolutionException(message: String) extends Exception(message)

case class InstanceInfo(
    id: String,
    name: String,
    tags: Map[String, String],
    launchTime: Instant
)

enum InstanceResolutionResult {
  case ResolvedInstance(instanceId: String)
  case NoInstancesFound(tags: InstanceTags)
  case MultipleInstancesFound(tags: InstanceTags, instances: List[InstanceInfo])
}
