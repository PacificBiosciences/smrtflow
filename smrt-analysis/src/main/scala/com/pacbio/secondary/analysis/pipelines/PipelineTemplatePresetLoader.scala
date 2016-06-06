package com.pacbio.secondary.analysis.pipelines

import java.nio.file.Path

import scala.xml

import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.pbsmrtpipe.PbsmrtpipeEngineOptions

/**
 * Loads the pbsmrptipe Pipeline Templates Presets.
 *
 *
 * Because the types of the task-options are encoded in the ToolContracts,
 * or in the ResolvedPipelineTemplates, the types are kept as strings
 *
 * Created by mkocher on 10/21/15.
 */
trait PipelineTemplatePresetLoader extends Loader[PipelineTemplatePreset] {

  val extFilter = Seq("xml")

  /**
   * Parses XML to extract the pbsmrtpipe TaskOptions
   * @param x XML node
   * @return
   */
  private def parseTaskOptions(x: xml.Elem, optionsTag: String = "options"): Seq[PipelineBaseOption] = {
    // pbsmrtpipe Task Options don't have the proper type, or other metadata
    // So all options are converted to String opts
    val taskOptions: Seq[PipelineBaseOption] = (x \ optionsTag \ "option")
      .map(i => ((i \ "@id").text, (i \ "value").text))
      .map(z => PipelineStrOption(z._1, s"Name ${z._1}", z._2, s"Description ${z._1}"))
    taskOptions
  }

  /**
   * Load 'raw' PipelineTemplate Presets
   *
   * Note, the task options parsed from the Preset are converted to
   * PipelineStrOption with populated metadata
   * @param path Path to preset.xml
   * @return
   */
  def loadFrom(path: Path): PipelineTemplatePreset = {
    val x = scala.xml.XML.loadFile(path.toFile)
    val engineOptions = parseTaskOptions(x, "options")
    val taskOptions = parseTaskOptions(x, "task-options")
    val presetId = (x \ "@id").text
    val pipelineId = (x \ "@pipeline-id").text

    PipelineTemplatePreset(presetId, pipelineId, engineOptions, taskOptions)
  }

  /**
   * Parses and converts the pipeline engine level "options"
   * @param rnode
   * @return
   */
  private def parseEngineOptions(rnode: scala.xml.Elem): Seq[PipelineBaseOption] = {

    /**
     * Cast String to bool. Default to false if can't parse value.
     *
     * (False|false) and {True|true) are supported.
     *
     * @param sx
     * @return
     */
    def castInt(sx: String): Boolean = {
      sx.replace("\n", "").replace(" ", "").toLowerCase match {
        case "true" => true
        case "false" => false
        case _ => false
      }
    }

    // Convert the Raw values and Cast to correct type
    def toV(optionId: String, optionValue: String): Option[PipelineBaseOption] = {
      PbsmrtpipeEngineOptions().getPipelineOptionById(optionId).map {
        case o: PipelineBooleanOption => o.copy(value = castInt(optionValue))
        case o: PipelineIntOption => o.copy(value = optionValue.toInt)
        case o: PipelineDoubleOption => o.copy(value = optionValue.toDouble)
        case o: PipelineStrOption => o.copy(value = optionValue)
      }
    }

    (rnode \ "options" \ "option")
      .map(x => ((x \ "@id").text, (x \ "value").text))
      .flatMap(x => toV(x._1, x._2))
  }

  /**
   * Parse the pbsmrtpipe Preset XML
   * @param path
   * @return
   */
  def parsePresetXml(path: Path): (Seq[PipelineBaseOption], Seq[PipelineBaseOption]) = {
    val x = scala.xml.XML.loadFile(path.toFile)
    val engineOptions = parseEngineOptions(x)
    val taskOptions = parseTaskOptions(x)
    (engineOptions, taskOptions)
  }
}

object PipelineTemplatePresetLoader extends PipelineTemplatePresetLoader
