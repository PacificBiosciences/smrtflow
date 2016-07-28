package com.pacbio.secondary.lims.tools


import java.io.File
import java.net.URL
import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.actor.ActorSystem
import com.pacbio.common.client.UrlUtils
import com.pacbio.common.models.ServiceStatus
import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.analysis.jobs.JobModels.EngineJob
import com.pacbio.secondary.analysis.reports.ReportModels.Report
import com.pacbio.secondary.lims.LimsSubreadSet
import com.pacbio.secondary.smrtlink.client.SmrtLinkServiceAccessLayer
import com.pacbio.secondary.smrtserver.client.AnalysisServiceAccessLayer
import com.pacbio.secondaryinternal.IOUtils
import com.pacbio.secondaryinternal.client.InternalAnalysisServiceClient
import com.pacbio.secondaryinternal.models.{ReseqConditions, ServiceConditionCsvPipeline}
import com.typesafe.scalalogging.LazyLogging
import scopt.OptionParser

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.language.postfixOps
import com.pacbio.secondaryinternal.tools.CommonClientToolRunner
import spray.client.pipelining._
import spray.http._
//import scala.concurrent.ExecutionContext.Implicits.global
import spray.json._
import spray.json.DefaultJsonProtocol._
import com.pacbio.secondary.lims.JsonProtocol._


class LimsClient(baseUrl: URL)(implicit actorSystem: ActorSystem)
  extends AnalysisServiceAccessLayer(baseUrl)(actorSystem)
  with LazyLogging {

  def this(host: String, port: Int)(implicit actorSystem: ActorSystem) {
    this(UrlUtils.convertToUrl(host, port))(actorSystem)
  }

  protected def toImportUrl(datasetType: String): String = toUrl(s"/smrt-lims/$datasetType/import")

  def importLimsSubreadSet: HttpRequest => Future[String] = sendReceive ~> unmarshal[String]

  def importLimsSubreadSetReport(path: Path): Future[String] = importLimsSubreadSet {
    val content = scala.io.Source.fromFile(path.toFile).mkString
    val httpEntity = HttpEntity(MediaTypes.`multipart/form-data`, HttpData(content)).asInstanceOf[HttpEntity.NonEmpty]
    val formFile = FormFile("file", httpEntity)
    val mfd = MultipartFormData(Seq(BodyPart(formFile, "file")))
    Post(toImportUrl("lims-subreadset"), mfd)
  }

  def getSubreads: HttpRequest => Future[Seq[LimsSubreadSet]] = sendReceive ~> unmarshal[Seq[LimsSubreadSet]]

  def subreadsByRuncode(runcode: String): Future[Seq[LimsSubreadSet]] = getSubreads {
    Get(s"/smrt-lims/lims-subreadset/$runcode")
  }

  def subreadsByExp(expid: Int): Future[Seq[LimsSubreadSet]] = getSubreads {
    Get(s"/smrt-lims/lims-subreadset/$expid")
  }

  def subreadsByUUID(uuid: UUID): Future[Seq[LimsSubreadSet]] = getSubreads {
    Get(s"/smrt-lims/lims-subreadset/$uuid")
  }
}

/**
 * Modes
 *   - IMPORT = Import lims.yml + related .subreadset.xml via HTTP multi-part form post
 *   - GET_EXPID = Retrieve LimsSubreadSet records by lims.yml expid.
 *   - GET_RUNCODE = Retrieve LimsSubreadSet records by lims.yml run code.
 *   - GET_UUID = Retrieve the LimsSubreadSet record by .subreadset.xml UUID.
 */
object Modes {
  sealed trait Mode { val name: String }
  case object IMPORT extends Mode { val name = "import"}
  //case object FIND_AND_IMPORT extends Mode { val name = "find_and_import"} // TODO: add dir scan
  case object GET_EXPID extends Mode { val name = "get-expid"}
  case object GET_RUNCODE extends Mode { val name = "get-runcode"}
  case object GET_UUID extends Mode { val name = "get-uuid"}
  // TODO: Ask MK to help clarify how `smrt-client-lims resolve my-lambda-neb`. `DataSetMetadata` from the SQLiteDB?
  case object UNKNOWN extends Mode { val name = "unknown"}
}

case class CustomConfig(
    mode: Modes.Mode = Modes.UNKNOWN,
    command: CustomConfig => Unit,
    host: String = "http://smrt-lims",
    port: Int = 8081,
    path: Path = Paths.get("."),
    runcode: String,
    expid: Int,
    uuid: UUID
) extends LoggerConfig

