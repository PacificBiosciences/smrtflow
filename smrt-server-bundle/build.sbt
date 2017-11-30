// Necessary for sqlite to not have class loading JNI problem
fork := true

fork in Test := true

// Don't run the test before building the jar
test in assembly := {}

val mainServer =
  "com.pacbio.secondary.smrtserverbundle.app.BundleUpdateServerApp"

Revolver.settings

mainClass in (Compile, run) := Some(mainServer)

mainClass in assembly := Some(mainServer)

assemblyMergeStrategy in assembly := {
  case PathList("application.conf") => MergeStrategy.first
  case p if p.endsWith("eclipse.inf") => MergeStrategy.first
  case PathList("org", "slf4j", xs @ _ *) => MergeStrategy.first
  case PathList("org", "eclipse", xs @ _ *) => MergeStrategy.first
  case "logback.xml" => MergeStrategy.first
  case "plugin.properties" => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

packSettings

packMain := Map("smrt-server-data-bundle" -> mainServer)

packGenerateWindowsBatFile := false
