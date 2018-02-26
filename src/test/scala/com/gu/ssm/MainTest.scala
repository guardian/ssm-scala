package com.gu.ssm

import org.scalatest.{EitherValues, FreeSpec, Matchers}
import com.gu.ssm.Logic.computeIncorrectInstances
import com.gu.ssm.model.{InstanceId, InstanceIds}


class MainTest extends FreeSpec with Matchers with EitherValues {

  "computeIncorrectInstances" - {
    "should return empty list when matching list of Instance Ids" in {
      val executionTarget = InstanceIds(List(InstanceId("i-096fdd62fd48b5b99")))
      val instanceIds = List(InstanceId("i-096fdd62fd48b5b99"))
      computeIncorrectInstances(executionTarget, instanceIds) shouldEqual Nil
    }
    "should return incorrectly submitted Instance Id" in {
      val executionTarget = InstanceIds(List(InstanceId("i-096fdd62fd48b5b99"),InstanceId("i-12345")))
      val instanceIds = List(InstanceId("i-096fdd62fd48b5b99"))
      computeIncorrectInstances(executionTarget, instanceIds) shouldEqual List(InstanceId("i-12345"))
    }
  }

}
