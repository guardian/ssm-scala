package com.gu.ssm

import org.scalatest.{EitherValues, FreeSpec, Matchers}
import java.io.{File, IOException}


class SSHTest extends FreeSpec with Matchers with EitherValues {
  "create add key command" - {
    import SSH.addKeyCommand

    "correct content" in {
      addKeyCommand("XXX") shouldEqual "\n /bin/mkdir -p /home/ubuntu/.ssh;\n /bin/echo 'XXX' >> /home/ubuntu/.ssh/authorized_keys;\n /bin/chmod 0600 /home/ubuntu/.ssh/authorized_keys\n"
    }

  }

  "create remove key command" - {
    import SSH.removeKeyCommand

    "create with no stupid characters" in {
      removeKeyCommand("XXX") shouldEqual "\n /bin/sleep 30;\n /bin/sed -i '/XXX/d' /home/ubuntu/.ssh/authorized_keys;\n"
    }

    "create with stupid characters" in {
      removeKeyCommand("X/Y/Z") shouldEqual "\n /bin/sleep 30;\n /bin/sed -i '/X\\/Y\\/Z/d' /home/ubuntu/.ssh/authorized_keys;\n"
    }

  }

  "create ssh command" - {
    import SSH.sshCmd
    import scala.concurrent.ExecutionContext.Implicits.global

    "create ssh command" in {
      val file:File = new File("/banana")
      val instance:Instance = Instance(InstanceId("X"), Some("Y"))
      sshCmd(file, instance)._1.id shouldEqual "X"
      sshCmd(file, instance)._2.isRight shouldBe true
//      shouldEqual "\n # Execute the following command within the next 30 seconds:\n ssh -i /banana ubuntu@Y\n"
    }
  }

//  "create ssh commands" - {
//    import SSH.sshCmd
//
//    "returns command if it was provided" in {
//      sshCmd(ToExecute(cmdOpt = Some("ls"))).right.value shouldEqual "ls"
//    }
//  }
}

//  def sshCmd(tempFile: File, instance: Instance)(implicit ec: ExecutionContext): (InstanceId, Either[CommandStatus, CommandResult]) = {
//    val cmd = s"""
//                 | # Execute the following command within the next $delay seconds:
//                 | ssh -i ${tempFile.getCanonicalFile.toString} ubuntu@${instance.publicIpAddressOpt.get}
//    """.stripMargin
//    instance.id -> Right(CommandResult(cmd, ""))
//  }
//
//  def sshCmds(tempFile: File, instances: List[Instance])(implicit ec: ExecutionContext): List[(InstanceId, Either[CommandStatus, CommandResult])] = {
//    instances.map(i => sshCmd(tempFile, i))
//  }
//
//}
