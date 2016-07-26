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
import scala.util.{Failure, Success, Try}
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
                        host: String = "http://smrtlink-internal",
                        port: Int = 8081,
                        pipelineId: String = "pbsmrtpipe.pipelines.internal_cond_dev_r",
                        jobName: String = "Condition Job",
                        pathToCSV: Path,
                        pathToReseqConditions: Path,
                        outputPathToReseqConditions: Path = Paths.get("reseq-conditions.json")) extends LoggerConfig


trait CommonClientToolRunner extends LazyLogging{

  // Add this as an implicit to the fun calls
  implicit val TIMEOUT: Duration

  def nullSummary[T](x: T): Unit = {}
  def defaultSummary[T](x: T): Unit = println(s"-> Summary $x")
  def statusSummary(x: ServiceStatus): String = s"System ${x.id} ${x.version} ${x.message}"

  def runAwait[T](f: () => Future[T]) : T = {
    //val fx = Future.fromTry(Try { f()} )
    Await.result(f(), TIMEOUT)
  }

  def runAwaitWithActorSystem[T](summary: (T => Unit))(f: (ActorSystem => Future[T])): Int = {
    implicit val actorSystem = ActorSystem("slia")
    val exitCode = Try { Await.result(f(actorSystem), TIMEOUT) }.map(summary) match {
      case Success(result) => 0
      case Failure(err) =>
        System.err.println(s"Failed to run $err")
        1
    }
    logger.info("Shutting down actor system")
    actorSystem.shutdown()
    exitCode
  }

}

/**
  * This should be refactored into a common layer.
  */
trait InternalAnalysisClientToolRunner extends CommonClientToolRunner{


  def runStatus(host: String, port: Int): Int =
    runAwaitWithActorSystem[ServiceStatus](defaultSummary[ServiceStatus]){ (system: ActorSystem) =>
      val client = new InternalAnalysisServiceClient(host, port)(system)
      client.getStatus
    }

  // Write ReseqCondition JSON file
  def runConvert(host: String, port: Int, pathToCsvPath: Path, outputPath: Path, jobName: String, pipelineId: String): Int =
    runAwaitWithActorSystem[ReseqConditions](nullSummary) { (system: ActorSystem) =>
      val client = new InternalAnalysisServiceClient(host, port)(system)

      for {
        _ <- client.getStatus
        csvContents <- Future { scala.io.Source.fromFile(pathToCsvPath.toFile).mkString }
        sx <- Future {ServiceConditionCsvPipeline(pipelineId, csvContents, jobName, s"Job $jobName")}
        reseqConditions <- client.resolveConditionRecord(sx)
        _ <- Future { IOUtils.writeReseqConditions(reseqConditions, outputPath) }
      } yield reseqConditions
    }

  // Submit a Job from Reseq Conditions
  def runSumbit(host: String, port: Int,  pathToCsv: Path, pipelineId: String, jobName: String, jobDescription: String): Int = {
    runAwaitWithActorSystem[EngineJob](defaultSummary) { (system: ActorSystem) =>
      //FIXME(mpkocher)(2016-7-24) Add more validation
      val _ = IOUtils.parseConditionCsv(pathToCsv)
      val csvContents = scala.io.Source.fromFile(pathToCsv.toFile).mkString
      val sx = ServiceConditionCsvPipeline(pipelineId, csvContents, jobName, jobDescription)
      val client = new InternalAnalysisServiceClient(host, port)(system)

      for {
        _ <- client.getStatus
        engineJob <- client.submitReseqCondition(sx)
      } yield engineJob
    }
  }
}



trait InternalAnalysisClientToolParser {

  def printDefaults(c: CustomConfig) = println(s"Config $c")
  def showVersion(): Unit = { println(VERSION) }

  lazy val TOOL_ID = "slia"
  lazy val NAME = "SLIA"
  lazy val VERSION = "0.1.5"
  lazy val DESCRIPTION =
    """
      |SMRT Link Client Internal Analysis Tool
    """.stripMargin

