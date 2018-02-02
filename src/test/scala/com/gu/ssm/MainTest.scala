package com.gu.ssm

import org.scalatest.{EitherValues, FreeSpec, Matchers}
import com.gu.ssm.Main.computeIncorrectInstances


class MainTest extends FreeSpec with Matchers with EitherValues {

  "computeIncorrectInstances" - {
    "should return empty list when matching list of Instance Ids" in {
      val executionTarget = ExecutionTarget(Some(List(InstanceId("i-096fdd62fd48b5b99"))))
      val results = List((InstanceId("i-096fdd62fd48b5b99"), Right(CommandResult("", "")) ))
      computeIncorrectInstances(executionTarget, results) shouldEqual Nil
    }
    "should return incorrectly submitted Instance Id" in {
      val executionTarget = ExecutionTarget(Some(List(InstanceId("i-096fdd62fd48b5b99"),InstanceId("i-12345"))))
      val results = List((InstanceId("i-096fdd62fd48b5b99"), Right(CommandResult("", "")) ))
      computeIncorrectInstances(executionTarget, results) shouldEqual List(InstanceId("i-12345"))
    }
  }

}
