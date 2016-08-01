package com.pacbio.secondary.lims.tools


import java.nio.file.Files
import java.net.URL
import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.actor.ActorSystem
import com.pacbio.common.client.{ServiceAccessLayer, UrlUtils}
import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.lims.LimsSubreadSet
import com.typesafe.scalalogging.LazyLogging
import scopt.OptionParser

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import com.pacbio.secondaryinternal.tools.CommonClientToolRunner
import spray.client.pipelining._
import spray.http._
import spray.httpx.SprayJsonSupport._

import scala.collection.JavaConversions._


class LimsClient(baseUrl: URL)(implicit actorSystem: ActorSystem)
  extends ServiceAccessLayer(baseUrl)(actorSystem)
  with LazyLogging {

  import spray.json._
  import spray.json.DefaultJsonProtocol._
  import com.pacbio.secondary.lims.LimsJsonProtocol._

  def this(host: String, port: Int)(implicit actorSystem: ActorSystem) {
    this(UrlUtils.convertToUrl(host, port))(actorSystem)
  }

  protected def toImportUrl(datasetType: String): String = toUrl(s"/smrt-lims/$datasetType/import")
  
  def importLimsSubreadSet: HttpRequest => Future[String] = sendReceive ~> unmarshal[String]

  def importLimsSubreadSet(path: Path): Future[String] = {
    val content = scala.io.Source.fromFile(path.toFile).mkString
    val httpEntity = HttpEntity(MediaTypes.`multipart/form-data`, HttpData(content)).asInstanceOf[HttpEntity.NonEmpty]
    val formFile = FormFile("file", httpEntity)
    val mfd = MultipartFormData(Seq(BodyPart(formFile, "file")))
    importLimsSubreadSet(mfd)
  }
  def importLimsSubreadSet(mfd: MultipartFormData): Future[String] = importLimsSubreadSet {
    Post(toImportUrl("lims-subreadset"), mfd)
  }

  def getSubread: HttpRequest => Future[Option[LimsSubreadSet]] = sendReceive ~> unmarshal[Option[LimsSubreadSet]]

  def getSubreads: HttpRequest => Future[Seq[LimsSubreadSet]] = sendReceive ~> unmarshal[Seq[LimsSubreadSet]]

  def subreadsByRuncode(runcode: String): Future[Seq[LimsSubreadSet]] = getSubreads {
    Get(toUrl(s"/smrt-lims/lims-subreadset/runcode/$runcode"))
  }

  def subreadsByExp(expid: Int): Future[Seq[LimsSubreadSet]] = getSubreads {
    Get(toUrl(s"/smrt-lims/lims-subreadset/expid/$expid"))
  }

  def subreadByUUID(uuid: UUID): Future[Option[LimsSubreadSet]] = getSubread {
    Get(toUrl(s"/smrt-lims/lims-subreadset/uuid/$uuid"))
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
    uuid: UUID,
    exitJvm: Boolean = true
) extends LoggerConfig

trait LimsClientToolRunner extends CommonClientToolRunner { // TODO: move CommonClientToolRunner out of analysis? ATM, it is the only dep requiring smrtServerAnalysisInternal

  import scala.concurrent.ExecutionContext.Implicits.global

  private def stdout[T](t: T) : Unit = System.out.println(t)

  def runImportLimsYml(host: String, port: Int, path: Path): Int =
    runAwaitWithActorSystem[String](stdout[String]){ (system: ActorSystem) =>
      val client = new LimsClient(host, port)(system)
      path.toFile.exists() match {
        case false => throw new Exception(s"Path $path is not a file or directory.")
        case _ => path.toFile.isDirectory match {
          case false => client.importLimsSubreadSet(path)
          case _ => {
            val work = for {
              p <- Files.walk(path).iterator() if p.getFileName() == "lims.yml"
            } yield client.importLimsSubreadSet(p)
            Future{
              work.map(p => {
                println(Await.result(p, 10 seconds))
              })
              s"Batch import attempted on ${work.size} files"
            }
          }
        }
      }
    }

  def runGetSubreadsByRuncode(host: String, port: Int, runcode: String): Int =
    runAwaitWithActorSystem[Seq[LimsSubreadSet]](stdout[Seq[LimsSubreadSet]]){ (system: ActorSystem) =>
      val client = new LimsClient(host, port)(system)
      client.subreadsByRuncode(runcode)
    }

  def runGetSubreadsByExp(host: String, port: Int, expid: Int): Int =
    runAwaitWithActorSystem[Seq[LimsSubreadSet]](stdout[Seq[LimsSubreadSet]]){ (system: ActorSystem) =>
      val client = new LimsClient(host, port)(system)
      client.subreadsByExp(expid)
    }

  def runGetSubreadByUUID(host: String, port: Int, uuid: UUID): Int =
    runAwaitWithActorSystem[Option[LimsSubreadSet]](stdout[Option[LimsSubreadSet]]){ (system: ActorSystem) =>
      val client = new LimsClient(host, port)(system)
      client.subreadByUUID(uuid)
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
        opt[Int]("port") action { (x, c) =>  c.copy(port = x)} text s"Services port on smrtlink server (Default: ${DEFAULT.port})",
        opt[String]("path") action { (x, c) => c.copy(path = Paths.get(x)) } text s"Path of lims.yml file"
        ) text "Import lims.yml + .subreadset.xml file via file upload. If the path is a file, only that file is uploaded. If a directory, it is recursively scanned for lims.yml files to upload."

    cmd(Modes.GET_RUNCODE.name) action { (_, c) =>
      c.copy(command = (c) => println("with " + c), mode = Modes.GET_RUNCODE)
    } children(
        opt[String]("host") action { (x, c) => c.copy(host = x) } text s"Hostname of smrtlink server (Default: ${DEFAULT.host})",
        opt[Int]("port") action { (x, c) =>  c.copy(port = x)} text s"Services port on smrtlink server (Default: ${DEFAULT.port})",
        opt[String]("runcode") action { (x, c) => c.copy(runcode = x) } text s"Lookup LimsSubreadSet instances by lims.yml 'runcode' value"
        ) text "Lookup LimsSubreadSet entries by runcode"

    cmd(Modes.GET_EXPID.name) action { (_, c) =>
      c.copy(command = (c) => println("with " + c), mode = Modes.GET_EXPID)
    } children(
        opt[String]("host") action { (x, c) => c.copy(host = x) } text s"Hostname of smrtlink server (Default: ${DEFAULT.host})",
        opt[Int]("port") action { (x, c) =>  c.copy(port = x)} text s"Services port on smrtlink server (Default: ${DEFAULT.port})",
        opt[Int]("expid") action { (x, c) => c.copy(expid = x) } text s"Lookup LimsSubreadSet instances by lims.yml 'expid' value"
        ) text "Lookup LimsSubreadSet entries by expid"

    cmd(Modes.GET_UUID.name) action { (_, c) =>
      c.copy(command = (c) => println("with " + c), mode = Modes.GET_UUID)
    } children(
        opt[String]("host") action { (x, c) => c.copy(host = x) } text s"Hostname of smrtlink server (Default: ${DEFAULT.host})",
        opt[Int]("port") action { (x, c) =>  c.copy(port = x)} text s"Services port on smrtlink server (Default: ${DEFAULT.port})",
        opt[String]("uuid") action { (x, c) => c.copy(uuid = UUID.fromString(x)) } text s"Lookup LimsSubreadSet by UUID"
        ) text "Lookup LimsSubreadSet entry by UUID in .subreadset.xml file"

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show options and exit"

    opt[Unit]('v', "version") action { (x, c) =>
      showVersion()
      sys.exit(0)
    } text "Show version and exit"

    opt[Unit]("no-jvm-exit") action { (x, c) => c.copy(exitJvm = false) } text "Test flag. Disables JVM termination during CLI code testing."

    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
  }
}

object LimsClientToolsApp extends App
    with LimsClientToolParser
    with LimsClientToolRunner {

  implicit val TIMEOUT = 20 seconds

  def runCustomConfig(c: CustomConfig): Int = {
    c.mode match {
      case Modes.IMPORT => runImportLimsYml(c.host, c.port, c.path)
      case Modes.GET_RUNCODE => runGetSubreadsByRuncode(c.host, c.port, c.runcode)
      case Modes.GET_EXPID => runGetSubreadsByExp(c.host, c.port, c.expid)
      case Modes.GET_UUID => runGetSubreadByUUID(c.host, c.port, c.uuid)
      case unknown =>
        System.err.println(s"Unknown mode '$unknown'")
        1
    }
  }

  def runner(args: Array[String]) = {
    val co = parser.parse(args, DEFAULT)
    val exitCode = co.map(runCustomConfig).getOrElse(1)
    co.map(c => {
      // This is the ONLY place System.exit should be called
      if (c.exitJvm) {
        println(s"Exiting $NAME $VERSION with exitCode $exitCode")
        System.exit(exitCode)
      }
    })
  }

  runner(args)
}