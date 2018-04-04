package com.gu.ssm

import com.gu.ssm.utils.attempt._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor}
import com.gu.ssm.ArgumentParser.argParser

object Main {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  private val maximumWaitTime = 25.seconds

  def main(args: Array[String]): Unit = {
    argParser.parse(args, Arguments.empty()) match {
      case Some(Arguments(Some(executionTarget), toExecuteOpt, profile, region, Some(mode), Some(user), sism, _, _, usePrivate, rawOutput, bastionInstanceIdOpt, bastionPortNumberOpt)) =>
        val awsClients = Logic.getClients(profile, region)
        mode match {
          case SsmRepl =>
            new InteractiveProgram(awsClients).main(profile, region, executionTarget)
          case SsmCmd =>
            toExecuteOpt match {
              case Some(toExecute) => execute(awsClients, executionTarget, toExecute)
              case _ => fail()
            }
          case SsmSsh => bastionInstanceIdOpt match {
            case None => setUpStandardSSH(awsClients, executionTarget, user, sism, usePrivate, rawOutput)
            case Some(bastionInstanceId) => setUpBastionSSH(awsClients, executionTarget, user, sism, usePrivate, rawOutput, bastionInstanceId, bastionPortNumberOpt)
          }
        }
      case Some(_) => fail()
      case None => System.exit(ArgumentsError.code) // parsing cmd line args failed, help message will have been displayed
    }
  }

  private def fail(): Unit = {
    UI.printErr("Impossible application state! This should be enforced by the CLI parser.  Did not receive valid instructions")
    System.exit(UnhandledError.code)
  }

  private def setUpStandardSSH(awsClients: AWSClients, executionTarget: ExecutionTarget, user: String, sism: SingleInstanceSelectionMode, usePrivate: Boolean, rawOutput: Boolean) = {
    val fProgramResult = for {
      config <- IO.getSSMConfig(awsClients.ec2Client, awsClients.stsClient, executionTarget)
      sshArtifacts <- Attempt.fromEither(SSH.createKey())
      (privateKeyFile, publicKey) = sshArtifacts
      addAndRemovePublicKeyCommand = SSH.addTaintedCommand(config.name) + SSH.addPublicKeyCommand(user, publicKey) + SSH.removePublicKeyCommand(user, publicKey)
      instance <- Attempt.fromEither(Logic.getSSHInstance(config.targets, sism, usePrivate))
      _ <- IO.tagAsTainted(instance.id, config.name, awsClients.ec2Client)
      _ <- IO.installSshKey(instance.id, config.name, addAndRemovePublicKeyCommand, awsClients.ssmClient)
      address <- Attempt.fromEither(Logic.getAddress(instance, usePrivate))
    } yield SSH.sshCmdStandard(rawOutput)(privateKeyFile, instance, user, address)
    val programResult = Await.result(fProgramResult.asFuture, maximumWaitTime)
    programResult.fold(UI.outputFailure, UI.sshOutput(rawOutput))
    System.exit(programResult.fold(_.exitCode, _ => 0))
  }

  private def setUpBastionSSH(awsClients: AWSClients, executionTarget: ExecutionTarget, user: String, sism: SingleInstanceSelectionMode, usePrivate: Boolean, rawOutput: Boolean, bastionInstanceId: String, bastionPortNumberOpt: Option[Int]) = {
    val fProgramResult = for {
      sshArtifacts <- Attempt.fromEither(SSH.createKey())
      (privateKeyFile, publicKey) = sshArtifacts
      bastionConfig <- IO.getSSMConfig(awsClients.ec2Client, awsClients.stsClient, ExecutionTarget(Some(List(InstanceId(bastionInstanceId)))))
      bastionInstance <- Attempt.fromEither(Logic.getSSHInstance(bastionConfig.targets, SismUnspecified, false))
      bastionAddAndRemovePublicKeyCommand = SSH.addPublicKeyCommand(user, publicKey) + SSH.removePublicKeyCommand(user, publicKey)
      bastionAddress <- Attempt.fromEither(Logic.getAddress(bastionInstance, false))
      _ <- IO.tagAsTainted(bastionInstance.id, bastionConfig.name, awsClients.ec2Client)
      _ <- IO.installSshKey(bastionInstance.id, bastionConfig.name, bastionAddAndRemovePublicKeyCommand, awsClients.ssmClient)
      targetConfig <- IO.getSSMConfig(awsClients.ec2Client, awsClients.stsClient, executionTarget)
      targetInstance <- Attempt.fromEither(Logic.getSSHInstance(targetConfig.targets, sism, true))
      targetAddress <- Attempt.fromEither(Logic.getAddress(targetInstance, true))
      targetAddAndRemovePublicKeyCommand = SSH.addTaintedCommand(targetConfig.name) + SSH.addPublicKeyCommand(user, publicKey) + SSH.removePublicKeyCommand(user, publicKey)
      _ <- IO.tagAsTainted(targetInstance.id, targetConfig.name, awsClients.ec2Client)
      _ <- IO.installSshKey(targetInstance.id, targetConfig.name, targetAddAndRemovePublicKeyCommand, awsClients.ssmClient)
    } yield SSH.sshCmdBastion(rawOutput)(privateKeyFile, bastionInstance, targetInstance, user, bastionAddress, targetAddress, bastionPortNumberOpt)
    val programResult = Await.result(fProgramResult.asFuture, maximumWaitTime)
    programResult.fold(UI.outputFailure, UI.sshOutput(rawOutput))
    System.exit(programResult.fold(_.exitCode, _ => 0))
  }

  private def execute(awsClients: AWSClients, executionTarget: ExecutionTarget, toExecute: String): Unit = {
    val fProgramResult = for {
      config <- IO.getSSMConfig(awsClients.ec2Client, awsClients.stsClient, executionTarget)
      _ <- Attempt.fromEither(Logic.checkInstancesList(config))
      results <- IO.executeOnInstances(config.targets.map(i => i.id), config.name, toExecute, awsClients.ssmClient)
      incorrectInstancesFromInstancesTag = Logic.computeIncorrectInstances(executionTarget, results.map(_._1))
    } yield ResultsWithInstancesNotFound(results,incorrectInstancesFromInstancesTag)
    val programResult = Await.result(fProgramResult.asFuture, maximumWaitTime)
    programResult.fold(UI.outputFailure, UI.output)
    System.exit(programResult.fold(_.exitCode, _ => 0))
  }
}
