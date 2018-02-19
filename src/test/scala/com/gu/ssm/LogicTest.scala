package com.gu.ssm

import org.scalatest.{EitherValues, FreeSpec, Matchers}


class LogicTest extends FreeSpec with Matchers with EitherValues {
  "extractSASTags" - {
    import Logic.extractSASTags

    "extracts stack app and stage from valid input" in {
      val expected = AppStackStage("app", "stack", "stage")
      extractSASTags("app,stack,stage").right.value shouldEqual expected
    }

    "provides error message if only two pieces of info are provided" in {
      extractSASTags("abc,def").isLeft shouldEqual true
    }

    "provides error message if only one piece of info is provided" in {
      extractSASTags("abc").isLeft shouldEqual true
    }

    "provides error if nothing is provided" in {
      extractSASTags("").isLeft shouldEqual true
    }

    "returns error if too much info is provided" in {
      extractSASTags("a,b,c,d").isLeft shouldEqual true
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

  "getRelevantInstance" - {
    import Logic.getSSHInstance
    import java.text.SimpleDateFormat

    val sip = Some("127.0.0.1")

    val formatter = new SimpleDateFormat("yyyy-MM-d HH:mm:ss")
    val dateOld = formatter.parse("2018-02-17 13:00:14")
    val dateNew = formatter.parse("2018-02-17 15:05:22")

    val instanceIdX = InstanceId("X")
    val instanceIdY = InstanceId("Y")
    val instanceXWithoutIP = Instance(instanceIdX, None, dateOld)
    val instanceYWithoutIP = Instance(instanceIdY, None, dateNew)
    val instanceXWithIP = Instance(instanceIdX, sip, dateOld)
    val instanceYWithIP = Instance(instanceIdY, sip, dateNew)

    "if given no instances, should be Left" in {
      getSSHInstance(List(), SismUnspecified).isLeft shouldBe true
    }

    "Given one instance" - {

      "Instance is ill-formed should be Left" in {
        val oneInstanceWithoutIP = List(instanceXWithoutIP)
        getSSHInstance(oneInstanceWithoutIP, SismUnspecified).isLeft shouldBe true
      }

      "Instance is well-formed, should return argument in all cases" - {
        val oneInstanceWithIP = List(instanceXWithIP)

        "If single instance selection mode is SismNewest, returns argument" in {
          getSSHInstance(oneInstanceWithIP, SismNewest).right.get shouldEqual instanceXWithIP
        }

        "If single instance selection mode is SismOldest, returns argument" in {
          getSSHInstance(oneInstanceWithIP, SismOldest).right.get shouldEqual instanceXWithIP
        }

        "If single instance selection mode is SismUnspecified, returns argument" in {
          getSSHInstance(oneInstanceWithIP,  SismUnspecified).right.get shouldEqual instanceXWithIP
        }
      }
    }

    "Given more than one instance" - {

      "All instances are ill-formed" - {
        val twoInstancesWithoutIP = List(instanceYWithoutIP, instanceXWithoutIP)

        "should be Left" in {
          getSSHInstance(twoInstancesWithoutIP, SismUnspecified).isLeft shouldBe true
        }
      }

      "At least one instance is well formed" - {
        val twoMixedInstances = List(instanceYWithoutIP, instanceXWithIP)

        "If single instance selection mode is SismNewest, selects the well-formed instance" in {
          getSSHInstance(twoMixedInstances, SismNewest).right.get shouldEqual instanceXWithIP
        }

        "If single instance selection mode is SismOldest, selects the well-formed instance" in {
          getSSHInstance(twoMixedInstances, SismOldest).right.get shouldEqual instanceXWithIP
        }

        "If single instance selection mode is SismUnspecified, should be Left" in {
          getSSHInstance(twoMixedInstances, SismUnspecified).right.get shouldEqual instanceXWithIP
        }
      }

      "All instances are well formed" - {
        val twoInstancesWithIP = List(instanceYWithIP, instanceXWithIP)

        "If single instance selection mode is SismNewest, selects the instance with the most recent launch DateTime" in {
          getSSHInstance(twoInstancesWithIP, SismNewest).right.get shouldEqual instanceYWithIP
        }

        "If single instance selection mode is SismOldest, selects the instance with the oldest launch DateTime" in {
          getSSHInstance(twoInstancesWithIP, SismOldest).right.get shouldEqual instanceXWithIP
        }

        "If single instance selection mode is SismUnspecified, should be Left" in {
          getSSHInstance(twoInstancesWithIP, SismUnspecified).isLeft shouldBe true
        }
      }
    }
  }
}
