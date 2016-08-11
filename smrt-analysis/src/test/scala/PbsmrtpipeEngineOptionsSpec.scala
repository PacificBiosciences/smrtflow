import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.pbsmrtpipe.{PbsmrtpipeEngineOptions, PbsmrtpipeConstants, IOUtils}
import org.specs2.mutable.Specification

import java.io.File

class PbsmrtpipeEngineOptionsSpec extends Specification{

  sequential

  "Simple translating of XML opts to EngineOptions" should {
    "Simple pipeline option of nproc=7 dist=false" in {
      val opts = Seq(
        PipelineIntOption(PbsmrtpipeConstants.MAX_NPROC.id, "Max Nproc", 7, "desc"),
        PipelineBooleanOption(PbsmrtpipeConstants.DISTRIBUTED_MODE.id, "Distributed Mode", false, "desc")
      )

      val eopts = PbsmrtpipeEngineOptions(opts)
      eopts.maxNproc must beEqualTo(7)
      eopts.distributedMode must beEqualTo(false)
    }
  }
  "Write and read preset XML" should {
    "mixed task options and engine options" in {
      val options = Seq(
        PipelineIntOption(PbsmrtpipeConstants.MAX_NPROC.id, "Max Nproc", 7, "desc"),
        PipelineBooleanOption(PbsmrtpipeConstants.DISTRIBUTED_MODE.id, "Distributed Mode", false, "desc"))
      val taskOptions: Seq[PipelineBaseOption] = Seq(
        PipelineStrOption("id1", "name1", "asdf", "Description 1"),
        PipelineDoubleOption("id2", "name2", 1.2345, "Description 2"),
        PipelineIntOption("id3", "name3", 6789, "Description 3"),
        PipelineBooleanOption("id4", "name4", true, "Description 4"),
        PipelineStrOption("id5", "name5", "", "Description 5"))
      val presetXml = IOUtils.toPresetXml(options, taskOptions)
      val tmpFile = File.createTempFile("presets", ".xml").toPath
      IOUtils.writePresetXml(tmpFile, presetXml)
      val presets = scala.xml.XML.loadFile(tmpFile.toFile)
      println(presets)
      val (opts, _) = IOUtils.parsePresetXml(tmpFile)
      val eopts = PbsmrtpipeEngineOptions(opts)
      eopts.maxNproc must beEqualTo(7)
      eopts.distributedMode must beEqualTo(false)
    }
  }
}
