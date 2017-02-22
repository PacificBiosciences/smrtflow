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

version in ThisBuild := "0.4.3-SNAPSHOT"

organization in ThisBuild := "pacbio.smrt.smrtflow"

// Seeing a lot of evicted calls
scalaVersion in ThisBuild := "2.11.8"

scalacOptions in ThisBuild := Seq("-unchecked", "-deprecation", "-encoding", "utf8", "-feature")

parallelExecution in ThisBuild := false

fork in ThisBuild := true

javaOptions in ThisBuild += "-Xms256m"

javaOptions in ThisBuild += "-Xmx8g"

// tmp files are written during testing; cannot be mounted noexec because of sqlite
javaOptions in ThisBuild += "-Djava.io.tmpdir=" + (if (sys.env.get("TMP").isDefined) sys.env("TMP") else "/tmp")

// Custom keys for this build.

val gitHeadCommitSha = taskKey[String]("Determines the current git commit SHA")

val makeVersionProperties = taskKey[Seq[File]]("Creates a version.properties file we can find at runtime.")

val makePacBioComponentManifest = taskKey[Seq[File]]("Creates a pacbio-manifest.json as a managed resource")

val akkaV = "2.3.6"

val sprayV = "1.3.3"

val bambooBuildNumberEnv = "bamboo_buildNumber"

credentials in ThisBuild += Credentials(Path.userHome / ".ivy2" / ".credentials")

publishTo in ThisBuild := {
  val nexus = "http://ossnexus.pacificbiosciences.com/repository/"
  if (isSnapshot.value) Some("Nexus snapshots" at nexus + "maven-snapshots")
  else Some("Nexus releases" at nexus + "maven-releases")
}

// Common settings/definitions for the build
lazy val baseSettings = Seq(
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "com.enragedginger" %% "akka-quartz-scheduler" % "1.4.0-akka-2.3.x",
  "com.github.samtools" % "htsjdk" % "1.129",
  "com.github.fge" % "json-schema-validator" % "2.2.5",
  "com.github.fommil" %% "spray-json-shapeless" % "1.2.0",
  "com.github.nscala-time" %% "nscala-time" % "1.4.0",
  "com.github.scopt" %% "scopt" % "3.4.0",
  "com.github.t3hnar" %% "scala-bcrypt" % "2.4",
  "com.github.tototoshi" %% "slick-joda-mapper" % "2.2.0",
  "com.h2database" % "h2" % "1.4.192",
  "com.jason-goodwin" %% "authentikat-jwt" % "0.4.1",
  "com.jsuereth" %% "scala-arm" % "1.4",
  "com.lihaoyi" % "ammonite" % "0.7.8" % "test" cross CrossVersion.full,
  "com.novocode" % "junit-interface" % "0.10" % "test",
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-slf4j" % akkaV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "com.typesafe.slick" %% "slick" % "3.1.0",
  "com.typesafe.slick" % "slick-hikaricp_2.11" % "3.1.0",
  "com.unboundid" % "unboundid-ldapsdk" % "2.3.3",
  "commons-cli" % "commons-cli" % "1.2",
  "commons-io" % "commons-io" % "2.4",
  "commons-lang" % "commons-lang" % "2.6",
  "org.apache.commons" % "commons-compress" % "1.13",
  "io.spray" % "spray-can_2.11" % sprayV,
  "io.spray" % "spray-client_2.11" % sprayV,
  "io.spray" % "spray-http_2.11" % sprayV,
  "io.spray" % "spray-io_2.11" % sprayV,
  "io.spray" % "spray-routing-shapeless2_2.11" % sprayV,
  "io.spray" % "spray-servlet_2.11" % sprayV,
  "io.spray" % "spray-testkit_2.11" % sprayV % "test",
  "io.spray" % "spray-util_2.11" % sprayV,
  "io.spray" %% "spray-json" % "1.3.2",
  "joda-time" % "joda-time" % "2.4",
  "net.sourceforge.saxon" % "saxon" % "9.1.0.8",
  "org.apache.avro" % "avro" % "1.8.0",
  "org.apache.commons" % "commons-dbcp2" % "2.0.1",
  "org.eclipse.persistence" % "eclipselink" % "2.6.0",
  "org.eclipse.persistence" % "org.eclipse.persistence.moxy" % "2.6.0",
  "org.flywaydb" % "flyway-core" % "4.0",
  "org.ini4j" % "ini4j" % "0.5.4",
  "org.joda" % "joda-convert" % "1.6",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.2",
  "org.scalaj" %% "scalaj-http" % "2.3.0",
  "org.scalaz" % "scalaz-core_2.11" % "7.0.6",
  "org.specs2" % "specs2_2.11" % "2.4.1-scalaz-7.0.6" % "test",
  "org.xerial" % "sqlite-jdbc" % "3.8.11.2",
  "org.postgresql" % "postgresql" % "9.4.1212",
  "org.utgenome.thirdparty" % "picard" % "1.86.0",
  "log4j" % "log4j" % "1.2.17",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "4.6.0.201612231935-r",
  "com.github.zafarkhaja" % "java-semver" % "0.9.0"
)

def PacBioProject(name: String): Project = (
    Project(name, file(name))
        settings (
        libraryDependencies ++= baseSettings
        )
    )
    .disablePlugins(plugins.JUnitXmlReportPlugin)
    .settings(
      testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "junitxml", "console"))


