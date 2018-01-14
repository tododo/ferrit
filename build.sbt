import Dependencies.Library
import sbt._

lazy val ferrit2 =
  Project(id = "ferrit", base = file("."))
    .configs(IntegrationTest)
    .settings(
      Defaults.itSettings,
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
