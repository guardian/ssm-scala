package com.gu.ssm

import org.scalatest.{EitherValues, FreeSpec, Matchers}
import java.time.{LocalDateTime, ZoneId}

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

    def makeInstance(id: String, ipOpt:Option[String], launchDateDayShift: Int): Instance =
      Instance(InstanceId(id), ipOpt, LocalDateTime.now().plusDays(launchDateDayShift).atZone(ZoneId.systemDefault()).toInstant())

    "if given no instances, should be Left" in {
      getSSHInstance(List(), SismUnspecified).isLeft shouldBe true
    }

    "Given one instance" - {

      "Instance is ill-formed should be Left" in {
        val i = makeInstance("X", None, 0)
        getSSHInstance(List(i), SismUnspecified).isLeft shouldBe true
      }

      "Instance is well-formed, should return argument in all cases" - {

        "If single instance selection mode is SismNewest, returns argument" in {
          val i = makeInstance("X", Some("127.0.0.1"), 0)
          getSSHInstance(List(i), SismNewest).right.get shouldEqual i
        }

        "If single instance selection mode is SismOldest, returns argument" in {
          val i = makeInstance("X", Some("127.0.0.1"), 0)
          getSSHInstance(List(i), SismOldest).right.get shouldEqual i
        }

        "If single instance selection mode is SismUnspecified, returns argument" in {
          val i = makeInstance("X", Some("127.0.0.1"), 0)
          getSSHInstance(List(i),  SismUnspecified).right.get shouldEqual i
        }
      }
    }

    "Given more than one instance" - {

      "All instances are ill-formed, should be Left" in {
        val i1 = makeInstance("X", None, -7)
        val i2 = makeInstance("Y", None, 0)
        getSSHInstance(List(i1, i2), SismUnspecified).isLeft shouldBe true
      }

      "Multiple instances are well formed" - {
        val i1 = makeInstance("X", None, -7)
        val i2 = makeInstance("Y", Some("127.0.0.1"), -1)
        val i3 = makeInstance("Z", Some("127.0.0.1"), 0)

        "If single instance selection mode is SismNewest, selects the newest well-formed instance" in {
          getSSHInstance(List(i1, i2, i3), SismNewest).right.get shouldEqual i3
        }

        "If single instance selection mode is SismOldest, selects the oldest well-formed instance" in {
          getSSHInstance(List(i1, i2, i3), SismOldest).right.get shouldEqual i2
        }

        "If single instance selection mode is SismUnspecified, should be Left" in {
          getSSHInstance(List(i1, i2, i3), SismUnspecified).isLeft shouldBe true
        }
      }
    }
  }
}
