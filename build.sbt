// multi-project structure Borrowed/Inspired by
// https://github.com/jsuereth/sbt-in-action-examples/blob/master/chapter3/build.sbt
// Project structure
// - smrt-common-models (i.e., pb-common-models)
// - smrt-analysis (i.e., pbscala)
// - smrt-server-base
// - smrt-server-smrtlink
// - smrt-server-analysis
// - smrt-server-sim


name := "smrtflow"

version in ThisBuild := "0.6.8-SNAPSHOT"

organization in ThisBuild := "pacbio.smrt.smrtflow"

// Seeing a lot of evicted calls
scalaVersion in ThisBuild := "2.11.8"

scalacOptions in ThisBuild := Seq("-unchecked", "-deprecation", "-encoding", "utf8", "-feature")

// NOT WORKING. This should enables Ctl+C to not exit SBT
// cancelable in Global := true

parallelExecution in ThisBuild := true

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

val slickV = "3.1.1"

val bambooBuildNumberEnv = "bamboo_globalBuildNumber"


resolvers in ThisBuild += "mbilski" at "http://dl.bintray.com/mbilski/maven"


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
  "com.github.scopt" %% "scopt" % "3.5.0",
  "com.github.t3hnar" %% "scala-bcrypt" % "2.4",
  "com.github.tototoshi" %% "slick-joda-mapper" % "2.2.0",
  "com.jason-goodwin" %% "authentikat-jwt" % "0.4.1",
  "com.jsuereth" %% "scala-arm" % "1.4",
  "com.lihaoyi" % "ammonite" % "0.8.4" cross CrossVersion.full,
  "com.novocode" % "junit-interface" % "0.10" % "test",
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-slf4j" % akkaV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "com.typesafe.slick" %% "slick" % slickV,
  "com.typesafe.slick" % "slick-hikaricp_2.11" % slickV,
  "commons-cli" % "commons-cli" % "1.2",
  "commons-io" % "commons-io" % "2.4",
  "commons-lang" % "commons-lang" % "2.6",
  "org.apache.commons" % "commons-compress" % "1.13",
  "io.spray" % "spray-can_2.11" % sprayV,
  "io.spray" % "spray-client_2.11" % sprayV,
  "io.spray" % "spray-http_2.11" % sprayV,
  "io.spray" % "spray-io_2.11" % sprayV,
  "io.spray" % "spray-routing-shapeless2_2.11" % sprayV,
  "io.spray" % "spray-testkit_2.11" % sprayV % "test",
  "io.spray" % "spray-util_2.11" % sprayV,
  "io.spray" %% "spray-json" % "1.3.2",
  "joda-time" % "joda-time" % "2.4",
  "net.sourceforge.saxon" % "saxon" % "9.1.0.8",
  "org.apache.avro" % "avro" % "1.8.0",
  "org.apache.commons" % "commons-dbcp2" % "2.0.1",
  "org.eclipse.persistence" % "eclipselink" % "2.6.0",
  "org.eclipse.persistence" % "org.eclipse.persistence.moxy" % "2.6.0",
  "org.flywaydb" % "flyway-core" % "4.0.3",
  "org.ini4j" % "ini4j" % "0.5.4",
  "org.joda" % "joda-convert" % "1.6",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.2",
  "org.scalaj" %% "scalaj-http" % "2.3.0",
  "org.scalaz" % "scalaz-core_2.11" % "7.0.6",
  "org.specs2" % "specs2_2.11" % "2.4.1-scalaz-7.0.6" % "test,it",
  "org.xerial" % "sqlite-jdbc" % "3.8.11.2",
  "org.postgresql" % "postgresql" % "9.4.1212",
  "org.utgenome.thirdparty" % "picard" % "1.86.0",
  "log4j" % "log4j" % "1.2.17",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "4.6.0.201612231935-r",
  "com.github.zafarkhaja" % "java-semver" % "0.9.0",
  "mbilski" % "spray-hmac_2.11" % "1.0.1"
).map(_.exclude("io.spray", "spray-routing_2.11")) // Only spray routing shapeless or spray routing can be used. We'll use the shapeless version for everything


gitHeadCommitSha in ThisBuild := Process("git rev-parse HEAD").lines.head

def getBuildNumber(): Option[Int] = sys.env.get(bambooBuildNumberEnv).map(_.toInt)

// Util to mirror bash clear
def clearConsoleCommand = Command.command("clear") { state =>
  val cr = new jline.console.ConsoleReader()
  cr.clearScreen
  state
}

/**
  * Util func to write the version.properties managed file
  *
  * @param path          Path to the version properties file
  * @param versionString Major.Minor.Patch version string
  * @param gitSha        GIT SHA
  * @param buildNumber   Bamboo Build number or empty string
  * @return
  */
