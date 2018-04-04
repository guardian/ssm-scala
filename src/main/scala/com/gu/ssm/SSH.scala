package com.gu.ssm

import java.io.{File, IOException}
import java.security.{NoSuchAlgorithmException, NoSuchProviderException}
import java.util.Calendar

import com.gu.ssm.utils.attempt._
import com.gu.ssm.utils.{KeyMaker, FilePermissions}

object SSH {

  val sshCredentialsLifetimeSeconds = 30

  def createKey(): Either[FailedAttempt, (File, String)] = {

    // Write key to file.
    val prefix = "security_ssm-scala_temporary-rsa-private-key"
    val suffix = ".tmp"
    val keyAlgorithm = "RSA"
    val keyProvider = "BC"

    try {
      val privateKeyFile = File.createTempFile(prefix, suffix)
      FilePermissions(privateKeyFile, "0600")
      val publicKey = KeyMaker.makeKey(privateKeyFile, keyAlgorithm, keyProvider)
      Right((privateKeyFile, publicKey))
    } catch {
      case e:IOException => Left(FailedAttempt(
        Failure(s"Unable to create private key file", "Error creating key on disk", UnhandledError, None, Some(e))
      ))
      case e:NoSuchAlgorithmException => Left(FailedAttempt(
        Failure(s"Unable to create key pair with algorithm $keyAlgorithm", s"Error creating key with algorithm $keyAlgorithm", UnhandledError, None, Some(e))
      ))
      case e:NoSuchProviderException => Left(FailedAttempt(
        Failure(s"Unable to create key pair with provider $keyProvider", s"Error creating key with provider $keyProvider", UnhandledError, None, Some(e))
      ))
    }
  }

  def addTaintedCommand(name: String): String = {
    s"""
       | /usr/bin/test -d /etc/update-motd.d/ &&
       | ( /usr/bin/test -f /etc/update-motd.d/99-tainted || /bin/echo -e '#!/bin/bash' | /usr/bin/sudo /usr/bin/tee -a /etc/update-motd.d/99-tainted >> /dev/null;
       |   /bin/echo -e 'echo -e "\033[0;31mThis instance should be considered tainted.\033[0;39m"' | /usr/bin/sudo /usr/bin/tee -a /etc/update-motd.d/99-tainted >> /dev/null;
       |   /bin/echo -e 'echo -e "\033[0;31mIt was accessed by $name at ${Calendar.getInstance().getTime}\033[0;39m"' | /usr/bin/sudo /usr/bin/tee -a /etc/update-motd.d/99-tainted >> /dev/null;
       |   /usr/bin/sudo /bin/chmod 0755 /etc/update-motd.d/99-tainted;
       |   /usr/bin/sudo /bin/run-parts /etc/update-motd.d/ | /usr/bin/sudo /usr/bin/tee /run/motd.dynamic >> /dev/null;
       | ) """.stripMargin
  }

  def addPublicKeyCommand(user: String, publicKey: String): String =
    s"""
      | /bin/mkdir -p /home/$user/.ssh;
      | /bin/echo '$publicKey' >> /home/$user/.ssh/authorized_keys;
      | /bin/chmod 0600 /home/$user/.ssh/authorized_keys;
      |""".stripMargin

  def removePublicKeyCommand(user: String, publicKey: String): String =
    s"""
      | /bin/sleep $sshCredentialsLifetimeSeconds;
      | /bin/sed -i '/${publicKey.replaceAll("/", "\\\\/")}/d' /home/$user/.ssh/authorized_keys;
      |""".stripMargin

  def sshCmdStandard(rawOutput: Boolean)(privateKeyFile: File, instance: Instance, user: String, ipAddress: String): (InstanceId, String) = {
    val connectionString = s"ssh -i ${privateKeyFile.getCanonicalFile.toString} $user@$ipAddress"
    val cmd = if(rawOutput) {
      s"$connectionString -t -t"
    }else{
      s"""
         | # Execute the following command within the next $sshCredentialsLifetimeSeconds seconds:
         | ${connectionString};
         |""".stripMargin
    }
    (instance.id, cmd)
  }

  def sshCmdBastion(rawOutput: Boolean)(privateKeyFile: File, bastionInstance: Instance, targetInstance: Instance, targetInstanceUser: String, bastionIpAddress: String, targetIpAddress: String, bastionPortNumberOpt: Option[Int], bastionUser: String): (InstanceId, String) = {
    val portSpecifications = bastionPortNumberOpt match {
      case Some(portNumber) => s"-p ${portNumber} " // trailing space is important
      case _ => ""
    }

    val connectionString1 = s"ssh -A ${portSpecifications}-i ${privateKeyFile.getCanonicalFile.toString} $bastionUser@$bastionIpAddress"
    val connectionString2 = s"ssh $targetInstanceUser@$targetIpAddress"
    val connectionString = s"${connectionString1} -t -t ${connectionString2}"
    val cmd = if(rawOutput) {
      s"$connectionString -t -t"
    }else{
      s"""
         | # Execute the following commands within the next $sshCredentialsLifetimeSeconds seconds:
         | ${connectionString};
         |""".stripMargin
    }
    (targetInstance.id, cmd)
  }

}
