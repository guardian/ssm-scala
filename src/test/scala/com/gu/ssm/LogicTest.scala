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
    import Logic.getRelevantInstance
    val instanceIdX = InstanceId("X")
    val instanceIdY = InstanceId("Y")
    val instanceX = Instance(instanceIdX, None)
    val instanceY = Instance(instanceIdY, None)
    "should be Left if given no instances" in {
      getRelevantInstance(List(), true).isLeft shouldBe true
    }
    "with one instance given" - {
      "should return passed argument if takeAnySingleInstance is true" in {
        getRelevantInstance(List(instanceX), true) shouldBe Right(instanceX)
      }
      "should return passed argument if takeAnySingleInstance is false" in {
        getRelevantInstance(List(instanceX), false) shouldBe Right(instanceX)
      }
    }
    "with two instances given" - {
      "should select the first (in lexicographic order of InstanceId) if takeAnySingleInstance is true" in {
        getRelevantInstance(List(instanceX, instanceY), true) shouldBe Right(instanceX)
      }
      "should be Left if takeAnySingleInstance is false" in {
        getRelevantInstance(List(instanceX, instanceY), false).isLeft shouldBe true
      }
    }
  }

}
