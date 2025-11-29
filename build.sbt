import scala.collection.immutable.Seq

name := "dexy"

version := "0.1"

scalaVersion := "2.13.12"

resolvers ++= Seq(
  "Bintray" at "https://jcenter.bintray.com/", //for org.ethereum % leveldbjni-all
  "Typesafe maven releases" at "https://dl.bintray.com/typesafe/maven-releases/",
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "SonaType" at "https://oss.sonatype.org/content/groups/public",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
)

libraryDependencies ++= Seq(
  "org.scalaj" %% "scalaj-http" % "2.4.2",
  "org.ergoplatform" %% "ergo-appkit" % "5.0.4",
  "org.ergoplatform" %% "kiosk" % "1.0.1",
  "org.ergoplatform" %% "ergo-core" % "5.0.20",
  "org.ergoplatform" %% "ergo-wallet" % "5.0.20",
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
