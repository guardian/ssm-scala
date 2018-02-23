package com.gu.ssm

import org.scalatest.{EitherValues, FreeSpec, Matchers}
import java.io.File
import java.time.Instant

class SSHTest extends FreeSpec with Matchers with EitherValues {

  "create add key command" - {
    import SSH.addKeyCommand

    "make ssh directory" in {
      addKeyCommand("XXX") should include ("/bin/mkdir -p /home/ubuntu/.ssh;")
    }

    "make authorised keys" in {
      addKeyCommand("XXX") should include ("/bin/echo 'XXX' >> /home/ubuntu/.ssh/authorized_keys;")
    }

    "ensure authorised key file permissions are correct" in {
      addKeyCommand("XXX") should include ("/bin/chmod 0600 /home/ubuntu/.ssh/authorized_keys;")
    }

  }

  "create taintedcommand" - {

    "ensure motd command file is present" in {
      import SSH.addTaintedCommand
      addTaintedCommand("XXX") should include ("[[ -f /etc/update-motd.d/99-tainted ]] || /bin/echo -e '#!/bin/bash' | /usr/bin/sudo /usr/bin/tee -a /etc/update-motd.d/99-tainted >> /dev/null;")
    }
    "ensure motd command file contains tainted message" in {
      import SSH.addTaintedCommand
      addTaintedCommand("XXX") should include ("This instance should be considered tainted.") // much text removed from this because of color codes
    }
    "ensure motd command file contains accessed message" in {
      import SSH.addTaintedCommand
      addTaintedCommand("XXX") should include ("It was accessed by XXX at") // much text removed from this because of color codes
    }
    "ensure motd command file has correct permissions" in {
      import SSH.addTaintedCommand
      addTaintedCommand("XXX") should include ("/bin/chmod 0755 /etc/update-motd.d/99-tainted;")
    }
    "ensure motd update is executed" in {
      import SSH.addTaintedCommand
      addTaintedCommand("XXX") should include ("/usr/bin/sudo /bin/run-parts /etc/update-motd.d/ | /usr/bin/sudo /usr/bin/tee /run/motd.dynamic >> /dev/null;")
    }
  }

  "create ssh command" - {
    import SSH.sshCmd
    import java.util.Date

    "create ssh command" in {
      val file = new File("/banana")
      val instance = Instance(InstanceId("raspberry"), Some("34.1.1.10"), "10.1.1.10", Instant.now())
      val cmd = sshCmd(file, instance, "34.1.1.10")
      cmd._1.id shouldEqual "raspberry"
      cmd._2 should include ("ssh -i /banana ubuntu@34.1.1.10")
    }
  }
}
