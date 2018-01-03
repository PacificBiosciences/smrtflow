// Necessary for sqlite to not have class loading JNI problem
fork := true

fork in Test := true

// Don't run the test before building the jar
test in assembly := {}

val mainServer = "com.pacbio.secondary.smrtlink.app.SmrtLinkSmrtServer"

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

packMain := Map(
  "smrt-server-link-analysis" -> mainServer,
  "tech-support-bundler" -> "com.pacbio.secondary.smrtlink.tools.TechSupportFileBundlerApp",
  "tech-support-uploader" -> "com.pacbio.secondary.smrtlink.tools.TechSupportUploaderApp",
  "pbservice" -> "com.pacbio.secondary.smrtlink.tools.PbServiceApp",
  "pbtestkit-service-runner" -> "com.pacbio.secondary.smrtlink.testkit.TestkitRunnerApp",
  "smrt-db-tool" -> "com.pacbio.secondary.smrtlink.tools.DatabaseToolApp",
  "amclient" -> "com.pacbio.secondary.smrtlink.tools.AmClientApp",
  "bundler-validate-config" -> "com.pacbio.secondary.smrtlink.tools.BundlerConfigApp",
  "bundler-get-status" -> "com.pacbio.secondary.smrtlink.tools.GetSystemStatusToolApp",
  "bundler-apply-config" -> "com.pacbio.secondary.smrtlink.tools.ApplyConfigToolApp",
  "bundler-set-password" -> "com.pacbio.secondary.smrtlink.tools.SetPasswordToolApp",
  "accept-user-agreement" -> "com.pacbio.secondary.smrtlink.tools.AcceptUserAgreementApp",
  "validate-run-design" -> "com.pacbio.secondary.smrtlink.tools.ValidateRunApp",
  "send-test-email" -> "com.pacbio.secondary.smrtlink.tools.SendTestEmailApp",
  "smrtlink-repl" -> "com.pacbio.secondary.smrtlink.tools.SmrtLinkReplApp", // Start of Analysis Tools
  "fasta-to-reference" -> "com.pacbio.secondary.smrtlink.analysis.tools.FastaToReferenceApp",
  "fasta-to-gmap-reference" -> "com.pacbio.secondary.smrtlink.analysis.tools.FastaToGmapReferenceSetApp",
  "movie-metadata-to-dataset" -> "com.pacbio.secondary.smrtlink.analysis.tools.MovieMetaDataToDataSetApp",
  "movie-metadata-to-dataset-rtc" -> "com.pacbio.secondary.smrtlink.analysis.tools.MovieMetaDataToDataSetRtcApp",
  "validate-dataset" -> "com.pacbio.secondary.smrtlink.analysis.tools.ValidateDataSetApp",
  "merge-datasets" -> "com.pacbio.secondary.smrtlink.analysis.tools.DataSetMergerApp",
  "ds-tools" -> "com.pacbio.secondary.smrtlink.analysis.tools.PbDataSetToolsApp",
  "smrtflow-example-tool" -> "com.pacbio.secondary.smrtlink.analysis.tools.ExampleToolApp",
  "smrtflow-example-subparser-tool" -> "com.pacbio.secondary.smrtlink.analysis.tools.ExampleSubParserToolApp"
)

packGenerateWindowsBatFile := false
