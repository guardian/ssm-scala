package com.gu.ssm

import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceAsync
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsync
import com.gu.ssm.aws.{EC2, SSM, STS}
import com.gu.ssm.utils.attempt._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import com.gu.ssm.ArgumentParser.argParser
import com.gu.ssm.SSH.sshCmd

object Main {
  implicit val ec = ExecutionContext.global

  def main(args: Array[String]): Unit = {
    argParser.parse(args, Arguments.empty()) match {
      case Some(Arguments(Some(executionTarget), toExecute, Some(profile), region, interactive, ssh)) =>
        implicit val stsClient = STS.client(profile, region)
        implicit val ssmClient = SSM.client(profile, region)
        implicit val ec2Client = EC2.client(profile, region)

        (toExecute, interactive, ssh) match {
          case (None, true, false) =>
            interactiveLoop(executionTarget)
          case (Some(toExecute), false, false) =>
            execute(executionTarget, toExecute)
          case (None, false, true) =>
            setUpSSH(executionTarget)
          case _ => fail
        }
      case Some(_) => fail
      case None => System.exit(ArgumentsError.code) // parsing cmd line args failed, help message will have been displayed
    }
  }

  private def fail = {
    UI.printErr("Impossible application state! This should be enforced by the CLI parser.  Did not receive valid instructions")
    System.exit(UnhandledError.code)
  }

  private def setUpSSH(executionTarget: ExecutionTarget)(implicit stsClient: AWSSecurityTokenServiceAsync, ssmClient: AWSSimpleSystemsManagementAsync, ec2Client: AmazonEC2Async) = {
    val fProgramResult = for {
      config <- Attempt.map2(IO.resolveInstances(executionTarget, ec2Client), STS.getCallerIdentity(stsClient))((_, _))
      (instances, name) = config
      sshArtifacts <- Attempt.fromEither(SSH.createKey())
      (authFile, authKey) = sshArtifacts
      addAndRemoveKeyCommand = SSH.addTaintedCommand(name) +SSH.addKeyCommand(authKey) + SSH.removeKeyCommand(authKey)
      instance <- Attempt.fromEither(getSingleInstance(instances))
      _ <- IO.fireAndForgetOnInstances(instance, name, addAndRemoveKeyCommand, ssmClient)
    } yield instances.map(SSH.sshCmd(authFile, _))

    val programResult = Await.result(fProgramResult.asFuture, 25.seconds)

    programResult.fold(UI.outputFailure, UI.output)
    System.exit(programResult.fold(_.exitCode, _ => 0))
  }

  def getSingleInstance(instances: List[Instance]): Either[FailedAttempt, List[InstanceId]] =
    if (instances.tail.nonEmpty) Left(FailedAttempt(
      Failure(s"Unable to identify a single instance", s"Error choosing single instance, found ${instances.map(i => i.id.id).mkString(", ")}", UnhandledError, None, None)))
    else Right(instances.map(i => i.id))


  private def execute(executionTarget: ExecutionTarget, toExecute: ToExecute)(implicit stsClient: AWSSecurityTokenServiceAsync, ssmClient: AWSSimpleSystemsManagementAsync, ec2Client: AmazonEC2Async) = {
    val fProgramResult = for {
      config <- Attempt.map2(IO.resolveInstances(executionTarget, ec2Client), STS.getCallerIdentity(stsClient))((_, _))
      (instances, name) = config
      cmd <- Attempt.fromEither(Logic.generateScript(toExecute))
      results <- IO.executeOnInstances(instances.map(i => i.id), name, cmd, ssmClient)
    } yield results
    val programResult = Await.result(fProgramResult.asFuture, 25.seconds)

    programResult.fold(UI.outputFailure, UI.output)
    System.exit(programResult.fold(_.exitCode, _ => 0))
  }

  private def interactiveLoop(executionTarget: ExecutionTarget)(implicit stsClient: AWSSecurityTokenServiceAsync, ssmClient: AWSSimpleSystemsManagementAsync, ec2Client: AmazonEC2Async) = {
    val configAttempt = Attempt.map2(
      IO.resolveInstances(executionTarget, ec2Client),
      STS.getCallerIdentity(stsClient)
    )((_, _))
    val interactive = new InteractiveProgram(ssmClient)(ec)
    interactive.main(configAttempt)
  }
}
