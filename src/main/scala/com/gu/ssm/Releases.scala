package com.gu.ssm

import fansi.{Bold, Color}
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto.*
import sttp.client4.*
import sttp.client4.circe.*
import sttp.client4.httpurlconnection.HttpURLConnectionBackend

import scala.util.{Failure, Success, Try}

/** Handles fetching and parsing release information from the ssm GitHub repository.
  *
  * This is to support the CLI tool's ability to check if it is running the latest version, and to
  * recommend a compatible update, if available.
  *
  * See https://docs.github.com/en/rest/releases/releases?apiVersion=2022-11-28
  */
object Releases {
  private val OWNER = "guardian"
  private val REPO  = "ssm-scala"
  private val API_URL =
    s"https://api.github.com/repos/$OWNER/$REPO/releases/latest"

  // extracts the release name and architecture from ssm's asset name
  private val ReleaseNameParts = """ssm-(\d{8}-\d{6}(?:-dev)?)-(\w+-\w+)""".r

  // this backend works with graalvm
  private val httpBackend = HttpURLConnectionBackend()

  def checkForUpdate(
      currentReleaseVersion: String,
      architectureOpt: Option[String],
      branch: Option[String],
      latestRelease: Release
  ): UpdateCheckResult =
    if (currentReleaseVersion == "dev" || !branch.contains("main")) {
      UpdateCheckResult.DevMode(latestRelease)
    } else {
      isNewerVersion(currentReleaseVersion, latestRelease) match {
        case Some(newerRelease) =>
          architectureOpt match {
            case Some(architecture) =>
              compatibleAsset(architecture, latestRelease) match {
                case Some(asset) =>
                  UpdateCheckResult.UpdateAvailable(newerRelease, asset)
                case None =>
                  UpdateCheckResult.NoCompatibleAsset(newerRelease)
              }
            case None =>
              UpdateCheckResult.NoArchitectureInfo(latestRelease)
          }
        case None =>
          UpdateCheckResult.UpToDate
      }
    }

  def fetchLatestRelease(): Try[Release] = {
    val response = basicRequest
      .get(uri"$API_URL")
      .response(asJson[Release])
      .send(httpBackend)

    response.body.toTry.recoverWith { err =>
      Failure(new Exception(s"Error fetching latest release from GitHub: $err", err))
    }
  }

  def formatUpdateCheckResult(
      result: Try[UpdateCheckResult],
      currentVersion: String,
      architecture: Option[String]
  ): String =
    result match {
      case Success(UpdateCheckResult.UpToDate) =>
        val header  = Bold.On(Color.Green("✅ Up-to-date"))
        val divider = Color.Green("━" * 60)
        val message = Color.Green(s"ssm ${Bold.On(currentVersion)} is the latest version.")
        s"""$header
           |$divider
           |$message
           |""".stripMargin

      case Success(UpdateCheckResult.DevMode(latestRelease)) =>
        val header  = Bold.On(Color.Yellow("✓ Development mode"))
        val divider = Color.Yellow("━" * 60)
        val message =
          Color.Yellow("ssm is in development mode; cannot check for updates.")
        val latest =
          Color.Yellow(s"The latest released version is ${Bold.On(latestRelease.tagName)}")
        s"""$header
           |$divider
           |$message
           |$latest
           |""".stripMargin

      case Success(UpdateCheckResult.UpdateAvailable(newerRelease, asset)) =>
        val header   = Bold.On(Color.Yellow("⬆\uFE0F Update available"))
        val divider  = Color.Yellow("━" * 60)
        val message  = Color.Yellow(s"An update is available")
        val update   = s"$currentVersion → ${Bold.On(newerRelease.tagName)}"
        val release  = Color.Cyan(newerRelease.htmlUrl)
        val download = Color.Cyan(asset.browserDownloadUrl)
        s"""$header
           |$divider
           |$message
           |
           |  $update
           |
           |Release notes and installation instructions:
           |  $release
           |
           |Or download from:
           |  $download
           |""".stripMargin

      case Success(UpdateCheckResult.NoCompatibleAsset(newerRelease)) =>
        val header  = Bold.On(Color.Red("❌ No compatible update"))
        val divider = Color.Red("━" * 60)
        val notice  = Color.Yellow(s"An update is available:")
        val update  = s"$currentVersion → ${Bold.On(newerRelease.tagName)}"
        val detail = Color.Red(
          s"No compatible download was found for: ${Bold.On(architecture.getOrElse("unknown"))}"
        )
        val release = Color.Cyan(newerRelease.htmlUrl)
        s"""$header
           |$divider
           |$notice
           |
           |  $update
           |
           |$detail
           |
           |Release notes:
           |  $release
           |""".stripMargin

      case Success(UpdateCheckResult.NoArchitectureInfo(newerRelease)) =>
        val header  = Bold.On(Color.Red("❔ Cannot verify compatibility"))
        val divider = Color.Red("━" * 60)
        val notice  = Color.Yellow(s"An update is available:")
        val update  = s"$currentVersion → ${Bold.On(newerRelease.tagName)}"
        val detail = Color.Yellow(
          s"""CPU architecture information for your current version is not available.
             |Cannot check for a compatible download.""".stripMargin
        )
        val release = Color.Cyan(newerRelease.htmlUrl)
        s"""$header
           |$divider
           |$notice
           |
           |  $update
           |
           |$detail
           |
           |Release notes:
           |  $release
           |""".stripMargin

      case Failure(exception) =>
        val header  = Bold.On(Color.Red("❌ Update check failed"))
        val divider = Color.Red("━" * 60)
        val message = Color.Red("An error occurred while checking for updates:")
        val error   = Color.Red(exception.getMessage)
        s"""$header
           |$divider
           |$message
           |$error
           |""".stripMargin
    }

  private def isNewerVersion(
      currentReleaseVersion: String,
      latestRelease: Release
  ): Option[Release] =
    if (latestRelease.tagName != currentReleaseVersion) {
      Some(latestRelease)
    } else {
      None
    }

  private def compatibleAsset(
      architecture: String,
      latestRelease: Release
  ): Option[ReleaseAsset] =
    latestRelease.assets.find { asset =>
      asset.name match {
        case ReleaseNameParts(_, arch) =>
          arch == architecture
        case _ =>
          false
      }
    }

  enum UpdateCheckResult(val successful: Boolean) {
    case UpToDate                  extends UpdateCheckResult(successful = true)
    case DevMode(release: Release) extends UpdateCheckResult(successful = true)
    case UpdateAvailable(release: Release, asset: ReleaseAsset)
        extends UpdateCheckResult(successful = true)
    case NoCompatibleAsset(release: Release)  extends UpdateCheckResult(successful = false)
    case NoArchitectureInfo(release: Release) extends UpdateCheckResult(successful = false)
  }

  /** Represents a GitHub release and its assets.
    *
    * These are subsets of GitHub's datastructures that focus in on the relevant fields.
    *
    * https://docs.github.com/en/rest/releases/releases?apiVersion=2022-11-28
    */

  // ensures our derived codecs will convert GitHub's snake case fields to normal Scala names
  given Configuration = Configuration.default.withSnakeCaseMemberNames

  case class Release(
      id: Long,
      tagName: String,
      assets: List[ReleaseAsset],
      htmlUrl: String
  )
  case class ReleaseAsset(
      id: Long,
      name: String,
      browserDownloadUrl: String,
      digest: String
  )
}
