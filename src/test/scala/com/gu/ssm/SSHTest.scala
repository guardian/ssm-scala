package com.gu.ssm

import org.scalatest.{EitherValues, FreeSpec, Matchers}
import java.io.File
import java.time.Instant

class SSHTest extends FreeSpec with Matchers with EitherValues {

  "create add key command" - {
    import SSH.addPublicKeyCommand

    "make ssh directory" in {
      addPublicKeyCommand("user1", "XXX") should include ("/bin/mkdir -p /home/user1/.ssh;")
    }

    "make authorised keys" in {
      addPublicKeyCommand("user2", "XXX") should include ("/bin/echo 'XXX' >> /home/user2/.ssh/authorized_keys;")
    }

    "ensure authorised key file permissions are correct" in {
      addPublicKeyCommand("user3", "XXX") should include ("/bin/chmod 0600 /home/user3/.ssh/authorized_keys;")
    }

  }

  "create taintedcommand" - {

    "ensure motd command file is present" in {
      import SSH.addTaintedCommand
      addTaintedCommand("XXX") should include ("test -f /etc/update-motd.d/99-tainted || /bin/echo -e '#!/bin/bash' | /usr/bin/sudo /usr/bin/tee -a /etc/update-motd.d/99-tainted >> /dev/null;")
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
    import SSH.sshCmdStandard
    import SSH.sshCmdBastion
    import SSH.sshCredentialsLifetimeSeconds

    "create standard ssh command" - {

      val file = new File("/banana")
      val instance = Instance(InstanceId("raspberry"), None, Some("34.1.1.10"), "10.1.1.10", Instant.now())

      "instance id is correct" in {
        val (instanceId, _) = sshCmdStandard(false)(file, instance, "user4", "34.1.1.10", None)
        instanceId.id shouldEqual "raspberry"
      }

      "user command" - {
        "is correctly formed without port specification" in {
          val (_, command) = sshCmdStandard(false)(file, instance, "user4", "34.1.1.10", None)
          command should include ("ssh -i /banana user4@34.1.1.10")
        }

        "is correctly formed with port specification" in {
          val (_, command) = sshCmdStandard(false)(file, instance, "user4", "34.1.1.10", Some(2345))
          command should include ("ssh -p 2345 -i /banana user4@34.1.1.10")
        }
      }

      "machine command" - {
        "is correctly formed without port specification" in {
          val (_, command) = sshCmdStandard(true)(file, instance, "user4", "34.1.1.10", None)
          command should equal ("ssh -i /banana -t -t user4@34.1.1.10")
        }

        "is correctly formed with port specification" in {
          val (_, command) = sshCmdStandard(true)(file, instance, "user4", "34.1.1.10", Some(2345))
          command should equal ("ssh -p 2345 -i /banana -t -t user4@34.1.1.10")
        }
      }
    }

    "create bastion ssh command" - {

      val file = new File("/banana")
      val bastionInstance = Instance(InstanceId("raspberry"), None, Some("34.1.1.10"), "10.1.1.10", Instant.now())
      val targetInstance = Instance(InstanceId("strawberry"), None, Some("34.1.1.11"), "10.1.1.11", Instant.now())

      "instance id is correct" in {
        val (instanceId, _) = sshCmdBastion(false)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", None, "bastionuser", None, false)
        instanceId.id shouldEqual "strawberry"
      }

      "user command" - {
        "contains the user instructions" in {
          val (_, command) = sshCmdBastion(false)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", None, "bastionuser", None, false)
          command should include (s"# Execute the following commands within the next $sshCredentialsLifetimeSeconds seconds")
        }

        "contains the ssh command" in {
          val (_, command) = sshCmdBastion(false)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", None, "bastionuser", None, false)
          command should include ("ssh")
        }
      }

      "machine command" - {
        "is well formed without any port specification" - {
          "no agent" in {
            val (_, command) = sshCmdBastion(true)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", None, "bastionuser", None, false)
            command should equal ("ssh -A -i /banana -t -t bastionuser@34.1.1.10 -t -t ssh -t -t user5@10.1.1.11")
          }

          "with agent" in {
            val (_, command) = sshCmdBastion(true)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", None, "bastionuser", None, true)
            command should equal (s"ssh-add -t $sshCredentialsLifetimeSeconds /banana && ssh -A -t -t bastionuser@34.1.1.10 -t -t ssh -t -t user5@10.1.1.11")
          }
        }

        "is well formed with target instance port specification" - {
          "no agent" in {
            val (_, command) = sshCmdBastion(true)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", None, "bastionuser", Some(2345), false)
            command should equal ("ssh -A -i /banana -t -t bastionuser@34.1.1.10 -t -t ssh -p 2345 -t -t user5@10.1.1.11")
          }

          "with agent" in {
            val (_, command) = sshCmdBastion(true)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", None, "bastionuser", Some(2345), true)
            command should equal (s"ssh-add -t $sshCredentialsLifetimeSeconds /banana && ssh -A -t -t bastionuser@34.1.1.10 -t -t ssh -p 2345 -t -t user5@10.1.1.11")
          }
        }

        "is well formed with bastion port specification" - {
          "no agent" in {
            val (_, command) = sshCmdBastion(true)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", Some(1234), "bastionuser", None, false)
            command should equal ("ssh -A -p 1234 -i /banana -t -t bastionuser@34.1.1.10 -t -t ssh -t -t user5@10.1.1.11")
          }

          "with agent" in {
            val (_, command) = sshCmdBastion(true)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", Some(1234), "bastionuser", None, true)
            command should equal (s"ssh-add -t $sshCredentialsLifetimeSeconds /banana && ssh -A -p 1234 -t -t bastionuser@34.1.1.10 -t -t ssh -t -t user5@10.1.1.11")
          }
        }

        "is well formed with both bastion port and target instance port specifications" - {
          "no agent" in {
            val (_, command) = sshCmdBastion(true)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", Some(1234), "bastionuser", Some(2345), false)
            command should equal ("ssh -A -p 1234 -i /banana -t -t bastionuser@34.1.1.10 -t -t ssh -p 2345 -t -t user5@10.1.1.11")
          }

          "with agent" in {
            val (_, command) = sshCmdBastion(true)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", Some(1234), "bastionuser", Some(2345), true)
            command should equal (s"ssh-add -t $sshCredentialsLifetimeSeconds /banana && ssh -A -p 1234 -t -t bastionuser@34.1.1.10 -t -t ssh -p 2345 -t -t user5@10.1.1.11")
          }
        }
      }
    }
  }
}
