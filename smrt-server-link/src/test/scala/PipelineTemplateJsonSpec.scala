import java.nio.file.Paths

import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.SecondaryJobJsonProtocol
import com.pacbio.secondary.smrtlink.analysis.pipelines.JsonPipelineTemplatesLoader
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._
import spray.json._


class PipelineTemplateJsonSpec extends Specification with SecondaryJobJsonProtocol with LazyLogging{

  sequential

  "Test pipeline template JSON serialization" should {
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
    "Test all task option types" in {
      val name = "pipeline-templates/example_pipeline_template_01.json"
      val path = getClass.getResource(name)
      val ppath = Paths.get(path.toURI)
      val pipelineTemplate = JsonPipelineTemplatesLoader.loadFrom(ppath)
      val j = pipelineTemplate.toJson
      val p = j.convertTo[PipelineTemplate]
      val tOpts = p.taskOptions.map(o => (o.id, o)).toMap
      tOpts("pbsmrtpipe.task_options.alpha") must haveClass[PipelineStrOption]
      tOpts("pbsmrtpipe.task_options.beta") must haveClass[PipelineBooleanOption]
      tOpts("pbsmrtpipe.task_options.gamma") must haveClass[PipelineIntOption]
      tOpts("pbsmrtpipe.task_options.delta") must haveClass[PipelineDoubleOption]
      tOpts("pbsmrtpipe.task_options.a") must haveClass[PipelineChoiceStrOption]
      tOpts("pbsmrtpipe.task_options.b") must haveClass[PipelineChoiceIntOption]
      tOpts("pbsmrtpipe.task_options.c") must haveClass[PipelineChoiceDoubleOption]
    }
  }
}
