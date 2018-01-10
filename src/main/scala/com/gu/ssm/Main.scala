package com.gu.ssm

import java.io.File

import com.amazonaws.regions.{Region, Regions}
import com.gu.ssm.aws.{SSM, STS}
import scopt.OptionParser

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.io.Source


object Main {
  implicit val ec = ExecutionContext.global

  def main(args: Array[String]): Unit = {

    // TODO attempt to read from stdin to get commands and populate initial arguments thus

    argParser.parse(args, Arguments.empty()) match {
      case Some(Arguments(instances, cmdOpt, srcFileOpt, Some(profile), region)) =>
        val cmd = cmdOpt.orElse(srcFileOpt.map(Source.fromFile(_, "UTF-8").mkString)).get //

        val stsClient = STS.client(profile, region)
        val ssmClient = SSM.client(profile, region)
        val fProgramResult: Future[List[(Instance, Either[CommandStatus, CommandResult])]] = for {
          name <- STS.getCallerIdentity(stsClient)
          cmdId <- SSM.sendCommand(instances, cmd, name, ssmClient)
          results <- SSM.getCmdOutputs(instances, cmdId, ssmClient)
        } yield results

        val instanceResults = Await.result(fProgramResult, 15.seconds)
        instanceResults.foreach { case (instance, result) =>
          UI.printMetadata(s"========= ${instance.id} =========")
          if (result.isLeft) {
            UI.printMetadata(result.left.get.toString)
          } else {
            val output = result.right.get
            UI.printMetadata(s"STDOUT:")
            println(output.stdOut)
            UI.printMetadata(s"STDERR:")
            UI.printErr(output.stdErr)
          }
        }

      case Some(Arguments(instances, cmdOpt, srcFileOpt, profileOpt, region)) =>
        throw new RuntimeException("Currently assuming command and profile are provided")
      case None =>
        // parsing cmd line args failed, help message will have been displayed
    }
  }

  private val argParser = new OptionParser[Arguments]("ssm") {
    opt[String]("profile")
      .action { case (profile, args) =>
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
    opt[Seq[String]]('i', "instances").required()
      .action { case (instanceIds, args) =>
        val instances = instanceIds.map(Instance).toList
        args.copy(instances = instances)
      } text "specify the instance ID(s) on which the specified command(s) should execute"
    opt[String]('c', "cmd")
      .action { case (command, args) =>
        args.copy(command = Some(command))
      } text "a (bash) command to execute"
    opt[File]('f', "src-file")
        .action { case (file, args) =>
          args.copy(srcFile = Some(file))
        } text "a file containing bash commands to execute"
    checkConfig { args =>
      if (args.command.isEmpty && args.srcFile.isEmpty) {
        Left("You must provide cmd or src-file")
      } else {
        Right(())
      }
    }
  }
}
