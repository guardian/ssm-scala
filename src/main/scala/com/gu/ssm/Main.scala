package com.gu.ssm

import com.amazonaws.regions.{Region, Regions}
import com.gu.ssm.aws.{SSM, STS}
import scopt.OptionParser

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._


object Main {
  def main(args: Array[String]): Unit = {
    implicit val ec = ExecutionContext.global

    argParser.parse(args, Arguments.empty()) match {
      case Some(Arguments(instances, Some(cmd), Some(profile), region)) =>
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

      case Some(Arguments(instances, cmdOpt, profileOpt, region)) =>
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
    opt[Seq[String]]("instances").required()
      .action { case (instanceIds, args) =>
        val instances = instanceIds.map(Instance).toList
        args.copy(instances = instances)
      } text "specify the instance ID(s) on which this command should run"
    opt[String]("cmd")
      .action { case (command, args) =>
        args.copy(command = Some(command))
      } text "the (bash) command to execute"
  }
}
