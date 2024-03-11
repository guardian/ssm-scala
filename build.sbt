scalaVersion := "3.3.3"

name := "ssm-scala"
organization := "com.gu"
version := "3.5.0"

val awsSdkVersion = "1.12.667"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-ssm" % awsSdkVersion,
  "com.amazonaws" % "aws-java-sdk-sts" % awsSdkVersion,
  "com.amazonaws" % "aws-java-sdk-ec2" % awsSdkVersion,
  "com.amazonaws" % "aws-java-sdk-rds" % awsSdkVersion,
  "com.github.scopt" %% "scopt" % "4.1.0",
  "com.googlecode.lanterna" % "lanterna" % "3.1.2",
  "ch.qos.logback" %  "logback-classic" % "1.5.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.16.1",
  "org.bouncycastle" % "bcpkix-jdk18on" % "1.77",
  "org.scalatest" %% "scalatest" % "3.2.18" % Test
)

// Required as jackson causes a merge issue with sbt-assembly
// See: https://github.com/sbt/sbt-assembly/issues/391
assemblyMergeStrategy := {
  case PathList("META-INF", _*) => MergeStrategy.discard
  case _                        => MergeStrategy.first
}
assemblyJarName := "ssm.jar"

scalacOptions := Seq("-unchecked", "-deprecation")

