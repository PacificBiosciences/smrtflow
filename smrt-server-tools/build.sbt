
initialCommands in (Test, console) := """ammonite.repl.Main.run("")"""

packSettings

packMain := Map(
  "get-smrt-server-status" -> "com.pacbio.secondary.smrttools.tools.GetStatusApp",
  "pbservice" -> "com.pacbio.secondary.smrttools.tools.PbServiceApp"
)
