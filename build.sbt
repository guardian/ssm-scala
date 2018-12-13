scalaVersion := "2.12.4"

name := "ssm-scala"
organization := "com.gu"
version := "1.4.0"

val awsSdkVersion = "1.11.258"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-ssm" % awsSdkVersion,
  "com.amazonaws" % "aws-java-sdk-sts" % awsSdkVersion,
  "com.amazonaws" % "aws-java-sdk-ec2" % awsSdkVersion,
  "com.github.scopt" %% "scopt" % "3.7.0",
  "com.googlecode.lanterna" % "lanterna" % "3.0.0",
  "ch.qos.logback" %  "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.6",
  "org.bouncycastle" % "bcpkix-jdk15on" % "1.60",
  "org.scalatest" %% "scalatest" % "3.0.4" % Test
)

scalacOptions := Seq("-unchecked", "-deprecation")
assemblyJarName in assembly := "ssm.jar"
