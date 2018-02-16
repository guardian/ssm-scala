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
    val instanceIdX = InstanceId("X")
    val instanceIdY = InstanceId("Y")
    val instanceXWithoutIP = Instance(instanceIdX, None)
    val instanceYWithoutIP = Instance(instanceIdY, None)
    val instanceXWithIP = Instance(instanceIdX, Some("1278.0.0.1"))
    val instanceYWithIP = Instance(instanceIdY, Some("1278.0.0.1"))

    "if given no instances, should be Left" in {
      getSSHInstance(List(), true).isLeft shouldBe true
    }

    "Given one instance" - {

      "Instance is ill-formed" - {
        val oneInstanceWithoutIP = List(instanceXWithoutIP)

        "If takeAnySingleInstance is true, should be Left" in {
          getSSHInstance(oneInstanceWithoutIP, true).isLeft shouldBe true
        }

        "If takeAnySingleInstance is false, should be Left" in {
          getSSHInstance(oneInstanceWithoutIP, false).isLeft shouldBe true
        }
      }

      "Instance is well-formed" - {
        val oneInstanceWithIP = List(instanceXWithIP)

        "If takeAnySingleInstance is true, returns argument" in {
          getSSHInstance(oneInstanceWithIP, true).right.get shouldEqual instanceXWithIP
        }

        "If takeAnySingleInstance is false, should be Left" in {
          getSSHInstance(oneInstanceWithIP, false).right.get shouldEqual instanceXWithIP
        }
      }
    }

    "Given more than one instance" - {

      "All instances are ill-formed" - {
        val twoInstancesWithIP = List(instanceYWithoutIP, instanceXWithoutIP)

        "If takeAnySingleInstance is true, should be Left" in {
          getSSHInstance(twoInstancesWithIP, true).isLeft shouldBe true
        }

        "If takeAnySingleInstance is false, should be Left" in {
          getSSHInstance(twoInstancesWithIP, false).isLeft shouldBe true
        }
      }

      "At least one instance is well formed" - {
        val twoMixedInstances = List(instanceYWithoutIP, instanceXWithIP)

        "If takeAnySingleInstance is true, selects the well-formed instance" in {
          getSSHInstance(twoMixedInstances, true).right.get shouldEqual instanceXWithIP
        }

        "If takeAnySingleInstance is false, should be Left" in {
          getSSHInstance(twoMixedInstances, false).right.get shouldEqual instanceXWithIP
        }
      }

      "All instances are well formed" - {
        val twoInstancesWithIP = List(instanceYWithIP, instanceXWithIP)

        "If takeAnySingleInstance is true, selects the first well-formed instance (in lexicographic order of InstanceId)" in {
          getSSHInstance(twoInstancesWithIP, true).right.get shouldEqual instanceXWithIP
        }

        "If takeAnySingleInstance is false, should be Left" in {
          getSSHInstance(twoInstancesWithIP, false).isLeft shouldBe true
        }
      }
    }
  }
}
