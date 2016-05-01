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

version in ThisBuild := "0.1.0-SNAPSHOT"

//FIXME(mpkocher)(2016-4-30) This should be com.pacb, PacBio doesn't own pacbio.com
organization in ThisBuild := "com.pacbio"

scalaVersion in ThisBuild := "2.11.7"

// Custom keys for this build.

val gitHeadCommitSha = taskKey[String]("Determines the current git commit SHA")

val makeVersionProperties = taskKey[Seq[File]]("Creates a version.properties file we can find at runtime.")


// Common settings/definitions for the build

def PacBioProject(name: String): Project = (
  Project(name, file(name))
  settings(
    libraryDependencies ++=  Seq(
      //"com.lihaoyi" % "ammonite-repl" % "0.5.7" % "test",
      "org.scalaz" % "scalaz-core_2.11" % "7.0.6",
      "org.specs2" % "specs2_2.11" % "2.4.1-scalaz-7.0.6" % "test")
  )
)

gitHeadCommitSha in ThisBuild := Process("git rev-parse HEAD").lines.head


// Projects in this build

lazy val common = (
  PacBioProject("smrt-common-models")
  settings(
    makeVersionProperties := {
      val propFile = (resourceManaged in Compile).value / "version.properties"
      val content = "version=%s" format (gitHeadCommitSha.value)
      IO.write(propFile, content)
      Seq(propFile)
    },
    resourceGenerators in Compile <+= makeVersionProperties
  )
)

// "pbscala" or pacbio-secondary in perforce repo
lazy val smrtAnalysis = (
  PacBioProject("smrt-analysis")
  dependsOn(common)
  settings()
)

lazy val smrtServerBase = (
  PacBioProject("smrt-server-base")
  dependsOn(common, smrtAnalysis)
  settings()
)

lazy val smrtServerLink = (
  PacBioProject("smrt-server-link")
  dependsOn(common, smrtAnalysis)
  settings()
)

lazy val smrtServerAnalysis = (
  PacBioProject("smrt-server-analysis")
  dependsOn(common, smrtAnalysis, smrtServerLink)
  settings()
)