  // This requires some nonsense null values. I don't think there's away to get around this
  lazy val DEFAULT = CustomConfig(Modes.UNKNOWN, command = printDefaults, pathToCSV = null, pathToReseqConditions = null)

  lazy val parser = new OptionParser[CustomConfig]("slia") {
    head(NAME, VERSION)
    note(DESCRIPTION)

    cmd(Modes.STATUS.name) action { (_, c) =>
      c.copy(command = (c) => println("with " + c), mode = Modes.STATUS)
    } children(
        opt[String]("host") action { (x, c) => c.copy(host = x) } text s"Hostname of smrtlink server (Default: ${DEFAULT.host})",
        opt[Int]("port") action { (x, c) =>  c.copy(port = x)} text s"Services port on smrtlink server (Default: ${DEFAULT.port})"
        ) text "SMRT Link Internal Aanlysis Server Status Summary"

    // Convert
    cmd(Modes.CONVERT.name) action { (_, c) =>
      c.copy(command = (c) => println(s"with $c"), mode = Modes.CONVERT)
    } children(
        arg[File]("csv") action { (x, c) => c.copy(pathToCSV = x.toPath) } text "Path to Reseq Conditions CSV",
        opt[File]("reseq-json") action { (x, c) => c.copy(outputPathToReseqConditions = x.toPath) } text s"Path to Output Reseq Conditions JSON (Default: ${DEFAULT.outputPathToReseqConditions})"
        ) text "Convert CSV Summary"

    cmd(Modes.SUBMIT.name) action { (_, c) => c.copy(command = (c) => println(s"with $c"), mode = Modes.SUBMIT)
    } children(
        arg[File]("csv") action { (x, c) => c.copy(pathToCSV = x.toPath) } text "Path to Reseq Conditions CSV",
        opt[String]('p', "pipeline-id") action { (x, c) => c.copy(pipelineId = x) } text s"Pipeline Id to use (Default: ${DEFAULT.pipelineId})",
        opt[String]('n', "name") action { (x, c) => c.copy(jobName = x)} text s"Job Name (Default: ${DEFAULT.jobName})",
        opt[String]("host") action { (x, c) => c.copy(host = x) } text s"Hostname of smrtlink server (Default: ${DEFAULT.host})",
        opt[Int]("port") action { (x, c) =>  c.copy(port = x)} text s"Services port on smrtlink server (Default: ${DEFAULT.port}"
        ) text "Submit Reseq Condition CSV Job to SMRTLink Internal Analysis Services"

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show options and exit"

    opt[Unit]('v', "version") action { (x, c) =>
      showVersion()
      sys.exit(0)
    } text "Show Version and Exit"

    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
  }
}


object InternalAnalysisClientToolApp extends App
    with InternalAnalysisClientToolParser
    with InternalAnalysisClientToolRunner
    with LazyLogging{

  implicit val TIMEOUT = 10 seconds

  def runCustomConfig(c: CustomConfig): Int = {
    println(s"Running with config $c")
    c.mode match {
      case Modes.STATUS => runStatus(c.host, c.port)
      case Modes.CONVERT => runConvert(c.host, c.port, c.pathToCSV, c.pathToCSV, c.jobName, c.pipelineId)
      case Modes.SUBMIT => runSumbit(c.host, c.port, c.pathToCSV, c.pipelineId, c.jobName, s"Job Description ${c.jobName}")
      case unknown =>
        System.err.println(s"Unknown mode '$unknown'")
        1
    }
  }

  def runner(args: Array[String]) = {
    val exitCode = parser.parse(args, DEFAULT)
        .map(runCustomConfig)
        .getOrElse(1)
    println(s"Exiting $NAME $VERSION with exitCode $exitCode")
    // This is the ONLY place System.exit should be called
    System.exit(exitCode)
  }

  runner(args)

}
