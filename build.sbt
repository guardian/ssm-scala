name := "ssm-scala"
organization := "com.gu"
version := "3.8.1"

// be sure to also update this in the `generate-executable.sh` script
scalaVersion := "3.7.3"

// Enable BuildInfo plugin to generate version information
enablePlugins(BuildInfoPlugin)
buildInfoKeys := Seq[BuildInfoKey](name, version)
buildInfoPackage := "com.gu.ssm"

val awsSdkVersion = "2.35.11"

libraryDependencies ++= Seq(
  "software.amazon.awssdk" % "ssm" % awsSdkVersion,
  "software.amazon.awssdk" % "sts" % awsSdkVersion,
  "software.amazon.awssdk" % "ec2" % awsSdkVersion,
  "software.amazon.awssdk" % "rds" % awsSdkVersion,
  "com.github.scopt" %% "scopt" % "4.1.0",
  "com.googlecode.lanterna" % "lanterna" % "3.1.3",
  "ch.qos.logback" %  "logback-classic" % "1.5.20",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.6",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.20.1",
  "org.bouncycastle" % "bcpkix-jdk18on" % "1.82",
  "org.scalatest" %% "scalatest" % "3.2.19" % Test
)

// Discard required as jackson causes a merge issue with sbt-assembly,
// while we need to keep services for AWS SDK.
// See: https://github.com/sbt/sbt-assembly/issues/391,
// https://github.com/aws/aws-sdk-java-v2/issues/446
assemblyMergeStrategy := {
  case PathList("META-INF", "services", _*) => MergeStrategy.deduplicate
  case PathList("META-INF", _*)             => MergeStrategy.discard
  case _                                    => MergeStrategy.first
}
assemblyJarName := "ssm.jar"

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-release:11",
)
