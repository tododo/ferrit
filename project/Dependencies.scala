import sbt.{ModuleID, _}

object Dependencies {

  object Version {
    val akka = "2.5.8"
    val akkaHttp = "10.0.11"
    val logback = "1.2.3"
    val scalaTest = "3.0.4"
    val scalamock = "4.0.0"
    val jsoup = "1.11.2"
    val slf4j = "1.7.25"
    val mockito = "2.13.0"
    val cassandra = "3.3.2"
    val play = "2.6.8"
    val ning = "1.9.40"
    val akkaHttpCirce = "1.19.0"
  }



  object Library {





    object Akka {
      val testkit: ModuleID = "com.typesafe.akka" %% "akka-testkit" % Version.akka
      val akkaHttp: ModuleID = "com.typesafe.akka" %% "akka-http" % Version.akkaHttp
      val akkaStream: ModuleID = "com.typesafe.akka" %% "akka-stream" % Version.akka
      val akkaActor: ModuleID = "com.typesafe.akka" %% "akka-actor" % Version.akka
      val slf4j: ModuleID = "com.typesafe.akka" %% "akka-slf4j" % Version.akka
      val akkaStreamTestkit: ModuleID = "com.typesafe.akka" %% "akka-stream-testkit" % Version.akka
      val akkaHttpTestkit: ModuleID = "com.typesafe.akka" %% "akka-http-testkit" % Version.akkaHttp
    }
    // https://mvnrepository.com/artifact/com.datastax.cassandra/cassandra-driver-core
    val cassandra: ModuleID = "com.datastax.cassandra" % "cassandra-driver-core" % Version.cassandra

    object Play {
      val json: ModuleID =  "com.typesafe.play" %% "play-json" % Version.play
    }
    // https://mvnrepository.com/artifact/ch.qos.logback/logback-classic
    val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"


    val akkaHttpCirce: ModuleID = "de.heikoseeberger" %% "akka-http-circe" % Version.akkaHttpCirce
    val akkaHttpPlayJsonCirce: ModuleID = "de.heikoseeberger" %% "akka-http-play-json" % Version.akkaHttpCirce
    val jsoup: ModuleID = "org.jsoup" % "jsoup" % Version.jsoup
    val scalaTest: ModuleID = "org.scalatest" %% "scalatest" % Version.scalaTest
    val sl4jApi: ModuleID = "org.slf4j" % "slf4j-api" % Version.slf4j
    val mockito: ModuleID = "org.mockito" % "mockito-core" % Version.mockito
    val ningHttp: ModuleID = "com.ning" % "async-http-client" % Version.ning
    val scalamock: ModuleID = "org.scalamock" %% "scalamock" % Version.scalamock
  }
}
