name := "simul"

version := "0.1"

scalaVersion := "2.12.7"

resolvers ++= Seq(
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "SonaType" at "https://oss.sonatype.org/content/groups/public",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
)

updateOptions := updateOptions.value.withLatestSnapshots(false)


libraryDependencies += "io.github.ergoplatform" %% "ergojde" % "1.0"

libraryDependencies ++= Seq(
  "com.squareup.okhttp3" % "mockwebserver" % "3.14.9",
  "org.scalatest" %% "scalatest" % "3.0.8" ,
  "org.scalacheck" %% "scalacheck" % "1.14.+" ,
  "org.mockito" % "mockito-core" % "2.23.4"
)