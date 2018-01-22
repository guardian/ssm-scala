package com.gu.ssm

import java.io.{File, IOException}
import java.security.{NoSuchAlgorithmException, NoSuchProviderException}
import java.util.Calendar

import scala.concurrent.ExecutionContext
import com.gu.ssm.utils.attempt._
import com.gu.ssm.utils.{KeyMaker, chmod}

object SSH {

  val delay = 30

  def createKey(): Either[FailedAttempt, (File, String)] = {

    // Write key to file.
    val prefix = "security-magic-rsa-private-key"
    val suffix = ".tmp"
    try {
      val tempFile = File.createTempFile(prefix, suffix)
      chmod(tempFile, "0600")
      val authKey = KeyMaker.makeKey(tempFile)
      Right((tempFile, authKey))
    } catch {
      case e:IOException => Left(FailedAttempt(
        Failure(s"Unable to create private key file", "Error creating key on disk", UnhandledError, None, Some(e))
      ))
      case e:NoSuchAlgorithmException => Left(FailedAttempt(
        Failure(s"Unable to create key pair with that algorithm", "Error creating key", UnhandledError, None, Some(e))
      ))
      case e:NoSuchProviderException => Left(FailedAttempt(
        Failure(s"Unable to create key pair with that provider", "Error creating key", UnhandledError, None, Some(e))
      ))
    }
  }

  def addTaintedCommand(name: String): String = {
    s"""
       | [[ -f /etc/update-motd.d/99-tainted ]] || /bin/echo -e '#!/bin/bash' | /usr/bin/sudo /usr/bin/tee -a /etc/update-motd.d/99-tainted >> /dev/null;
       | /bin/echo -e 'echo -e "\033[0;31mThis instance was tainted by ${name} at ${Calendar.getInstance().getTime()}\033[0;30m"' | /usr/bin/sudo /usr/bin/tee -a /etc/update-motd.d/99-tainted >> /dev/null;
       | /usr/bin/sudo /bin/chmod 0755 /etc/update-motd.d/99-tainted;
       | /usr/bin/sudo /bin/run-parts /etc/update-motd.d/ | /usr/bin/sudo /usr/bin/tee /run/motd.dynamic >> /dev/null;
       | """.stripMargin
  }

  def addKeyCommand(authKey: String): String =
    s"""
      | /bin/mkdir -p /home/ubuntu/.ssh;
      | /bin/echo '$authKey' >> /home/ubuntu/.ssh/authorized_keys;
      | /bin/chmod 0600 /home/ubuntu/.ssh/authorized_keys;
      |""".stripMargin

  def removeKeyCommand(authKey: String): String =
    s"""
      | /bin/sleep $delay;
      | /bin/sed -i '/${authKey.replaceAll("/", "\\\\/")}/d' /home/ubuntu/.ssh/authorized_keys;
      |""".stripMargin

  def sshCmd(tempFile: File, instance: Instance)(implicit ec: ExecutionContext): (InstanceId, Either[CommandStatus, CommandResult]) = {
    val cmd = s"""
      | # Execute the following command within the next $delay seconds:
      | ssh -i ${tempFile.getCanonicalFile.toString} ubuntu@${instance.publicIpAddressOpt.get};
      |""".stripMargin
    instance.id -> Right(CommandResult(cmd, ""))
  }

  def sshCmds(tempFile: File, instances: List[Instance])(implicit ec: ExecutionContext): List[(InstanceId, Either[CommandStatus, CommandResult])] = {
    instances.map(i => sshCmd(tempFile, i))
  }

  def getSingleInstance(instances: List[Instance]): Either[FailedAttempt, List[InstanceId]] =
    if (instances.length != 1) Left(FailedAttempt(
      Failure(s"Unable to identify a single instance", s"Error choosing single instance, found ${instances.map(i => i.id.id).mkString(", ")}", UnhandledError, None, None)))
    else Right(instances.map(i => i.id))
}
