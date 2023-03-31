name := "dexy"

version := "0.1"

scalaVersion := "2.12.7"

resolvers ++= Seq(
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "SonaType" at "https://oss.sonatype.org/content/groups/public",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
)

libraryDependencies ++= Seq(
  "io.github.ergoplatform" %% "kiosk" % "1.0",
  "com.squareup.okhttp3" % "mockwebserver" % "3.14.9",
  "org.scalatest" %% "scalatest" % "3.0.8" ,
  "org.scalacheck" %% "scalacheck" % "1.14.+" ,
  "org.mockito" % "mockito-core" % "2.23.4"
)

val circeVersion = "0.12.3"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

libraryDependencies ++= Seq(
  "org.scalaj" %% "scalaj-http" % "2.3.0",
  ("org.ergoplatform" %% "ergo" % "v4.0.13-5251a78b-SNAPSHOT")
    .excludeAll(
      ExclusionRule(organization = "com.typesafe.akka"),
      ExclusionRule(organization = "ch.qos.logback"),
      ExclusionRule(organization = "org.ethereum"),
      ExclusionRule(organization = "javax.xml.bind"),
    ).force(),
)