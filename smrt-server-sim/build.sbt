import java.io.File

packSettings
//assemblySettings

fork in IntegrationTest := true

fork in Test := true

//parallelExecution in Test := true

//parallelExecution in IntegrationTest := true

//testForkedParallel := true

packGenerateWindowsBatFile := false

packMain := Map(
  "scenario-runner" -> "com.pacbio.simulator.Sim"
)


mainClass in(Compile, run) := Some("com.pacbio.simulator.Sim")
mainClass in assembly := Some("com.pacbio.simulator.Sim")

packGenerateWindowsBatFile := false


val getSmrtLinkAssemblyJar = taskKey[File]("Resolve to SMRT Link Analysis Assembly Jar file")

// FIXME. This shouldn't be harcoded to scala-2.X
//(bd / "smrt-server-link/target/scala-2.11/smrt-server-link-analysis.jar").getAbsoluteFile
getSmrtLinkAssemblyJar := {
  val classpathJars = Build.data((dependencyClasspath in Runtime).value) // Not clear why this is added
  val bd = (baseDirectory in ThisBuild).value
  new File("./smrt-server-link/target/scala-2.11/smrt-server-link-analysis.jar").getAbsoluteFile
}

val smrtLinkServerRunner = taskKey[SmrtLinkServerRunner]("Integration Test SMRT Link Server Runner")

smrtLinkServerRunner := new SmrtLinkAnalysisServerRunner(getSmrtLinkAssemblyJar.value)

testOptions in IntegrationTest += Tests.Setup { () => smrtLinkServerRunner.value.start(streams.value.log)}

testOptions in IntegrationTest += Tests.Cleanup { _ => smrtLinkServerRunner.value.stop(streams.value.log)}

// Util func for starting the SL Server. This requires a manual call
// to smrt-server-link/{compile,assembly}
val runSmrtLinkServer = taskKey[Int]("run SMRT Link Analysis Server")

// Note, this is duplicated in the SmrtLinkServerRunner
runSmrtLinkServer := {
  val smrtServerJar = getSmrtLinkAssemblyJar.value
  val arguments = Seq("-jar", smrtServerJar.getAbsolutePath, "--log-level", "DEBUG", "--log-file", "sim-server.log")
  val mainClass = "com.pacbio.secondary.smrtlink.app.SmrtLinkSmrtServer"
  //val forkOptions = ForkOptions(envVars = Map("KEY" -> "value"))
  val forkOptions = ForkOptions()
  Fork.java(forkOptions, arguments)
}