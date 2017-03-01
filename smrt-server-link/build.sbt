// Necessary for sqlite to not have class loading JNI problem
fork := true

// Don't run the test before building the jar
test in assembly := {}

Revolver.settings

mainClass in (Compile, run) := Some("com.pacbio.secondary.smrtlink.app.SecondaryAnalysisServer")

assemblyMergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) => {
  case PathList("application.conf") => MergeStrategy.first
  case p if p.endsWith("eclipse.inf") => MergeStrategy.first
  case PathList("org", "slf4j", xs @ _*) => MergeStrategy.first
  case PathList("org", "eclipse", xs @ _*) => MergeStrategy.first
  case "logback.xml" => MergeStrategy.first
  case "plugin.properties" => MergeStrategy.first
  case x => old(x)
}
}

packSettings

packMain := Map(
  "smrt-server-link" -> "com.pacbio.secondary.smrtlink.app.SmrtLinkSmrtServer", // Remove this. There is no longer a concept of an "Analysis"-less SL
  "smrt-server-analysis" -> "com.pacbio.secondary.smrtlink.app.SecondaryAnalysisServer",
  "smrt-server-events" -> "com.pacbio.secondary.smrtlink.app.SmrtEventServerApp",
  "tech-support-bundler" -> "com.pacbio.secondary.smrtlink.tools.TechSupportFileBundlerApp",
  "pbservice" -> "com.pacbio.secondary.smrtlink.tools.PbServiceApp",
  "pbtestkit-service-runner" -> "com.pacbio.secondary.smrtlink.testkit.TestkitRunnerApp",
  "smrt-db-tool" -> "com.pacbio.secondary.smrtlink.tools.DatabaseToolApp",
  "amclient" -> "com.pacbio.secondary.smrtlink.tools.AmClientApp",
  "bundler-migrate-legacy-db" -> "com.pacbio.secondary.smrtlink.database.legacy.SqliteToPostgresConverterApp",
  "bundler-migrate-legacy-config" -> "com.pacbio.secondary.smrtlink.tools.LegacyConvertConfigJsonToolApp",
  "bundler-validate-config" -> "com.pacbio.secondary.smrtlink.tools.BundlerConfigApp",
  "accept-user-agreement" -> "com.pacbio.secondary.smrtlink.tools.AcceptUserAgreementApp",
  "validate-run-design" -> "com.pacbio.secondary.smrtlink.tools.ValidateRunApp"
)


packGenerateWindowsBatFile := false