import java.io.File

fork in IntegrationTest := true

fork in Test := true

parallelExecution in Test := true

parallelExecution in IntegrationTest := true

packGenerateWindowsBatFile := false

packMain := Map(
  "scenario-runner" -> "com.pacbio.simulator.Sim"
)

mainClass in (Compile, run) := Some("com.pacbio.simulator.Sim")
mainClass in assembly := Some("com.pacbio.simulator.Sim")

packGenerateWindowsBatFile := false

val getSmrtLinkAssemblyJar =
  taskKey[File]("Resolve to SMRT Link Analysis Assembly Jar file")

getSmrtLinkAssemblyJar := {
  val classpathJars = Attributed.data((dependencyClasspath in Runtime).value) // Not clear why this is added
  val bd = (baseDirectory in ThisBuild).value
  new File(
    "./smrt-server-link/target/scala-2.12/smrt-server-link-analysis.jar").getAbsoluteFile
}

val smrtLinkServerRunner =
  taskKey[SmrtLinkServerRunner]("Integration Test SMRT Link Server Runner")

smrtLinkServerRunner := new SmrtLinkAnalysisServerRunner(
  getSmrtLinkAssemblyJar.value,
  streams.value.log)

testOptions in IntegrationTest := {
  val r = smrtLinkServerRunner.value
  r.start()
  (testOptions in IntegrationTest).value
}

val runInt =
  taskKey[String](
    "Start Development server and run integration tests. This requires external setup  and hence, must be called from the makefile via make test-int")

runInt := {
  val r = smrtLinkServerRunner.value
  //r.start()
  // FIXME. This needs to the set the PATH to included exe's from pack and a task dependency on sbt-pack
  (executeTests in IntegrationTest).value
  r.stop()
  "Completed running integration tests"
}
