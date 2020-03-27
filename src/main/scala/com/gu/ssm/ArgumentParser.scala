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

    opt[String]('t', "tags")
      .validate { tagsStr =>
        Logic.extractSASTags(tagsStr).map(_ => ())
      }
      .action { (tagsStr, args) =>
        Logic.extractSASTags(tagsStr)
          .fold(
            _ => args,
            ass => args.copy(executionTarget = Some(ExecutionTarget(ass = Some(ass))))
          )
      } text "Search for instances by tag e.g. '--tags app,stack,stage'"

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
          .text("Unix pipe-able ssh connection string - note: you must use 'eval' to execute this due to nested quoting"),
        opt[Unit]('x', "execute").optional()
          .action((_, args) => {
            args.copy(
              rawOutput = true)
          })
          .text("Makes ssm behave like a single command (eg: `--raw` with automatic piping to the shell)"),
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
          .text(s"Connect through the given bastion specified by its instance id; implies -A (use agent) unless followed by -a. --ssm-tunnel can be used to avoid the need for a bastion instance"),
        opt[String]('B', "bastion-tags").optional()
          .validate { tagsStr =>
            Logic.extractSASTags(tagsStr).map(_ => ())
          }
          .action { (tagsStr, args) =>
            Logic.extractSASTags(tagsStr)
              .fold(
                _ => args,
                ass => {
                  args
                    .copy(bastionInstance = Some(ExecutionTarget(None, Some(ass))))
                }
              )
          } text(s"Connect through the given bastion identified by its tags; implies -a (use agent) unless followed by -A. --ssm-tunnel can be used to avoid the need for a bastion instance"),
        opt[Int]("bastion-port").optional()
          .action((bastionPortNumber, args) => args.copy(bastionPortNumber = Some(bastionPortNumber)))
          .text(s"Connect through the given bastion at a given port. --ssm-tunnel can be used to avoid the need for a bastion instance"),
        opt[String]("bastion-user").optional()
          .action((bastionUser, args) => args.copy(bastionUser = Some(bastionUser)))
          .text(s"Connect to bastion as this user (default: $bastionDefaultUser). --ssm-tunnel can be used to avoid the need for a bastion instance"),
        opt[String]("host-key-alg-preference").optional().unbounded()
          .action((alg, args) => args.copy(hostKeyAlgPreference = alg :: args.hostKeyAlgPreference))
          .text(s"The preferred host key algorithms, can be specified multiple times - last is preferred (default: ${defaultHostKeyAlgPreference.mkString(", ")})"),

        opt[Unit]("ssm-tunnel").optional()
          .action((_, args) => args.copy(tunnelThroughSystemsManager = true))
          .text("Connect to the host proxying through AWS Systems Manager, rather than directly to port 22. Requires Systems Manager Agent > 2.3.672.0 to be installed."),

        checkConfig( c =>
          if (c.isSelectionModeOldest && c.isSelectionModeNewest) failure("You cannot both specify --newest and --oldest")
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
          .action((_, args) => args.copy(tunnelThroughSystemsManager = true))
          .text("Connect to the host proxying through AWS Systems Manager, rather than directly to port 22. Requires Systems Manager Agent > 2.3.672.0 to be installed."),

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
      else if (args.executionTarget.isEmpty) Left("You must provide a list of target instances; or Stack, Stage, App tags")
      else if (args.profile.isEmpty && !System.getenv().containsKey("AWS_PROFILE")) Left("--profile switch or environment variable AWS_PROFILE expected")
      else Right(())
    }
  }
}
