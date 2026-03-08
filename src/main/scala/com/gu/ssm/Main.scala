package com.gu.ssm

import com.gu.ssm
import fansi.Bold
import mainargs.{arg, main, ParserForMethods}

import scala.util.{Failure, Success}

/** Handles the CLI parsing and entry point for the application.
  */
object Main {
  def main(args: Array[String]): Unit = {
    ParserForMethods(this)
      .runOrExit(args.toSeq, sorted = false)
    ()
  }

  @main(
    doc = "Start an Main session to an EC2 instance by instance ID or discovered via tags"
  )
  def ssh(
      @arg(short = 'p', doc = "The AWS profile name to use") profile: String,
      @arg(short = 'r', doc = "AWS region (default: eu-west-1)") region: String = "eu-west-1",
      @arg(short = 'i', doc = "Connect to this EC2 instance ID") instance: Option[String] = None,
      @arg(short = 't', doc = "Discover by App[,Stack[,Stage]] tags") tags: Option[String] = None,
      @arg(doc = "Select the most recently launched instance") newest: Boolean = false,
      @arg(doc = "Select the least recently launched instance") oldest: Boolean = false
  ): Unit = {
    val instanceResolver = new AwsInstanceResolver(profile, region)
    Ssm.ssh(profile, region, instance, tags, newest, oldest, instanceResolver) match {
      case Success(exitCode) =>
        if (exitCode != 0) {
          // for convenience in dev mode, only terminate with non-default exit codes
//          sys.exit(exitCode)
        }
      case Failure(exception) =>
        // handle unexpected errors
        System.err.println(s"Error: ${exception.getMessage}")
//        sys.exit(1)
    }
  }

  @main(doc = "Print version information about this tool")
  def version(): Unit = {
    // these properties are set at build time via environment variables
    // release defaults to "dev" for local development builds
    val releaseStr = Bold.On(Version.release)
    // architecture and branch have no fallbacks and will be empty when running locally
    val architectureStr = Version.architecture.fold("")(arch => s" ($arch)")
    val branchStr       = Version.branch.fold("")(branch => s" [$branch]")

    println(s"$releaseStr$architectureStr$branchStr")
  }
}
