


assemblyMergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) => {
  case PathList("application.conf") => MergeStrategy.first
  case "logback.xml" => MergeStrategy.first
  case x => old(x)
}
}

packSettings

packMain := Map(
  "get-smrt-server-status" -> "com.pacbio.common.tools.GetStatusApp",
  "pbservice" -> "com.pacbio.common.tools.PbServiceApp"
)
