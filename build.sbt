scalaVersion := "2.13.10"

name := "ssm-scala"
organization := "com.gu"
version := "2.3.0"

val awsSdkVersion = "1.12.352"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-ssm" % awsSdkVersion,
  "com.amazonaws" % "aws-java-sdk-sts" % awsSdkVersion,
  "com.amazonaws" % "aws-java-sdk-ec2" % awsSdkVersion,
  "com.github.scopt" %% "scopt" % "4.1.0",
  "com.googlecode.lanterna" % "lanterna" % "3.1.1",
  "ch.qos.logback" %  "logback-classic" % "1.4.5",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.14.1",
  "org.bouncycastle" % "bcpkix-jdk18on" % "1.72",
  "org.scalatest" %% "scalatest" % "3.2.14" % Test
)

scalacOptions := Seq("-unchecked", "-deprecation")
assembly / assemblyJarName := "ssm.jar"
