package com.gu.ssm

import java.time.Instant

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
  case ResolutionError(message: String, tags: InstanceTags)
}
