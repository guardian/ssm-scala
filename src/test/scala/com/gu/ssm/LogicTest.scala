package com.gu.ssm

import org.scalatest.EitherValues

import java.time.{Instant, LocalDateTime, ZoneId}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class LogicTest extends AnyFreeSpec with Matchers with EitherValues {
  "extractSASTags" - {
    import Logic.extractSASTags

    "extracts stack app and stage from valid input" in {
      val expected = Right(List("app", "stack", "stage"))
      extractSASTags(Seq("app", "stack", "stage")) shouldEqual expected
    }

    "provides error if nothing is provided" in {
      extractSASTags(Seq("")).isLeft shouldEqual true
    }

    "returns error if more than 3 tags are provided" in {
      extractSASTags(Seq("a", "b", "c", "d")).isLeft shouldEqual true
    }
  }

  "extractTunnelConfig" - {
    import Logic.extractTunnelConfig

    val hostname = "example-db.rds.amazonaws.com"

    "extracts tunnel config given ports and hostname" in {
      val expected = Right(TunnelTargetWithHostName(5432, hostname, 5432))
      extractTunnelConfig(s"5432:$hostname:5432") shouldBe expected
    }

    "returns error if ports are not integers" in {
      extractTunnelConfig(s"5432i:$hostname:5432").isLeft shouldBe true
      extractTunnelConfig(s"5432:$hostname:5432i").isLeft shouldBe true
    }
  }

  "extractRDSTunnelConfig" - {
    import Logic.extractRDSTunnelConfig

    "extracts tunnel config given ports and tags" in {
      val expected = Right(TunnelTargetWithRDSTags(5432, List("APP", "STACK", "STAGE")))
      extractRDSTunnelConfig(s"5432:APP,STACK,STAGE") shouldBe expected
    }

    "returns error if no tags are given" in {
      extractRDSTunnelConfig(s"5432:,").isLeft shouldBe true
    }
  }

  "generateScript" - {
    import Logic.generateScript

    "returns command if it was provided" in {
      generateScript(Left("ls")) shouldEqual "ls"
    }

    "returns script contents if it was provided" ignore {
      // TODO: testing IO is hard, should extract file's content separately
    }
  }

  "getSSHInstance" - {
    import Logic.getSSHInstance

    def makeInstance(id: String, publicIpOpt: Option[String], privateIp: String, launchDateDayShift: Int): Instance =
      Instance(InstanceId(id), None, publicIpOpt, privateIp, LocalDateTime.now().plusDays(launchDateDayShift).atZone(ZoneId.systemDefault()).toInstant())

    "if given no instances, should be Left" in {
      getSSHInstance(List(), SismUnspecified).isLeft shouldBe true
    }

    "Given one instance" - {
      "If single instance selection mode is SismNewest, returns argument" in {
        val i = makeInstance("X", Some("127.0.0.1"), "10.1.1.10", 0)
        getSSHInstance(List(i), SismNewest).value shouldEqual i
      }

      "If single instance selection mode is SismOldest, returns argument" in {
        val i = makeInstance("X", Some("127.0.0.1"), "10.1.1.10", 0)
        getSSHInstance(List(i), SismOldest).value shouldEqual i
      }

      "If single instance selection mode is SismUnspecified, returns argument" in {
        val i = makeInstance("X", Some("127.0.0.1"), "10.1.1.10", 0)
        getSSHInstance(List(i), SismUnspecified).value shouldEqual i
      }
    }

    "Given more than one instance" - {
      val i1 = makeInstance("X", None, "10.1.1.10", -7)
      val i2 = makeInstance("Y", Some("127.0.0.1"), "10.1.1.10", -1)
      val i3 = makeInstance("Z", Some("127.0.0.1"), "10.1.1.10", 0)

      "If single instance selection mode is SismNewest, selects the newest instance with public IP" in {
        getSSHInstance(List(i1, i2, i3), SismNewest).value shouldEqual i3
      }

      "If single instance selection mode is SismOldest, selects the oldest instance with public IP" in {
        getSSHInstance(List(i1, i2, i3), SismOldest).value shouldEqual i1
      }

      "If single instance selection mode is SismUnspecified, should be Left" in {
        getSSHInstance(List(i1, i2, i3), SismUnspecified).isLeft shouldBe true
      }
    }

  }

  "getIpAddress" - {
    import Logic.getAddress

    def makeInstance(id: String, publicDnsOpt: Option[String], publicIpOpt: Option[String], privateIp: String): Instance =
      Instance(InstanceId(id), publicDnsOpt, publicIpOpt, privateIp, Instant.now())

    val instanceWithPrivateIpOnly = makeInstance("id-e32cb1c9d09d", None, None, "10.1.1.10")
    val instanceWithPublicIpAndPrivateIp = makeInstance("id-a78414cb9b14", None, Some("34.1.1.10"), "10.1.1.10")
    val instanceWithPublicDnsAndPublicIPAndPrivateIp = makeInstance("id-a78414cb9b14", Some("ec2-dnsname"), Some("34.1.1.10"), "10.1.1.10")

    "specifying we want private IP" - {
      "return private if only private exists" in {
        val result = getAddress(instanceWithPrivateIpOnly, onlyUsePrivateIP = true)
        result.value shouldEqual "10.1.1.10"
      }

      "return private if public and private exists" in {
        val result = getAddress(instanceWithPublicIpAndPrivateIp, onlyUsePrivateIP = true)
        result.value shouldEqual "10.1.1.10"
      }
    }

    "not specifying we want private IP" - {
      "return public if it exists" in {
        val result = getAddress(instanceWithPublicIpAndPrivateIp, onlyUsePrivateIP = false)
        result.value shouldEqual "34.1.1.10"
      }

      "return private if no public and no dns" in {
        val result = getAddress(instanceWithPrivateIpOnly, onlyUsePrivateIP = false)
        result.value shouldEqual "10.1.1.10"
      }

      "return public IP if it exists, even if public DNS exists" in {
        val result = getAddress(instanceWithPublicDnsAndPublicIPAndPrivateIp, onlyUsePrivateIP = false)
        result.value shouldEqual "34.1.1.10"
      }
    }
  }

  "getHostKeyEntry" - {
    "when the results are sane" - {
      val results =
        """
          |mfdsafkdlajskl;fjkadls;jfkl;adjs
          |fjdlasjfkld;jskl;
          |ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCgfV3YLgQ6PKhz3NHwFOhQA1ZgBBxYq9duNF0RdHezuBDQAdz51UKssvsIBi74/DuHk7RjaPPMZaC6yNkAuRMTyJk82S93GGow36iMTQD4HTpDuUFloT+SiTrjez/mkS2Wk+fm4brhjo9Xb8M3TXpOn65AXC/3mrB8JrZwx5Y9d2IwEQT1/r6aM1mUo2JJrSQJ1zv+3+ZFKfij1UncjG7rXsUegmR0lmt8bfAkpef1I+LK3CERgxRNCcuM80ptTws3vgxyP9cS60IiF7W1lwuwtvDvZ9LuDnHlrMi+t1t5EvwRm1CE9eLw9+qTQQijBFVjZlXT03St/6IJLMvBazI7 root@ip-10-248-50-51
          |ssh-dss AAAAB3NzaC1kc3MAAACBANIaavW/LDw5eBfY2Gimz5avEEQFDEIn/16LZ5a76VFBZdVgSDwZEhxtclfrdOf6JSe7kyvJL/6vFK6nb4dtgCG3Te3Tj0DU/df13SNokRo165OAe1SASpRw7JqOEdX0fMj1GHCmWZ3HhBtv4zZ1qS0IpSe6VdOZ96JtqMQc6xBvAAAAFQDoegry2E7y3iRPWQnsSDO91YLjiwAAAIAUBdldLO++SverqAgcMbNdNNnvqKgmiwfJ1UJ41tDPjw09WeMKdZ0ht2E1GdWMZXPaO/lPffP8nJlFURhW6Tihw4RW8csdJUrD63EWgXbxVTczqC3I0YWlcT7bCVOm9h0/rXOizdPl4ZtseRZ41DwSpKlSTalKAHlOTONl1DdbjAAAAIAfPZ/qIZdVvQUYeUD7fkbScm3zCj3lXbkleg4BFfBZYHtsscqxowRkJXxLHTFSvhtaKYzEAC6J1rlJRuBdr/fTTD9rpLpz+21Gc0H/2+D5ZlWrsyEfeX03pucCpdhBdQjvC5mexZyevBh7y+vD1KeimyZJMGO5MiBn4+QQ/joxMw== root@ip-10-248-50-51
          |ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBKDHXJ6sXLoKprcNzMDLF6YVroaf5ycshemnS1TJggIA6cf/FW5EmdzUlf+P0QfBdLsqjBVBxQhyWTtHXD4Byds= root@ip-10-248-50-51
          |ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIC1H8FzNqOefx3ApIF1DuY8UqFhzcAAvhAgb8+jkNlKy root@ip-10-248-50-51
          |fndsljfkdls;ajkfla
        """.stripMargin

      "return the host key using the first algorithm when there is a match" in {
        val hostKey = Logic.getHostKeyEntry(Right(CommandResult(results, "", true)), List("ecdsa-sha2-nistp256", "ssh-rsa"))
        hostKey.value shouldBe "ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBKDHXJ6sXLoKprcNzMDLF6YVroaf5ycshemnS1TJggIA6cf/FW5EmdzUlf+P0QfBdLsqjBVBxQhyWTtHXD4Byds= root@ip-10-248-50-51"
      }

      "return the host key using the second algorithm when there is a match for the first" in {
        val hostKey = Logic.getHostKeyEntry(Right(CommandResult(results, "", true)), List("ecdsa-idontexist", "ssh-rsa"))
        hostKey.value shouldBe "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCgfV3YLgQ6PKhz3NHwFOhQA1ZgBBxYq9duNF0RdHezuBDQAdz51UKssvsIBi74/DuHk7RjaPPMZaC6yNkAuRMTyJk82S93GGow36iMTQD4HTpDuUFloT+SiTrjez/mkS2Wk+fm4brhjo9Xb8M3TXpOn65AXC/3mrB8JrZwx5Y9d2IwEQT1/r6aM1mUo2JJrSQJ1zv+3+ZFKfij1UncjG7rXsUegmR0lmt8bfAkpef1I+LK3CERgxRNCcuM80ptTws3vgxyP9cS60IiF7W1lwuwtvDvZ9LuDnHlrMi+t1t5EvwRm1CE9eLw9+qTQQijBFVjZlXT03St/6IJLMvBazI7 root@ip-10-248-50-51"
      }

      "error when there are no suitable host keys" in {
        val hostKey = Logic.getHostKeyEntry(Right(CommandResult(results, "", true)), List("ssh-bob"))
        hostKey.left.value.failures.head.friendlyMessage shouldBe "The remote instance did not return a host key with any preferred algorithm (preferred: List(ssh-bob))"
      }
    }

    "when the query goes wrong" - {
      "error when there are no suitable host keys" in {
        val hostKey = Logic.getHostKeyEntry(Left(ExecutionTimedOut), List("ssh-bob"))
        hostKey.left.value.failures.head.friendlyMessage shouldBe "The remote instance failed to return the host keys within the timeout window (status: ExecutionTimedOut)"
      }
    }
  }
}
