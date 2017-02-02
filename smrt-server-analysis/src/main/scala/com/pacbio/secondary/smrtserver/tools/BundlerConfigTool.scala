package com.pacbio.secondary.smrtserver.tools

import java.nio.file.Path
import java.io.File

import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.analysis.tools.{CommandLineToolRunner, ToolFailure, ToolSuccess}
import com.pacbio.secondary.smrtlink.models.ConfigModels.RootSmrtflowConfig
import com.pacbio.secondary.smrtlink.models._
import scopt.OptionParser
import spray.json._
import DefaultJsonProtocol._
import org.joda.time.{DateTime => JodaDateTime}

import scala.util.{Try,Success,Failure}

/**
  * Created by mkocher on 1/17/17.
  *
  * Commandline Tool to validate the SL System config JSON file
  */
case class BundlerConfigOptions(file: File) extends LoggerConfig

object BundlerConfigTool extends CommandLineToolRunner[BundlerConfigOptions]{

  import ConfigModelsJsonProtocol._

  val toolId = "smrtflow.tools.bundler_config"
  val VERSION = "0.1.2"
  val DESCRIPTION =
    """
      |Load and Validate SMRT Link System JSON Config
    """.stripMargin

  val defaults = BundlerConfigOptions(null)

  val parser = new OptionParser[BundlerConfigOptions]("bundler-config") {
    head("Bundler Config Validator")
    note(DESCRIPTION)
    arg[File]("config").action {(x, c) => c.copy(file = x)}.text("Path to Bundler Config JSON schema version 2")

    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
  }

  /**
    * This should be built out to validate
    *
    * - log directory exists
    * - tmp directory exists
    * - jobs root exists
    * - load and validate pbsmrtpipe Preset XML/JSON
    * - Load and validate manifest file (if provided)
    *
    * @param c SL System config JSON/HCON bundle config
    * @return
    */
  def validate(c: RootSmrtflowConfig): RootSmrtflowConfig = c

  def loadConfig(file: File): RootSmrtflowConfig = {
    scala.io.Source.fromFile(file)
        .mkString
        .parseJson
        .convertTo[RootSmrtflowConfig]
  }

  def run(c: BundlerConfigOptions): Either[ToolFailure, ToolSuccess] = {
    val startedAt = JodaDateTime.now()

    val tx = for {
      a <- Try { loadConfig(c.file)}
      b <- Try { validate(a)}
    } yield b

    val runTime = computeTimeDelta(JodaDateTime.now(), startedAt)

    tx match {
      case Success(smrtflowConfig) =>
        logger.info(s"Successfully loaded ${c.file}")
        logger.debug(s"$smrtflowConfig")
        println(s"Successfully validated config ${c.file}")
        Right(ToolSuccess(toolId, runTime))
      case Failure(ex) =>
        val msg = s"Failed to valid ${c.file} ${ex.getMessage}"
        logger.error(msg)
        Left(ToolFailure(toolId, runTime, msg))
    }
  }
}

object BundlerConfigApp extends App {
  import BundlerConfigTool._
  runner(args)
}
