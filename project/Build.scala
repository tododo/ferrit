import sbt._
import Keys._

object B extends Build {

  lazy val scalaTest = "org.scalatest" %% "scalatest" % "2.2.4" % "it,test"

  lazy val root =
    Project("root", file("."))
      .configs(IntegrationTest)
      .settings(Defaults.itSettings : _*)
      .settings(libraryDependencies += scalaTest)

}