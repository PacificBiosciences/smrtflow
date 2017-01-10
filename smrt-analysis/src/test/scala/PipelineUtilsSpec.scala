import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.pipelines.PipelineUtils
import org.specs2.mutable.Specification

class PipelineUtilsSpec extends Specification{

  val defaultEngineOptions = Seq[PipelineBaseOption]()

  val rsPipelineTemplate = {
    val taskOptions: Seq[PipelineBaseOption] = Seq(
      PipelineIntOption("id-a", "name-a", 1234, "Description-A"),
      PipelineStrOption("id-b", "name-b", "value-b", "Description-B"),
      PipelineDoubleOption("id-c", "name-c", 4567.89, "Description-C"),
      PipelineBooleanOption("id-d", "name-d", true, "Description-D"),
      PipelineChoiceIntOption("id-e", "name-e", 1, "Description-E", Seq(1,2,3)),
      PipelineChoiceStrOption("id-f", "name-f", "b", "Description-F", Seq("a", "b", "c")),
      PipelineChoiceDoubleOption("id-g", "name-g", 0.1, "Description-G", Seq(0.01, 0.1, 1.0)))

    val entryPoints = Seq[EntryPoint]()
    val tags = Seq("dev", "example")
    val presets = Seq[PipelineTemplatePreset]()
    PipelineTemplate("pbsmrtpipe.pipelines.sa3_resequencing", "Name", "Desc", "0.1.0",
      defaultEngineOptions, taskOptions, entryPoints, tags, presets)
  }

  // Correctly typed
  val preset1 = {
    val taskOptions = Seq(
      PipelineIntOption("id-a", "name-a", 99999, "Description-A"),
      PipelineStrOption("id-b", "name-b", "value-preset-b", "Description-B"),
      PipelineDoubleOption("id-c", "new-name-c", 9999.99, "Descrption-new-C"),
      PipelineBooleanOption("id-d", "new-name-c", false, "Descrption-new-D"),
      PipelineChoiceIntOption("id-e", "new-name-c", 1, "Descrption-new-E", Seq(1,2,3)),
      PipelineChoiceStrOption("id-f", "new-name-c", "a", "Descrption-new-F", Seq("a","b","c")),
      PipelineChoiceDoubleOption("id-g", "new-name-c", 0.01, "Descrption-new-G", Seq(0.01, 0.1, 1.0))
    )
    PipelineTemplatePreset("preset-01", rsPipelineTemplate.id, defaultEngineOptions, taskOptions)
  }

  // Loaded from XML
  val preset2 = {
    val taskOptions = Seq(
      PipelineStrOption("id-a", "name-a", "99999", "Description-A"),
      PipelineStrOption("id-b", "name-b", "value-preset-b", "Description-B"),
      PipelineStrOption("id-c", "new-name-c", "9999.99", "Descrption-new-C"),
      PipelineStrOption("id-d", "new-name-d", "false", "Descrption-new-D"),
      PipelineStrOption("id-e", "new-name-e", "2", "Descrption-new-E"),
      PipelineStrOption("id-f", "new-name-f", "a", "Descrption-new-F"),
      PipelineStrOption("id-g", "new-name-g", "0.01", "Descrption-new-G")
    )
    PipelineTemplatePreset("preset-02", rsPipelineTemplate.id, defaultEngineOptions, taskOptions)
  }

  // Empty options
  val preset3 = {
    val taskOptions = Seq()
    PipelineTemplatePreset("preset-03", rsPipelineTemplate.id, defaultEngineOptions, taskOptions)
  }

  // wrong options
  val preset4 = {
    val taskOptions = Seq(
      PipelineStrOption("id-h", "name-h", "99999", "Description-H"),
      PipelineStrOption("id-i", "name-i", "value-preset-i", "Description-I")
    )
    PipelineTemplatePreset("preset-04", rsPipelineTemplate.id, defaultEngineOptions, taskOptions)
  }

