package com.gu.ssm

import mainargs.{arg, main, ParserForMethods}

import scala.util.{Failure, Success}

object SSM {
  def main(args: Array[String]): Unit = {
    ParserForMethods(this)
      .runOrExit(args.toSeq)
    ()
  }

  @main(doc = "Start an SSM session to an EC2 instance discovered by instance ID or tags")
  def ssh(
      @arg(short = 'p', doc = "The AWS profile name to use") profile: String,
      @arg(short = 'r', doc = "AWS region (default: eu-west-1)") region: String = "eu-west-1",
      @arg(short = 'i', doc = "Connect directly to this EC2 instance ID") instance: Option[String] =
        None,
      @arg(short = 't', doc = "Discover by App[,Stack[,Stage]] tags") tags: Option[String] = None,
      @arg(doc = "Select the most recently launched instance") newest: Boolean = false,
      @arg(doc = "Select the least recently launched instance") oldest: Boolean = false
  ): Unit = {
    // validation
    // ...
    // TODO move ec2Client creation behind tag resolution strategy, so it is only created if needed
    val ec2Client = InstanceResolution.makeEc2Client(profile, region)
    val result = for {
      resolutionStrategy <- InstanceResolution.resolveInstanceStrategy(
        instance,
        tags,
        newest,
        oldest
      )
      // print resolution strategy
      resolutionResult <- InstanceResolution.resolveInstance(resolutionStrategy, ec2Client)
    } yield resolutionResult
    val exitCode = result match {
      case Success(InstanceResolutionResult.ResolvedInstance(targetInstanceId)) =>
        println(s"Starting SSM session to $targetInstanceId (profile=$profile, region=$region)")
        handoffToAwsCli(profile, region, targetInstanceId)
      case Success(InstanceResolutionResult.NoInstancesFound(_)) =>
        ???
      case Success(InstanceResolutionResult.MultipleInstancesFound(_, _)) =>
        ???
      case Failure(exception) =>
        // TODO handle expected AWS errors
        // e.g. expired credentials, no matching AWS profile
        System.err.println(s"Error: ${exception.getMessage}")
        1
    }
    if (exitCode != 0) {
      // for convenience in dev mode, only terminate with non-default exit codes
      sys.exit(exitCode)
    }
  }

  @main(doc = "Print version information about this tool")
  def version(
  ): Unit =
    println(Version.branch)

  /** Handoff to the AWS CLI to start the SSM session. The AWS CLI can then handle the interactive
    * session in this terminal.
    */
  def handoffToAwsCli(profile: String, region: String, targetInstanceId: String): Int = {
    val pb = new ProcessBuilder(
      // format: off
      "aws", "ssm", "start-session",
      "--target", targetInstanceId,
      "--profile", profile,
      "--region", region
      // format: on
    )
    pb.inheritIO()
    val process = pb.start()
    process.waitFor()
  }
}
