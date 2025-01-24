package com.gu.ssm

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class UITest extends AnyFreeSpec with Matchers {
  "hasAnyCommandFailed" - {
    "returns false if no commands failed" in {
      val command = CommandResult("", "", commandFailed = false)
      UI.hasAnyCommandFailed(
        List(InstanceId("test") -> Right(command))
      ) shouldBe false
    }

    "returns true if a single command failed" in {
      val command = CommandResult("", "", commandFailed = true)
      UI.hasAnyCommandFailed(
        List(InstanceId("test") -> Right(command))
      ) shouldBe true
    }

    "returns true if at least one command failed" in {
      val commands = List(
        InstanceId("test1") -> Right(
          CommandResult("", "", commandFailed = true)
        ),
        InstanceId("test2") -> Right(
          CommandResult("", "", commandFailed = false)
        )
      )

      UI.hasAnyCommandFailed(commands) shouldBe true
    }
  }
}
