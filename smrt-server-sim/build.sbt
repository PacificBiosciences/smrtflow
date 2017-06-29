import java.io.File

packSettings

fork in IntegrationTest := true

fork in Test := true

parallelExecution in Test := true

parallelExecution in IntegrationTest := true


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
  val classpathJars = Attributed.data((dependencyClasspath in Runtime).value) // Not clear why this is added
  val bd = (baseDirectory in ThisBuild).value
  new File("./smrt-server-link/target/scala-2.11/smrt-server-link-analysis.jar").getAbsoluteFile
}

val smrtLinkServerRunner = taskKey[SmrtLinkServerRunner]("Integration Test SMRT Link Server Runner")

// this can be initialized from assembly.value but this creates a bunch of de-dupe yakk shaving
// Hard coding this value for now
smrtLinkServerRunner := new SmrtLinkAnalysisServerRunner(getSmrtLinkAssemblyJar.value, streams.value.log)


testOptions in IntegrationTest := {
  val r = smrtLinkServerRunner.value
  r.start()
  (testOptions in IntegrationTest).value
}

// Using the above section to start
// Original Version. runner.start() has compile error of ` error: value flatMap is not a member of Unit`
// but runner.stop() doesn't raise a compiler error
executeTests in IntegrationTest <<=
    (executeTests in IntegrationTest, smrtLinkServerRunner) apply {
      (testTask, runnerTask) =>
        for {
          runner <- runnerTask
          //_ <- runner.start()
          result <- testTask.andFinally(runner.stop())
        } yield result
    }
