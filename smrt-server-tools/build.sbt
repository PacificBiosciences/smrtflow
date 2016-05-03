
initialCommands in (Test, console) := """ammonite.repl.Main.run("")"""

packSettings

packMain := Map(
  "get-status" -> "com.pacbio.secondary.smrttools.tools.GetStatusApp"
)
