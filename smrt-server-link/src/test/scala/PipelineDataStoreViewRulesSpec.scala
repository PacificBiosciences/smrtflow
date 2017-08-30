import java.nio.file.{Path, Paths}

import com.pacbio.secondary.smrtlink.analysis.pbsmrtpipe.PbsmrtpipeConstants
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{DataStoreFileViewRule, PipelineDataStoreViewRules}
import com.pacbio.secondary.smrtlink.analysis.jobs.SecondaryJobJsonProtocol
import com.pacbio.secondary.smrtlink.analysis.pipelines.PipelineDataStoreViewRulesDao
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable.Specification
import spray.json._

import scala.io.Source

/**
  * Created by mkocher on 8/18/16.
  */
class PipelineDataStoreViewRulesSpec extends Specification with SecondaryJobJsonProtocol with LazyLogging {

  args(skipAll = scala.util.Properties.envOrNone(PbsmrtpipeConstants.ENV_BUNDLE_DIR).isEmpty)

  val RESOURCE_DIR = "pipeline-datastore-view-rules"

  def getTestResource(name: String): Path = {
    val xs = s"$RESOURCE_DIR/$name"
    // println(s"Attempting to load/get $xs")
    Paths.get(getClass.getResource(s"$RESOURCE_DIR/$name").toURI)
  }

  "Test loading PipelineDataStoreViewRules" should {
    "Sanity test for loading Test file" in {
      val name = "pipeline_datastore_view_rules-dev_01.json"
      val p = getTestResource(name)
      val x = Source.fromFile(p.toFile).mkString
      // println(x)
      val pipelineTemplateRules = x.parseJson.convertTo[PipelineDataStoreViewRules]
      pipelineTemplateRules.pipelineId must beEqualTo("pbsmrtpipe.pipelines.dev_01")
    }
    "Convert PipelineDataStoreViewRules to Json" in {
      val r1 = DataStoreFileViewRule("source-id", FileTypes.LOG.fileTypeId, isHidden = false, Option("File Name"), Option("File desc"))
      val r2 = DataStoreFileViewRule("source-id-2", FileTypes.FASTA.fileTypeId, isHidden = true, None, None)
      val p = PipelineDataStoreViewRules("pipeline-id", Seq(r1), "0.1.2-fe4516")
      val sx = p.toJson.prettyPrint
      //println(sx)
      (sx.length > 0) must beTrue
    }
    "Load PipelineDataStoreViewRules from JSON string" in {

      val sx =
        """
          |{
          |  "smrtlinkVersion": "1.2.3",
          |  "pipelineId": "pbsmrtpipe.pipelines.dev_01",
          |  "rules": [
          |    {
          |      "fileTypeId": "PacBio.FileTypes.log",
          |      "sourceId": "pbsmrtpipe::pbsmrtpipe.log",
          |      "name": "Name",
          |      "description": "Description",
          |      "isHidden": false
          |    },
          |    {
          |      "fileTypeId": "PacBio.FileTypes.log",
          |      "sourceId": "pbsmrtpipe::master.log",
          |      "isHidden": true,
          |      "description": null,
          |      "name": null
          |    }
          |  ]
          |}
          |
        """.stripMargin

      val pt = sx.parseJson.convertTo[PipelineDataStoreViewRules]
      pt.pipelineId must beEqualTo("pbsmrtpipe.pipelines.dev_01")
    }
  }

  "Test PipelineDataStoreViewRulesDao" should {
    val rules = Seq(
      PipelineDataStoreViewRules("dev_01", Seq.empty[DataStoreFileViewRule], "4.0"),
      PipelineDataStoreViewRules("dev_01", Seq.empty[DataStoreFileViewRule], "5.0"),
      PipelineDataStoreViewRules("dev_01", Seq.empty[DataStoreFileViewRule], "5.1"),
      PipelineDataStoreViewRules("dev_02", Seq.empty[DataStoreFileViewRule], "5.1"))
    val dao = new PipelineDataStoreViewRulesDao(rules)
    "Retrieve rules as list" in {
      dao.getResources.map(r => (r.pipelineId, r.smrtlinkVersion)) must beEqualTo(Seq(("dev_01", "5.1"), ("dev_02", "5.1")))
    }
    "Retrieve individual rules" in {
      dao.getById("dev_02").map(_.pipelineId) must beEqualTo(Some("dev_02"))
      dao.getById("dev_01").map(_.pipelineId) must beEqualTo(Some("dev_01"))
      dao.getById("dev_01").map(_.smrtlinkVersion) must beEqualTo(Some("5.1"))
      dao.getById("dev_01", Some("5.0")).map(_.smrtlinkVersion) must beEqualTo(Some("5.0"))
      dao.getById("dev_01", Some("5.0.0.SNAPSHOT1234")).map(_.smrtlinkVersion) must beEqualTo(Some("5.0"))
    }
    "Failure modes" in {
      dao.getById("dev_03") must beNone
      dao.getById("dev_01", Some("3.0")) must beNone
      val dao2 = new PipelineDataStoreViewRulesDao(Seq.empty[PipelineDataStoreViewRules])
      dao2.getById("dev_01") must beNone
      dao2.getResources.isEmpty must beTrue
    }
  }

}
