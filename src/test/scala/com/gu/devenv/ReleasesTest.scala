package com.gu.devenv

import org.scalacheck.Gen
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class ReleasesTest extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks {
  import Releases.*

  private val testRelease = Release(
    id = 123456L,
    tagName = "20260115-120000",
    assets = List(
      ReleaseAsset(
        id = 1L,
        name = "devenv-20250115-120000-linux-x86_64",
        browserDownloadUrl = "https://example.com/download",
        digest = "sha256:abc123"
      )
    ),
    htmlUrl = "https://github.com/guardian/devenv/releases/tag/20250115-120000"
  )

  "Releases.checkForUpdate" - {
    "development mode" - {
      "returns DevMode when current version is 'dev'" in {
        val result = checkForUpdate(
          currentReleaseVersion = "dev",
          architectureOpt = Some("linux-x86_64"),
          branch = Some("main"),
          latestRelease = testRelease
        )

        result match {
          case UpdateCheckResult.DevMode(release) =>
            release shouldBe testRelease
          case other =>
            fail(s"Expected DevMode but got: $other")
        }
      }

      "returns DevMode when branch is not main" in {
        val genNonMainBranch =
          Gen.alphaNumStr.suchThat(branch => branch.nonEmpty && branch != "main")

        forAll(genNonMainBranch) { branch =>
          val result = checkForUpdate(
            currentReleaseVersion = "20250101-120000",
            architectureOpt = Some("linux-x86_64"),
            branch = Some(branch),
            latestRelease = testRelease
          )

          result match {
            case UpdateCheckResult.DevMode(_) => succeed
            case other                        => fail(s"Expected DevMode but got: $other")
          }
        }
      }

      "returns DevMode when branch is None" in {
        val result = checkForUpdate(
          currentReleaseVersion = "20250101-120000",
          architectureOpt = Some("linux-x86_64"),
          branch = None,
          latestRelease = testRelease
        )

        result match {
          case UpdateCheckResult.DevMode(_) => succeed
          case other                        => fail(s"Expected DevMode but got: $other")
        }
      }
    }

    "up to date" - {
      "returns UpToDate when current version matches latest release" in {
        val result = checkForUpdate(
          currentReleaseVersion = testRelease.tagName,
          architectureOpt = Some("linux-x86_64"),
          branch = Some("main"),
          latestRelease = testRelease
        )

        result shouldBe UpdateCheckResult.UpToDate
      }
    }

    "update available" - {
      "returns UpdateAvailable when newer version exists and compatible asset found" in {
        val result = checkForUpdate(
          currentReleaseVersion = "20250101-120000",
          architectureOpt = Some("linux-x86_64"),
          branch = Some("main"),
          latestRelease = testRelease
        )

        result match {
          case UpdateCheckResult.UpdateAvailable(release, asset) =>
            release shouldBe testRelease
            asset.name shouldBe "devenv-20250115-120000-linux-x86_64"
          case other =>
            fail(s"Expected UpdateAvailable but got: $other")
        }
      }

      "returns NoCompatibleAsset when newer version exists but no matching architecture" in {
        val result = checkForUpdate(
          currentReleaseVersion = "20250101-120000",
          architectureOpt = Some("darwin-aarch64"),
          branch = Some("main"),
          latestRelease = testRelease
        )

        result match {
          case UpdateCheckResult.NoCompatibleAsset(release) =>
            release shouldBe testRelease
          case other =>
            fail(s"Expected NoCompatibleAsset but got: $other")
        }
      }

      "returns NoArchitectureInfo when newer version exists but architecture is None" in {
        val result = checkForUpdate(
          currentReleaseVersion = "20250101-120000",
          architectureOpt = None,
          branch = Some("main"),
          latestRelease = testRelease
        )

        result match {
          case UpdateCheckResult.NoArchitectureInfo(release) =>
            release shouldBe testRelease
          case other =>
            fail(s"Expected NoArchitectureInfo but got: $other")
        }
      }
    }
  }
}
