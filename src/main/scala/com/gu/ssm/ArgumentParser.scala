package com.gu.ssm

import java.io.File

import com.amazonaws.regions.{Region, Regions}
import com.gu.ssm.Arguments.{targetInstanceDefaultUser, bastionDefaultUser, defaultHostKeyAlgPreference}
import scopt.OptionParser


object ArgumentParser {

  val argParser: OptionParser[Arguments] = new OptionParser[Arguments]("ssm") {

    opt[String]('p', "profile").optional()
      .action { (profile, args) =>
        args.copy(profile = Some(profile))
      } text "The AWS profile name to use for authenticating this execution"

    opt[Seq[String]]('i', "instances")
      .action { (instanceIds, args) =>
        val instances = instanceIds.map(i => InstanceId(i)).toList
        args.copy(executionTarget = Some(ExecutionTarget(instances = Some(instances))))
      } text "Specify the instance ID(s) on which the specified command(s) should execute"

    opt[Seq[String]]('t', "tags")
      .validate { tagsStr =>
        Logic.extractSASTags(tagsStr).map(_ => ())
      }
      .action { (tagsStr, args) =>
        Logic.extractSASTags(tagsStr)
          .fold(
            _ => args,
            tagValues => args.copy(executionTarget = Some(ExecutionTarget(tagValues = Some(tagValues))))
          )
      } text "Search for instances by tag. If you provide less than 3 tags assumed order is app,stage,stack." +
      " e.g. '--tags riff-raff,prod' or '--tags grafana' Upper/lowercase variations will be tried."

    opt[String]('r', "region").optional()
      .validate { region =>
        try {
          Region.getRegion(Regions.fromName(region))
          success
        } catch {
          case _: IllegalArgumentException =>
            failure(s"Invalid AWS region name, $region")
        }
      } action { (region, args) =>
      args.copy(region = Region.getRegion(Regions.fromName(region)))
    } text "AWS region name (defaults to eu-west-1)"

    opt[Unit]("verbose").action( (_, c) =>
      c.copy(verbose = true) ).text("enable more verbose logging")

    opt[Unit]("use-default-credentials-provider").optional()
      .action((value, args) => args.copy(useDefaultCredentialsProvider = true))
      .text("Use the default AWS credentials provider chain rather than profile credentials. " +
            "This option is required when running within AWS itself.")

    cmd("cmd")
      .action((_, c) => c.copy(mode = Some(SsmCmd)))
      .text("Execute a single (bash) command, or a file containing bash commands")
      .children(
        opt[String]('u', "user").optional()
          .action((user, args) => args.copy(targetInstanceUser = Some(user)))
          .text(s"Execute command on remote host as this user (default: $targetInstanceDefaultUser)"),
        opt[String]('c', "cmd").optional()
          .action((cmd, args) => args.copy(toExecute = Some(cmd)))
          .text("A bash command to execute"),
        opt[File]('f', "file").optional()
          .action((file, args) => args.copy(toExecute = Some(Logic.generateScript(Right(file)))))
          .text("A file containing bash commands to execute")
      )

    cmd("repl")
      .action((_, c) => c.copy(mode = Some(SsmRepl)))
      .text("Run SSM in interactive/repl mode")

    cmd("ssh")
      .action((_, c) => c.copy(mode = Some(SsmSsh)))
      .text("Create and upload a temporary ssh key")
      .children(
        opt[String]('u', "user").optional()
          .action((user, args) => args.copy(targetInstanceUser = Some(user)))
          .text(s"Connect to remote host as this user (default: $targetInstanceDefaultUser)"),
        opt[Int]("port").optional()
          .action((port, args) => args.copy(targetInstancePortNumber = Some(port)))
          .text(s"Connect to remote host on this port"),
        opt[Unit]("newest").optional()
          .action((_, args) => {
            args.copy(
              singleInstanceSelectionMode = SismNewest,
              isSelectionModeNewest = true)
          })
          .text("Selects the newest instance if more than one instance was specified"),
        opt[Unit]("oldest").optional()
          .action((_, args) => {
            args.copy(
              singleInstanceSelectionMode = SismOldest,
              isSelectionModeOldest = true)
          })
          .text("Selects the oldest instance if more than one instance was specified"),
        opt[Unit]("private").optional()
          .action((_, args) => {
            args.copy(
              usePrivateIpAddress = true)
          })
          .text("Use private IP address (must be routable via VPN Gateway)"),
        opt[Unit]("raw").optional()
          .action((_, args) => {
            args.copy(
              rawOutput = true)
          })
          .text("Unix pipe-able ssh connection string. Note: disables automatic execution. You must use 'eval' to execute this due to nested quoting"),
        opt[Unit]('x', "execute").optional()
          .action((_, args) => {
            args.copy(
              rawOutput = false)
          })
          .text("[Deprecated - new default behaviour] Makes ssm behave like a single command (eg: `--raw` with automatic piping to the shell)"),
        opt[Unit]('d', "dryrun").optional()
          .action((_, args) => {
            args.copy(
              rawOutput = false)
          })
          .text("Generate SSH command but do not execute (previous default behaviour)"),
        opt[Unit]('A', "agent").optional()
          .action((_, args) => {
            args.copy(
              useAgent = Some(true))
          })
          .text("Use the local ssh agent to register the private key (and do not use -i); only bastion connections"),
        opt[Unit]('a', "no-agent").optional()
          .action((_, args) => {
            args.copy(
              useAgent = Some(false))
          })
          .text("Do not use the local ssh agent"),
        opt[String]('b', "bastion").optional()
          .action((bastion, args) => {
            args
              .copy(bastionInstance = Some(ExecutionTarget(Some(List(InstanceId(bastion))), None)))
          })
          .text(s"Connect through the given bastion specified by its instance id; implies -A (use agent) unless followed by -a."),
        opt[Seq[String]]('B', "bastion-tags").optional()
          .validate { tagsStr =>
            Logic.extractSASTags(tagsStr).map(_ => ())
          }
          .action { (tagsStr, args) =>
            Logic.extractSASTags(tagsStr)
              .fold(
                _ => args,
                tagValues => {
                  args
                    .copy(bastionInstance = Some(ExecutionTarget(None, Some(tagValues))))
                }
              )
          } text(s"Connect through the given bastion identified by its tags; implies -a (use agent) unless followed by -A."),
        opt[Int]("bastion-port").optional()
          .action((bastionPortNumber, args) => args.copy(bastionPortNumber = Some(bastionPortNumber)))
          .text(s"Connect through the given bastion at a given port. "),
        opt[String]("bastion-user").optional()
          .action((bastionUser, args) => args.copy(bastionUser = Some(bastionUser)))
          .text(s"Connect to bastion as this user (default: $bastionDefaultUser). "),
        opt[String]("host-key-alg-preference").optional().unbounded()
          .action((alg, args) => args.copy(hostKeyAlgPreference = alg :: args.hostKeyAlgPreference))
          .text(s"The preferred host key algorithms, can be specified multiple times - last is preferred (default: ${defaultHostKeyAlgPreference.mkString(", ")})"),
        opt[Unit]("ssm-tunnel").optional()
          .text("[deprecated]"),
        opt[Unit]("no-ssm-proxy").optional()
          .action((_, args) => args.copy(tunnelThroughSystemsManager = false))
          .text("Do not connect to the host proxying via AWS Systems Manager - go direct to port 22. Useful for  instances running old versions of systems manager (< 2.3.672.0)"),
        opt[String]("tunnel").optional()
          .validate { tunnelStr =>
            Logic.extractTunnelConfig(tunnelStr).map(_ => ())
          }
          .action((tunnelStr, args) => {
            Logic.extractTunnelConfig(tunnelStr)
              .fold(
                _ => args,
                tunnelTarget => args.copy(tunnelTarget = Some(tunnelTarget)))
          })
          .text("Forward traffic from the given local port to the given host and port on the remote side. Accepts the format `localPort:host:remotePort`, " +
            "e.g. --tunnel 5000:a.remote.host.com:5000"),
        opt[String]("rds-tunnel").optional()
          .validate { tunnelStr =>
            Logic.extractRDSTunnelConfig(tunnelStr).map(_ => ())
          }
          .action((tunnelStr, args) => {
            Logic.extractRDSTunnelConfig(tunnelStr)
              .fold(
                _ => args,
                tunnelTarget => args.copy(rdsTunnelTarget = Some(tunnelTarget)))
          })
          .text("Forward traffic from a given local port to a RDS database specified by tags. Accepts the format `localPort:tags`, where `tags` is a comma-separated list of tag values, " +
            "e.g. --rds-tunnel 5000:app,stack,stage"),
        checkConfig( c =>
          if (c.isSelectionModeOldest && c.isSelectionModeNewest) failure("You cannot both specify --newest and --oldest")
          else if (c.tunnelTarget.isDefined && c.rdsTunnelTarget.isDefined) failure("You cannot specify both --tunnel and --rdsTunnel")
          else success )
      )

    cmd("scp")
      .action((_, c) => c.copy(mode = Some(SsmScp)))
      .text("Secure Copy")
      .children(
        opt[String]('u', "user").optional()
          .action((user, args) => args.copy(targetInstanceUser = Some(user)))
          .text(s"Connect to remote host as this user (default: $targetInstanceDefaultUser)"),
        opt[Int]("port").optional()
          .action((port, args) => args.copy(targetInstancePortNumber = Some(port)))
          .text(s"Connect to remote host on this port"),
        opt[Unit]("newest").optional()
          .action((_, args) => {
            args.copy(
              singleInstanceSelectionMode = SismNewest,
              isSelectionModeNewest = true)
          })
          .text("Selects the newest instance if more than one instance was specified"),
        opt[Unit]("oldest").optional()
          .action((_, args) => {
            args.copy(
              singleInstanceSelectionMode = SismOldest,
              isSelectionModeOldest = true)
          })
          .text("Selects the oldest instance if more than one instance was specified"),
        opt[Unit]("private").optional()
          .action((_, args) => {
            args.copy(
              usePrivateIpAddress = true)
          })
          .text("Use private IP address (must be routable via VPN Gateway)"),
        opt[Unit]("raw").optional()
          .action((_, args) => {
            args.copy(
              rawOutput = true)
          })
          .text("Unix pipe-able scp connection string"),
        opt[Unit]('x', "execute").optional()
          .action((_, args) => {
            args.copy(
              rawOutput = true)
          })
          .text("Makes ssm behave like a single command (eg: `--raw` with automatic piping to the shell)"),
        opt[Unit]("ssm-tunnel").optional()
          .text("[deprecated]"),
        opt[Unit]("no-ssm-proxy").optional()
          .action((_, args) => args.copy(tunnelThroughSystemsManager = false))
          .text("Do not connect to the host proxying via AWS Systems Manager - go direct to port 22. Useful for instances running old versions of systems manager (< 2.3.672.0)"),

        arg[String]("[:]<sourceFile>...").required()
          .action( (sourceFile, args) => args.copy(sourceFile = Some(sourceFile)) )
          .text("Source file for the scp sub command. See README for details"),
        arg[String]("[:]<targetFile>...").required()
          .action( (targetFile, args) => args.copy(targetFile = Some(targetFile)) )
          .text("Target file for the scp sub command. See README for details"),
        checkConfig( c =>
          if (c.isSelectionModeOldest && c.isSelectionModeNewest) failure("You cannot both specify --newest and --oldest")
          else success )
      )

    checkConfig { args =>
      if (args.mode.isEmpty) Left("You must select a mode to use: cmd, repl or ssh")
      else if (args.toExecute.isEmpty && args.mode.contains(SsmCmd)) Left("You must provide commands to execute (src-file or cmd)")
      else if (args.executionTarget.isEmpty) Left("You must provide a list of target instances (-i) or instance App/Stage/Stack tags (-t)")
      else if (!args.useDefaultCredentialsProvider && args.profile.isEmpty && !System.getenv().containsKey("AWS_PROFILE")) Left("Expected --profile, --use-default-credentials-provider or AWS_PROFILE environment variable")
      else Right(())
    }
  }
}
