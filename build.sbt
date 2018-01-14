import Dependencies.Library
import sbt._


lazy val commonSettings = Seq(
  name := "ferrit",
  organization := "org.ferrit",
  version := "0.2.0-SNAPSHOT",
  scalaVersion := "2.12.4",
  crossScalaVersions := Seq("2.11.11", "2.12.3"),
  coverageMinimum := 70,
  coverageFailOnMinimum := false,
  coverageHighlighting := true,
  parallelExecution in Test := false
)


lazy val ferrit2 =
  Project(id = "ferrit", base = file("."))
    .configs(IntegrationTest)
    .settings(
      commonSettings,
      Defaults.itSettings,
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
      libraryDependencies ++= Seq(
        Library.Akka.akkaActor,
        Library.Akka.akkaHttp,
        Library.Akka.akkaStream,
        Library.Akka.slf4j,
        Library.Play.json,
        Library.ningHttp,
        Library.jsoup,
        Library.akkaHttpCirce,
        Library.akkaHttpPlayJsonCirce,
        Library.cassandra,
        Library.logback,
        Library.sl4jApi,
        Library.scalaTest % Test,
        Library.scalaTest % IntegrationTest,
        Library.mockito % Test,
        Library.mockito % IntegrationTest,
        Library.Akka.testkit % Test,
        Library.Akka.testkit % IntegrationTest,
        Library.Akka.akkaStreamTestkit % Test,
        Library.Akka.akkaStreamTestkit % IntegrationTest,
        Library.scalamock % Test,
        Library.scalamock % IntegrationTest,
        Library.Akka.akkaHttpTestkit % Test,
        Library.Akka.akkaHttpTestkit % IntegrationTest,
      )
    )
    .enablePlugins(JavaAppPackaging)
    .enablePlugins(DockerPlugin)
    .enablePlugins(ScoverageSbtPlugin)