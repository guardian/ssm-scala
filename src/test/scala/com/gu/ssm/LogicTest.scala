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
    import java.util.Date
    val instanceIdX = InstanceId("X")
    val instanceIdY = InstanceId("Y")
    val instanceXWithoutIP = Instance(instanceIdX, None, new Date())
    val instanceYWithoutIP = Instance(instanceIdY, None, new Date())
    val instanceXWithIP = Instance(instanceIdX, Some("1278.0.0.1"), new Date())
    val instanceYWithIP = Instance(instanceIdY, Some("1278.0.0.1"), new Date())

    "if given no instances, should be Left" in {
      getSSHInstance(List(), Some("any")).isLeft shouldBe true
    }

    "Given one instance" - {

      "Instance is ill-formed" - {
        val oneInstanceWithoutIP = List(instanceXWithoutIP)

        "If singleInstanceSelectionModeOpt is Some(\"any\"), should be Left" in {
          getSSHInstance(oneInstanceWithoutIP, Some("any")).isLeft shouldBe true
        }

        "If singleInstanceSelectionModeOpt is None, should be Left" in {
          getSSHInstance(oneInstanceWithoutIP, None).isLeft shouldBe true
        }
      }

      "Instance is well-formed" - {
        val oneInstanceWithIP = List(instanceXWithIP)

        "IfsingleInstanceSelectionModeOpt is Some(\"any\"), returns argument" in {
          getSSHInstance(oneInstanceWithIP, Some("any")).right.get shouldEqual instanceXWithIP
        }

        "If singleInstanceSelectionModeOpt is None, should be Left" in {
          getSSHInstance(oneInstanceWithIP, None).right.get shouldEqual instanceXWithIP
        }
      }
    }

    "Given more than one instance" - {

      "All instances are ill-formed" - {
        val twoInstancesWithIP = List(instanceYWithoutIP, instanceXWithoutIP)

        "If singleInstanceSelectionModeOpt is Some(\"any\"), should be Left" in {
          getSSHInstance(twoInstancesWithIP, Some("any")).isLeft shouldBe true
        }

        "If singleInstanceSelectionModeOpt is None, should be Left" in {
          getSSHInstance(twoInstancesWithIP, None).isLeft shouldBe true
        }
      }

      "At least one instance is well formed" - {
        val twoMixedInstances = List(instanceYWithoutIP, instanceXWithIP)

        "If singleInstanceSelectionModeOpt is Some(\"any\"), selects the well-formed instance" in {
          getSSHInstance(twoMixedInstances, Some("any")).right.get shouldEqual instanceXWithIP
        }

        "If singleInstanceSelectionModeOpt is None, should be Left" in {
          getSSHInstance(twoMixedInstances, None).right.get shouldEqual instanceXWithIP
        }
      }

      "All instances are well formed" - {
        val twoInstancesWithIP = List(instanceYWithIP, instanceXWithIP)

        "If singleInstanceSelectionModeOpt is Some(\"any\"), selects the first well-formed instance (in lexicographic order of InstanceId)" in {
          getSSHInstance(twoInstancesWithIP, Some("any")).right.get shouldEqual instanceXWithIP
        }

        "If singleInstanceSelectionModeOpt is None, should be Left" in {
          getSSHInstance(twoInstancesWithIP, None).isLeft shouldBe true
        }
      }
    }
  }
}
