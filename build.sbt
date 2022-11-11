scalaVersion := "2.12.17"

name := "ssm-scala"
organization := "com.gu"
version := "2.3.0"

val awsSdkVersion = "1.12.339"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-ssm" % awsSdkVersion,
  "com.amazonaws" % "aws-java-sdk-sts" % awsSdkVersion,
  "com.amazonaws" % "aws-java-sdk-ec2" % awsSdkVersion,
  "com.github.scopt" %% "scopt" % "4.1.0",
  "com.googlecode.lanterna" % "lanterna" % "3.0.0",
  "ch.qos.logback" %  "logback-classic" % "1.2.11",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.13.0",
  "org.bouncycastle" % "bcpkix-jdk15on" % "1.70",
  "org.scalatest" %% "scalatest" % "3.2.14" % Test
)

scalacOptions := Seq("-unchecked", "-deprecation")
assembly / assemblyJarName := "ssm.jar"
