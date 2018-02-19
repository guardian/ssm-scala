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
      getSSHInstance(List(), None).isLeft shouldBe true
    }

    "Given one instance" - {

      "Instance is ill-formed should be Left" in {
        val oneInstanceWithoutIP = List(instanceXWithoutIP)
        getSSHInstance(oneInstanceWithoutIP, None).isLeft shouldBe true
      }

      "Instance is well-formed, should return argument in all cases" - {
        val oneInstanceWithIP = List(instanceXWithIP)

        "If single instance selection mode is None, returns argument" in {
          getSSHInstance(oneInstanceWithIP,  None).right.get shouldEqual instanceXWithIP
        }

        "If single instance selection mode is Some(SismNewest), returns argument" in {
          getSSHInstance(oneInstanceWithIP, Some(SismNewest)).right.get shouldEqual instanceXWithIP
        }

        "If single instance selection mode is Some(SismOldest), returns argument" in {
          getSSHInstance(oneInstanceWithIP, Some(SismOldest)).right.get shouldEqual instanceXWithIP
        }
      }
    }

    "Given more than one instance" - {

      "All instances are ill-formed" - {
        val twoInstancesWithoutIP = List(instanceYWithoutIP, instanceXWithoutIP)

        "should be Left" in {
          getSSHInstance(twoInstancesWithoutIP, None).isLeft shouldBe true
        }
      }

      "At least one instance is well formed" - {
        val twoMixedInstances = List(instanceYWithoutIP, instanceXWithIP)

        "If single instance selection mode is None, should be Left" in {
          getSSHInstance(twoMixedInstances, None).right.get shouldEqual instanceXWithIP
        }

        "If single instance selection mode is Some(SismNewest), selects the well-formed instance" in {
          getSSHInstance(twoMixedInstances, Some(SismNewest)).right.get shouldEqual instanceXWithIP
        }

        "If single instance selection mode is Some(SismOldest), selects the well-formed instance" in {
          getSSHInstance(twoMixedInstances, Some(SismOldest)).right.get shouldEqual instanceXWithIP
        }
      }

      "All instances are well formed" - {
        val twoInstancesWithIP = List(instanceYWithIP, instanceXWithIP)

        "If single instance selection mode is none, should be Left" in {
          getSSHInstance(twoInstancesWithIP, None).isLeft shouldBe true
        }

        "If single instance selection mode is Some(SismNewest), selects the instance with the most recent launch DateTime" in {
          getSSHInstance(twoInstancesWithIP, Some(SismNewest)).right.get shouldEqual instanceYWithIP
        }

        "If single instance selection mode is Some(SismOldest), selects the instance with the oldest launch DateTime" in {
          getSSHInstance(twoInstancesWithIP, Some(SismOldest)).right.get shouldEqual instanceXWithIP
        }
      }
    }
  }
}
