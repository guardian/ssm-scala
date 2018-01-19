package com.gu.ssm

import java.io.File

import scala.concurrent.ExecutionContext
import com.gu.ssm.utils.attempt.{ArgumentsError, Attempt, FailedAttempt, Failure}
import com.gu.ssm.utils.{KeyMaker, chmod}

object SSH {

  def createKey(): Either[FailedAttempt, (File, String)] = {

    // Write key to file.
    val prefix = "security-magic-rsa-private-key"
    val suffix = ".tmp"
    val tempFile = File.createTempFile(prefix, suffix)
    chmod(tempFile, "0600")
    val authKey = KeyMaker.makeKey(tempFile)
    Right((tempFile, authKey))
  }

  def addKeyCommand(authKey: String): Attempt[String] = {
    Attempt.fromEither(Right(s"""
      | /bin/mkdir -p /home/ubuntu/.ssh;
      | /bin/echo '$authKey' >> /home/ubuntu/.ssh/authorized_keys;
      | /bin/chmod 0600 /home/ubuntu/.ssh/authorized_keys
    """.stripMargin))
  }

  def removeKeyCommand(authKey: String, delay: Integer): Attempt[String] = {
    Attempt.fromEither(Right(s"""
      | /bin/sleep $delay;
      | /bin/sed -i '/${authKey.replaceAll("/", "\\\\/")}/d' /home/ubuntu/.ssh/authorized_keys;
    """.stripMargin))
  }

  def sshCmd(tempFile: File, instance: Instance, delay: Integer)(implicit ec: ExecutionContext): Attempt[(InstanceId, Either[CommandStatus, CommandResult])] = {
    for {
      cmd <- Attempt.fromEither(Right(s"""
        | # Execute the following command within the next $delay seconds:
        | ssh -i ${tempFile.getCanonicalFile.toString} ubuntu@${instance.publicIpAddressOpt.get}
      """.stripMargin))
    } yield instance.id -> Right(CommandResult(cmd, ""))
  }

  def sshCmds(tempFile: File, instances: List[Instance], delay: Integer)(implicit ec: ExecutionContext): Attempt[List[(InstanceId, Either[CommandStatus, CommandResult])]] = {
    Attempt.traverse(instances)(sshCmd(tempFile, _, delay))
  }

}
