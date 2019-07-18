package com.gu.ssm

import org.scalatest.{EitherValues, FreeSpec, Matchers}
import java.io.File
import java.time.Instant

import com.amazonaws.regions.{Region, Regions}

class SSHTest extends FreeSpec with Matchers with EitherValues {

  "create add key command" - {
    import SSH.addPublicKeyCommand

    "make ssh directory" in {
      addPublicKeyCommand("user1", "XXX") should include ("/bin/mkdir -p /home/user1/.ssh;")
    }

    "make authorised keys" in {
      addPublicKeyCommand("user2", "XXX") should include ("/bin/echo 'XXX' >> /home/user2/.ssh/authorized_keys;")
    }

    "ensure authorised key file ownership is correct" in {
      addPublicKeyCommand("user3", "XXX") should include ("/bin/chown user3 /home/user3/.ssh/authorized_keys;")
    }

    "ensure authorised key file permissions are correct" in {
      addPublicKeyCommand("user4", "XXX") should include ("/bin/chmod 0600 /home/user4/.ssh/authorized_keys;")
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
    val EU_WEST_1 = Region.getRegion(Regions.EU_WEST_1)

    "create standard ssh command" - {

      val file = new File("/banana")
      val instance = Instance(InstanceId("raspberry"), None, Some("34.1.1.10"), "10.1.1.10", Instant.now())

      "instance id is correct" in {
        val (instanceId, _) = sshCmdStandard(false)(file, instance, "user4", "34.1.1.10", None, None, Some(false), None, EU_WEST_1)
        instanceId.id shouldEqual "raspberry"
      }

      "user command" - {
        "is correctly formed without port specification" in {
          val (_, command) = sshCmdStandard(false)(file, instance, "user4", "34.1.1.10", None, None, Some(false), None, EU_WEST_1)
          command should contain (Out("""ssh -o "IdentitiesOnly yes" -a -i /banana user4@34.1.1.10;"""))
        }

        "is correctly formed with port specification" in {
          val (_, command) = sshCmdStandard(false)(file, instance, "user4", "34.1.1.10", Some(2345), None, Some(false), None, EU_WEST_1)
          command should contain (Out("""ssh -o "IdentitiesOnly yes" -a -p 2345 -i /banana user4@34.1.1.10;"""))
        }

        "is correctly formed with a hosts file" in {
          val (_, command) = sshCmdStandard(false)(file, instance, "user4", "34.1.1.10", Some(2345), Some(new File("/tmp/hostsfile")), Some(false), None, EU_WEST_1)
          command should contain (Out("""ssh -o "IdentitiesOnly yes" -a -o "UserKnownHostsFile /tmp/hostsfile" -o "StrictHostKeyChecking yes" -p 2345 -i /banana user4@34.1.1.10;"""))
        }

        "is correctly formed with agent forwarding file" in {
          val (_, command) = sshCmdStandard(false)(file, instance, "user4", "34.1.1.10", Some(2345), None, Some(true), None, EU_WEST_1)
          command should contain (Out("""ssh -o "IdentitiesOnly yes" -A -p 2345 -i /banana user4@34.1.1.10;"""))
        }
      }

      "machine command" - {
        "is correctly formed without port specification" in {
          val (_, command) = sshCmdStandard(true)(file, instance, "user4", "34.1.1.10", None, None, Some(false), None, EU_WEST_1)
          command.head.text should equal ("""ssh -o "IdentitiesOnly yes" -a -i /banana -t -t user4@34.1.1.10""")
        }

        "is correctly formed with port specification" in {
          val (_, command) = sshCmdStandard(true)(file, instance, "user4", "34.1.1.10", Some(2345), None, Some(false), None, EU_WEST_1)
          command.head.text should equal ("""ssh -o "IdentitiesOnly yes" -a -p 2345 -i /banana -t -t user4@34.1.1.10""")
        }
      }
    }

    "create bastion ssh command" - {

      val file = new File("/banana")
      val bastionInstance = Instance(InstanceId("raspberry"), None, Some("34.1.1.10"), "10.1.1.10", Instant.now())
      val targetInstance = Instance(InstanceId("strawberry"), None, Some("34.1.1.11"), "10.1.1.11", Instant.now())

      "instance id is correct" in {
        val (instanceId, _) = sshCmdBastion(false)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", None, "bastionuser", None, Some(false), None)
        instanceId.id shouldEqual "strawberry"
      }

      "user command" - {
        "contains the user instructions" in {
          val (_, command) = sshCmdBastion(false)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", None, "bastionuser", None, Some(false), None)
          command should contain (Metadata(s"# Execute the following command within the next $sshCredentialsLifetimeSeconds seconds:"))
        }

        "contains the ssh command" in {
          val (_, command) = sshCmdBastion(false)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", None, "bastionuser", None, Some(false), None)
          command.find(_.isInstanceOf[Out]).head.text should include ("ssh")
        }
      }

      "machine command" - {
        "is well formed without any port specification" - {
          "agent-agnostic" in {
            val (_, command) = sshCmdBastion(true)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", None, "bastionuser", None, None, None)
            command.head.text should equal ("""ssh -o "IdentitiesOnly yes" -i /banana -o 'ProxyCommand ssh -o "IdentitiesOnly yes" -i /banana -p 22 bastionuser@34.1.1.10 nc 10.1.1.11 22' -t -t user5@10.1.1.11""")
          }

          "no agent" in {
            val (_, command) = sshCmdBastion(true)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", None, "bastionuser", None, Some(false), None)
            command.head.text should equal ("""ssh -a -o "IdentitiesOnly yes" -i /banana -o 'ProxyCommand ssh -o "IdentitiesOnly yes" -i /banana -p 22 bastionuser@34.1.1.10 nc 10.1.1.11 22' -t -t user5@10.1.1.11""")
          }

          "with agent" in {
            val (_, command) = sshCmdBastion(true)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", None, "bastionuser", None, Some(true), None)
            command.head.text should equal ("""ssh -A -o "IdentitiesOnly yes" -i /banana -o 'ProxyCommand ssh -o "IdentitiesOnly yes" -i /banana -p 22 bastionuser@34.1.1.10 nc 10.1.1.11 22' -t -t user5@10.1.1.11""")
          }
        }

        "is well formed with target instance port specification" - {
          "agent-agnostic" in {
            val (_, command) = sshCmdBastion(true)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", None, "bastionuser", Some(2345), None, None)
            command.head.text should equal ("""ssh -o "IdentitiesOnly yes" -i /banana -o 'ProxyCommand ssh -o "IdentitiesOnly yes" -i /banana -p 22 bastionuser@34.1.1.10 nc 10.1.1.11 2345' -t -t user5@10.1.1.11""")
          }
          "no agent" in {
            val (_, command) = sshCmdBastion(true)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", None, "bastionuser", Some(2345), Some(false), None)
            command.head.text should equal ("""ssh -a -o "IdentitiesOnly yes" -i /banana -o 'ProxyCommand ssh -o "IdentitiesOnly yes" -i /banana -p 22 bastionuser@34.1.1.10 nc 10.1.1.11 2345' -t -t user5@10.1.1.11""")
          }

          "with agent" in {
            val (_, command) = sshCmdBastion(true)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", None, "bastionuser", Some(2345), Some(true), None)
            command.head.text should equal ("""ssh -A -o "IdentitiesOnly yes" -i /banana -o 'ProxyCommand ssh -o "IdentitiesOnly yes" -i /banana -p 22 bastionuser@34.1.1.10 nc 10.1.1.11 2345' -t -t user5@10.1.1.11""")
          }
        }

        "is well formed with bastion port specification" - {
          "agent-agnostic" in {
            val (_, command) = sshCmdBastion(true)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", Some(1234), "bastionuser", None, None, None)
            command.head.text should equal ("""ssh -o "IdentitiesOnly yes" -i /banana -o 'ProxyCommand ssh -o "IdentitiesOnly yes" -i /banana -p 1234 bastionuser@34.1.1.10 nc 10.1.1.11 22' -t -t user5@10.1.1.11""")
          }
          "no agent" in {
            val (_, command) = sshCmdBastion(true)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", Some(1234), "bastionuser", None, Some(false), None)
            command.head.text should equal ("""ssh -a -o "IdentitiesOnly yes" -i /banana -o 'ProxyCommand ssh -o "IdentitiesOnly yes" -i /banana -p 1234 bastionuser@34.1.1.10 nc 10.1.1.11 22' -t -t user5@10.1.1.11""")
          }

          "with agent" in {
            val (_, command) = sshCmdBastion(true)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", Some(1234), "bastionuser", None, Some(true), None)
            command.head.text should equal ("""ssh -A -o "IdentitiesOnly yes" -i /banana -o 'ProxyCommand ssh -o "IdentitiesOnly yes" -i /banana -p 1234 bastionuser@34.1.1.10 nc 10.1.1.11 22' -t -t user5@10.1.1.11""")
          }
        }

        "is well formed with both bastion port and target instance port specifications" - {
          "agent-agnostic" in {
            val (_, command) = sshCmdBastion(true)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", Some(1234), "bastionuser", Some(2345), None, None)
            command.head.text should equal ("""ssh -o "IdentitiesOnly yes" -i /banana -o 'ProxyCommand ssh -o "IdentitiesOnly yes" -i /banana -p 1234 bastionuser@34.1.1.10 nc 10.1.1.11 2345' -t -t user5@10.1.1.11""")
          }

          "no agent" in {
            val (_, command) = sshCmdBastion(true)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", Some(1234), "bastionuser", Some(2345), Some(false), None)
            command.head.text should equal ("""ssh -a -o "IdentitiesOnly yes" -i /banana -o 'ProxyCommand ssh -o "IdentitiesOnly yes" -i /banana -p 1234 bastionuser@34.1.1.10 nc 10.1.1.11 2345' -t -t user5@10.1.1.11""")
          }

          "with agent" in {
            val (_, command) = sshCmdBastion(true)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", Some(1234), "bastionuser", Some(2345), Some(true), None)
            command.head.text should equal ("""ssh -A -o "IdentitiesOnly yes" -i /banana -o 'ProxyCommand ssh -o "IdentitiesOnly yes" -i /banana -p 1234 bastionuser@34.1.1.10 nc 10.1.1.11 2345' -t -t user5@10.1.1.11""")
          }
        }

        "is well formed with a host key file" in {
          val (_, command) = sshCmdBastion(true)(file, bastionInstance, targetInstance, "user5", "34.1.1.10", "10.1.1.11", Some(1234), "bastionuser", Some(2345), Some(false), Some(new File("/tmp/hostfile")))
          command.head.text should equal ("""ssh -a -o "IdentitiesOnly yes" -i /banana -o "UserKnownHostsFile /tmp/hostfile" -o "StrictHostKeyChecking yes" -o 'ProxyCommand ssh -o "IdentitiesOnly yes" -i /banana -o "UserKnownHostsFile /tmp/hostfile" -o "StrictHostKeyChecking yes" -p 1234 bastionuser@34.1.1.10 nc 10.1.1.11 2345' -t -t user5@10.1.1.11""")
        }
      }
    }
  }

  "create scp command" - {
    import SSH.scpCmdStandard

    "create standard scp command" - {

      val file = new File("/banana")
      val instance = Instance(InstanceId("raspberry"), None, Some("34.1.1.10"), "10.1.1.10", Instant.now())

      "instance id is correct" in {
        val (instanceId, _) = scpCmdStandard(false)(file, instance, "user4", "34.1.1.10", None, Some(false), None, "/path/to/sourceFile", ":/path/to/targetFile")
        instanceId.id shouldEqual "raspberry"
      }

      "user command" - {

        "process correctly remote server specifications" - {

          "target file is remote" in {
            val (_, command) = scpCmdStandard(false)(file, instance, "user4", "34.1.1.10", None, Some(false), None, "/path/to/sourceFile", ":/path/to/targetFile")
            command should contain (Out("""scp -o "IdentitiesOnly yes" -a -i /banana /path/to/sourceFile user4@34.1.1.10:/path/to/targetFile;"""))
          }
          "source file is remote" in {
            val (_, command) = scpCmdStandard(false)(file, instance, "user4", "34.1.1.10", None, Some(false), None, ":/path/to/sourceFile", "/path/to/targetFile")
            command should contain (Out("""scp -o "IdentitiesOnly yes" -a -i /banana user4@34.1.1.10:/path/to/sourceFile /path/to/targetFile;"""))
          }
          "incorrect specifications in" in {
            val (_, command) = scpCmdStandard(false)(file, instance, "user4", "34.1.1.10", None, Some(false), None, ":/path/to/sourceFile", ":/path/to/targetFile")
            command.head.text should include ("Incorrect remote server specifications")
          }
        }

        "is correctly formed without port specification" in {
          val (_, command) = scpCmdStandard(false)(file, instance, "user4", "34.1.1.10", None, Some(false), None, "/path/to/sourceFile", ":/path/to/targetFile")
          command should contain (Out("""scp -o "IdentitiesOnly yes" -a -i /banana /path/to/sourceFile user4@34.1.1.10:/path/to/targetFile;"""))
        }

        "is correctly formed with port specification" in {
          val (_, command) = scpCmdStandard(false)(file, instance, "user4", "34.1.1.10", Some(2345), Some(false), None, "/path/to/sourceFile", ":/path/to/targetFile")
          command should contain (Out("""scp -o "IdentitiesOnly yes" -a -p 2345 -i /banana /path/to/sourceFile user4@34.1.1.10:/path/to/targetFile;"""))
        }

        "is correctly formed with a hosts file" in {
          val (_, command) = scpCmdStandard(false)(file, instance, "user4", "34.1.1.10", Some(2345), Some(false), Some(new File("/tmp/hostsfile")), "/path/to/sourceFile", ":/path/to/targetFile")
          command should contain (Out("""scp -o "IdentitiesOnly yes" -a -o "UserKnownHostsFile /tmp/hostsfile" -o "StrictHostKeyChecking yes" -p 2345 -i /banana /path/to/sourceFile user4@34.1.1.10:/path/to/targetFile;"""))
        }

        "is correctly formed with agent forwarding file" in {
          val (_, command) = scpCmdStandard(false)(file, instance, "user4", "34.1.1.10", Some(2345), Some(true), None, "/path/to/sourceFile", ":/path/to/targetFile")
          command should contain (Out("""scp -o "IdentitiesOnly yes" -A -p 2345 -i /banana /path/to/sourceFile user4@34.1.1.10:/path/to/targetFile;"""))
        }
      }

      "machine command" - {
        "process correctly remote server specifications" - {

          "target file is remote" in {
            val (_, command) = scpCmdStandard(true)(file, instance, "user4", "34.1.1.10", None, Some(false), None, "/path/to/sourceFile", ":/path/to/targetFile")
            command.head.text should equal ("""scp -o "IdentitiesOnly yes" -a -i /banana /path/to/sourceFile user4@34.1.1.10:/path/to/targetFile""")
          }
          "source file is remote" in {
            val (_, command) = scpCmdStandard(true)(file, instance, "user4", "34.1.1.10", None, Some(false), None, ":/path/to/sourceFile", "/path/to/targetFile")
            command.head.text should equal ("""scp -o "IdentitiesOnly yes" -a -i /banana user4@34.1.1.10:/path/to/sourceFile /path/to/targetFile""")
          }
          "incorrect specifications in" in {
            val (_, command) = scpCmdStandard(true)(file, instance, "user4", "34.1.1.10", None, Some(false), None, ":/path/to/sourceFile", ":/path/to/targetFile")
            command.head.text should include ("Incorrect remote server specifications")
          }
        }

        "is correctly formed without port specification" in {
          val (_, command) = scpCmdStandard(true)(file, instance, "user4", "34.1.1.10", None, Some(false), None, ":/path/to/sourceFile", "/path/to/targetFile")
          command.head.text should equal ("""scp -o "IdentitiesOnly yes" -a -i /banana user4@34.1.1.10:/path/to/sourceFile /path/to/targetFile""")
        }

        "is correctly formed with port specification" in {
          val (_, command) = scpCmdStandard(true)(file, instance, "user4", "34.1.1.10", Some(2345), Some(false), None, "/path/to/sourceFile", ":/path/to/targetFile")
          command.head.text should equal ("""scp -o "IdentitiesOnly yes" -a -p 2345 -i /banana /path/to/sourceFile user4@34.1.1.10:/path/to/targetFile""")
        }
      }
    }
  }
}
