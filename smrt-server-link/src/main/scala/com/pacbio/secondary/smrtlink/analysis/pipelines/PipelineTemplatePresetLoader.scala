package com.pacbio.secondary.smrtlink.analysis.pipelines

import java.nio.file.Path

import scala.xml
import scala.io.Source

import org.apache.commons.io.FilenameUtils
import spray.json._

import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.SecondaryJobJsonProtocol
import com.pacbio.secondary.smrtlink.analysis.pbsmrtpipe.PbsmrtpipeEngineOptions

/**
 * Loads the pbsmrptipe Pipeline Templates Presets.
 *
 *
 * Because the types of the task-options are encoded in the ToolContracts,
 * or in the ResolvedPipelineTemplates, the types are kept as strings
 *
 * Created by mkocher on 10/21/15.
 */
trait PipelineTemplatePresetLoader extends Loader[PipelineTemplatePreset] with SecondaryJobJsonProtocol {

  val extFilter = Seq("json", "xml")

  /**
   * Parses XML to extract the pbsmrtpipe TaskOptions
   * @param x XML node
   * @return
   */
  private def parseTaskOptions(x: xml.Elem, optionsTag: String = "task-options"): Seq[ServiceTaskOptionBase] = {
    // pbsmrtpipe Task Options don't have the proper type, or other metadata
    // So all options are converted to String opts
    val taskOptions: Seq[ServiceTaskOptionBase] = (x \ optionsTag \ "option")
      .map(i => ((i \ "@id").text, (i \ "value").text))
      .map(z => ServiceTaskStrOption(z._1, z._2))
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
    if (FilenameUtils.getExtension(path.toString) == "xml") parsePresetXml(path)
    else parsePresetJson(path)
  }

  /**
   * Parses and converts the pipeline engine level "options"
   * @param rnode
   * @return
   */
  private def parseEngineOptions(rnode: scala.xml.Elem): Seq[ServiceTaskOptionBase] = {

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
    def toV(optionId: String, optionValue: String): Option[ServiceTaskOptionBase] = {
      PbsmrtpipeEngineOptions().getPipelineOptionById(optionId).map {
        case o: PipelineBooleanOption => o.copy(value = castInt(optionValue))
        case o: PipelineIntOption => o.copy(value = optionValue.toInt)
        case o: PipelineDoubleOption => o.copy(value = optionValue.toDouble)
        case o: PipelineStrOption => o.copy(value = optionValue)
      } map (_.asServiceOption)
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
  def parsePresetXml(path: Path): PipelineTemplatePreset = {
    val x = scala.xml.XML.loadFile(path.toFile)
    val presetId = (x \ "@id").text
    val pipelineId = (x \ "@pipeline-id").text
    val engineOptions = parseEngineOptions(x)
    val taskOptions = parseTaskOptions(x)
    PipelineTemplatePreset(presetId, pipelineId, engineOptions, taskOptions)
  }

  def parsePresetJson(path: Path): PipelineTemplatePreset = {
    val jsonSrc = Source.fromFile(path.toFile).getLines.mkString
    jsonSrc.parseJson.convertTo[PipelineTemplatePreset]
  }

}

object PipelineTemplatePresetLoader extends PipelineTemplatePresetLoader
