package com.pacbio.secondary.analysis.configloaders

import java.nio.file.{Path, Files, Paths}

import com.pacbio.secondary.analysis.pbsmrtpipe.{CommandTemplate, PbsmrtpipeEngineOptions}
import com.pacbio.secondary.analysis.pipelines.PipelineTemplatePresetLoader
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging

import scala.util.{Failure, Success, Try}


object PbsmrtpipeConfigConstants extends EngineCoreConfigConstants {

  // pbsmrtpipe Preset that will be used in all pipelines for the
  // pbsmrtpipe 'workflow engine' level options. The tasks options
  // will be taken from the Resolved Pipeline Templates
  val PB_SMRTPIPE_PRESET_XML = "smrtflow.engine.pbsmrtpipePresetXml"

  // the pbsmrtpipe exe can be wrapped in a specific wrapper that
  // uses the similar "cluster" template interface as the pbsmrtpipe
  // If no value is given, bash is called directly (which is the same as
  // setting the template to "bash ${CMD}"
  val PB_ENGINE_CMD_TEMPLATE = "smrtflow.engine.pb-cmd-template"
}

/**
 *
 * Created by mkocher on 10/8/15.
 */
trait PbsmrtpipeConfigLoader extends EngineCoreConfigLoader with LazyLogging {

  /**
   * Loads the pbsmrtpipe PRESET XML config which is used as the base layer for pipelines run in pbsmrtipe
   *
   *
   *
   * @param config
   * @return
   */
  private def loadPresetXmlFrom(config: Config): Option[Path] = {
    val px = for {
      p <- Try {conf.getString(PbsmrtpipeConfigConstants.PB_SMRTPIPE_PRESET_XML)}
      presetPath <- Try {Paths.get(p)}
      if Files.exists(Paths.get(p))
    } yield presetPath

    px match {
      case Success(path) =>
        logger.info(s"Loaded pbsmrtpipe preset from ${path.toAbsolutePath.toString}")
        Option(path)
      case Failure(ex) =>
        logger.warn(s"Failed to load pbsmrtpipe preset from conf. Defaulting to pbsmrtpipe defined defaults. ${ex.getMessage}")
        None
    }
  }

  lazy val pbsmrtpipePresetXML: Option[Path] = loadPresetXmlFrom(conf)

  /**
   * Loads the preset XML and converts into Pbsmrtipe Engine Options or returns
   * the default options
   * @param config
   * @return
   */
  def loadPbsmrtpipeEngineConfigFrom(config: Config): Option[PbsmrtpipeEngineOptions] = {
    pbsmrtpipePresetXML.map { x =>
      logger.info(s"Loading pbsmrtpipe preset.xml from $x")
      val presets = PipelineTemplatePresetLoader.loadFrom(x)
      logger.info(s"Loaded pbsmrtpipe Engine Opts ${presets.options}")
      PbsmrtpipeEngineOptions(presets.options)
    }
  }

  private def loadFrom(path: Path): String = {
    scala.io.Source.fromFile(path.toFile).getLines().mkString("\n")
  }

  /*
  Load command template from the path to file. The template should have the form "bash ${CMD}"
   */
  def loadCmdTemplateFrom(config: Config): Option[CommandTemplate] = {
    val fx = for {
      path <- Try { config.getString(PbsmrtpipeConfigConstants.PB_ENGINE_CMD_TEMPLATE) }
      templateString <- Try { loadFrom(Paths.get(path.toString)) }
      cmdTemplate <- Try { CommandTemplate(templateString) }
    } yield cmdTemplate

    fx match {
      case Success(x) => Option(x)
      case Failure(ex) =>
        val emsg = s"Unable to load CMD template from ${PbsmrtpipeConfigConstants.PB_ENGINE_CMD_TEMPLATE} Error ${ex.getMessage}"
        logger.warn(emsg)
        None
    }
  }

  def loadCmdTemplate = loadCmdTemplateFrom(conf)

  def loadPbsmrtpipeEngineConfig = loadPbsmrtpipeEngineConfigFrom(conf)

  def loadPbsmrtpipeEngineConfigOrDefaults = loadPbsmrtpipeEngineConfig getOrElse PbsmrtpipeEngineOptions.defaults
}

object PbsmrtpipeConfigLoader extends PbsmrtpipeConfigLoader
