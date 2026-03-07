package com.gu.ssm

import fansi.*
import mainargs.{ParserForMethods, arg, main}
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.*

import java.time.format.DateTimeFormatter
import java.time.{Duration, Instant, ZoneId}
import scala.jdk.CollectionConverters.*

object SSMTmp {

  // --- Semantic Styling using Fansi ---
  private val bold   = fansi.Bold.On
  private val red    = fansi.Color.Red
  private val green  = fansi.Color.Green
  private val yellow = fansi.Color.Yellow
  private val cyan   = fansi.Color.Cyan
  private val dim    = fansi.Color.DarkGray

  private def die(msg: String): Nothing = {
    System.err.println(red(bold(s"Error:")) ++ s" $msg")
    sys.exit(1)
  }

  private def info(msg: String): Unit                      = println(s"${cyan("▸")} $msg")
  private def heading(msg: String): Unit                   = println(bold(msg))
  private def label(key: String, value: String): fansi.Str = dim(key) ++ green(value)

  case class InstanceInfo(
      id: String,
      launchTime: Instant,
      name: String,
      tags: Map[String, String]
  )

  @main
  def run(
      @arg(short = 'p', doc = "The AWS profile name to use") profile: String,
      @arg(short = 'r', doc = "AWS region (default: eu-west-1)") region: String = "eu-west-1",
      @arg(short = 'i', doc = "Connect directly to this EC2 instance ID") instance: Option[String] =
        None,
      @arg(short = 't', doc = "Discover by App[,Stack[,Stage]] tags") tags: Option[String] = None,
      @arg(doc = "Select the most recently launched instance") newest: Boolean = false,
      @arg(doc = "Select the least recently launched instance") oldest: Boolean = false
  ): Unit = {

    // 1. Validation logic
    if (instance.isEmpty && tags.isEmpty)
      die(s"Either ${yellow("--instance")} or ${yellow("--tags")} must be provided")
    if (instance.isDefined && tags.isDefined) die("Cannot specify both --instance and --tags")

    val targetId = instance.getOrElse {
      resolveInstanceViaTags(profile, region, tags.get, newest, oldest)
    }

    // 2. Start Session (Handoff to AWS CLI)
    info(s"Starting SSM session to ${cyan(targetId)} ${dim(s"(profile=$profile, region=$region)")}")

    val pb = new java.lang.ProcessBuilder(
      "aws",
      "ssm",
      "start-session",
      "--target",
      targetId,
      "--profile",
      profile,
      "--region",
      region
    )
    pb.inheritIO()

    val process = pb.start()
    sys.exit(process.waitFor())
  }

  private def resolveInstanceViaTags(
      profile: String,
      regionName: String,
      tagInput: String,
      newest: Boolean,
      oldest: Boolean
  ): String = {
    val tagParts = tagInput.split(",").toSeq
    if (tagParts.isEmpty || tagParts.size > 3) die("Tags must be App[,Stack[,Stage]]")

    val ec2 = Ec2Client
      .builder()
      .credentialsProvider(ProfileCredentialsProvider.create(profile))
      .region(Region.of(regionName))
      .httpClient(UrlConnectionHttpClient.create())
      .build()

    val filters = List.newBuilder[Filter]
    filters += Filter.builder().name("tag:App").values(tagParts.head).build()
    tagParts.lift(1).foreach(s => filters += Filter.builder().name("tag:Stack").values(s).build())
    tagParts.lift(2).foreach(s => filters += Filter.builder().name("tag:Stage").values(s).build())
    filters += Filter.builder().name("instance-state-name").values("running").build()

    // Logging search parameters
    val searchKeys = Seq("App", "Stack", "Stage")
    val searchDesc = tagParts.zip(searchKeys).map { case (v, k) => label(s"$k=", v) }.mkString(", ")
    info(s"Discovering instances matching $searchDesc ...")

    val response = ec2.describeInstances(
      DescribeInstancesRequest.builder().filters(filters.result().asJava).build()
    )

    val found = response
      .reservations()
      .asScala
      .flatMap(_.instances().asScala)
      .map { i =>
        val tMap = i.tags().asScala.map(t => t.key() -> t.value()).toMap
        InstanceInfo(i.instanceId(), i.launchTime(), tMap.getOrElse("Name", "-"), tMap)
      }
      .toList

    found match {
      case Nil =>
        die(s"No running instances found using profile ${green(profile)}")

      case List(single) =>
        single.id

      case multiple =>
        if (!newest && !oldest) {
          println("")
          heading("Multiple instances found:")
          val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.of("UTC"))

          multiple.foreach { i =>
            val age    = Duration.between(i.launchTime, Instant.now())
            val ageStr = if (age.toDays > 0) s"${age.toDays}d ago" else s"${age.toHours}h ago"

            println(s"  ${cyan(i.id)}  ${i.name}")
            val meta = Seq(
              label("App=", i.tags.getOrElse("App", "-")),
              label("Stack=", i.tags.getOrElse("Stack", "-")),
              label("Stage=", i.tags.getOrElse("Stage", "-")),
              label("launched ", ageStr) ++ dim(s" at ${fmt.format(i.launchTime)}")
            ).mkString(" ")
            println(s"    $meta")
          }
          println("")
          die(
            s"""Use ${yellow("--newest")} or ${yellow("--oldest")} to select automatically.
               |Or specify the instance ID directly with ${yellow("--instance")} ${cyan(
                "<id>"
              )}.""".stripMargin
          )
        }

        val sorted = multiple.sortBy(_.launchTime.toEpochMilli)
        val picked = if (newest) sorted.last else sorted.head
        info(s"Resolved instance: ${cyan(picked.id)}")
        picked.id
    }
  }

  def main(args: Array[String]): Unit = {
    ParserForMethods(this).runOrExit(args.toSeq)
    ()
  }
}
