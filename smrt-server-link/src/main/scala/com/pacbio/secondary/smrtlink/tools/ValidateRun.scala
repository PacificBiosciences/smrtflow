
package com.pacbio.secondary.smrtlink.tools

import java.io.File

import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.analysis.tools._
import com.pacbio.secondary.smrtlink.models._
import org.joda.time.{DateTime => JodaDateTime}
import scopt.OptionParser
import spray.json._

import scala.io.Source
import scala.language.postfixOps
import scala.util.Try


case class ValidateRunConfig(path: File) extends LoggerConfig

object ValidateRun extends CommandLineToolRunner[ValidateRunConfig] with SmrtLinkJsonProtocols {

  val toolId = "pbscala.tools.validate_run"
  val VERSION = "0.1.0"
  val DESCRIPTION = "PacBio Run Design Validation Tool"
  lazy val defaults = ValidateRunConfig(null)

  lazy val parser = new OptionParser[ValidateRunConfig]("validate-run") {
    head(DESCRIPTION, VERSION)

    arg[File]("run").action { (p,c) =>
      c.copy(path = p)
    } text "Path to run JSON or XML"

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show Options and exit"

    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
  }

  override def runTool(c: ValidateRunConfig): Try[String] =
    Try { validateRun(c) }


  def validateRun(c: ValidateRunConfig): String = {
    val contents = Source.fromFile(c.path).getLines.mkString
    val dataModel = if (c.path.toString.endsWith(".json")) {
      contents.parseJson.convertTo[RunCreate].dataModel
    } else contents
    val pr = DataModelParserImpl(dataModel)
    s"Successfully parsed run ${pr.run.name}"
  }

  // delete me when this is removed from the base interface
  def run(opt: ValidateRunConfig) =
    Left(ToolFailure(toolId, 0, "Not Supported"))


}

object ValidateRunApp extends App {
  import ValidateRun._
  runnerWithArgsAndExit(args)
}
