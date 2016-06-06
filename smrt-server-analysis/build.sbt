// Don't run the test before building the jar
test in assembly := {}

mainClass in (Compile, run) := Some("com.pacbio.secondary.smrtserver.appcomponents.SecondaryAnalysisServer")

//parallelExecution in Test := false

// Necessary for sqlite to not have class loading JNI problem
//fork := true

//Revolver.settings

assemblyMergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) => {
  case PathList("application.conf") => MergeStrategy.first
  case p if p.endsWith("eclipse.inf") => MergeStrategy.first
  case PathList("org", "slf4j", xs @ _*) => MergeStrategy.first
  case PathList("org", "eclipse", xs @ _*) => MergeStrategy.first
  case "logback.xml" => MergeStrategy.first
  case x => old(x)
}
}

packSettings

packMain := Map(
  "pbservice" -> "com.pacbio.secondary.smrtserver.tools.PbServiceApp",
  "pbtestkit-service-runner" -> "com.pacbio.secondary.smrtserver.tools.TestkitRunnerApp"
)
