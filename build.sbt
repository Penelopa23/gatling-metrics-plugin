name := "gatling-prometheus-plugin"

version := "1.4.0"

scalaVersion := "2.13.12"

organization := "penelopa"

description := "Gatling plugin for Prometheus metrics export with exact VU tracking"

// Dependencies for Prometheus metrics and HTTP client
libraryDependencies ++= Seq(
  "io.prometheus" % "simpleclient" % "0.16.0",
  "io.prometheus" % "simpleclient_common" % "0.16.0",
  "io.prometheus" % "simpleclient_pushgateway" % "0.16.0",
  "org.slf4j" % "slf4j-api" % "1.7.36",
  "org.apache.httpcomponents.client5" % "httpclient5" % "5.2.1",
  "org.xerial.snappy" % "snappy-java" % "1.1.10.5",
  "com.google.protobuf" % "protobuf-java" % "3.21.12",
  "org.scalatest" %% "scalatest" % "3.2.15" % "test",
  
  // Gatling dependencies for AutoChains
  "io.gatling" % "gatling-core" % "3.10.3" % "provided",
  "io.gatling" % "gatling-http" % "3.10.3" % "provided",
  "io.gatling.highcharts" % "gatling-charts-highcharts" % "3.10.3" % "provided"
)

// Compiler options
scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-Ywarn-dead-code",
  "-Ywarn-unused"
)

// Test configuration
Test / parallelExecution := false
Test / logBuffered := false

// Assembly plugin configuration for fat JAR
assembly / assemblyJarName := "gatling-prometheus-plugin-fat.jar"
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "module-info.class" => MergeStrategy.discard
  case x => MergeStrategy.first
}
