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
  // kiosk is provided as an unmanaged jar in lib/ (see README "Vendored Kiosk"): master of
  // github.com/ergoplatform/kiosk @ 1e7aaa3 plus two dexy patches; 1.0.2 is unavailable
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

// scrypto snapshot that sigma-state 5.0.13 points to was purged from Sonatype; force the identical release
dependencyOverrides += "org.scorexfoundation" %% "scrypto" % "2.3.0"

// lib/ contains vendored kiosk jars for both 2.12 and 2.13 (see README "Vendored Kiosk");
// put only the one matching the build's Scala version on the classpath
Compile / unmanagedJars := (Compile / unmanagedJars).value.filter { j =>
  !j.data.getName.matches("kiosk_2\\.1[23](-sources)?\\.jar") ||
    j.data.getName.contains(s"_${scalaBinaryVersion.value}")
}

// lib/ vendors kiosk for both Scala 2.12 (for external consumers) and 2.13; keep only the jar
// matching this build's binary version on the classpath (plus the sources jars for IDEs)
Compile / unmanagedJars := (Compile / unmanagedJars).value.filter { j =>
  val name = j.data.getName
  name.endsWith("-sources.jar") || name.contains(s"_${scalaBinaryVersion.value}")
}

// sigmastate class initialization is not safe under parallel test suites on this toolchain
// (circular <clinit> across SType$/Values$/SCollection$ deadlocks); run suites sequentially
Test / parallelExecution := false
