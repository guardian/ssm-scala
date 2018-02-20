package com.gu.ssm

import java.io.File

import com.amazonaws.regions.{Region, Regions}
import scopt.OptionParser


object ArgumentParser {

  val argParser: OptionParser[Arguments] = new OptionParser[Arguments]("ssm") {

    opt[String]('p', "profile").required()
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

    cmd("cmd")
      .action((_, c) => c.copy(mode = Some(SsmCmd)))
      .text("Execute a single (bash) command, or a file containing bash commands")
      .children(
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
        checkConfig( c =>
          if (c.isSelectionModeOldest && c.isSelectionModeNewest) failure("You cannot both specify --newest and --oldest")
          else success )
      )

    checkConfig { args =>
      if (args.mode.isEmpty) Left("You must select a mode to use: cmd, repl or ssh")
      else if (args.toExecute.isEmpty && args.mode.contains(SsmCmd)) Left("You must provide commands to execute (src-file or cmd)")
      else if (args.executionTarget.isEmpty) Left("You must provide a list of target instances; or Stack, Stage, App tags")
      else Right(())
    }
  }
}
