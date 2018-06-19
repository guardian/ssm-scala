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
      case Some(Arguments(Some(executionTarget), toExecuteOpt, profile, region, Some(mode), Some(user), sism, _, _, onlyUsePrivateIP, rawOutput, bastionInstanceIdOpt, bastionPortNumberOpt, Some(bastionUser), targetInstancePortNumberOpt, useAgent, Some(sshdConfigPath), preferredAlgs, shouldDisplayIdentityFileOnly)) =>
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
            case None => setUpStandardSSH(awsClients, executionTarget, user, sism, onlyUsePrivateIP, rawOutput, targetInstancePortNumberOpt, sshdConfigPath, preferredAlgs, useAgent, shouldDisplayIdentityFileOnly)
            case Some(bastionInstance) => setUpBastionSSH(awsClients, executionTarget, user, sism, onlyUsePrivateIP, rawOutput, bastionInstance, bastionPortNumberOpt, bastionUser, targetInstancePortNumberOpt, useAgent, sshdConfigPath, preferredAlgs, shouldDisplayIdentityFileOnly)
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

  private def setUpStandardSSH(
    awsClients: AWSClients,
    executionTarget: ExecutionTarget,
    user: String,
    sism: SingleInstanceSelectionMode,
    onlyUsePrivateIP: Boolean,
    rawOutput: Boolean,
    targetInstancePortNumberOpt: Option[Int],
    sshdConfigPath: String,
    preferredAlgs: List[String],
    useAgent: Option[Boolean],
    shouldDisplayIdentityFileOnly: Boolean) = {
    val fProgramResult = for {
      config <- IO.getSSMConfig(awsClients.ec2Client, awsClients.stsClient, executionTarget)
      sshArtifacts <- Attempt.fromEither(SSH.createKey())
      (privateKeyFile, publicKey) = sshArtifacts
      addPublicKeyCommand = SSH.addTaintedCommand(config.name) + SSH.addPublicKeyCommand(user, publicKey) + SSH.outputHostKeysCommand(sshdConfigPath)
      removePublicKeyCommand = SSH.removePublicKeyCommand(user, publicKey)
      instance <- Attempt.fromEither(Logic.getSSHInstance(config.targets, sism))
      _ <- IO.tagAsTainted(instance.id, config.name, awsClients.ec2Client)
      result <- IO.executeOnInstance(instance.id, config.name, addPublicKeyCommand, awsClients.ssmClient)
      _ <- IO.executeOnInstanceAsync(instance.id, config.name, removePublicKeyCommand, awsClients.ssmClient)
      hostKey <- Attempt.fromEither(Logic.getHostKeyEntry(result, preferredAlgs))
      address <- Attempt.fromEither(Logic.getAddress(instance, onlyUsePrivateIP))
      hostKeyFile <- SSH.writeHostKey((address, hostKey))
    } yield {
      SSH.sshCmdStandard(rawOutput, shouldDisplayIdentityFileOnly, privateKeyFile, instance, user, address, targetInstancePortNumberOpt, Some(hostKeyFile), useAgent)
    }
    val programResult = Await.result(fProgramResult.asFuture, maximumWaitTime)
    programResult.fold(UI.outputFailure, UI.sshOutput(rawOutput || shouldDisplayIdentityFileOnly))
    System.exit(programResult.fold(_.exitCode, _ => 0))
  }

  private def setUpBastionSSH(
    awsClients: AWSClients,
    executionTarget: ExecutionTarget,
    user: String,
    sism: SingleInstanceSelectionMode,
    onlyUsePrivateIP: Boolean,
    rawOutput: Boolean,
    bastionInstance: ExecutionTarget,
    bastionPortNumberOpt: Option[Int],
    bastionUser: String,
    targetInstancePortNumberOpt: Option[Int],
    useAgent: Option[Boolean],
    sshdConfigPath: String,
    preferredAlgs: List[String],
    shouldDisplayIdentityFileOnly: Boolean) = {
    val fProgramResult = for {
      sshArtifacts <- Attempt.fromEither(SSH.createKey())
      (privateKeyFile, publicKey) = sshArtifacts
      bastionConfig <- IO.getSSMConfig(awsClients.ec2Client, awsClients.stsClient, bastionInstance)
      bastionInstance <- Attempt.fromEither(Logic.getSSHInstance(bastionConfig.targets, sism))
      bastionAddPublicKeyCommand = SSH.addPublicKeyCommand(user, publicKey) + SSH.outputHostKeysCommand(sshdConfigPath)
      bastionRemovePublicKeyCommand = SSH.removePublicKeyCommand(user, publicKey)
      bastionAddress <- Attempt.fromEither(Logic.getAddress(bastionInstance, onlyUsePrivateIP))
      targetConfig <- IO.getSSMConfig(awsClients.ec2Client, awsClients.stsClient, executionTarget)
      targetInstance <- Attempt.fromEither(Logic.getSSHInstance(targetConfig.targets, sism))
      targetAddress <- Attempt.fromEither(Logic.getAddress(targetInstance, true))
      targetAddPublicKeyCommand = SSH.addTaintedCommand(targetConfig.name) + SSH.addPublicKeyCommand(user, publicKey) + SSH.outputHostKeysCommand(sshdConfigPath)
      targetRemovePublicKeyCommand = SSH.removePublicKeyCommand(user, publicKey)
      bastionResult <- IO.executeOnInstance(bastionInstance.id, bastionConfig.name, bastionAddPublicKeyCommand, awsClients.ssmClient)
      _ <- IO.executeOnInstanceAsync(bastionInstance.id, bastionConfig.name, bastionRemovePublicKeyCommand, awsClients.ssmClient)
      _ <- IO.tagAsTainted(targetInstance.id, targetConfig.name, awsClients.ec2Client)
      targetResult <- IO.executeOnInstance(targetInstance.id, targetConfig.name, targetAddPublicKeyCommand, awsClients.ssmClient)
      _ <- IO.executeOnInstanceAsync(targetInstance.id, targetConfig.name, targetRemovePublicKeyCommand, awsClients.ssmClient)
      bastionHostKey <- Attempt.fromEither(Logic.getHostKeyEntry(bastionResult, preferredAlgs))
      targetHostKey <- Attempt.fromEither(Logic.getHostKeyEntry(targetResult, preferredAlgs))
      hostKeyFile <- SSH.writeHostKey((bastionAddress, bastionHostKey), (targetAddress, targetHostKey))
    } yield SSH.sshCmdBastion(rawOutput, shouldDisplayIdentityFileOnly, privateKeyFile, bastionInstance, targetInstance, user, bastionAddress, targetAddress, bastionPortNumberOpt, bastionUser, targetInstancePortNumberOpt, useAgent, Some(hostKeyFile))
    val programResult = Await.result(fProgramResult.asFuture, maximumWaitTime)
    programResult.fold(UI.outputFailure, UI.sshOutput(rawOutput || shouldDisplayIdentityFileOnly))
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