trait LimsClientToolRunner extends CommonClientToolRunner { // TODO: move CommonClientToolRunner out of analysis? ATM, it is the only dep requiring smrtServerAnalysisInternal

  def runImportLimsYml(host: String, port: Int, path: Path): Int =
    runAwaitWithActorSystem[String](defaultSummary[String]){ (system: ActorSystem) =>
      val client = new LimsClient(host, port)(system)
      //client.getStatus
      client.importLimsSubreadSetReport(path)
    }

  def runGetSubreadsByRuncode(host: String, port: Int, runcode: String): Int =
    runAwaitWithActorSystem[Seq[LimsSubreadSet]](defaultSummary[Seq[LimsSubreadSet]]){ (system: ActorSystem) =>
      val client = new LimsClient(host, port)(system)
      client.subreadsByRuncode(runcode)
    }

  def runGetSubreadsByExp(host: String, port: Int, expid: Int): Int =
    runAwaitWithActorSystem[Seq[LimsSubreadSet]](defaultSummary[Seq[LimsSubreadSet]]){ (system: ActorSystem) =>
      val client = new LimsClient(host, port)(system)
      client.subreadsByExp(expid)
    }

  def runGetSubreadsByUUID(host: String, port: Int, uuid: UUID): Int =
    runAwaitWithActorSystem[Seq[LimsSubreadSet]](defaultSummary[Seq[LimsSubreadSet]]){ (system: ActorSystem) =>
      val client = new LimsClient(host, port)(system)
      client.subreadsByUUID(uuid)
    }

}

trait LimsClientToolParser {

  def printDefaults(c: CustomConfig) = println(s"Config $c")
  def showVersion(): Unit = { println(VERSION) }

  lazy val TOOL_ID = "lims"
  lazy val NAME = "Lims"
  lazy val VERSION = "0.1.0"
  lazy val DESCRIPTION =
    """
      |SMRT Link LIMS client
    """.stripMargin

  // This requires some nonsense null values. I don't think there's away to get around this
  lazy val DEFAULT = CustomConfig(Modes.UNKNOWN, command = printDefaults, path = null, runcode = null, expid = -1, uuid = null)

  lazy val parser = new OptionParser[CustomConfig]("slia") {
    head(NAME, VERSION)
    note(DESCRIPTION)

    cmd(Modes.IMPORT.name) action { (_, c) =>
      c.copy(command = (c) => println("with " + c), mode = Modes.IMPORT)
    } children(
        opt[String]("host") action { (x, c) => c.copy(host = x) } text s"Hostname of smrtlink server (Default: ${DEFAULT.host})",
        opt[Int]("port") action { (x, c) =>  c.copy(port = x)} text s"Services port on smrtlink server (Default: ${DEFAULT.port})"
        //opt[String]("path") action { (x, c) => c.copy(Paths.get(path) = x) } text s"Path of lims.yml file",
        ) text "Import lims.yml + .subreadset.xml file via file upload"

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show options and exit"

    opt[Unit]('v', "version") action { (x, c) =>
      showVersion()
      sys.exit(0)
    } text "Show version and exit"

    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
  }
}


object LimsClientToolsApp extends App
    with LimsClientToolParser
    with LimsClientToolRunner{

  implicit val TIMEOUT = 20 seconds

  def runCustomConfig(c: CustomConfig): Int = {
    println(s"Running with config $c")
    c.mode match {
      case Modes.IMPORT => runImportLimsYml(c.host, c.port, c.path)
      case Modes.GET_RUNCODE => runGetSubreadsByRuncode(c.host, c.port, c.runcode)
      case Modes.GET_EXPID => runGetSubreadsByExp(c.host, c.port, c.expid)
      case Modes.GET_UUID => runGetSubreadsByUUID(c.host, c.port, c.uuid)
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

// TODO: temporary test. need to move equivalent to the test suite.
object LimsClientToolsAppTest extends App
    with LimsClientToolRunner{

  implicit val TIMEOUT = 20 seconds

  // test an import of a known resource
  println("Running Import")
  runImportLimsYml("127.0.0.1", 8081, Paths.get("/pbi/collections/312/3120145/r54009_20160426_164705/1_A01/lims.yml"))

  println("Running GetByUUID")
  runGetSubreadsByUUID("127.0.0.1", 8081, UUID.fromString("5fe01e82-c694-4575-9173-c23c458dd0e1"))

  println("Running GetByRuncode")
  runGetSubreadsByRuncode("127.0.0.1", 8081, "3120145-0010")

  println("Running GetByExp")
  runGetSubreadsByExp("127.0.0.1", 8081, 3120145)

}