  // wrong pipeline
  val preset5 = {
    val taskOptions = Seq(
      PipelineStrOption("id-a", "name-a", "99999", "Description-A"),
      PipelineStrOption("id-b", "name-b", "value-preset-b", "Description-B")
    )
    PipelineTemplatePreset("preset-05", "other_pipeline_id", defaultEngineOptions, taskOptions)
  }

  def getOpt(p: PipelineTemplate, id: String): Option[PipelineBaseOption] =
    p.presets.headOption.map(x => x.taskOptions.filter(_.id == id)).get.headOption

  "Test " should {
    "Simple load/merge Pipeline Presets" in {
      val pipelineTemplate = PipelineUtils.updatePipelinePreset(rsPipelineTemplate, Seq(preset1))
      val px2 = getOpt(pipelineTemplate, "id-a")
      px2 must beSome[PipelineBaseOption]
      px2.map(x => x.asInstanceOf[PipelineIntOption].value) must beEqualTo(Some(99999))
      val n = pipelineTemplate.presets.headOption.map(x => x.taskOptions.size)
      n must beEqualTo(Some(7))
    }
    "Convert from string values" in {
      val pipelineTemplate = PipelineUtils.updatePipelinePreset(rsPipelineTemplate, Seq(preset2))
      val n = pipelineTemplate.presets.headOption.map(x => x.taskOptions.size)
      n must beEqualTo(Some(7))
      var px = getOpt(pipelineTemplate, "id-a")
      px.map(x => x.asInstanceOf[PipelineIntOption].value) must beEqualTo(Some(99999))
      val px2 = getOpt(pipelineTemplate, "id-b")
      px2.map(x => x.asInstanceOf[PipelineStrOption].value) must beEqualTo(Some("value-preset-b"))
      val px3 = getOpt(pipelineTemplate, "id-c")
      px3.map(x => x.asInstanceOf[PipelineDoubleOption].value) must beEqualTo(Some(9999.99))
      val px4 = getOpt(pipelineTemplate, "id-d")
      px4.map(x => x.asInstanceOf[PipelineBooleanOption].value) must beEqualTo(Some(false))
      val px5 = getOpt(pipelineTemplate, "id-e")
      px5.map(x => x.asInstanceOf[PipelineChoiceIntOption].value) must beEqualTo(Some(2))
      val px6 = getOpt(pipelineTemplate, "id-f")
      px6.map(x => x.asInstanceOf[PipelineChoiceStrOption].value) must beEqualTo(Some("a"))
      val px7 = getOpt(pipelineTemplate, "id-g")
      px7.map(x => x.asInstanceOf[PipelineChoiceDoubleOption].value) must beEqualTo(Some(0.01))
    }
    "Process empty presets" in {
      val pipelineTemplate = PipelineUtils.updatePipelinePreset(rsPipelineTemplate, Seq(preset3))
      val n = pipelineTemplate.presets.headOption.map(x => x.taskOptions.size)
      n must beEqualTo(Some(7))
      // still default
      var px = getOpt(pipelineTemplate, "id-a")
      px.map(x => x.asInstanceOf[PipelineIntOption].value) must beEqualTo(Some(1234))
    }
    "Process inappropriate options" in {
      val pipelineTemplate = PipelineUtils.updatePipelinePreset(rsPipelineTemplate, Seq(preset4))
      val n = pipelineTemplate.presets.headOption.map(x => x.taskOptions.size)
      n must beEqualTo(Some(7))
      // still default
      var px = getOpt(pipelineTemplate, "id-a")
      px.map(x => x.asInstanceOf[PipelineIntOption].value) must beEqualTo(Some(1234))
    }
    "Process presets for incorrect pipeline" in { // but correct option IDs!
      val pipelineTemplate = PipelineUtils.updatePipelinePreset(rsPipelineTemplate, Seq(preset5))
      val n = pipelineTemplate.presets.headOption.map(x => x.taskOptions.size)
      n must beEqualTo(None)
    }
  }
}
