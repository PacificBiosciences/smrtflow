import java.nio.file.Paths

import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.PipelineTemplateViewRule
import com.pacbio.secondary.smrtlink.analysis.jobs.SecondaryJobJsonProtocol
import com.pacbio.secondary.smrtlink.analysis.pipelines.{PipelineTemplateViewRulesLoader, PipelineTemplateAvroLoader}
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable.Specification
import spray.json._

/**
 *
 * Created by mkocher on 9/19/15.
 */
class PipelineTemplateViewRulesSpec extends Specification with SecondaryJobJsonProtocol with LazyLogging {

  sequential

  val RESOURCE_DIR = "pipeline-template-view-rules"

  def getTestResource(name: String) = getClass.getResource(s"$RESOURCE_DIR/$name")

  "Test pipeline loading from Avro file" should {
    "Smoke test for pipeline" in {
      val name = "pipeline-template-rules-01.avro"
      val path = getTestResource(name)

      val p = Paths.get(path.toURI)
      val pipelineTemplateViewRules = PipelineTemplateViewRulesLoader.loadFrom(p)
      // this is the json representation
      logger.info(s"Pipeline template $pipelineTemplateViewRules")

      pipelineTemplateViewRules.getId.toString mustEqual "pbsmrtpipe.pipelines.sa3_sat"
    }
    "Load avro templates from dir" in {
      val path = getClass.getResource(RESOURCE_DIR)
      val p = Paths.get(path.toURI)
      logger.info(s"Loading pipeline templates from $p")
      val pts = PipelineTemplateViewRulesLoader.loadFromDir(p)
      pts.length mustEqual 1
    }
    "Load JSON File" in {
      val name = "pipeline-template-view-rules-01.json"
      val px = getTestResource(name)
      val xs = scala.io.Source.fromURI(px.toURI).mkString
      val jx = xs.parseJson
      val ptvr = jx.convertTo[PipelineTemplateViewRule]
      1 mustEqual 1
    }
  }
}
