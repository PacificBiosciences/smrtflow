


assemblyMergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) => {
  case PathList("application.conf") => MergeStrategy.first
  case "logback.xml" => MergeStrategy.first
  case x => old(x)
}
}