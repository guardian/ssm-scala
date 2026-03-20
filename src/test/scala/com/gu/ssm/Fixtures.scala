package com.gu.ssm

import org.scalacheck.Gen

import java.time.Instant
import com.gu.ssm.InstanceResolutionResult.ResolutionError

import scala.util.{Success, Try}

object Fixtures {

  /** A test InstanceResolver that always succeeds with the provided instance list.
    *
    * Provides a fixed list of instances for the instance lookup, and always succeeds (with exit
    * code 0) when asked to hand off the session.
    */
  class StubInstanceManager(instances: List[InstanceInfo]) extends InstanceManager {
    def findMatchingInstances(instanceTags: InstanceTags): Try[Either[ResolutionError, List[InstanceInfo]]] =
      Success(Right(instances))
    def handoffSession(resolutionResult: InstanceResolutionResult): Try[Int] =
      Success(0)
  }

  /** A test InstanceResolver that always returns a ResolutionError. */
  class FailingInstanceManager(error: ResolutionError) extends InstanceManager {
    def findMatchingInstances(instanceTags: InstanceTags): Try[Either[ResolutionError, List[InstanceInfo]]] =
      Success(Left(error))
    def handoffSession(resolutionResult: InstanceResolutionResult): Try[Int] =
      Success(0)
  }

  // --- Generators -----------------------------------------------------------

  val genInstanceId: Gen[String] = Gen.identifier

  val genInstant: Gen[Instant] =
    Gen.choose(0L, 4_000_000_000L).map(Instant.ofEpochSecond)

  val genInstanceInfo: Gen[InstanceInfo] =
    for {
      id         <- genInstanceId
      name       <- Gen.identifier
      launchTime <- genInstant
    } yield InstanceInfo(id, name, Map.empty, launchTime)

  val genNonEmptyInstanceList: Gen[List[InstanceInfo]] =
    Gen.nonEmptyListOf(genInstanceInfo)

  val genMultipleInstanceList: Gen[List[InstanceInfo]] =
    Gen.listOfN(2, genInstanceInfo).flatMap { two =>
      Gen.listOf(genInstanceInfo).map(two ++ _)
    }

  val genTagStr: Gen[String] = Gen.identifier.suchThat(_.nonEmpty)

  val genInstanceTags: Gen[InstanceTags] = {
    val generator = for {
      app        <- Gen.identifier
      maybeStack <- Gen.option(Gen.identifier)
      maybeStage <- if (maybeStack.isDefined) Gen.option(Gen.identifier) else Gen.const(None)
    } yield InstanceTags(app, maybeStack.map(_ -> maybeStage))
    generator.suchThat { it =>
      it.app.nonEmpty && it.stack.exists(_.nonEmpty) && it.stage.exists(_.nonEmpty)
    }
  }

  /** A valid comma-separated tag string (App, App,Stack, or App,Stack,Stage) that round-trips
    * through parseTags without error.
    */
  val genTagsAndStr: Gen[(InstanceTags, String)] =
    genInstanceTags.map { tags =>
      val parts = List(Some(tags.app), tags.stack, tags.stage).flatten
      (tags, parts.mkString(","))
    }
}
