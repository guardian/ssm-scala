package com.gu.ssm

import java.io.File

import com.amazonaws.regions.{Region, Regions}
import com.gu.ssm.aws.{EC2, SSM, STS}
import com.gu.ssm.utils.attempt.{ArgumentsError, Attempt, UnhandledError}
import scopt.OptionParser

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}


object Main {
  implicit val ec = ExecutionContext.global

  def main(args: Array[String]): Unit = {
    // TODO attempt to read from stdin to get commands and populate initial arguments thus

    argParser.parse(args, Arguments.empty()) match {
      case Some(Arguments(Some(executionTarget), Some(toExecute), Some(profile), region, interactive)) =>
        // config
        val stsClient = STS.client(profile, region)
        val ssmClient = SSM.client(profile, region)
        val ec2Client = EC2.client(profile, region)

        // resolve user and instances in parallel
        val configAttempt = Attempt.map2(
          IO.resolveInstances(executionTarget, ec2Client),
          STS.getCallerIdentity(stsClient)
        )((_, _))

        if (interactive) {
          val interactive = new InteractiveProgram(ssmClient)(ec)
          interactive.main(configAttempt)
        } else {
          val programAttempt = for {
            config <- configAttempt
            (instances, username) = config
            results <- IO.executeOnInstances(instances, username, toExecute, ssmClient)
          } yield results
          val programResult = Await.result(programAttempt.asFuture, 25.seconds)
          // output and exit
          programResult.fold(UI.outputFailure, UI.output)
          System.exit(programResult.fold(_.exitCode, _ => 0))
        }

      case Some(Arguments(instances, toExecuteOpt, profileOpt, region, interactive)) =>
        // the CLI parser's `checkConfig` function means this should be unreachable code
        UI.printErr("Impossible application state! This should be enforced by the CLI parser")
        System.exit(UnhandledError.code)
      case None =>
        // parsing cmd line args failed, help message will have been displayed
        System.exit(ArgumentsError.code)
    }
  }

  private val argParser = new OptionParser[Arguments]("ssm") {
    opt[String]("profile").required()
      .action { (profile, args) =>
        args.copy(profile = Some(profile))
      } text "the AWS profile name to use for authenticating this execution"
    opt[String]("region").optional()
      .validate { region =>
        try {
          Region.getRegion(Regions.fromName(region))
          success
        } catch {
          case e: IllegalArgumentException =>
            failure(s"Invalid AWS region name, $region")
        }
      } action { (region, args) =>
      args.copy(region = Region.getRegion(Regions.fromName(region)))
    } text "AWS region name (defaults to eu-west-1)"
    // TODO: make these args instead of opts
    opt[Seq[String]]('i', "instances")
      .action { (instanceIds, args) =>
        val instances = instanceIds.map(Instance).toList
        args.copy(executionTarget = Some(ExecutionTarget(instances = Some(instances))))
      } text "specify the instance ID(s) on which the specified command(s) should execute"
    opt[String]('t', "ass-tags")
      .validate { tagsStr =>
        Logic.extractSASTags(tagsStr).map(_ => ())
      }
      .action { (tagsStr, args) =>
        Logic.extractSASTags(tagsStr)
          .fold(
            _ => args,
            ass => args.copy(executionTarget = Some(ExecutionTarget(ass = Some(ass))))
          )
      } text "search for instances by tag e.g. --ssa-tags app,stack,stage"
    opt[String]('c', "cmd")
      .action { (command, args) =>
        args.copy(toExecute = Some(ToExecute(cmdOpt = Some(command))))
      } text "a (bash) command to execute"
    opt[File]('f', "src-file")
      .action { (file, args) =>
        args.copy(toExecute = Some(ToExecute(scriptOpt = Some(file))))
      } text "a file containing bash commands to execute"
    opt[Unit]('I', "interactive")
      .action { (_, args) =>
        args.copy(interactive = true)
      } text "run SSM in interactive mode"
    checkConfig { args =>
      if (args.toExecute.isEmpty && !args.interactive) {
        Left("You must provide cmd or src-file")
      } else {
        if (args.executionTarget.isEmpty) {
          Left("You must provide a list of target instances, Stack, Stage, App tags")
        } else {
          Right(())
        }
      }
    }
  }
}
