package com.gu.ssm

import java.io._
import java.security.{NoSuchAlgorithmException, NoSuchProviderException}
import java.util.Calendar

import com.amazonaws.regions.Region
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
      val privateKeyFile = File.createTempFile(
        prefix,
        suffix,
        new File(System.getProperty("java.io.tmpdir"))
      )
      FilePermissions(privateKeyFile, "0600")
      val publicKey =
        KeyMaker.makeKey(privateKeyFile, keyAlgorithm, keyProvider)
      Right((privateKeyFile, publicKey))
    } catch {
      case e: IOException =>
        Left(
          FailedAttempt(
            Failure(
              s"Unable to create private key file",
              "Error creating key on disk",
              UnhandledError,
              e
            )
          )
        )
      case e: NoSuchAlgorithmException =>
        Left(
          FailedAttempt(
            Failure(
              s"Unable to create key pair with algorithm $keyAlgorithm",
              s"Error creating key with algorithm $keyAlgorithm",
              UnhandledError,
              e
            )
          )
        )
      case e: NoSuchProviderException =>
        Left(
          FailedAttempt(
            Failure(
              s"Unable to create key pair with provider $keyProvider",
              s"Error creating key with provider $keyProvider",
              UnhandledError,
              e
            )
          )
        )
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
      case e: IOException =>
        Attempt.Left(
          Failure(
            s"Unable to create host key file",
            "Error creating host key on disk",
            UnhandledError,
            e
          )
        )
    }
  }

  def addTaintedCommand(name: String): String = {
    s"""
       | /usr/bin/test -d /etc/update-motd.d/ &&
       | ( /usr/bin/test -f /etc/update-motd.d/99-tainted || /bin/echo -e '#!/bin/bash' | /usr/bin/sudo /usr/bin/tee -a /etc/update-motd.d/99-tainted >> /dev/null;
       |   /bin/echo -e 'echo -e "\\033[0;31mThis instance should be considered tainted.\\033[0;39m"' | /usr/bin/sudo /usr/bin/tee -a /etc/update-motd.d/99-tainted >> /dev/null;
       |   /bin/echo -e 'echo -e "\\033[0;31mIt was accessed by $name at ${Calendar
        .getInstance()
        .getTime}\\033[0;39m"' | /usr/bin/sudo /usr/bin/tee -a /etc/update-motd.d/99-tainted >> /dev/null;
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
      | /bin/sed -i '/${publicKey.replaceAll(
        "/",
        "\\\\/"
      )}/d' /home/$user/.ssh/authorized_keys;
      |""".stripMargin

  def outputHostKeysCommand(): String =
    """
       | for hostkey in $(sshd -T 2> /dev/null |grep "^hostkey " | cut -d ' ' -f 2); do cat $hostkey.pub; done
     """.stripMargin

  def sshCmdStandard(rawOutput: Boolean)(
      privateKeyFile: File,
      instance: Instance,
      user: String,
      ipAddress: String,
      targetInstancePortNumberOpt: Option[Int],
      hostsFile: Option[File],
      useAgent: Option[Boolean],
      profile: Option[String],
      region: Region,
      tunnelThroughSystemsManager: Boolean,
      tunnelTarget: Option[TunnelTargetWithHostName]
  ): (InstanceId, Seq[Output]) = {
    val targetPortSpecifications = targetInstancePortNumberOpt match {
      case Some(portNumber) => s" -p ${portNumber}"
      case _                => ""
    }
    val theTTOptions = if (rawOutput) { " -t -t" }
    else { "" }
    val useAgentFragment = useAgent match {
      case None           => ""
      case Some(decision) => if (decision) " -A" else " -a"
    }
    val hostsFileString = hostsFile
      .map(file =>
        s""" -o "UserKnownHostsFile $file" -o "StrictHostKeyChecking yes""""
      )
      .getOrElse("")
    val proxyFragment = if (tunnelThroughSystemsManager) {
      s""" -o "ProxyCommand sh -c \\"aws ssm start-session --target ${instance.id.id} --document-name AWS-StartSSHSession --parameters 'portNumber=22' --region $region ${profile
          .map("--profile " + _)
          .getOrElse("")}\\"""""
    } else { "" }

    val (tunnelString, tunnelMeta) = tunnelTarget
      .map(t =>
        (
          s"-L ${t.localPort}:${t.remoteHostName}:${t.remotePort} -N -f",
          Seq(
            Metadata(
              s"# If the command succeeded, a tunnel has been established."
            ),
            Metadata(s"# Local port: ${t.localPort}"),
            Metadata(s"# Remote address: ${t.remoteHostName}:${t.remotePort}")
          ) ++ t.remoteTags.map(_.toLowerCase).find(_.contains("prod")).map {
            _ =>
              Metadata(
                s"# The tags indicate that this is a PRODUCTION resource. Please take care! Perhaps bring a pair?"
              )
          }
        )
      )
      .getOrElse(("", Seq.empty))

    val connectionString =
      s"""ssh -o "IdentitiesOnly yes"$useAgentFragment$hostsFileString$targetPortSpecifications$proxyFragment -i ${privateKeyFile.getCanonicalFile.toString}${theTTOptions} $user@$ipAddress $tunnelString"""
        .trim()

    val cmd = if (rawOutput) {
      Seq(Out(s"$connectionString", newline = false)) ++ tunnelMeta.toList
    } else {
      Seq(
        Metadata(
          s"# Dryrun mode. The command below will remain valid for $sshCredentialsLifetimeSeconds seconds:"
        ),
        Out(s"$connectionString;")
      )
    }
    (instance.id, cmd)
  }

  def sshCmdBastion(rawOutput: Boolean)(
      privateKeyFile: File,
      bastionInstance: Instance,
      targetInstance: Instance,
      targetInstanceUser: String,
      bastionIpAddress: String,
      targetIpAddress: String,
      bastionPortNumberOpt: Option[Int],
      bastionUser: String,
      targetInstancePortNumberOpt: Option[Int],
      useAgent: Option[Boolean],
      hostsFile: Option[File]
  ): (InstanceId, Seq[Output]) = {
    val bastionPort = bastionPortNumberOpt.getOrElse(22)
    val targetPort = targetInstancePortNumberOpt.getOrElse(22)
    val hostsFileString = hostsFile
      .map(file =>
        s""" -o "UserKnownHostsFile $file" -o "StrictHostKeyChecking yes""""
      )
      .getOrElse("")
    val identityFragment = s"-i ${privateKeyFile.getCanonicalFile.toString}"
    val proxyFragment =
      s"""-o 'ProxyCommand ssh -o "IdentitiesOnly yes" $identityFragment$hostsFileString -p $bastionPort $bastionUser@$bastionIpAddress nc $targetIpAddress $targetPort'"""
    val stringFragmentTTOptions = if (rawOutput) { " -t -t" }
    else { "" }
    val useAgentFragment = useAgent match {
      case None           => ""
      case Some(decision) => if (decision) " -A" else " -a"
    }
    val connectionString =
      s"""ssh$useAgentFragment -o "IdentitiesOnly yes" $identityFragment$hostsFileString $proxyFragment$stringFragmentTTOptions $targetInstanceUser@$targetIpAddress"""
    val cmd = if (rawOutput) {
      Seq(Out(s"$connectionString", newline = false))
    } else {
      Seq(
        Metadata(
          s"# Dryrun mode. The command below will remain valid for $sshCredentialsLifetimeSeconds seconds:"
        ),
        Out(s"$connectionString;")
      )
    }
    (targetInstance.id, cmd)
  }

  // The first file goes to the second file
  // The remote file is indicated by a colon

  def scpCmdStandard(rawOutput: Boolean)(
      privateKeyFile: File,
      instance: Instance,
      user: String,
      ipAddress: String,
      targetInstancePortNumberOpt: Option[Int],
      useAgent: Option[Boolean],
      hostsFile: Option[File],
      sourceFile: String,
      targetFile: String,
      profile: Option[String],
      region: Region,
      tunnelThroughSystemsManager: Boolean
  ): (InstanceId, Seq[Output]) = {

    def isRemote(filepath: String): Boolean = {
      filepath.startsWith(":")
    }

    def exactlyOneArgumentIsRemote(
        filepath1: String,
        filepath2: String
    ): Boolean = {
      List(filepath1, filepath2).map(isRemote).count(_ == true) == 1
    }

    val targetPortSpecifications = targetInstancePortNumberOpt match {
      case Some(portNumber) => s" -p ${portNumber}"
      case _                => ""
    }
    val hostsFileString = hostsFile
      .map(file =>
        s""" -o "UserKnownHostsFile $file" -o "StrictHostKeyChecking yes""""
      )
      .getOrElse("")
    val proxyFragment = if (tunnelThroughSystemsManager) {
      s""" -o "ProxyCommand sh -c \\"aws ssm start-session --target ${instance.id.id} --document-name AWS-StartSSHSession --parameters 'portNumber=22' --region $region ${profile
          .map("--profile " + _)
          .getOrElse("")}\\"""""
    } else { "" }
    val useAgentFragment = useAgent match {
      case None           => ""
      case Some(decision) => if (decision) " -A" else " -a"
    }
    // We are using colon to designate the remote file.
    // There should be only one.
    if (exactlyOneArgumentIsRemote(sourceFile, targetFile)) {
      val connectionString =
        if (isRemote(sourceFile)) {
          s"""scp -o "IdentitiesOnly yes"$useAgentFragment$hostsFileString$proxyFragment${targetPortSpecifications} -i ${privateKeyFile.getCanonicalFile.toString} $user@$ipAddress:${sourceFile
              .stripPrefix(":")} ${targetFile}"""
        } else {
          s"""scp -o "IdentitiesOnly yes"$useAgentFragment$hostsFileString$proxyFragment${targetPortSpecifications} -i ${privateKeyFile.getCanonicalFile.toString} ${sourceFile} $user@$ipAddress:${targetFile
              .stripPrefix(":")}"""
        }
      val cmd = if (rawOutput) {
        Seq(Out(s"$connectionString", newline = false))
      } else {
        Seq(
          Metadata(
            s"# Dryrun mode. The command below will remain valid for $sshCredentialsLifetimeSeconds seconds:"
          ),
          Out(s"$connectionString;")
        )
      }
      (instance.id, cmd)
    } else {
      (
        instance.id,
        Seq(
          Err(
            "Incorrect remote server specifications, only one file should carry the starting colon"
          )
        )
      )
    }

  }

}
