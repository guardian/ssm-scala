package com.gu.ssm

import org.scalatest.EitherValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import com.gu.ssm.Logic.computeIncorrectInstances


class MainTest extends AnyFreeSpec with Matchers with EitherValues {

  "computeIncorrectInstances" - {
    "should return empty list when matching list of Instance Ids" in {
      val executionTarget = ExecutionTarget(Some(List(InstanceId("i-096fdd62fd48b5b99"))))
      val instanceIds = List(InstanceId("i-096fdd62fd48b5b99"))
      computeIncorrectInstances(executionTarget, instanceIds) shouldEqual Nil
    }
    "should return incorrectly submitted Instance Id" in {
      val executionTarget = ExecutionTarget(Some(List(InstanceId("i-096fdd62fd48b5b99"),InstanceId("i-12345"))))
      val instanceIds = List(InstanceId("i-096fdd62fd48b5b99"))
      computeIncorrectInstances(executionTarget, instanceIds) shouldEqual List(InstanceId("i-12345"))
    }
  }

}
