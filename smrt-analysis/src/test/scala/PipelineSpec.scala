import com.pacbio.secondary.analysis.jobs.JobModels.{PipelineTemplatePreset, EntryPoint, PipelineBaseOption, PipelineTemplate}
import com.pacbio.secondary.analysis.pipelines.PipelineTemplateDao
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

import spray.json._
import com.pacbio.secondary.analysis.jobs.SecondaryJobJsonProtocol

/**
 * Test for all pipeline related specs
 * Created by mkocher on 5/6/15.
 */
class PipelineSpec extends Specification with SecondaryJobJsonProtocol with LazyLogging {

  sequential

  val ROOT_PIPELINE_TEMPLATES = "pipeline-templates"

  val rsPipelineTemplate = {
      val engineOptions = Seq[PipelineBaseOption]()
      val taskOptions = Seq[PipelineBaseOption]()
      val entryPoints = Seq[EntryPoint]()
      val tags = Seq("dev", "example")
      val presets = Seq[PipelineTemplatePreset]()
      PipelineTemplate("pbsmrtpipe.pipelines.sa3_resequencing", "Name", "Desc", "0.1.0", engineOptions, taskOptions, entryPoints, tags, presets)
  }

  "Test pipeline serialization" should {
    "Smoke test for serializing pipeline" in {
      val pipelineId = "pbsmrtpipe.pipelines.sa3_resequencing"
      val pipelineTemplates = Seq(rsPipelineTemplate)
      val dao = new PipelineTemplateDao(pipelineTemplates)
      val p = dao.getPipelineTemplateById(pipelineId)
      p must beSome
      val s = p.get.toJson
      logger.info("Pipeline serialization")
      logger.info(s.prettyPrint)
      //println(s.prettyPrint)

      // load PT back in, but need to translate the JSONSchema task Options back to PipelineOption format
      // This can't do the round-trip because read->write API is stupid
      //val px = p.get.toJson.convertTo[PipelineTemplate]

      val presets = dao.getPresetsFromPipelineTemplateId(pipelineId)
      logger.info(s"Number of pipeline $pipelineId presets ${presets.size}")
      p must beSome
    }

    "JSON Reading in PT " in {
      val name = "pbsmrtpipe.pipelines.sa3_ds_align_pipeline_template.json"
      val resource = s"$ROOT_PIPELINE_TEMPLATES/$name"
      val n = getClass.getResource(resource)
      //println(s"Resource $n")
      val xs = scala.io.Source.fromURI(n.toURI).mkString
      val jx = xs.parseJson
      val px = jx.convertTo[PipelineTemplate]
      px.id must beEqualTo("pbsmrtpipe.pipelines.sa3_align")
      //val topt = px.taskOptions.head
      //topt.value must beEqualTo(70)
      px.tags.length must beEqualTo(1)
    }
  }
}
