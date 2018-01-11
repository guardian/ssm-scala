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
}
