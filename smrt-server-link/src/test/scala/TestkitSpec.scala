import java.nio.file.Paths

import scala.io.Source
import scala.collection.immutable.Seq
import org.specs2.mutable.Specification
import spray.json._
import com.pacbio.secondary.smrtlink.testkit._

class TestkitSpec extends Specification with TestkitJsonProtocol {
  import com.pacbio.secondary.smrtlink.testkit.TestkitModels._

  val test_cfg = "testkit/testkit_cfg.json"
  val test_cfg2 = "testkit/import_cfg.json"

  "Testkit config" should {
    "Convert mock testkit config object to JSON" in {
      val cfg = MockConfig.makeCfg
      val json = cfg.toJson
      //println(json.prettyPrint)
      val cfg2 = json.convertTo[TestkitConfig]
      true must beEqualTo(cfg.testId == cfg2.testId)
    }
    "Read in testkit cfg from file" in {
      val jsonPath = Paths.get(getClass.getResource(test_cfg).toURI).toString
      val jsonSrc = Source.fromFile(jsonPath).getLines.mkString
      val jsonAst = jsonSrc.parseJson
      val cfg = jsonAst.convertTo[TestkitConfig]
      cfg.testId must beEqualTo("test_job")
      val json = cfg.toJson
      val cfg2 = json.convertTo[TestkitConfig]
      cfg.reportTests(0).rules(0).attrId must beEqualTo(
        cfg2.reportTests(0).rules(0).attrId)
      cfg.reportTests(0).rules(0).value must beEqualTo(
        cfg2.reportTests(0).rules(0).value)
    }
    "Read in testkit cfg with shorthand report rule syntax" in {
      val jsonPath = Paths.get(getClass.getResource(test_cfg2).toURI).toString
      val jsonSrc = Source.fromFile(jsonPath).getLines.mkString
      val jsonAst = jsonSrc.parseJson
      val cfg = jsonAst.convertTo[TestkitConfig]
      cfg.testId must beEqualTo("import_subreads_example")
      val json = cfg.toJson
      //println(json.prettyPrint)
      val cfg2 = json.convertTo[TestkitConfig]
      cfg.reportTests(0).rules(0).attrId must beEqualTo(
        cfg2.reportTests(0).rules(0).attrId)
      cfg.reportTests(0).rules(0).value must beEqualTo(
        cfg2.reportTests(0).rules(0).value)
    }
  }
}
