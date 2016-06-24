
import java.nio.file.Paths

import scala.io.Source
import scala.collection.immutable.Seq

import org.specs2.mutable.Specification

import spray.json._

import com.pacbio.secondary.smrtserver.testkit._

class TestkitSpec extends Specification with TestkitJsonProtocol {
  import TestkitModels._

  val test_cfg = "testkit/testkit_cfg.json"

  "Testkit config" should {
    "Convert testkit config object to JSON" in {
      val entryPoints = Seq(
        EntryPointPath("eid_subread", Paths.get("/path/to/subreadset.xml")),
        EntryPointPath("eid_ref_dataset", Paths.get("/path/to/referenceset.xml")))
      val reportTests = Seq(ReportTestRules(
        "example_report",
        Seq(
          ReportAttributeLongRule("mapped_reads_n", 100, "gt"),
          ReportAttributeDoubleRule("concordance", 0.85, "ge"),
          ReportAttributeStringRule("instrument", "54006"))))
      val cfg = TestkitConfig(
        "test_job",
        "pbsmrtpipe",
        "simple test config",
        Some("pbsmrtpipe.pipelines.sa3_sat"),
        None,
        Some("preset.xml"),
        entryPoints,
        reportTests)
      val json = cfg.toJson
      println(json.prettyPrint)
      val cfg2 = json.convertTo[TestkitConfig]
      true must beEqualTo(cfg.jobName == cfg2.jobName)
      val jsonPath = Paths.get(getClass.getResource(test_cfg).toURI).toString
      val jsonSrc = Source.fromFile(jsonPath).getLines.mkString
      val jsonAst = jsonSrc.parseJson
      val cfg3 = jsonAst.convertTo[TestkitConfig]
      true must beEqualTo(cfg.jobName == cfg3.jobName)
    }
  }
}
