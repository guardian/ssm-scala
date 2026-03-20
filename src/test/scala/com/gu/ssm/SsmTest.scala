package com.gu.ssm

import org.scalacheck.{Gen, Shrink}
import org.scalatest.TryValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import com.gu.ssm.InstanceResolutionResult.ResolutionError

import scala.util.{Failure, Success, Try}

class SsmTest extends AnyFreeSpec with Matchers with TryValues with ScalaCheckPropertyChecks {
  import Fixtures.*

  "resolveInstanceStrategy" - {
    "InstanceId" - {
      "should succeed with InstanceId strategy when only instance is given" in {
        forAll(genInstanceId) { instanceId =>
          val result =
            Ssm.resolveInstanceStrategy(Some(instanceId), None, newest = false, oldest = false)
          result shouldEqual Success(InstanceResolutionStrategy.InstanceId(instanceId))
        }
      }
    }

    "TagDiscovery" - {
      "should succeed with Single strategy when tags are given and neither newest nor oldest" in {
        forAll(genTagsAndStr) { case (expectedTags, tagsStr) =>
          val result =
            Ssm.resolveInstanceStrategy(None, Some(tagsStr), newest = false, oldest = false)
          result shouldEqual Success(
            InstanceResolutionStrategy.TagDiscovery(expectedTags, TagDiscoveryStrategy.Single)
          )
        }
      }

      "should succeed with Newest strategy when tags and newest are given" in {
        forAll(genTagsAndStr) { case (expectedTags, tagsStr) =>
          val result =
            Ssm.resolveInstanceStrategy(None, Some(tagsStr), newest = true, oldest = false)
          result shouldEqual Success(
            InstanceResolutionStrategy.TagDiscovery(expectedTags, TagDiscoveryStrategy.Newest)
          )
        }
      }

      "should succeed with Oldest strategy when tags and oldest are given" in {
        forAll(genTagsAndStr) { case (expectedTags, tagsStr) =>
          val result =
            Ssm.resolveInstanceStrategy(None, Some(tagsStr), newest = false, oldest = true)
          result shouldEqual Success(
            InstanceResolutionStrategy.TagDiscovery(expectedTags, TagDiscoveryStrategy.Oldest)
          )
        }
      }
    }

    "Failure cases" - {
      "should fail when both newest and oldest are given" in {
        forAll(genTagsAndStr) { case (_, tagsStr) =>
          val result =
            Ssm.resolveInstanceStrategy(None, Some(tagsStr), newest = true, oldest = true)
          result shouldBe a[Failure[_]]
        }
      }

      "should fail when both instance and tags are given" in {
        forAll(genInstanceId, genTagsAndStr, Gen.oneOf(true, false), Gen.oneOf(true, false)) {
          case (instanceId, (_, tagsStr), newest, oldest) =>
            val result =
              Ssm.resolveInstanceStrategy(Some(instanceId), Some(tagsStr), newest, oldest)
            result shouldBe a[Failure[_]]
        }
      }

      "should fail when neither instance nor tags are given" in {
        forAll(Gen.oneOf(true, false), Gen.oneOf(true, false)) { (newest, oldest) =>
          val result = Ssm.resolveInstanceStrategy(None, None, newest, oldest)
          result shouldBe a[Failure[_]]
        }
      }

      "should fail when tags string is empty" in {
        // oldest is always false here; newest=true && oldest=true would be a different failure
        // path (the guard), so we keep oldest fixed to isolate the empty-string Failure from parseTags
        forAll(Gen.oneOf(true, false), Gen.const(false)) { (newest, oldest) =>
          val result = Ssm.resolveInstanceStrategy(None, Some(""), newest, oldest)
          result shouldBe a[Failure[_]]
        }
      }
    }
  }