def writeVersionProperties(path: File, versionString: String, gitSha: String, buildNumber: String) = {
  val content = "version=%s\nsha1=%s\nbuildNumber=%s" format(versionString, gitSha, buildNumber)
  IO.write(path, content)
  Seq(path)
}

/**
  * Util func for writing the pacbio-manifest.json Managed file
  *
  * @param path          Path to pacbio-manifest.json
  * @param versionString Major.Minor.Patch version string
  * @param gitSha        Git Short SHA
  * @param buildNumber   Bamboo Build number or empty string
  * @return
  */
def writePacBioManifest(path: File, versionString: String, gitSha: String, buildNumber: String) = {
  val pacbioVersion = "%s+%s%s" format(versionString, buildNumber, gitSha)
  val manifest = s"""
                    |{
                    | "id":"smrtlink_services",
                    | "name": "SMRT Analysis Services",
                    | "version": "$pacbioVersion",
                    | "description":"SMRT Link Analysis Services and Job Orchestration engine",
                    | "dependencies": ["pbsmrtpipe", "sawriter", "gmap", "ngmlr"]
                    |}
              """.stripMargin
  IO.write(path, manifest)
  Seq(path)
}

/**
  * Util to generate a PacBio Project with default Settings
  *
  * @param name subproject name
  * @return
  */
def toPacBioProject(name: String): Project =
  Project(name, file(name))
      .settings(Defaults.itSettings : _*)
      .settings(libraryDependencies ++= baseSettings)
      .settings(coverageEnabled := false)
      .settings(fork in Test := true)
      .settings(fork in IntegrationTest := true)
      .settings(testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "junitxml", "console"))
      .disablePlugins(plugins.JUnitXmlReportPlugin) // MK. Why is this disabled?
      .configs(IntegrationTest)


// Project to use the ammonite repl
lazy val smrtflow = project.in(file("."))
    .settings(moduleName := "smrtflow")
    .settings(publish := {})
    .settings(publishLocal := {})
    .settings(publishArtifact := false)
    .settings(fork in Test := true)
    .settings(fork in IntegrationTest := true)
    .settings(fork in run := true)
    .settings(javaOptions in (Test, console) += "-Xmx4G") // Bump for repl usage
    .settings(libraryDependencies ++= baseSettings)
    .settings(exportJars := true)
    .settings(coverageEnabled := false) // ammonite will disable it because <dataDir> is not defined
    //.settings(parallelExecution in Test := false) // run each Spec sequentially
    .settings(initialCommands in (Test, console) := """ammonite.Main().run()""")
    .settings(Defaults.itSettings : _*)
    .settings(testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "junitxml", "console"))
    .disablePlugins(plugins.JUnitXmlReportPlugin) // MK. Why is this disabled?
    .configs(IntegrationTest)
    .dependsOn(logging, common, smrtAnalysis, smrtServerBase, smrtServerLink, smrtServerSim)
    .aggregate(logging, common, smrtAnalysis, smrtServerBase, smrtServerLink, smrtServerSim)


lazy val logging = toPacBioProject("smrt-server-logging").settings()

lazy val common =
  toPacBioProject("smrt-common-models")
      .settings(
        resourceGenerators in Compile += Def.task {
          val propFile = (resourceManaged in Compile).value / "version.properties"
          writeVersionProperties(propFile, version.value, gitHeadCommitSha.value, getBuildNumber().map(_.toString).getOrElse("Unknown"))
        }.taskValue
      )
      .settings(
        resourceGenerators in Compile += Def.task {
          val propFile = (resourceManaged in Compile).value / "pacbio-manifest.json"
          val sfVersion = version.value.replace("-SNAPSHOT", "")
          val bambooBuildNumber = getBuildNumber().map(number => s"$number.").getOrElse("")
          writePacBioManifest(propFile, sfVersion, gitHeadCommitSha.value.take(7), bambooBuildNumber)
        }.taskValue
      )

lazy val smrtAnalysis =
  toPacBioProject("smrt-analysis")
      .dependsOn(logging, common)
      .settings()

lazy val smrtServerBase =
  toPacBioProject("smrt-server-base")
      .dependsOn(logging, common, smrtAnalysis)
      .settings()

lazy val smrtServerLink =
  toPacBioProject("smrt-server-link")
      .dependsOn(logging, common, smrtAnalysis, smrtServerBase)
      .settings(
        mainClass in assembly := Some("com.pacbio.secondary.smrtlink.app.SecondaryAnalysisServer"),
        assemblyJarName in assembly := "smrt-server-link-analysis.jar")

lazy val smrtServerSim =
  toPacBioProject("smrt-server-sim")
      .dependsOn(logging, common, smrtAnalysis, smrtServerBase, smrtServerLink)
      .settings()
