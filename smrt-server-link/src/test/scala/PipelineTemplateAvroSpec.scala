import java.nio.file.Paths

import com.pacbio.secondary.smrtlink.analysis.jobs.SecondaryJobJsonProtocol
import com.pacbio.secondary.smrtlink.analysis.pipelines.PipelineTemplateAvroLoader
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

/**
  * Sanity test for loading PipelineTemplates from avro
  * @author mkocher
  */
class PipelineTemplateAvroSpec
    extends Specification
    with SecondaryJobJsonProtocol
    with LazyLogging {

  sequential

  "Test pipeline loading from Avro file" should {
    "Smoke test for pipeline" in {
      val name =
        "pipeline-templates-avro/pbsmrtpipe.pipelines.sa3_sat_pipeline_template.avro"
      val path = getClass.getResource(name)

      val p = Paths.get(path.toURI)
      val pipelineTemplate = PipelineTemplateAvroLoader.loadFrom(p)
      // this is the json representation
      logger.info(s"Pipeline template $pipelineTemplate")

      pipelineTemplate.getId.toString mustEqual "pbsmrtpipe.pipelines.sa3_sat"
    }
    "Load avro templates from dir" in {
      val name = "pipeline-templates-avro"
      val path = getClass.getResource(name)
      val p = Paths.get(path.toURI)
      logger.info(s"Loading pipeline templates from $p")
      val pts = PipelineTemplateAvroLoader.loadFromDir(p)
      pts.length mustEqual 2
    }
  }
}
