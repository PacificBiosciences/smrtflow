// Don't run the test before building the jar
test in assembly := {}

mainClass in (Compile, run) := Some("com.pacbio.secondary.lims.MainSimple")

mainClass in assembly := Some("com.pacbio.secondary.lims.MainSimple")

assemblyMergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) => {
  case PathList("application.conf") => MergeStrategy.first
  case PathList("org", "slf4j", xs@_*) => MergeStrategy.first
  case PathList("org", "eclipse", xs@_*) => MergeStrategy.first
  case p if p.endsWith("eclipse.inf") => MergeStrategy.discard
  case "logback.xml" => MergeStrategy.first
  case x => old(x)
}
}
