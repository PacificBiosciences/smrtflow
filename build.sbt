// multi-project structure Borrowed/Inspired by
// https://github.com/jsuereth/sbt-in-action-examples/blob/master/chapter3/build.sbt
// Project structure
// - smrt-common-models (i.e., pb-common-models)
// - smrt-analysis (i.e., pbscala)
// - smrt-server-base
// - smrt-server-smrtlink
// - smrt-server-analysis
// - smrt-server-internal-analysis (Add in second pass)
// - smrt-server-simulator (from Paws)


name := "smrtflow"

version in ThisBuild := "0.1.3-SNAPSHOT"

//FIXME(mpkocher)(2016-4-30) This should be com.pacb, PacBio doesn't own pacbio.com
organization in ThisBuild := "com.pacbio"

// Seeing a lot of evicted calls
scalaVersion in ThisBuild := "2.11.8"

scalacOptions in ThisBuild := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

parallelExecution in ThisBuild := false

fork in ThisBuild := true

javaOptions in ThisBuild += "-Xms256m"

javaOptions in ThisBuild += "-Xmx2g"

// Custom keys for this build.

val gitHeadCommitSha = taskKey[String]("Determines the current git commit SHA")

val makeVersionProperties = taskKey[Seq[File]]("Creates a version.properties file we can find at runtime.")

val akkaV = "2.3.6"

val sprayV = "1.3.3"

// Common settings/definitions for the build

def PacBioProject(name: String): Project = (
  Project(name, file(name))
    settings (
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaV,
      "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
      "io.spray" %% "spray-json" % "1.3.2",
      "io.spray" % "spray-client_2.11" % sprayV,
      "com.github.nscala-time" %% "nscala-time" % "1.4.0",
      "joda-time" % "joda-time" % "2.4",
      "org.joda" % "joda-convert" % "1.6",
      "org.scala-lang.modules" %% "scala-xml" % "1.0.2",
      "com.github.scopt" %% "scopt" % "3.4.0",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
      "net.sourceforge.saxon" % "saxon" % "9.1.0.8",
      "org.scalaz" % "scalaz-core_2.11" % "7.0.6",
      "org.specs2" % "specs2_2.11" % "2.4.1-scalaz-7.0.6" % "test",
      "commons-io" % "commons-io" % "2.4",
      "commons-lang" % "commons-lang" % "2.6",
      "commons-cli" % "commons-cli" % "1.2",
      "org.eclipse.persistence" % "eclipselink" % "2.6.0",
      "org.eclipse.persistence" % "org.eclipse.persistence.moxy" % "2.6.0",
      "org.apache.avro" % "avro" % "1.7.7",
      "com.github.broadinstitute" % "picard" % "1.131",
      "com.typesafe.slick" %% "slick" % "3.1.0",
      "com.github.tototoshi" %% "slick-joda-mapper" % "2.2.0",
      // added from bss
      "io.spray" % "spray-io_2.11" % sprayV,
      "io.spray" %% "spray-json" % "1.3.2",
      "io.spray" % "spray-http_2.11" % sprayV,
      "io.spray" % "spray-routing-shapeless2_2.11" % sprayV,
      "io.spray" % "spray-util_2.11" % sprayV,
      "io.spray" % "spray-can_2.11" % sprayV,
      "io.spray" % "spray-servlet_2.11" % sprayV,
      "io.spray" % "spray-testkit_2.11" % sprayV % "test",
      "org.specs2" % "specs2_2.11" % "2.4.1-scalaz-7.0.6" % "test",
      "com.typesafe.akka" %% "akka-actor" % akkaV,
      "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
      "com.typesafe.akka" %% "akka-slf4j" % akkaV,
      "com.github.nscala-time" %% "nscala-time" % "1.4.0",
      "com.github.fge" % "json-schema-validator" % "2.2.5",
      "com.novocode" % "junit-interface" % "0.10" % "test",
      "org.scala-lang.modules" %% "scala-xml" % "1.0.2",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
      "ch.qos.logback" % "logback-classic" % "1.1.7",
      "com.github.t3hnar" %% "scala-bcrypt" % "2.4",
      "com.jason-goodwin" %% "authentikat-jwt" % "0.4.1",
      "com.unboundid" % "unboundid-ldapsdk" % "2.3.3",
      "com.jsuereth" %% "scala-arm" % "1.4",
      "com.enragedginger" %% "akka-quartz-scheduler" % "1.4.0-akka-2.3.x",
      "com.github.fommil" %% "spray-json-shapeless" % "1.2.0",
      "org.scalaj" %% "scalaj-http" % "1.1.5",
      "org.flywaydb" % "flyway-core" % "4.0",
      "com.lihaoyi" % "ammonite-repl" % "0.5.7" % "test" cross CrossVersion.full,
      "org.ini4j" % "ini4j" % "0.5.4",
      // database libraries
      "org.apache.commons" % "commons-dbcp2" % "2.0.1",
      "com.h2database" % "h2" % "1.4.192",
      "org.xerial" % "sqlite-jdbc" % "3.8.11.2"
)
    )
  )
    .disablePlugins (plugins.JUnitXmlReportPlugin)
    .settings(
      testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "junitxml", "console"))


gitHeadCommitSha in ThisBuild := Process("git rev-parse HEAD").lines.head


// Projects in this build

lazy val logging = (
  PacBioProject("smrt-server-logging")
  )

lazy val database = (
  PacBioProject("smrt-server-database")
  dependsOn(logging))


lazy val common = (
  PacBioProject("smrt-common-models")
    settings(
    makeVersionProperties := {
      val propFile = (resourceManaged in Compile).value / "version.properties"
      val content = "version=%s\nsha1=%s" format (version.value, gitHeadCommitSha.value)
      IO.write(propFile, content)
      Seq(propFile)
    },
    resourceGenerators in Compile <+= makeVersionProperties
    )
  )

// "pbscala" or pacbio-secondary in perforce repo
lazy val smrtAnalysis = (
  PacBioProject("smrt-analysis")
    dependsOn(logging, database, common)
    settings()
  )

lazy val smrtServerBase = (
  PacBioProject("smrt-server-base")
    dependsOn(logging, database, common, smrtAnalysis)
    settings()
  )

lazy val smrtServerLink = (
  PacBioProject("smrt-server-link")
    dependsOn(logging, database, common, smrtAnalysis, smrtServerBase)
    settings()
  )

lazy val smrtServerAnalysis = (
  PacBioProject("smrt-server-analysis")
    dependsOn(logging, database, common, smrtAnalysis, smrtServerBase, smrtServerLink)
    settings (mainClass in assembly := Some("com.pacbio.secondary.smrtserver.appcomponents.SecondaryAnalysisServer"))
  )

lazy val smrtServerAnalysisInternal = (
  PacBioProject("smrt-server-analysis-internal")
    dependsOn(logging, database, common, smrtAnalysis, smrtServerBase, smrtServerLink, smrtServerAnalysis, logging)
    settings (mainClass in assembly := Some("com.pacbio.secondaryinternal.SecondaryAnalysisInternalServer"))
  )
