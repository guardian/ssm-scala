package com.gu.ssm

import org.scalatest.{EitherValues, FreeSpec, Matchers}
import java.io.{File, IOException}

import com.gu.ssm.utils.attempt.FailedAttempt
import org.scalatest.matchers.BeMatcher


class SSHTest extends FreeSpec with Matchers with EitherValues {
  "create add key command" - {
    import SSH.addKeyCommand

    "correct content" in {
      addKeyCommand("XXX") should include ("/bin/mkdir -p /home/ubuntu/.ssh;")
        addKeyCommand("XXX") should include ("/bin/echo 'XXX' >> /home/ubuntu/.ssh/authorized_keys;")
        addKeyCommand("XXX") should include ("/bin/chmod 0600 /home/ubuntu/.ssh/authorized_keys;")
    }

  }

  "create taintedcommand" - {

    "create tainted command" in {
      import SSH.addTaintedCommand
      val cmd = addTaintedCommand("XXX")
      cmd should include ("[[ -f /etc/update-motd.d/99-tainted ]] || /bin/echo -e '#!/bin/bash' | /usr/bin/sudo /usr/bin/tee -a /etc/update-motd.d/99-tainted >> /dev/null;")
      cmd should include ("This instance was tainted by XXX at") // much text removed from this because of color codes
      cmd should include ("/bin/chmod 0755 /etc/update-motd.d/99-tainted;")
      cmd should include ("/usr/bin/sudo /bin/run-parts /etc/update-motd.d/ | /usr/bin/sudo /usr/bin/tee /run/motd.dynamic >> /dev/null;")
    }
  }

  "create ssh command" - {
    import SSH.sshCmd
    import scala.concurrent.ExecutionContext.Implicits.global

    "create ssh command" in {
      val file:File = new File("/banana")
      val instance:Instance = Instance(InstanceId("X"), Some("Y"))
        val cmd = sshCmd(file, instance)
      cmd._1.id shouldEqual "X"
      cmd._2.isRight shouldBe true
    }
  }

  "get Single Instance" - {
    import SSH.getSingleInstance

    "More than one instance" in {
      SSH.getSingleInstance(List(Instance(InstanceId("X"), None), Instance(InstanceId("Y"), None))).isLeft shouldBe true
    }

    "Exactly one instance" in {
      SSH.getSingleInstance(List(Instance(InstanceId("X"), None))).isRight shouldBe true
    }
  }

}
