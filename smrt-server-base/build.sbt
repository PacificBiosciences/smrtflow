// Disable tests before assembly
test in assembly := {}


assemblyMergeStrategy in assembly := {
  case PathList("application.conf") => MergeStrategy.first
  case "logback.xml" => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

packSettings

packMain := Map(
  "get-smrt-server-status" -> "com.pacbio.common.tools.GetStatusApp",
  "pbservice" -> "com.pacbio.common.tools.PbServiceApp"
)
