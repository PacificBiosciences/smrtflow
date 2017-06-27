// Necessary for sqlite to not have class loading JNI problem
fork := true

fork in Test := true

// Don't run the test before building the jar
test in assembly := {}

val mainServer = "com.pacbio.secondary.smrtlink.app.SecondaryAnalysisServer"

Revolver.settings

mainClass in (Compile, run) := Some(mainServer)

mainClass in assembly := Some(mainServer)

assemblyMergeStrategy in assembly := {
  case PathList("application.conf") => MergeStrategy.first
  case p if p.endsWith("eclipse.inf") => MergeStrategy.first
  case PathList("org", "slf4j", xs @ _*) => MergeStrategy.first
  case PathList("org", "eclipse", xs @ _*) => MergeStrategy.first
  case "logback.xml" => MergeStrategy.first
  case "plugin.properties" => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}


packSettings

packMain := Map(
  "smrt-server-link" -> "com.pacbio.secondary.smrtlink.app.SmrtLinkSmrtServer", // Remove this. There is no longer a concept of an "Analysis"-less SL
  "smrt-server-link-analysis" -> mainServer,
  "smrt-server-eve" -> "com.pacbio.secondary.smrtlink.app.SmrtEventServerApp",
  "smrt-server-data-bundle" -> "com.pacbio.secondary.smrtlink.app.SmrtPacBioDataBundleServerApp",
  "tech-support-bundler" -> "com.pacbio.secondary.smrtlink.tools.TechSupportFileBundlerApp",
  "tech-support-uploader" -> "com.pacbio.secondary.smrtlink.tools.TechSupportUploaderApp",
  "pbservice" -> "com.pacbio.secondary.smrtlink.tools.PbServiceApp",
  "pbtestkit-service-runner" -> "com.pacbio.secondary.smrtlink.testkit.TestkitRunnerApp",
  "smrt-db-tool" -> "com.pacbio.secondary.smrtlink.tools.DatabaseToolApp",
  "amclient" -> "com.pacbio.secondary.smrtlink.tools.AmClientApp",
  "bundler-migrate-legacy-db" -> "com.pacbio.secondary.smrtlink.database.legacy.SqliteToPostgresConverterApp",
  "bundler-migrate-legacy-config" -> "com.pacbio.secondary.smrtlink.tools.LegacyConvertConfigJsonToolApp",
  "bundler-validate-config" -> "com.pacbio.secondary.smrtlink.tools.BundlerConfigApp",
  "bundler-get-status" -> "com.pacbio.secondary.smrtlink.tools.GetSystemStatusToolApp",
  "bundler-apply-config" -> "com.pacbio.secondary.smrtlink.tools.ApplyConfigToolApp",
  "bundler-set-password" -> "com.pacbio.secondary.smrtlink.tools.SetPasswordToolApp",
  "accept-user-agreement" -> "com.pacbio.secondary.smrtlink.tools.AcceptUserAgreementApp",
  "validate-run-design" -> "com.pacbio.secondary.smrtlink.tools.ValidateRunApp",
  "smrtlink-repl" -> "com.pacbio.secondary.smrtlink.tools.SmrtLinkReplApp"

)


packGenerateWindowsBatFile := false