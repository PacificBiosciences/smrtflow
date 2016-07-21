package com.pacbio.secondaryinternal.tools

import java.io.File
import java.net.URL
import java.nio.file.{Path, Paths}

import akka.actor.ActorSystem
import com.pacbio.common.models.ServiceStatus
import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.analysis.jobs.JobModels.EngineJob
import com.pacbio.secondaryinternal.IOUtils
import com.pacbio.secondaryinternal.client.InternalAnalysisServiceClient
import com.pacbio.secondaryinternal.models.{ReseqConditions, ServiceConditionCsvPipeline}
import com.typesafe.scalalogging.LazyLogging
import scopt.OptionParser

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try
import scala.language.postfixOps


/**
  * Modes
  *   - Status
  *   - Convert CSV to ReseqConditionJson
  *   - Submit Job from ReseqConditionJson
  *
  *
  */
object Modes {
  sealed trait Mode { val name: String }
  case object STATUS extends Mode { val name = "status"}
  case object CONVERT extends Mode { val name = "convert"}
  case object SUBMIT extends Mode { val name = "submit"}
  case object UNKNOWN extends Mode { val name = "unknown"}
}

case class CustomConfig(mode: Modes.Mode = Modes.UNKNOWN,
                        command: CustomConfig => Unit,
                        host: String = "smrtlink-internal",
                        port: Int = 8081,
                        pipelineId: String = "pbsmrtpipe.pipelines.reseq_cond",
                        jobName: String = "Condition Job",
                        pathToCSV: Path,
                        pathToReseqConditions: Path,
                        outputPathToReseqConditions: Path = Paths.get("reseq-conditions.json")) extends LoggerConfig


trait InternalAnalysisClientToolParser {

  def printDefaults(c: CustomConfig) = println(s"Config $c")

  lazy val TOOL_ID = "slia"
  lazy val NAME = "SLIA"
  lazy val VERSION = "0.1.0"
  lazy val DESCRIPTION =
    """
      |SMRT Link Client Internal Analysis Tool
    """.stripMargin

  // This requires some nonsense null values. I don't think there's away to get around this
  lazy val DEFAULT = CustomConfig(Modes.UNKNOWN, command = printDefaults, pathToCSV = null, pathToReseqConditions = null)

  lazy val parser = new OptionParser[CustomConfig]("slia") {
    head(NAME, VERSION)
    note(DESCRIPTION)

    opt[String]("host") action { (x, c) =>
      c.copy(host = x)
    } text "Hostname of smrtlink server"

    opt[Int]("port") action { (x, c) =>
      c.copy(port = x)
    } text "Services port on smrtlink server"


    opt[Unit]("debug") action { (_, c) =>
      c.asInstanceOf[LoggerConfig].configure(c.logbackFile, c.logFile, debug = true, c.logLevel).asInstanceOf[CustomConfig]
    } text "Display debugging log output"

    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])

    cmd(Modes.STATUS.name) action { (_, c) =>
      c.copy(command = (c) => println("with " + c), mode = Modes.STATUS)
    }

    // Convert
    arg[File]("csv") action { (x, c) => c.copy(pathToCSV = x.toPath)} text "Path to Reseq Conditions CSV"
    opt[File]("reseq-json") action { (x, c ) => c.copy(outputPathToReseqConditions = x.toPath)} text "Path to Output Reseq Conditions JSON"
    cmd(Modes.CONVERT.name) action { (_, c) =>
      c.copy(command = (c) => println(s"with $c"), mode = Modes.CONVERT)
    }

    // Submit Reseq Conditions JSON
    arg[File]("reseq-json") action { (x, c) => c.copy(pathToReseqConditions = x.toPath)} text "Path to Reseq Condition JSON"

    opt[String]("name") action { (x, c) => c.copy(jobName = x)} text "Job Name"

    opt[String]("host") action { (x, c) => c.copy(host = x)} text "Service Host"

    opt[Int]("port") action { (x, c) => c.copy(port = x)} text "Service Port"

    cmd(Modes.SUBMIT.name) action { (_, c) =>
      c.copy(command = (c) => println(s"with $c"), mode = Modes.SUBMIT)
    }

  }
}


object InternalAnalysisClientToolApp extends App
    with InternalAnalysisClientToolParser
    with LazyLogging{

  val TIMEOUT = 10 seconds

  def runAwait[T](f: () => Future[T]) : Int = {
    val fx = Future.fromTry(Try { f()} ).map(_ => 0)
    Await.result(fx, TIMEOUT)
  }

  def runAwaitWithActorSystem[T](f: (ActorSystem => Future[T])): Int = {
    implicit val actorSystem = ActorSystem("pbservice")
    val result = runAwait[T](() => { f(actorSystem) })
    actorSystem.shutdown()
    result
  }

  def convertToURL(host: String, port: Int) =  {
    val h = host.replaceFirst("http://", "")
    new URL(s"http://$h:$port")
  }

  def runStatus(host: String, port: Int): Int =
    runAwaitWithActorSystem[ServiceStatus] { (system: ActorSystem) =>
      val client = new InternalAnalysisServiceClient(convertToURL(host, port))(system)
      client.getStatus
    }

  // Write ReseqCondition JSON file
  def runConvert(host: String, port: Int, pathToCsvPath: Path, outputPath: Path, jobName: String, pipelineId: String): Int =
    runAwaitWithActorSystem[ReseqConditions] { (system: ActorSystem) =>
      val client = new InternalAnalysisServiceClient(convertToURL(host, port))(system)

      for {
        _ <- client.getStatus
        csvContents <- Future { scala.io.Source.fromFile(pathToCsvPath.toFile).mkString }
        sx <- Future {ServiceConditionCsvPipeline(pipelineId, csvContents, jobName, s"Job $jobName")}
        reseqConditions <- client.resolveConditionRecord(sx)
        _ <- Future { IOUtils.writeReseqConditions(reseqConditions, outputPath) }
      } yield reseqConditions
  }

  // Submit ReseqCondition JSON Job
  def runSubmit(host: String, port: Int, reseqConditionJsonPath: Path): Int = {
    runAwaitWithActorSystem[EngineJob] { (system: ActorSystem) =>
      val client = new InternalAnalysisServiceClient(convertToURL(host, port))(system)
      for {
        _ <- client.getStatus
        reseqConditions <- Future { IOUtils.loadReseqConditions(reseqConditionJsonPath) }
        engineJob <- client.submitReseqConditions(reseqConditions)
      } yield engineJob
    }
  }


  def runCustomConfig(c: CustomConfig): Int = {
    c.mode match {
      case Modes.STATUS => runStatus(c.host, c.port)
      case Modes.CONVERT => runConvert(c.host, c.port, c.pathToCSV, c.pathToCSV, "Job Name", c.pipelineId)
      case Modes.SUBMIT => runSubmit(c.host, c.port, c.pathToReseqConditions)
      case unknown =>
        System.err.println(s"Unknown mode '$unknown'")
        1
    }
  }

  def runner(args: Array[String]) = {
    val exitCode = parser.parse(args, DEFAULT)
        .map(runCustomConfig)
        .getOrElse(1)
    logger.info(s"Exiting $NAME $VERSION with $exitCode")
    System.exit(exitCode)
  }

  runner(args)

}
