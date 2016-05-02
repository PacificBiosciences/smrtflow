// Not sure this is actually working as expected. Getting this error/warning when building the jar.
// no main manifest attribute, in ./smrt-server-analysis/target/scala-2.11/smrt-server-analysis-assembly-0.1.0-SNAPSHOT.jar
val customMainClass = "com.pacbio.secondary.smrtserver.appcomponents.SecondaryAnalysisServer"

mainClass in(Compile, run) := Some(customMainClass)

// Don't run the test before building the jar
test in assembly := {}

parallelExecution in Test := false

// Necessary for sqlite to not have class loading JNI problem
fork := true

Revolver.settings

assemblyMergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) => {
  case PathList("application.conf") => MergeStrategy.first
  case p if p.endsWith("eclipse.inf") => MergeStrategy.first
  case PathList("org", "slf4j", xs @ _*) => MergeStrategy.first
  case PathList("org", "eclipse", xs @ _*) => MergeStrategy.first
  case "logback.xml" => MergeStrategy.first
  case x => old(x)
}
}