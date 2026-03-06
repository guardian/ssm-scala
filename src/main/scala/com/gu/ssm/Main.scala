package com.gu.ssm

import fansi.{Bold, Color}

import scala.util.Success


/** Natively compiled tool that wraps the Scala logic in a CLI program.
  */
object Main {
  def main(args: Array[String]): Unit = {
    val exitCode = args.headOption match {
      case Some("help" | "--help" | "-h") =>
        printUsage()
        ExitCode.Success
      case Some("update") =>
        update()
      case Some("version" | "--version" | "-v") =>
        printVersion()
        ExitCode.Success
      case Some(unknown) =>
        System.err.println(Color.Red(s"Unknown command: $unknown"))
        printUsage()
        ExitCode.InvalidUsage
      case None =>
        printUsage()
        ExitCode.InvalidUsage
    }
    if (exitCode != ExitCode.Success) {
      // for convenience in dev mode, only terminate with non-default exit codes
      sys.exit(exitCode.code)
    }
  }

  def update(): ExitCode = {
    val currentVersion = Version.release
    val architecture   = Version.architecture
    val branch         = Version.branch

    val result = Releases
      .fetchLatestRelease()
      .map { latestRelease =>
        Releases.checkForUpdate(
          currentVersion,
          architecture,
          branch,
          latestRelease
        )
      }
    println(
      Releases.formatUpdateCheckResult(result, currentVersion, architecture)
    )
    result match {
      case Success(updateResult) if updateResult.successful =>
        ExitCode.Success
      case _ =>
        ExitCode.Error
    }
  }

  private def printUsage(): Unit = {
    val header = s"${Bold.On("Usage:")} ssm TODO"
    // ssm arguments
    val argumentsTitle = Bold.On("Arguments:")
    // ssm commands
    val commandsTitle = Bold.On("Commands:")
    val versionCmd    = Bold.On(Color.Cyan("version"))
    val updateCmd     = Bold.On(Color.Cyan("update"))
    val helpCmd       = Bold.On(Color.Cyan("help"))
    // version information
    val versionTitle   = Bold.On("Version:")
    val releaseLineStr = s"  release   ${Version.release}"
    val archLineStr    = Version.architecture.map(a => s"  arch      $a")
    val branchLineStr  = Version.branch.map(b => s"  branch    $b")
    val devModeNoteStr =
      if (Version.architecture.isEmpty && Version.branch.isEmpty) {
        Some("  (running in development mode)")
      } else {
        None
      }
    val versionInfoString =
      List(Some(releaseLineStr), archLineStr, branchLineStr, devModeNoteStr).flatten
        .mkString("\n")

    println(
      s"""$header
         |
         |Open SSM sessions on EC2 instances.
         |
         |$argumentsTitle
         |  TODO
         |
         |$commandsTitle
         |  $versionCmd   Show ssm's version
         |  $updateCmd    Check for updates to ssm's CLI
         |
         |  $helpCmd      Show this help text
         |
         |$versionTitle
         |$versionInfoString
         |""".stripMargin
    )
  }

  private def printVersion(): Unit = {
    // these properties are set at build time via environment variables
    // release defaults to "dev" for local development builds
    val releaseStr = Bold.On(Version.release).toString
    // architecture and branch have no fallbacks and will be empty when running locally
    val architectureStr = Version.architecture.fold("")(arch => s" ($arch)")
    val branchStr       = Version.branch.fold("")(branch => s" [$branch]")

    println(s"$releaseStr$architectureStr$branchStr")
  }

  /** Exit codes for the ssm CLI tool.
    *
    * Following Unix conventions:
    *   - 0 indicates success
    *   - 1 indicates a general error
    *   - 2 indicates invalid usage or command
    */
  enum ExitCode(val code: Int) {
    case Success      extends ExitCode(0)
    case Error        extends ExitCode(1)
    case InvalidUsage extends ExitCode(2)
  }
}
