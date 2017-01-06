import java.nio.file.Paths

import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

import spray.json._

import com.pacbio.secondary.analysis.pipelines.JsonPipelineTemplatesLoader
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobs.SecondaryJobJsonProtocol

/**
 * Sanity test for loading PipelineTemplates from avro
 * @author mkocher
 */
class PipelineTemplateJsonSpec extends Specification with SecondaryJobJsonProtocol with LazyLogging{

  sequential

  "Test pipeline loading from Json file" should {
    "Smoke test for json pipeline" in {
      val name = "pipeline-templates/pbsmrtpipe.pipelines.sa3_ds_resequencing_fat_pipeline_template.json"
      val path = getClass.getResource(name)

      val p = Paths.get(path.toURI)
      val pipelineTemplate = JsonPipelineTemplatesLoader.loadFrom(p)
      // this is the json representation
      logger.info(s"Pipeline template $pipelineTemplate")

      pipelineTemplate.id.toString mustEqual "pbsmrtpipe.pipelines.sa3_ds_resequencing_fat"
      val j = pipelineTemplate.toJson
      val ptFromJson = j.convertTo[PipelineTemplate]
      ptFromJson.id.toString mustEqual pipelineTemplate.id.toString
    }
    "Load json templates from dir" in {
      val name = "pipeline-templates"
      val path = getClass.getResource(name)
      val p = Paths.get(path.toURI)
      logger.info(s"Loading pipeline templates from $p")
      val pts = JsonPipelineTemplatesLoader.loadFromDir(p)
      pts.length mustEqual 3
    }
  }
}
