import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.pipelines.PipelineUtils
import org.specs2.mutable.Specification

class PipelineUtilsSpec extends Specification{

  val defaultEngineOptions = Seq[PipelineBaseOption]()

  val rsPipelineTemplate = {
    val taskOptions: Seq[PipelineBaseOption] = Seq(
      PipelineIntOption("id-a", "name-a", 1234, "Description-A"),
      PipelineStrOption("id-b", "name-b", "value-b", "Description-B"),
      PipelineDoubleOption("id-c", "name-c", 4567.89, "Description-C"))

    val entryPoints = Seq[EntryPoint]()
    val tags = Seq("dev", "example")
    val presets = Seq[PipelineTemplatePreset]()
    PipelineTemplate("pbsmrtpipe.pipelines.sa3_resequencing", "Name", "Desc", "0.1.0",
      defaultEngineOptions, taskOptions, entryPoints, tags, presets)
  }

  // Loaded from a preset xml with a correctly typed option ('id-c')
  val preset1 = {
    val taskOptions = Seq(PipelineStrOption("id-a", "name-a", "99999", "Description-A"),
      PipelineStrOption("id-b", "name-b", "value-preset-b", "Description-B"),
      PipelineDoubleOption("id-c", "new-name-c", 9999.99, "Descrption-new-C")
    )
      PipelineTemplatePreset("preset-01", rsPipelineTemplate.id, defaultEngineOptions, taskOptions)
  }

  "Test " should {
    "Simple load/merge Pipeline Presets" in {

      val pipelineTemplate = PipelineUtils.updatePipelinePreset(rsPipelineTemplate, Seq(preset1))
//      println("Processed Pipeline")
//      println(pipelineTemplate)
//      println("Preset")
//      println(pipelineTemplate.presets)

      val px = pipelineTemplate.presets.headOption.map(x => x.taskOptions.filter(_.id == "id-a"))
//      println(px)
      px must beSome[Seq[PipelineBaseOption]]
    }
  }
}
