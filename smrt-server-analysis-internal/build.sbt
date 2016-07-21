
test in assembly := {}

mainClass in (Compile, run) := Some("com.pacbio.secondaryinternal.SecondaryAnalysisInternalServer")


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

// Alias. Need to have a consistent naming model smrt-client-X to mirror the smrt-server-X naming convention
packMain := Map(
  "smrt-client-internal-analysis" -> "com.pacbio.secondaryinternal.tools.InternalAnalysisClientToolApp",
  "smrt-client-slia" -> "com.pacbio.secondaryinternal.tools.InternalAnalysisClientToolApp"

)