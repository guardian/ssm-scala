package com.gu.ssm

import fansi.{Bold, Color, Str}

object Output {
  def displayResolutionPlan(resolutionStrategy: InstanceResolutionStrategy): Str =
    resolutionStrategy match {
      case InstanceResolutionStrategy.InstanceId(instanceId) =>
        // if there's no resolution required, no need to print a resolution plan
        ""
      case InstanceResolutionStrategy.TagDiscovery(tags, tagDiscoverStrategy) =>
        val tagDescriptions = formatInstanceTags(tags)
        val strategyDescription = tagDiscoverStrategy match {
          case TagDiscoveryStrategy.Single =>
            """
              |    Will select the instance to connect to if exactly one matching instance is found.
              |""".stripMargin
          case TagDiscoveryStrategy.Newest =>
            s""" ${parameter("(newest)")}
               |    Will select the most recently launched matching instance
               |""".stripMargin
          case TagDiscoveryStrategy.Oldest =>
            s""" ${parameter("(oldest)")}
               |    Will select the longest running matching instance
               |""".stripMargin
        }
        s"${operation("Discovering instances matching")} $tagDescriptions$strategyDescription"
    }

  def displayResolutionResult(
      result: InstanceResolutionResult,
      profile: String,
      region: String
  ): Str =
    result match {
      case InstanceResolutionResult.ResolvedInstance(targetInstanceId) =>
        operation(
          s"Starting Main session to ${instance(targetInstanceId)} (${label("profile=", profile)}, ${label("region=", region)})"
        )
      case InstanceResolutionResult.NoInstancesFound(tags) =>
        error(s"No running instances found matching ${parameter(profile)}")
      case InstanceResolutionResult.MultipleInstancesFound(tags, instances) =>
        val tagInfo = formatInstanceTags(tags)
        val instanceList = instances
          .map { instanceInfo =>
            s"""  ${instance(instanceInfo.id)}  ${instanceInfo.name}
               |    $tagInfo""".stripMargin
          }
          .mkString("\n")
        s"""
           |${heading("Multiple running instances found:")}
           |$instanceList
           |
           |${error("Unable to determine which instance to connect to.")}
           |Use ${argWarning("--newest")} or ${argWarning("--oldest")} to select automatically.
           |Or specify the instance ID directly with ${argWarning("--instance")} ${instance(
            "<id>"
          )}.
           |""".stripMargin
      case InstanceResolutionResult.ResolutionError(message, tags) =>
        val tagInfo = formatInstanceTags(tags)
        s"""${heading(s"Could not resolve instance from")} $tagInfo
           |${error(message)}""".stripMargin
    }

  private def formatInstanceTags(tags: InstanceTags) =
    tags.asPairs.map { case (k, v) => label(s"$k=", v) }.mkString(" ")

  // style guide
  private val dim        = Color.DarkGray
  private val argWarning = Color.Yellow
  private val instance   = Color.Cyan
  private val emphasised = Color.Green

  // helpers
  private def error(message: String): Str     = s"${Color.Red(Bold.On("Error:"))} $message"
  private def operation(message: String): Str = s"${Color.Cyan("▸")} $message"
  private def heading(msg: String): Str       = s"${Bold.On(msg)}"
  private def parameter(name: String): Str    = s"${Color.LightCyan(name)}"
  private def label(key: String, value: String): Str =
    dim(key) ++ emphasised(value)
}
