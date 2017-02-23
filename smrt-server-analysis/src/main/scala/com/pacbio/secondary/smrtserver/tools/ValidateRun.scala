
package com.pacbio.secondary.smrtserver.tools

import java.net.URL
import java.io.File

import scala.io.Source
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Properties, Success, Try}
import scala.language.postfixOps

import org.joda.time.{DateTime => JodaDateTime}
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import scopt.OptionParser
import spray.json._
import spray.httpx.SprayJsonSupport

import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.analysis.tools._
import com.pacbio.secondary.smrtlink.models._


case class ValidateRunConfig(path: File) extends LoggerConfig

object ValidateRun
    extends CommandLineToolRunner[ValidateRunConfig]
    with SmrtLinkJsonProtocols {

  val toolId = "pbscala.tools.validate_run"
  val VERSION = "0.1.0"
  lazy val defaults = ValidateRunConfig(null)

  lazy val parser = new OptionParser[ValidateRunConfig]("validate-run") {
    head("PacBio Run Design Validation Tool", VERSION)

    arg[File]("run").action { (p,c) =>
      c.copy(path = p)
    } text "Path to run JSON or XML"

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show Options and exit"

    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
  }

  def validateRun(c: ValidateRunConfig) = {
    val contents = Source.fromFile(c.path).getLines.mkString
    val dataModel = if (c.path.toString.endsWith(".json")) {
      contents.parseJson.convertTo[RunCreate].dataModel
    } else contents
    val pr = DataModelParserImpl(dataModel)
    println(s"Successfully parsed run ${pr.run.name}")
    0
  }

  def run(c: ValidateRunConfig): Either[ToolFailure, ToolSuccess] = {
    val startedAt = JodaDateTime.now()
    Try { validateRun(c) } match {
      case Success(rc) => Right(ToolSuccess(toolId, computeTimeDeltaFromNow(startedAt)))
      case Failure(err) =>
        Left(ToolFailure(toolId, computeTimeDeltaFromNow(startedAt), err.getMessage))
    }
  }
}

object ValidateRunApp extends App {
  import ValidateRun._
  runner(args)
}
