package com.gu.ssm

import org.scalatest.{EitherValues, FreeSpec, Matchers}
import com.gu.ssm.Main.computeIncorrectInstances


class MainTest extends FreeSpec with Matchers with EitherValues {

  "computeIncorrectInstances" - {
    "compute correct difference 1" in {
      val executionTarget = ExecutionTarget(Some(List(InstanceId("i-096fdd62fd48b5b99"))))
      val results = List((InstanceId("i-096fdd62fd48b5b99"), Right(CommandResult("", "")) ))
      computeIncorrectInstances(executionTarget, results) shouldEqual Nil
    }
    "compute correct difference 2" in {
      val executionTarget = ExecutionTarget(Some(List(InstanceId("i-096fdd62fd48b5b99"),InstanceId("i-12345"))))
      val results = List((InstanceId("i-096fdd62fd48b5b99"), Right(CommandResult("", "")) ))
      computeIncorrectInstances(executionTarget, results) shouldEqual List(InstanceId("i-12345"))
    }
  }

}
