package com.gu.ssm

import org.scalatest.{EitherValues, FreeSpec, Matchers}


class MainTest extends FreeSpec with Matchers with EitherValues {

  "get Single Instance" - {
    import Main.getSingleInstance

    "More than one instance" in {
      getSingleInstance(List(Instance(InstanceId("X"), None), Instance(InstanceId("Y"), None))).isLeft shouldBe true
    }

    "Exactly one instance" in {
      getSingleInstance(List(Instance(InstanceId("X"), None))).isRight shouldBe true
    }
  }

}
