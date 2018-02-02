package com.gu.ssm

import com.amazonaws.regions.Region
import com.gu.ssm.utils.attempt._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor}
import com.gu.ssm.ArgumentParser.argParser

object Main {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  private val maximumWaitTime = 25.seconds

  def main(args: Array[String]): Unit = {
    argParser.parse(args, Arguments.empty()) match {
      case Some(Arguments(Some(executionTarget), toExecuteOpt, Some(profile), region, Some(mode))) =>
        val awsClients = Logic.getClients(profile, region)
        mode match {
          case SsmRepl =>
            new InteractiveProgram(awsClients).main(profile, region, executionTarget)
          case SsmCmd =>
            toExecuteOpt match {
              case Some(toExecute) => execute(awsClients, profile, region, executionTarget, toExecute)
              case _ => fail()
            }
          case SsmSsh =>
            setUpSSH(awsClients, profile, region, executionTarget)
        }
      case Some(_) => fail()
      case None => System.exit(ArgumentsError.code) // parsing cmd line args failed, help message will have been displayed
    }
  }

  private def fail(): Unit = {
    UI.printErr("Impossible application state! This should be enforced by the CLI parser.  Did not receive valid instructions")
    System.exit(UnhandledError.code)
  }

  private def setUpSSH(awsClients: AWSClients, profile: String, region: Region, executionTarget: ExecutionTarget): Unit = {
    val fProgramResult = for {
      config <- IO.getSSMConfig(awsClients.ec2Client, awsClients.stsClient, profile, region, executionTarget)
      sshArtifacts <- Attempt.fromEither(SSH.createKey())
      (authFile, authKey) = sshArtifacts
      addAndRemoveKeyCommand = SSH.addTaintedCommand(config.name) + SSH.addKeyCommand(authKey) + SSH.removeKeyCommand(authKey)
      instance <- Attempt.fromEither(Logic.getSingleInstance(config.targets))
      _ <- IO.tagAsTainted(instance, config.name, awsClients.ec2Client)
      _ <- IO.installSshKey(instance, config.name, addAndRemoveKeyCommand, awsClients.ssmClient)
    } yield config.targets.map(SSH.sshCmd(authFile, _))

    val programResult = Await.result(fProgramResult.asFuture, maximumWaitTime)
    programResult.fold(UI.outputFailure, UI.sshOutput)
    System.exit(programResult.fold(_.exitCode, _ => 0))
  }

  private def execute(awsClients: AWSClients, profile: String, region: Region, executionTarget: ExecutionTarget, toExecute: String): Unit = {
    val fProgramResult = for {
      config <- IO.getSSMConfig(awsClients.ec2Client, awsClients.stsClient, profile, region, executionTarget)
      _ <- Attempt.fromEither(Logic.checkInstancesList(config))
      results <- IO.executeOnInstances(config.targets.map(i => i.id), config.name, toExecute, awsClients.ssmClient)
      incorrectInstancesFromInstancesTag = Logic.computeIncorrectInstances(executionTarget, results)
    } yield ResultsWithInstancesNotFound(results,incorrectInstancesFromInstancesTag)
    val programResult = Await.result(fProgramResult.asFuture, maximumWaitTime)
    programResult.fold(UI.outputFailure, UI.output)
    System.exit(programResult.fold(_.exitCode, _ => 0))
  }
}
