package com.gu.ssm

import com.amazonaws.regions.Region
import com.gu.ssm.utils.attempt._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor}
import com.gu.ssm.ArgumentParser.argParser

object Main {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  def main(args: Array[String]): Unit = {
    val (result, verbose) = argParser.parse(args, Arguments.empty()) match {
      case Some(Arguments(verbose, Some(executionTarget), toExecuteOpt, profile, region, Some(mode), Some(user), sism, _, _, onlyUsePrivateIP, rawOutput, bastionInstanceIdOpt, bastionPortNumberOpt, Some(bastionUser), targetInstancePortNumberOpt, useAgent, preferredAlgs, sourceFileOpt, targetFileOpt, tunnelThroughSystemsManager)) =>
        val awsClients = Logic.getClients(profile, region)
        val r = mode match {
          case SsmRepl =>
            new InteractiveProgram(awsClients).main(profile, region, executionTarget)
            ProgramResult(Nil)
          case SsmCmd =>
            toExecuteOpt match {
              case Some(toExecute) => execute(awsClients, executionTarget, user, toExecute)
              case _ => fail
            }
          case SsmSsh => bastionInstanceIdOpt match {
            case None => setUpStandardSSH(awsClients, executionTarget, user, sism, onlyUsePrivateIP, rawOutput, targetInstancePortNumberOpt, preferredAlgs, useAgent, profile, region, tunnelThroughSystemsManager)
            case Some(bastionInstance) => setUpBastionSSH(awsClients, executionTarget, user, sism, onlyUsePrivateIP, rawOutput, bastionInstance, bastionPortNumberOpt, bastionUser, targetInstancePortNumberOpt, useAgent, preferredAlgs)
          }
          case SsmScp => (sourceFileOpt, targetFileOpt) match {
            case (Some(sourceFile), Some(targetFile)) => setUpStandardScp(awsClients, executionTarget, user, sism, onlyUsePrivateIP, rawOutput, targetInstancePortNumberOpt, preferredAlgs, useAgent, sourceFile, targetFile, profile, region, tunnelThroughSystemsManager)
            case _ => fail
          }
        }
        r -> verbose
      case Some(_) => fail -> false
      case None => ProgramResult(Nil, Some(ArgumentsError)) -> false // parsing cmd line args failed, help message will have been displayed
    }

    val ui = new UI(verbose)
    ui.printAll(result.output)
    System.exit(result.nonZeroExitCode.map(_.code).getOrElse(0))
  }

  private def fail: ProgramResult = {
    ProgramResult(Seq(Err("Impossible application state! This should be enforced by the CLI parser.  Did not receive valid instructions")), Some(UnhandledError))
  }

  private def setUpStandardSSH(
    awsClients: AWSClients,
    executionTarget: ExecutionTarget,
    user: String,
    sism: SingleInstanceSelectionMode,
    onlyUsePrivateIP: Boolean,
    rawOutput: Boolean,
    targetInstancePortNumberOpt: Option[Int],
    preferredAlgs: List[String],
    useAgent: Option[Boolean],
    profile: Option[String],
    region: Region,
    tunnelThroughSystemsManager: Boolean) = {
    val fProgramResult = for {
      config <- IO.getSSMConfig(awsClients.ec2Client, awsClients.stsClient, executionTarget)
      sshArtifacts <- Attempt.fromEither(SSH.createKey())
      (privateKeyFile, publicKey) = sshArtifacts
      addPublicKeyCommand = SSH.addTaintedCommand(config.name) + SSH.addPublicKeyCommand(user, publicKey) + SSH.outputHostKeysCommand()
      removePublicKeyCommand = SSH.removePublicKeyCommand(user, publicKey)
      instance <- Attempt.fromEither(Logic.getSSHInstance(config.targets, sism))
      _ <- IO.tagAsTainted(instance.id, config.name, awsClients.ec2Client)
      result <- IO.executeOnInstance(instance.id, config.name, addPublicKeyCommand, awsClients.ssmClient)
      _ <- IO.executeOnInstanceAsync(instance.id, config.name, removePublicKeyCommand, awsClients.ssmClient)
      hostKey <- Attempt.fromEither(Logic.getHostKeyEntry(result, preferredAlgs))
      address <- Attempt.fromEither(Logic.getAddress(instance, onlyUsePrivateIP))
      hostKeyFile <- SSH.writeHostKey((address, hostKey))
    } yield {
      SSH.sshCmdStandard(rawOutput)(privateKeyFile, instance, user, address, targetInstancePortNumberOpt, Some(hostKeyFile), useAgent, profile, region, tunnelThroughSystemsManager)
    }
    val programResult = Await.result(fProgramResult.asFuture, Duration.Inf)
    ProgramResult(programResult.map(UI.sshOutput(rawOutput)))
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
    preferredAlgs: List[String]) = {
    val fProgramResult = for {
      sshArtifacts <- Attempt.fromEither(SSH.createKey())
      (privateKeyFile, publicKey) = sshArtifacts
      bastionConfig <- IO.getSSMConfig(awsClients.ec2Client, awsClients.stsClient, bastionInstance)
      bastionInstance <- Attempt.fromEither(Logic.getSSHInstance(bastionConfig.targets, sism))
      bastionAddPublicKeyCommand = SSH.addPublicKeyCommand(user, publicKey) + SSH.outputHostKeysCommand()
      bastionRemovePublicKeyCommand = SSH.removePublicKeyCommand(user, publicKey)
      bastionAddress <- Attempt.fromEither(Logic.getAddress(bastionInstance, onlyUsePrivateIP))
      targetConfig <- IO.getSSMConfig(awsClients.ec2Client, awsClients.stsClient, executionTarget)
      targetInstance <- Attempt.fromEither(Logic.getSSHInstance(targetConfig.targets, sism))
      targetAddress <- Attempt.fromEither(Logic.getAddress(targetInstance, true))
      targetAddPublicKeyCommand = SSH.addTaintedCommand(targetConfig.name) + SSH.addPublicKeyCommand(user, publicKey) + SSH.outputHostKeysCommand()
      targetRemovePublicKeyCommand = SSH.removePublicKeyCommand(user, publicKey)
      bastionResult <- IO.executeOnInstance(bastionInstance.id, bastionConfig.name, bastionAddPublicKeyCommand, awsClients.ssmClient)
      _ <- IO.executeOnInstanceAsync(bastionInstance.id, bastionConfig.name, bastionRemovePublicKeyCommand, awsClients.ssmClient)
      _ <- IO.tagAsTainted(targetInstance.id, targetConfig.name, awsClients.ec2Client)
      targetResult <- IO.executeOnInstance(targetInstance.id, targetConfig.name, targetAddPublicKeyCommand, awsClients.ssmClient)
      _ <- IO.executeOnInstanceAsync(targetInstance.id, targetConfig.name, targetRemovePublicKeyCommand, awsClients.ssmClient)
      bastionHostKey <- Attempt.fromEither(Logic.getHostKeyEntry(bastionResult, preferredAlgs))
      targetHostKey <- Attempt.fromEither(Logic.getHostKeyEntry(targetResult, preferredAlgs))
      hostKeyFile <- SSH.writeHostKey((bastionAddress, bastionHostKey), (targetAddress, targetHostKey))
    } yield SSH.sshCmdBastion(rawOutput)(privateKeyFile, bastionInstance, targetInstance, user, bastionAddress, targetAddress, bastionPortNumberOpt, bastionUser, targetInstancePortNumberOpt, useAgent, Some(hostKeyFile))
    val programResult = Await.result(fProgramResult.asFuture, Duration.Inf)
    ProgramResult(programResult.map(UI.sshOutput(rawOutput)))
  }

