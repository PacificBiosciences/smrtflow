packSettings

packMain := Map(
  "scenario-runner" -> "com.pacbio.simulator.Sim"
)

mainClass in(Compile, run) := Some("com.pacbio.simulator.Sim")
mainClass in assembly := Some("com.pacbio.simulator.Sim")

val playVersion = "2.5.12"
libraryDependencies +=  "com.typesafe.play" %% "play-json" % playVersion