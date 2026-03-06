ThisBuild / scalaVersion := "3.3.7" // latest LTS
ThisBuild / organization := "com.gu"
ThisBuild / scalacOptions ++= Seq(
  "-encoding",
  "utf8",
  "-feature",
  "-unchecked",
  "-deprecation",
  "-Wunused:all",
  "-Wvalue-discard",
  "-Xfatal-warnings"
)

// shared library versions
val awsSdkVersion        = "2.42.7"
val circeVersion         = "0.14.15"
val sttpVersion          = "4.0.19"
val scalatestVersion     = "3.2.19"
val scalaCheckVersion    = "1.19.0"
val scalatestPlusVersion = "3.2.19.0"

// the packaged CLI application
lazy val ssm = (project in file("."))
  .enablePlugins(JavaAppPackaging, GraalVMNativeImagePlugin)
  .settings(
    name    := "ssm",
    version := "0.1.0",
    libraryDependencies ++= Seq(
      "com.lihaoyi"                   %% "fansi"                % "0.5.1",
      "com.lihaoyi"                   %% "mainargs"             % "0.7.8",
      "software.amazon.awssdk"         % "apache-client"        % awsSdkVersion,
      "com.softwaremill.sttp.client4" %% "core"                 % sttpVersion,
      "com.softwaremill.sttp.client4" %% "circe"                % sttpVersion,
      "io.circe"                      %% "circe-core"           % circeVersion,
      "io.circe"                      %% "circe-generic"        % circeVersion,
      "io.circe"                      %% "circe-parser"         % circeVersion,
      "io.circe"                      %% "circe-generic-extras" % "0.14.5-RC1",
      "software.amazon.awssdk"         % "ec2"                  % awsSdkVersion,
      "org.scalatest"                 %% "scalatest"            % scalatestVersion     % Test,
      "org.scalacheck"                %% "scalacheck"           % scalaCheckVersion    % Test,
      "org.scalatestplus"             %% "scalacheck-1-19"      % scalatestPlusVersion % Test
    ),
    Compile / mainClass  := Some("com.gu.ssm.Main"),
    executableScriptName := "ssm",

    // GraalVM Native Image configuration
    graalVMNativeImageOptions ++= {
      // Use compatibility mode for Linux builds to support older CPUs and containers
      // macOS builds use native optimizations for best performance
      val marchOption = sys.env.get("SSM_ARCHITECTURE") match {
        case Some(arch) if arch.startsWith("linux") => Seq("-march=compatibility")
        case _                                      => Seq.empty
      }

      Seq(
        "--no-fallback",                     // Fail if native image cannot be built
        "--initialize-at-build-time",        // Initialize most classes at build time
        "--enable-url-protocols=http,https", // Enable HTTP/HTTPS
        "-H:+ReportExceptionStackTraces",    // Better error reporting during build
        "-ESSM_RELEASE",      // Bake the CI build version environment variable into the binary
        "-ESSM_ARCHITECTURE", // Bake the architecture environment variable into the binary
        "-ESSM_BRANCH",       // Bake the branch name environment variable into the binary
        "--verbose",          // Show build progress
        // Optimization flags
        "-O2",        // Optimize for performance
        "--gc=serial" // Use serial GC (suitable for CLI tools)
      ) ++ marchOption
    },
    // Output binary name
    GraalVMNativeImage / name := "ssm"
  )
