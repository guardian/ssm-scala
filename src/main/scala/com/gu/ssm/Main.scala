package com.gu.ssm

import com.amazonaws.regions.Region
import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceAsync
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsync
import com.gu.ssm.aws.{EC2, SSM, STS}
import com.gu.ssm.utils.attempt._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor}
import com.gu.ssm.ArgumentParser.argParser

object Main {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  def main(args: Array[String]): Unit = {
    argParser.parse(args, Arguments.empty()) match {
      case Some(Arguments(Some(executionTarget), toExecuteOpt, Some(profile), region, Some(mode))) =>
        val ssmConfig = getSSMConfig(profile, region, executionTarget)
        mode match {
          case SsmRepl =>
            readEvaluatePrintLoop(ssmConfig)
          case SsmCmd if toExecuteOpt.nonEmpty =>
            val toExecute = toExecuteOpt.get
            execute(ssmConfig, toExecute)
          case SsmSsh =>
            setUpSSH(ssmConfig)
          case _ => fail()
        }
      case Some(_) => fail()
      case None => System.exit(ArgumentsError.code) // parsing cmd line args failed, help message will have been displayed
    }
  }

  private def fail(): Unit = {
    UI.printErr("Impossible application state! This should be enforced by the CLI parser.  Did not receive valid instructions")
    System.exit(UnhandledError.code)
  }

  private def getSSMConfig(profile :String, region: Region, executionTarget: ExecutionTarget) = {
    val stsClient: AWSSecurityTokenServiceAsync = STS.client(profile, region)
    val ssmClient: AWSSimpleSystemsManagementAsync = SSM.client(profile, region)
    val ec2Client: AmazonEC2Async = EC2.client(profile, region)
    for {
      instances <- IO.resolveInstances(executionTarget, ec2Client)
      name <- STS.getCallerIdentity(stsClient)
    } yield SSMConfig(stsClient, ssmClient, ec2Client, instances, name)
  }

  private def setUpSSH(ssmConfig: Attempt[SSMConfig]): Unit = {
    val fProgramResult = for {
      config <- ssmConfig
      sshArtifacts <- Attempt.fromEither(SSH.createKey())
      (authFile, authKey) = sshArtifacts
      addAndRemoveKeyCommand = SSH.addTaintedCommand(config.name) + SSH.addKeyCommand(authKey) + SSH.removeKeyCommand(authKey)
      instance <- Attempt.fromEither(getSingleInstance(config.targets))
      _ <- IO.tagAsTainted(instance, config.name, config.ec2Client)
      _ <- IO.installSshKey(instance, config.name, addAndRemoveKeyCommand, config.ssmClient)
    } yield config.targets.map(SSH.sshCmd(authFile, _))

    val programResult = Await.result(fProgramResult.asFuture, 25.seconds)

    programResult.fold(UI.outputFailure, UI.sshOutput)
    System.exit(programResult.fold(_.exitCode, _ => 0))
  }

  def getSingleInstance(instances: List[Instance]): Either[FailedAttempt, List[InstanceId]] = {
    if (instances.lengthCompare(1) != 0) Left(FailedAttempt(
      Failure(s"Unable to identify a single instance", s"Error choosing single instance, found ${instances.map(i => i.id.id).mkString(", ")}", UnhandledError, None, None)))
    else Right(instances.map(i => i.id))
  }

  private def execute(ssmConfig: Attempt[SSMConfig], toExecute: String): Unit = {
    val fProgramResult = for {
      config <- ssmConfig
      results <- IO.executeOnInstances(config.targets.map(i => i.id), config.name, toExecute, config.ssmClient)
    } yield results
    val programResult = Await.result(fProgramResult.asFuture, 25.seconds)

    programResult.fold(UI.outputFailure, UI.output)
    System.exit(programResult.fold(_.exitCode, _ => 0))
  }

  private def readEvaluatePrintLoop(ssmConfig: Attempt[SSMConfig]): Unit = {
    new InteractiveProgram(ssmConfig)(ec)
  }

}