gitHeadCommitSha in ThisBuild := Process("git rev-parse HEAD").lines.head


def getBuildNumber(): Option[Int] = sys.env.get(bambooBuildNumberEnv).map(_.toInt)


// still can't get these to be imported successfully within ammonite on startup
val replImports =
"""
  |import java.util.UUID
  |import akka.actor.ActorSystem
  |import scala.concurrent.duration._
  |import com.pacbio.secondary.smrtserver.client.{AnalysisServiceAccessLayer => Sal}
  |import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
  |import com.pacbio.secondary.analysis.constants._
  |import com.pacbio.secondary.analysis.datasets.io._
""".stripMargin

// Project to use the ammonite repl
lazy val smrtflow = project.in(file("."))
    .settings(moduleName := "smrtflow")
    //.settings(noPublish)
    .settings(publish := {})
    .settings(publishLocal := {})
    .settings(publishArtifact := false)
    .settings(javaOptions in (Test, console) += "-Xmx4G") // Bump for repl usage
    .settings(libraryDependencies ++= baseSettings)
    .settings(coverageEnabled := false) // ammonite will disable it because <dataDir> is not defined
    .settings(parallelExecution in Test := false) // run each Spec sequentially
    .settings(initialCommands in (Test, console) :=
    s"""
       |val welcomeBanner = Some("Welcome to the smrtflow REPL")
       |import ammonite.repl._
       |import ammonite.ops._
       |ammonite.Main("import java.util.UUID", welcomeBanner = welcomeBanner).run()
       |""".stripMargin)
    .dependsOn(logging, common, smrtAnalysis, smrtServerBase, smrtServerLink, smrtServerAnalysis, smrtServerSim)
    .aggregate(logging, common, smrtAnalysis, smrtServerBase, smrtServerLink, smrtServerAnalysis, smrtServerSim)


lazy val logging = PacBioProject("smrt-server-logging")

lazy val common = (
    PacBioProject("smrt-common-models")
        settings(
          makeVersionProperties := {
            val propFile = (resourceManaged in Compile).value / "version.properties"
            val content = "version=%s\nsha1=%s\nbuildNumber=%s" format(version.value, gitHeadCommitSha.value, getBuildNumber().getOrElse("Unknown"))
            IO.write(propFile, content)
            Seq(propFile)
          },
          resourceGenerators in Compile <+= makeVersionProperties
        )
        settings(
          makePacBioComponentManifest := {
            val propFile = (resourceManaged in Compile).value / "pacbio-manifest.json"
            val sfVersion = version.value.replace("-SNAPSHOT", "")
            val bambooBuildNumber = getBuildNumber().map(number => s"$number.").getOrElse("")
            // Generate a version format of {major}.{minor}.{patch}+{build-number}.{short-sha} or
            // {major}.{minor}.{patch}+{short-sha} if the build number is not assigned
            val pacbioVersion = "%s+%s%s" format(sfVersion, bambooBuildNumber, gitHeadCommitSha.value.take(7))
            val manifest = s"""
              |{
              | "id":"smrtlink_services",
              | "name": "SMRT Analysis Services",
              | "version": "$pacbioVersion",
              | "description":"SMRT Link Analysis Services and Job Orchestration engine",
              | "dependencies": ["pbsmrtpipe", "sawriter", "gmap"]
              |}
              """.stripMargin
            IO.write(propFile, manifest)
            Seq(propFile)
          },
          resourceGenerators in Compile <+= makePacBioComponentManifest
        )
    )

// "pbscala" or pacbio-secondary in perforce repo
lazy val smrtAnalysis = (
    PacBioProject("smrt-analysis")
        dependsOn(logging, common)
        settings()
    )

lazy val smrtServerBase = (
    PacBioProject("smrt-server-base")
        dependsOn(logging, common, smrtAnalysis)
        settings()
    )

lazy val smrtServerLink = (
    PacBioProject("smrt-server-link")
        dependsOn(logging, common, smrtAnalysis, smrtServerBase)
        settings()
    )

lazy val smrtServerLims = (
    PacBioProject("smrt-server-lims")
        dependsOn(logging, common, smrtAnalysis, smrtServerBase, smrtServerLink)
        settings ()
    )

lazy val smrtServerAnalysis = (
    PacBioProject("smrt-server-analysis")
        dependsOn(logging, common, smrtAnalysis, smrtServerBase, smrtServerLink)
        settings (mainClass in assembly := Some("com.pacbio.secondary.smrtserver.appcomponents.SecondaryAnalysisServer"))
    )

lazy val smrtServerAnalysisInternal = (
    PacBioProject("smrt-server-analysis-internal")
        dependsOn(logging, common, smrtAnalysis, smrtServerBase, smrtServerLink, smrtServerAnalysis, logging)
        settings (mainClass in assembly := Some("com.pacbio.secondaryinternal.SecondaryAnalysisInternalServer"))
    )

lazy val smrtServerSim = (
    PacBioProject("smrt-server-sim")
        dependsOn(logging, common, smrtAnalysis, smrtServerLink, smrtServerAnalysis)
        settings()
    )

//lazy val root = (project in file(".")).
//  aggregate(common, logging, smrtAnalysis, smrtServerBase, smrtServerLink, smrtServerLims, smrtServerAnalysis, smrtServerAnalysisInternal, smrtServerSim)
