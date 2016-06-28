
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
      val cfg = MockConfig.makeCfg
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
