packSettings

packMain := Map(
  "scenario-runner" -> "com.pacbio.simulator.Sim"
)

mainClass in(Compile, run) := Some("com.pacbio.simulator.Sim")
mainClass in assembly := Some("com.pacbio.simulator.Sim")

packGenerateWindowsBatFile := false
