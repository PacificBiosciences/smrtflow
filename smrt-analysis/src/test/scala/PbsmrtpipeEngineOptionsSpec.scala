import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.pbsmrtpipe.{PbsmrtpipeEngineOptions, PbsmrtpipeConstants, IOUtils}

import org.specs2.mutable.Specification

import scala.io.Source

import java.nio.file.Paths
import java.io.File

class PbsmrtpipeEngineOptionsSpec extends Specification{

  sequential

  "Simple translating of XML opts to EngineOptions" should {
    "Simple pipeline option of nproc=7 dist=false" in {
      val opts = Seq(
        ServiceTaskIntOption(PbsmrtpipeConstants.MAX_NPROC.id, 7),
        ServiceTaskBooleanOption(PbsmrtpipeConstants.DISTRIBUTED_MODE.id, false, "desc")
      )

      val eopts = PbsmrtpipeEngineOptions(opts)
      eopts.maxNproc must beEqualTo(7)
      eopts.distributedMode must beEqualTo(false)
    }
  }
  "Convert to and from file formats" should {
    val options = Seq(
      ServiceTaskIntOption(PbsmrtpipeConstants.MAX_NPROC.id, 7),
      ServiceTaskBooleanOption(PbsmrtpipeConstants.DISTRIBUTED_MODE.id, false))
    val taskOptions: Seq[ServiceTaskOptionBase] = Seq(
      PipelineStrOption("id1", "name1", "asdf", "Description 1"),
      PipelineDoubleOption("id2", "name2", 1.2345, "Description 2"),
      PipelineIntOption("id3", "name3", 6789, "Description 3"),
      PipelineBooleanOption("id4", "name4", true, "Description 4"),
      PipelineStrOption("id5", "name5", "", "Description 5"),
      PipelineChoiceStrOption("id6", "name6", "A", "Description 6", Seq("A","B","C")),
      PipelineChoiceIntOption("id7", "name7", 2, "Description 7", Seq(1,2,3)),
      PipelineChoiceDoubleOption("id8", "name8", 0.1, "Description 8", Seq(0.01,0.1,1.0))).map(_.asServiceOption)
    "READ and WRITE preset JSON with both task and engine options" in {
      val tmpFile = File.createTempFile("presets", ".json").toPath
      IOUtils.writePresetJson(tmpFile, "pipeline-id-1", options, taskOptions)
      val jsonSrc = Source.fromFile(tmpFile.toFile).getLines.mkString
      println(jsonSrc)
      val p = IOUtils.parsePresetJson(tmpFile)
      val eopts = PbsmrtpipeEngineOptions(p.options)
      eopts.maxNproc must beEqualTo(7)
      eopts.distributedMode must beFalse
    }
    "READ ONLY preset XML with both task and engine options" in {
      val name = "pipeline-template-presets/presets.xml"
      val path = getClass.getResource(name)
      val p = Paths.get(path.toURI)
      val preset = IOUtils.parsePresetXml(p)
      val eopts = PbsmrtpipeEngineOptions(preset.options)
      eopts.maxNproc must beEqualTo(7)
      eopts.distributedMode must beFalse
    }
  }
}
