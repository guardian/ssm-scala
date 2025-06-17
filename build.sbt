name := "ssm-scala"
organization := "com.gu"
version := "3.7.1"

// be sure to also update this in the `generate-executable.sh` script
scalaVersion := "3.7.1"

val awsSdkVersion = "1.12.784"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-ssm" % awsSdkVersion,
  "com.amazonaws" % "aws-java-sdk-sts" % awsSdkVersion,
  "com.amazonaws" % "aws-java-sdk-ec2" % awsSdkVersion,
  "com.amazonaws" % "aws-java-sdk-rds" % awsSdkVersion,
  "com.github.scopt" %% "scopt" % "4.1.0",
  "com.googlecode.lanterna" % "lanterna" % "3.1.3",
  "ch.qos.logback" %  "logback-classic" % "1.5.18",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.19.1",
  "org.bouncycastle" % "bcpkix-jdk18on" % "1.81",
  "org.scalatest" %% "scalatest" % "3.2.19" % Test
)

// Required as jackson causes a merge issue with sbt-assembly
// See: https://github.com/sbt/sbt-assembly/issues/391
assemblyMergeStrategy := {
  case PathList("META-INF", _*) => MergeStrategy.discard
  case _                        => MergeStrategy.first
}
assemblyJarName := "ssm.jar"

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-release:11",
)