  "resolveInstance" - {
    "InstanceId strategy" - {
      "should always resolve to the given instance ID without consulting the resolver" in {
        forAll(genInstanceId) { instanceId =>
          // prove the resolver is never called using an instance that will fail the test
          val failInstanceResolver = new InstanceManager {
            override def findMatchingInstances(
                instanceTags: InstanceTags
            ): Try[Either[ResolutionError, List[InstanceInfo]]] =
              fail("Resolver should not be called for InstanceId strategy")

            override def handoffSession(resolutionResult: InstanceResolutionResult): Try[Int] =
              fail("Handoff should not be called in this test")
          }

          val strategy = InstanceResolutionStrategy.InstanceId(instanceId)
          val result   = Ssm.resolveInstance(strategy, failInstanceResolver)
          result shouldEqual Success(InstanceResolutionResult.ResolvedInstance(instanceId))
        }
      }
    }

    "TagDiscovery / Single instance strategy" - {
      "should return NoInstancesFound when the resolver returns no instances" in {
        forAll(genInstanceTags) { tags =>
          val strategy = InstanceResolutionStrategy.TagDiscovery(tags, TagDiscoveryStrategy.Single)
          val result   = Ssm.resolveInstance(strategy, new StubInstanceManager(Nil))
          result shouldEqual Success(InstanceResolutionResult.NoInstancesFound(tags))
        }
      }

      "should resolve to that instance when the resolver returns exactly one instance" in {
        forAll(genInstanceTags, genInstanceInfo) { (tags, instance) =>
          val strategy = InstanceResolutionStrategy.TagDiscovery(tags, TagDiscoveryStrategy.Single)
          val result   = Ssm.resolveInstance(strategy, new StubInstanceManager(List(instance)))
          result shouldEqual Success(InstanceResolutionResult.ResolvedInstance(instance.id))
        }
      }

      "should return MultipleInstancesFound when the resolver returns more than one instance" in {
        forAll(genInstanceTags, genMultipleInstanceList) { (tags, instances) =>
          val strategy = InstanceResolutionStrategy.TagDiscovery(tags, TagDiscoveryStrategy.Single)
          val result   = Ssm.resolveInstance(strategy, new StubInstanceManager(instances))
          result shouldEqual Success(
            InstanceResolutionResult.MultipleInstancesFound(tags, instances)
          )
        }
      }

      "should propagate a ResolutionError from the instance manager" in {
        forAll(genInstanceTags) { tags =>
          val resolutionError: ResolutionError = ResolutionError("something went wrong", tags)
          val strategy = InstanceResolutionStrategy.TagDiscovery(tags, TagDiscoveryStrategy.Single)
          val result   = Ssm.resolveInstance(strategy, new FailingInstanceManager(resolutionError))
          result shouldEqual Success(resolutionError)
        }
      }
    }

    "TagDiscovery / Newest strategy" - {
      "should return NoInstancesFound when the resolver returns no instances" in {
        forAll(genInstanceTags) { tags =>
          val strategy = InstanceResolutionStrategy.TagDiscovery(tags, TagDiscoveryStrategy.Newest)
          val result   = Ssm.resolveInstance(strategy, new StubInstanceManager(Nil))
          result shouldEqual Success(InstanceResolutionResult.NoInstancesFound(tags))
        }
      }

      "should resolve to the instance with the latest launch time" in {
        forAll(genInstanceTags, genNonEmptyInstanceList) { (tags, instances) =>
          val strategy = InstanceResolutionStrategy.TagDiscovery(tags, TagDiscoveryStrategy.Newest)
          val result   = Ssm.resolveInstance(strategy, new StubInstanceManager(instances))
          val newestId = instances.maxBy(_.launchTime).id
          result shouldEqual Success(InstanceResolutionResult.ResolvedInstance(newestId))
        }
      }
    }

    "TagDiscovery / Oldest strategy" - {
      "should return NoInstancesFound when the resolver returns no instances" in {
        forAll(genInstanceTags) { tags =>
          val strategy = InstanceResolutionStrategy.TagDiscovery(tags, TagDiscoveryStrategy.Oldest)
          val result   = Ssm.resolveInstance(strategy, new StubInstanceManager(Nil))
          result shouldEqual Success(InstanceResolutionResult.NoInstancesFound(tags))
        }
      }

      "should resolve to the instance with the earliest launch time" in {
        forAll(genInstanceTags, genNonEmptyInstanceList) { (tags, instances) =>
          val strategy = InstanceResolutionStrategy.TagDiscovery(tags, TagDiscoveryStrategy.Oldest)
          val result   = Ssm.resolveInstance(strategy, new StubInstanceManager(instances))
          val oldestId = instances.minBy(_.launchTime).id
          result shouldEqual Success(InstanceResolutionResult.ResolvedInstance(oldestId))
        }
      }
    }
  }

  "parseTags" - {
    "should parse valid Stack,Stage and App tags" in {
      forAll(genTagStr, genTagStr, genTagStr) { (app, stack, stage) =>
        whenever(app.nonEmpty && stack.nonEmpty && stage.nonEmpty) {
          val tagsStr  = List(app, stack, stage).mkString(",")
          val expected = InstanceTags(app, Some((stack, Some(stage))))
          Ssm.parseTags(tagsStr) shouldEqual Success(expected)
        }
      }
    }

    "should parse valid App and Stack tags" in {
      forAll(genTagStr, genTagStr) { (app, stack) =>
        whenever(app.nonEmpty && stack.nonEmpty) {
          val tagsStr  = List(app, stack).mkString(",")
          val expected = InstanceTags(app, Some((stack, None)))
          Ssm.parseTags(tagsStr) shouldEqual Success(expected)
        }
      }
    }

    "should parse valid App tag only" in {
      forAll(genTagStr) { app =>
        whenever(app.nonEmpty) {
          val tagsStr  = app
          val expected = InstanceTags(app, None)
          Ssm.parseTags(tagsStr) shouldEqual Success(expected)
        }
      }
    }

    "should return Failure for an empty string" in {
      Ssm.parseTags("") shouldBe a[Failure[_]]
    }
  }
}
