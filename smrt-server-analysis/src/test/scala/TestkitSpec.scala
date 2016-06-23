
import java.nio.file.Paths

import org.specs2.mutable.Specification

import spray.json._

import com.pacbio.secondary.smrtserver.testkit._

class TestkitSpec extends Specification with TestkitJsonProtocol {
  import TestkitModels._


  "Testkit config" should {
    "Convert testkit config object to JSON" in {
      val entryPoints = Seq(
        EntryPointPath("eid_subread", Paths.get("/path/to/subreadset.xml")),
        EntryPointPath("eid_ref_dataset", Paths.get("/path/to/referenceset.xml")))
      val reportTests = Seq(ReportTestRules(
        "example_report",
        Seq(
          ReportAttributeLongRule("mapped_reads_n", "gt", 100),
          ReportAttributeDoubleRule("concordance", "ge", 0.85),
          ReportAttributeStringRule("instrument", "54006"))))
      val cfg = TestkitConfig(
        "test_job",
        "pbsmrtpipe",
        "simple test config",
        Some("pbsmrtpipe.pipelines.sa3_sat"),
        None,
        Some("preset.xml"),
        None,
        entryPoints,
        reportTests)
      val json = cfg.toJson
      println(json.prettyPrint)
      val cfg2 = json.convertTo[TestkitConfig]
      true must beEqualTo(cfg.jobName == cfg2.jobName)
    }
  }
}
