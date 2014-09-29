import AssemblyKeys._

assemblySettings

name := "warsjawa-ms-spray"

version := "1.0"

scalaVersion := "2.11.2"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

resolvers += "Typesafe repository releases" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "spray repo" at "http://repo.spray.io/"

libraryDependencies ++= {
  val akkaV = "2.3.6"
  val sprayV = "1.3.2-20140909"
  val sprayJsonV = "1.2.6"
  val reactivemongoV = "0.11.0-SNAPSHOT"
  val rediscalaV = "1.3.1"
  Seq(
    "io.spray" %% "spray-can" % sprayV,
    "io.spray" %% "spray-routing" % sprayV,
    "io.spray" %% "spray-json" % sprayJsonV,
    "io.spray" %% "spray-caching" % sprayV,
    "io.spray" %% "spray-client" % sprayV,
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-cluster" % akkaV,
    "com.typesafe.akka" %% "akka-kernel" % akkaV,
    "com.typesafe.akka" %% "akka-remote" % akkaV,
    "com.typesafe.akka" %% "akka-persistence-experimental" % akkaV,
    "com.etaty.rediscala" %% "rediscala" % rediscalaV,
    "org.reactivemongo" %% "reactivemongo" % reactivemongoV
  )
}

Revolver.settings
