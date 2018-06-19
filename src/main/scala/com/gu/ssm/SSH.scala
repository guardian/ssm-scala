package com.gu.ssm

import java.io._
import java.security.{NoSuchAlgorithmException, NoSuchProviderException}
import java.util.Calendar

import com.gu.ssm.utils.attempt._
import com.gu.ssm.utils.{FilePermissions, KeyMaker}

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

  def writeHostKey(addressHostKeyTuples: (String, String)*): Attempt[File] = {
    // Write key to file.
    val prefix = "security_ssm-scala_temporary-host-key"
    val suffix = ".tmp"

    try {
      val hostKeyFile = File.createTempFile(prefix, suffix)
      val writer = new PrintWriter(new FileOutputStream(hostKeyFile))
      try {
        addressHostKeyTuples.foreach { case (address, hostKey) =>
          writer.println(s"$address $hostKey")
        }
      } finally {
        writer.close()
      }
      Attempt.Right(hostKeyFile)
    } catch {
      case e:IOException => Attempt.Left(
        Failure(s"Unable to create host key file", "Error creating host key on disk", UnhandledError, None, Some(e))
      )
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
      | /bin/chown $user /home/$user/.ssh/authorized_keys;
      | /bin/chmod 0600 /home/$user/.ssh/authorized_keys;
      |""".stripMargin

  def removePublicKeyCommand(user: String, publicKey: String): String =
    s"""
      | /bin/sleep $sshCredentialsLifetimeSeconds;
      | /bin/sed -i '/${publicKey.replaceAll("/", "\\\\/")}/d' /home/$user/.ssh/authorized_keys;
      |""".stripMargin

  def outputHostKeysCommand(sshd_config_path: String): String =
    s"""
       | for hostkey in $$(grep ^HostKey $sshd_config_path| cut -d' ' -f 2); do cat $${hostkey}.pub; done
     """.stripMargin

  def sshCmdStandard(rawOutput: Boolean, shouldDisplayIdentityFileOnly: Boolean, privateKeyFile: File, instance: Instance, user: String, ipAddress: String, targetInstancePortNumberOpt: Option[Int], hostsFile: Option[File], useAgent: Option[Boolean]): (InstanceId, String) = {
    val targetPortSpecifications = targetInstancePortNumberOpt match {
      case Some(portNumber) => s" -p $portNumber" // trailing space is important
      case _ => ""
    }
    val theTTOptions = if(rawOutput) { " -t -t" }else{ "" }
    val useAgentFragment = useAgent match {
      case None => ""
      case Some(decision) => if(decision) " -A" else " -a"
    }
    val hostsFileString = hostsFile.map(file => s""" -o "UserKnownHostsFile $file" -o "StrictHostKeyChecking yes"""").getOrElse("")
    val connectionString = s"""ssh -o "IdentitiesOnly yes"$useAgentFragment$hostsFileString$targetPortSpecifications -i ${privateKeyFile.getCanonicalFile.toString}${theTTOptions} $user@$ipAddress"""
    val cmd = if(shouldDisplayIdentityFileOnly){
      privateKeyFile.getCanonicalFile.toString
    }else if(rawOutput) {
      s"$connectionString"
    }else{
      s"""
         | # Execute the following command within the next $sshCredentialsLifetimeSeconds seconds:
         | ${connectionString};
         |""".stripMargin
    }
    (instance.id, cmd)
  }

  def sshCmdBastion(rawOutput: Boolean, shouldDisplayIdentityFileOnly: Boolean, privateKeyFile: File, bastionInstance: Instance, targetInstance: Instance, targetInstanceUser: String, bastionIpAddress: String, targetIpAddress: String, bastionPortNumberOpt: Option[Int], bastionUser: String, targetInstancePortNumberOpt: Option[Int], useAgent: Option[Boolean], hostsFile: Option[File]): (InstanceId, String) = {
    val bastionPort = bastionPortNumberOpt.getOrElse(22)
    val targetPort = targetInstancePortNumberOpt.getOrElse(22)
    val hostsFileString = hostsFile.map(file => s""" -o "UserKnownHostsFile $file" -o "StrictHostKeyChecking yes"""").getOrElse("")
    val identityFragment = s"-i ${privateKeyFile.getCanonicalFile.toString}"
    val proxyFragment = s"""-o 'ProxyCommand ssh -o "IdentitiesOnly yes" $identityFragment$hostsFileString -p $bastionPort $bastionUser@$bastionIpAddress nc $targetIpAddress $targetPort'"""
    val stringFragmentTTOptions = if(rawOutput) { " -t -t" } else { "" }
    val useAgentFragment = useAgent match {
      case None => ""
      case Some(decision) => if(decision) " -A" else " -a"
    }
    val connectionString =
      s"""ssh$useAgentFragment -o "IdentitiesOnly yes" $identityFragment$hostsFileString $proxyFragment$stringFragmentTTOptions $targetInstanceUser@$targetIpAddress"""
    val cmd = if(shouldDisplayIdentityFileOnly){
      privateKeyFile.getCanonicalFile.toString
    }else if(rawOutput) {
      s"$connectionString"
    }else{
      s"""
         | # Execute the following commands within the next $sshCredentialsLifetimeSeconds seconds:
         | $connectionString;
         |""".stripMargin
    }
    (targetInstance.id, cmd)
  }

}
