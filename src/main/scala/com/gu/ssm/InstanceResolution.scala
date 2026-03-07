package com.gu.ssm

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.{DescribeInstancesRequest, Filter}

import java.time.Instant
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}

object InstanceResolution {
  def makeEc2Client(profile: String, region: String): Ec2Client = Ec2Client
    .builder()
    .credentialsProvider(ProfileCredentialsProvider.create(profile))
    .region(Region.of(region))
    .httpClient(UrlConnectionHttpClient.create())
    .build()

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
      ec2Client: Ec2Client
  ): Try[InstanceResolutionResult] =
    strategy match {
      case InstanceResolutionStrategy.InstanceId(instanceId) =>
        Success(InstanceResolutionResult.ResolvedInstance(instanceId))
      case InstanceResolutionStrategy.TagDiscovery(tags, tagDiscoveryStrategy) =>
        Try {
          val stateFilter = Filter
            .builder()
            .name("instance-state-name")
            .values("running")
            .build()
          val tagFilters = (tags.stackStage match {
            case None =>
              List(("tag:App", tags.app))
            case Some((stack, None)) =>
              List(
                ("tag:App", tags.app),
                ("tag:Stack", stack)
              )
            case Some((stack, Some(stage))) =>
              List(
                ("tag:App", tags.app),
                ("tag:Stack", stack),
                ("tag:Stage", stage)
              )
          }).map { case (name, value) =>
            Filter.builder().name(name).values(value).build()
          }
          val request = DescribeInstancesRequest
            .builder()
            .filters(stateFilter :: tagFilters: _*)
            .build()
          val response = ec2Client.describeInstances(request)
          val instances: List[InstanceInfo] = response
            .reservations()
            .asScala
            .flatMap(_.instances().asScala)
            .toList
            .map { instance =>
              val tags = instance.tags().asScala.map(tag => tag.key() -> tag.value()).toMap
              InstanceInfo(
                instance.instanceId(),
                tags.getOrElse("Name", "-"),
                tags,
                instance.launchTime()
              )
            }
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
