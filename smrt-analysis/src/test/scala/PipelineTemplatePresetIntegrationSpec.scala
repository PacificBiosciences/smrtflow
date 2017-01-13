import java.nio.file.Paths

import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

import com.pacbio.secondary.analysis.pipelines.JsonPipelineTemplatesLoader
import com.pacbio.secondary.analysis.pipelines.PipelineTemplatePresetLoader
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.pipelines.PipelineUtils


class PipelineTemplatePresetIntegrationSpec extends Specification with LazyLogging{

  sequential

  def loadPipeline = {
    val name = "pipeline-templates/example_pipeline_template_01.json"
    val path = getClass.getResource(name)
    val ppath = Paths.get(path.toURI)
    JsonPipelineTemplatesLoader.loadFrom(ppath)
  }

  def loadPreset(name: String) = {
    val path = getClass.getResource(name)
    val p = Paths.get(path.toURI)
    PipelineTemplatePresetLoader.loadFrom(p)
  }

  def getOpt(p: PipelineTemplate, id: String): Option[ServiceTaskOptionBase] =
    p.presets.headOption.map(x => x.taskOptions.filter(_.id == id)).get.headOption

  "Test end-to-end handling of pipeline presets " should {
    "Working XML presets for all types" in {
      val preset = loadPreset("pipeline-template-presets/example-02.xml")
      logger.info(s"Loaded $preset")
      preset.taskOptions.size must beEqualTo(7)
      val p = loadPipeline
      p.presets must beEmpty
      preset.pipelineId must beEqualTo(p.id)
      val pt = PipelineUtils.updatePipelinePreset(p, Seq(preset))
      pt.presets.size must beEqualTo(1)
      val n = pt.presets.headOption.map(x => x.taskOptions.size)
      n must beEqualTo(Some(7))
      var px = getOpt(pt, "pbsmrtpipe.task_options.gamma")
      px.map(x => x.asInstanceOf[ServiceTaskIntOption].value) must beEqualTo(Some(987654))
      px = getOpt(pt, "pbsmrtpipe.task_options.alpha")
      px.map(x => x.asInstanceOf[ServiceTaskStrOption].value) must beEqualTo(Some("Hello world"))
      px = getOpt(pt, "pbsmrtpipe.task_options.beta")
      px.map(x => x.asInstanceOf[ServiceTaskBooleanOption].value) must beEqualTo(Some(false))
      px = getOpt(pt, "pbsmrtpipe.task_options.delta")
      px.map(x => x.asInstanceOf[ServiceTaskDoubleOption].value) must beEqualTo(Some(3.14))
      px = getOpt(pt, "pbsmrtpipe.task_options.a")
      px.map(x => x.asInstanceOf[ServiceTaskStrOption].value) must beEqualTo(Some("C"))
      px = getOpt(pt, "pbsmrtpipe.task_options.b")
      px.map(x => x.asInstanceOf[ServiceTaskIntOption].value) must beEqualTo(Some(1))
      px = getOpt(pt, "pbsmrtpipe.task_options.c")
      px.map(x => x.asInstanceOf[ServiceTaskDoubleOption].value) must beEqualTo(Some(0.01))
    }
    def testPreset(name: String) = {
      val preset = loadPreset(name)
      preset.taskOptions.size must beEqualTo(7)
      logger.info(s"Loaded $preset")
      val p = loadPipeline
      preset.pipelineId must beEqualTo(p.id)
      p.presets must beEmpty
      val pt = PipelineUtils.updatePipelinePreset(p, Seq(preset))
      pt.presets.size must beEqualTo(1)
      val n = pt.presets.headOption.map(x => x.taskOptions.size)
      n must beEqualTo(Some(7))
      var px = getOpt(pt, "pbsmrtpipe.task_options.alpha")
      px.map(x => x.asInstanceOf[ServiceTaskStrOption].value) must beEqualTo(Some("Hello world"))
      px = getOpt(pt, "pbsmrtpipe.task_options.beta")
      px.map(x => x.asInstanceOf[ServiceTaskBooleanOption].value) must beEqualTo(Some(false))
      px = getOpt(pt, "pbsmrtpipe.task_options.gamma")
      px.map(x => x.asInstanceOf[ServiceTaskIntOption].value) must beEqualTo(Some(987654))
      px = getOpt(pt, "pbsmrtpipe.task_options.delta")
      px.map(x => x.asInstanceOf[ServiceTaskDoubleOption].value) must beEqualTo(Some(3.14))
      px = getOpt(pt, "pbsmrtpipe.task_options.a")
      px.map(x => x.asInstanceOf[ServiceTaskStrOption].value) must beEqualTo(Some("C"))
      px = getOpt(pt, "pbsmrtpipe.task_options.b")
      px.map(x => x.asInstanceOf[ServiceTaskIntOption].value) must beEqualTo(Some(1))
      px = getOpt(pt, "pbsmrtpipe.task_options.c")
      px.map(x => x.asInstanceOf[ServiceTaskDoubleOption].value) must beEqualTo(Some(0.01))
    }
    "Working JSON presets for all types (full schema)" in {
      // this is identical to the XML file above
      testPreset("pipeline-template-presets/example-03.json")
    }
    "Working JSON presets for all types (shorthand syntax)" in {
      // this is identical to the first JSON file (and the XML), but specifies
      // options as dictionaries
      testPreset("pipeline-template-presets/example-04.json")
    }
  }
}
