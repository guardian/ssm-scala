package com.gu.ssm

import java.io.File

import com.amazonaws.regions.{Region, Regions}
import com.gu.ssm.aws.{EC2, SSM, STS}
import com.gu.ssm.utils.attempt.{ArgumentsError, Attempt, FailedAttempt, UnhandledError}
import scopt.OptionParser

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}


object Main extends ArgumentParser {
  implicit val ec = ExecutionContext.global

  def main(args: Array[String]): Unit = {
    // TODO attempt to read from stdin to get commands and populate initial arguments thus

    argParser.parse(args, Arguments.empty()) match {
      case Some(Arguments(Some(executionTarget), _, Some(profile), region, true, false)) =>
        val stsClient = STS.client(profile, region)
        val ssmClient = SSM.client(profile, region)
        val ec2Client = EC2.client(profile, region)

        // resolve user and instances in parallel
        val configAttempt = Attempt.map2(
          IO.resolveInstances(executionTarget, ec2Client),
          STS.getCallerIdentity(stsClient)
        )((_, _))
        val interactive = new InteractiveProgram(ssmClient)(ec)
        interactive.main(configAttempt)

      case Some(Arguments(Some(executionTarget), Some(toExecute), Some(profile), region, false, false)) =>
        // config
        val stsClient = STS.client(profile, region)
        val ssmClient = SSM.client(profile, region)
        val ec2Client = EC2.client(profile, region)

        // execution
        val fProgramResult = for {
          // get identity and instances in parallel
          config <- Attempt.map2(IO.resolveInstances(executionTarget, ec2Client), STS.getCallerIdentity(stsClient))((_, _))
          (instances, name) = config
          cmd <- Attempt.fromEither(Logic.generateScript(toExecute))
          results <- IO.executeOnInstances(instances.map(i => i.id), name, cmd, ssmClient)
        } yield results
        val programResult = Await.result(fProgramResult.asFuture, 25.seconds)

        // output and exit
        programResult.fold(UI.outputFailure, UI.output)
        System.exit(programResult.fold(_.exitCode, _ => 0))

      case Some(Arguments(Some(executionTarget), None, Some(profile), region, false, true)) =>
        // config
        val stsClient = STS.client(profile, region)
        val ssmClient = SSM.client(profile, region)
        val ec2Client = EC2.client(profile, region)

        // execution
        val fProgramResult = for {
          config <- Attempt.map2(IO.resolveInstances(executionTarget, ec2Client), STS.getCallerIdentity(stsClient))((_, _))
          (instances, name) = config
          sshArtifacts <- Attempt.fromEither(SSH.createKey())
          (authFile, authKey) = sshArtifacts
          addKeyCommand <- SSH.addKeyCommand(authKey)
          delay = 30
          removeKeyCommand <- SSH.removeKeyCommand(authKey, delay)
          addKeyResults <- IO.executeOnInstances(instances.map(i => i.id), name, addKeyCommand, ssmClient)
          removeKeyResults <- IO.fireAndForgetOnInstances(instances.map(i => i.id), name, removeKeyCommand, ssmClient)
          sshCommands <- SSH.sshCmds(authFile, instances, delay)

        } yield sshCommands
        val programResult = Await.result(fProgramResult.asFuture, 25.seconds)

        // output and exit
        programResult.fold(UI.outputFailure, UI.output)
        System.exit(programResult.fold(_.exitCode, _ => 0))

      case Some(_) =>
        // the CLI parser's `checkConfig` function means this should be unreachable code
        UI.printErr("Impossible application state! This should be enforced by the CLI parser")
        System.exit(UnhandledError.code)
      case None =>
        // parsing cmd line args failed, help message will have been displayed
        System.exit(ArgumentsError.code)
    }
  }

}
