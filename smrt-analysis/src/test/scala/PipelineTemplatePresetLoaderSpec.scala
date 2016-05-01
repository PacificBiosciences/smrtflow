import java.nio.file.Paths

import com.pacbio.secondary.analysis.pipelines.PipelineTemplatePresetLoader
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._


class PipelineTemplatePresetLoaderSpec extends Specification with LazyLogging{

  sequential

  "Load Simple RTP Preset " should {

    "Hello Test" in {
      val name = "pipeline-template-presets/example-01.xml"
      val path = getClass.getResource(name)
      val p = Paths.get(path.toURI)
      val preset = PipelineTemplatePresetLoader.loadFrom(p)
      logger.info(s"Loaded $preset")
      preset.presetId must beEqualTo("pbsmrtpipe.pipeline_presets.sat_preset_01")
      preset.templateId must beEqualTo("pbsmrtpipe.pipelines.sat")
    }

  }

}