  private def setUpStandardScp(
                                awsClients: AWSClients,
                                executionTarget: ExecutionTarget,
                                user: String,
                                sism: SingleInstanceSelectionMode,
                                onlyUsePrivateIP: Boolean,
                                rawOutput: Boolean,
                                targetInstancePortNumberOpt: Option[Int],
                                preferredAlgs: List[String],
                                useAgent: Option[Boolean],
                                sourceFile: String,
                                targetFile: String,
                                profile: Option[String],
                                region: Region,
                                tunnelThroughSystemsManager: Boolean) = {
    val fProgramResult = for {
      config <- IO.getSSMConfig(awsClients.ec2Client, awsClients.stsClient, executionTarget)
      sshArtifacts <- Attempt.fromEither(SSH.createKey())
      (privateKeyFile, publicKey) = sshArtifacts
      addPublicKeyCommand = SSH.addTaintedCommand(config.name) + SSH.addPublicKeyCommand(user, publicKey) + SSH.outputHostKeysCommand()
      removePublicKeyCommand = SSH.removePublicKeyCommand(user, publicKey)
      instance <- Attempt.fromEither(Logic.getSSHInstance(config.targets, sism))
      _ <- IO.tagAsTainted(instance.id, config.name, awsClients.ec2Client)
      result <- IO.executeOnInstance(instance.id, config.name, addPublicKeyCommand, awsClients.ssmClient)
      _ <- IO.executeOnInstanceAsync(instance.id, config.name, removePublicKeyCommand, awsClients.ssmClient)
      hostKey <- Attempt.fromEither(Logic.getHostKeyEntry(result, preferredAlgs))
      address <- Attempt.fromEither(Logic.getAddress(instance, onlyUsePrivateIP))
      hostKeyFile <- SSH.writeHostKey((address, hostKey))
    } yield {
      SSH.scpCmdStandard(rawOutput)(privateKeyFile, instance, user, address, targetInstancePortNumberOpt, useAgent, Some(hostKeyFile), sourceFile, targetFile, profile, region, tunnelThroughSystemsManager)
    }
    val programResult = Await.result(fProgramResult.asFuture, Duration.Inf)
    ProgramResult(programResult.map(UI.sshOutput(rawOutput)))
  }

  private def execute(awsClients: AWSClients, executionTarget: ExecutionTarget, user: String, toExecute: String): ProgramResult = {
    val fProgramResult = for {
      config <- IO.getSSMConfig(awsClients.ec2Client, awsClients.stsClient, executionTarget)
      _ <- Attempt.fromEither(Logic.checkInstancesList(config))
      results <- IO.executeOnInstances(config.targets.map(i => i.id), user, toExecute, awsClients.ssmClient)
      incorrectInstancesFromInstancesTag = Logic.computeIncorrectInstances(executionTarget, results.map(_._1))
    } yield ResultsWithInstancesNotFound(results,incorrectInstancesFromInstancesTag)

    val programResult = Await.result(fProgramResult.asFuture, Duration.Inf)

    val anyCommandFailed = programResult.exists(_.results.exists(_._2.map(_.commandFailed).getOrElse(false)))
    val nonZeroExitCode = if(anyCommandFailed) { Some(ErrorCode) } else { None }

    ProgramResult(programResult.map(UI.output)).copy(nonZeroExitCode = nonZeroExitCode)
  }
}
