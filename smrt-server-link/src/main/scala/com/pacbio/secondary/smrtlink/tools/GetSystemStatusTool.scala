package com.pacbio.secondary.smrtlink.tools

import java.net.URL
import java.nio.file.{Files, Path}

import akka.actor.{ActorSystem, Scheduler}
import akka.pattern.after
import com.pacbio.common.client.{Retrying, UrlUtils}
import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.analysis.configloaders.ConfigLoader
import com.pacbio.secondary.analysis.tools.{CommandLineToolRunner, ToolFailure}
import com.pacbio.secondary.smrtlink.client.AnalysisServiceAccessLayer
import com.typesafe.scalalogging.LazyLogging
import scopt.OptionParser
import spray.client.pipelining._
import spray.http._
import spray.json._
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

/**
  * Migration of the get-status python code to scala to reduce duplication
  * and reuse the scala client(s)
  *
  * --max-retries
  * --sleep-time
  * -i --subcomponent-id  {smrtlink-analysis,tomcat,wso2,smrtview}
  * --version
  * --help
  * --log-file
  * --debug
  *
  * Created by mkocher on 3/1/17.
  */

// Centralize the subcomponet ids
object SubComponentIds {
  final val SLA = "smrtlink"
  final val TOMCAT = "tomcat"
  final val SVIEW = "smttview"
  final val WSO2 = "wso2"

  final val ALL = Set(SLA, TOMCAT, SVIEW, WSO2)

}


case class GetSystemStatusToolOptions(host: String = "localhost",
                                      maxRetries: Int = 3,
                                      sleepTimeSec: Int = 5,
                                      subComponentId: Option[String] = None,
                                      smrtLinkPort: Int,
                                      smrtViewPort: Int,
                                      tomcatPort: Int,
                                      wso2Port: Int
                                     ) extends LoggerConfig

/**
  * This abstraction encapsulates getting the Status of the SubComponent from both the
  *  Services and the PID file (if provided).
  *
  */
trait GetSubComponentStatus {
  val name: String
  val host: String
  val port: Int
  val pidFile: Option[Path]

  def getServiceStatus(maxRetries: Int = 3, retryDelay: FiniteDuration = 1.seconds)(implicit actorSystem: ActorSystem): Future[String]

  def getPidStatus(): Future[String] = {
    pidFile match {
      case Some(path) => checkPidFile(path)
      case _ => Future.successful("No PID file provided. Skipping PID file status check.")
    }
  }

  /**
    * Check for the Presence of PID file for subcomponent
    *
    * @param path
    */
  private def checkPidFile(path: Path): Future[String] =
    if (Files.exists(path)) Future(s"Found PID file $path")
    else Future.failed(throw new Exception(s"SubComponent $name is down. Unable to find PID file $path"))

  def getStatus(maxRetries: Int = 3, retryDelay: FiniteDuration = 1.second)(implicit actorSystem: ActorSystem): Future[String] = {
    for {
      pidStatus <- getPidStatus()
      serviceStatus <- getServiceStatus(maxRetries, retryDelay)
    } yield s"$pidStatus\n$serviceStatus"
  }
}

class GetSmrtViewComponentStatus(override val host: String, override val port: Int, override val pidFile: Option[Path]) extends GetSubComponentStatus{
  val name = SubComponentIds.SVIEW
  def getServiceStatus(maxRetries: Int = 3, retryDelay: FiniteDuration = 1.seconds)(implicit actorSystem: ActorSystem): Future[String] = {
    val client = new SmrtViewClient(host, port)
    client.getStatusWithRetry(maxRetries)
  }
}

class GetSmrtLinkComponentStatus(override val host: String, override val port: Int, override val pidFile: Option[Path]) extends GetSubComponentStatus{
  val name = SubComponentIds.SLA
  def getServiceStatus(maxRetries: Int = 3, retryDelay: FiniteDuration = 1.second)(implicit actorSystem: ActorSystem): Future[String] = {
    val client = new AnalysisServiceAccessLayer(host, port)
    //FIXME(mpkocher)(3-2-2017) This needs to support retryDelay
    client.getStatusWithRetry(maxRetries).map(status => s"Successfully got status ${status.message}")
  }
}

class GetTomcatComponentStatus(override val host: String, override val port: Int, override val pidFile: Option[Path]) extends GetSubComponentStatus{
  val name = SubComponentIds.TOMCAT
  def getServiceStatus(maxRetries: Int = 3, retryDelay: FiniteDuration = 1.second)(implicit actorSystem: ActorSystem): Future[String] = {
    val client = new TomcatClient(host, port)
    client.getStatusWithRetry(maxRetries, retryDelay)
  }
}

class GetWso2ComponentStatus(override val host: String, override val port: Int, override val pidFile: Option[Path]) extends GetSubComponentStatus{
  val name = SubComponentIds.WSO2

  //FIXME(mpkocher)(3-2-2017) This needs to be replaced by the AmClient. I Don't see a getStatus method
  def getServiceStatus(maxRetries: Int = 3, retryDelay: FiniteDuration = 1.second)(implicit actorSystem: ActorSystem): Future[String] =
    Future.successful("WARNING! Skipping WSO2 status check. Not Supported")
}


// This is kinda brutal. The Client interface needs to have a base trait or class
// to enable reuse of code. I'll have to copy in the retry funcs from ServerAccessLayer
abstract class BaseSmrtClient(baseUrl: URL)(implicit actorSystem: ActorSystem) extends Retrying{

  // Sanity Status EndPoint (must have leading slash, or can be empty string)
  val EP_STATUS: String

  lazy val STATUS_URL = toUrl(EP_STATUS)


  def toUrl(segment: String): URL =
    new URL(baseUrl.getProtocol, baseUrl.getHost, baseUrl.getPort, segment)

  def simplePipeline: HttpRequest => Future[HttpResponse] = sendReceive

  // The components that we control should implement the ServiceStatus as the interface
  def getStatus(): Future[String] = simplePipeline { Get(STATUS_URL.toString) }
      .map(_ => s"Successfully got status of $STATUS_URL")

  def getStatusWithRetry(maxRetries: Int = 3, retryDelay: FiniteDuration = 1.second): Future[String] =
    retry[String](getStatus, retryDelay, maxRetries)(actorSystem.dispatcher, actorSystem.scheduler)


}

class SmrtViewClient(baseUrl: URL)(implicit actorSystem: ActorSystem) extends BaseSmrtClient(baseUrl) {
  def this(host: String, port: Int)(implicit actorSystem: ActorSystem) {
    this(UrlUtils.convertToUrl(host, port))(actorSystem)
  }
  val EP_STATUS = "/smrtview/services/VersionService.VersionServiceHttpEndpoint/getVersion"
}

class TomcatClient(baseUrl: URL)(implicit actorSystem: ActorSystem) extends BaseSmrtClient(baseUrl) {
  def this(host: String, port: Int)(implicit actorSystem: ActorSystem) {
    this(UrlUtils.convertToUrl(host, port))(actorSystem)
  }
  val EP_STATUS = "/"
}


trait GetSubSystemStatus extends LazyLogging{

  def andLog[T](sx: T): T = {
    logger.info(sx.toString)
    sx
  }

  def getSmrtLinkStatus(host: String, port: Int, maxReties: Int, retryDelay: FiniteDuration)(implicit actorSystem: ActorSystem): Future[String] = {
    val client = new GetSmrtLinkComponentStatus(host, port, None)
    client.getServiceStatus(maxReties)(actorSystem)
  }

  def getSmrtViewStatus(host: String, port: Int, maxRetries: Int, retryDelay: FiniteDuration)(implicit actorSystem: ActorSystem): Future[String] = {
    val client = new GetSmrtViewComponentStatus(host, port, None)
    client.getServiceStatus(maxRetries)
  }

  def getTomcatStatus(host: String, port: Int, maxRetries: Int, retryDelay: FiniteDuration)(implicit actorSystem: ActorSystem): Future[String] = {
    val client = new GetTomcatComponentStatus(host, port, None)
    client.getServiceStatus(maxRetries)
  }

  def getWso2Status(host: String, port: Int, maxRetries: Int, retryDelay: FiniteDuration)(implicit actorSystem: ActorSystem): Future[String] = {
    val client = new GetWso2ComponentStatus(host, port, None)
    client.getServiceStatus(maxRetries)
  }


  def getSystemStatus(host: String, smrtLinkPort: Int, smrtViewPort: Int, tomcatPort: Int, wso2Port: Int, maxRetries: Int, retryDelay: FiniteDuration = 1.second)(implicit actorSystem: ActorSystem): Future[String] = {
    for {
      smrtLinkStatus <- getSmrtLinkStatus(host, smrtLinkPort, maxRetries, retryDelay: FiniteDuration).map(andLog)
      smrtViewStatus <- getSmrtViewStatus(host, smrtViewPort, maxRetries, retryDelay: FiniteDuration).map(andLog)
      tomcatStatus <- getTomcatStatus(host, tomcatPort, maxRetries, retryDelay: FiniteDuration).map(andLog)
      wso2Status <- getWso2Status(host, wso2Port, maxRetries, retryDelay: FiniteDuration).map(andLog)
    } yield "Successfully Got status of Subcomponents: SL Analysis, SMRT View, Tomcat and WSO2"
  }

  def getSubComponentSystemStatusById(id: String, host: String, port: Int, maxRetries: Int, retryDelay: FiniteDuration = 1.second)(implicit actorSystem: ActorSystem): Future[String] = {
    id.toLowerCase match {
      case SubComponentIds.SLA => getSmrtLinkStatus(host, port, maxRetries, retryDelay: FiniteDuration).map(andLog)
      case SubComponentIds.SVIEW => getSmrtViewStatus(host, port, maxRetries, retryDelay: FiniteDuration).map(andLog)
      case SubComponentIds.TOMCAT => getTomcatStatus(host, port, maxRetries, retryDelay: FiniteDuration).map(andLog)
      case SubComponentIds.WSO2 => getWso2Status(host, port, maxRetries, retryDelay: FiniteDuration).map(andLog)
      case x => Future.failed(throw new Exception(s"Invalid Subcomponent id '$x'"))
    }
  }
}

object GetSubSystemStatus extends GetSubSystemStatus

// The Tool is strongly coupled to the config.json file and the file system
// to get the *.pid files.
trait GetSystemStatusDefaultLoader extends ConfigLoader {

  private lazy val defaultSmrtLinkPort = conf.getInt("smrtflow.server.port")
  private lazy val defaultTomcatPort = Try { conf.getInt("smrtflow.pacBioSystem.tomcatPort")}.getOrElse(8080)
  private lazy val defaultSmrtViewPort = Try { conf.getInt("smrtflow.pacBioSystem.smrtViewPort")}.getOrElse(8084)
  // This needs to stop being hardcoded everywhere. The chickens will come home and roust at some point.
  private lazy val defaultWso2Port = 8243 // 9443

  lazy val defaults = GetSystemStatusToolOptions("localhost", 3, 5, None, defaultSmrtLinkPort, defaultSmrtViewPort, defaultTomcatPort, defaultWso2Port)

}


object GetSystemStatusTool extends CommandLineToolRunner[GetSystemStatusToolOptions] with GetSystemStatusDefaultLoader{

  val VERSION = "0.1.0"
  val DESCRIPTION = "Get Status of the SMRT Link Subcomponent Systems"
  val toolId = "smrtflow.tools.get_system_status"

  private def validateSubComponentId(sx: String): Either[String, Unit] = {
    if (SubComponentIds.ALL contains sx) Right(Unit)
    else Left(s"Invalid subcomponent id '$sx'. Valid Subcomponent ids ${SubComponentIds.ALL}")
  }

  lazy val parser = new OptionParser[GetSystemStatusToolOptions]("get-system-status") {

    def toM(sx: String) = s"(default :$sx)"

    opt[String]("host")
        .action((x, c) => c.copy(host = x))
        .text(s"Host name ${toM(defaults.host)}")

    opt[Int]("max-retries")
        .action((x, c) => c.copy(maxRetries = x))
        .text(s"Max Number of retries to the status calls to the Services ${toM(defaults.maxRetries.toString)}")

    opt[Int]("sleep-time")
        .action((x, c) => c.copy(sleepTimeSec = x))
        .text(s"Sleep time (in sec) between each service retry status check ${toM(defaults.sleepTimeSec.toString)}")

    opt[String]('i', "subcomponent-id")
        .action((x, c) => c.copy(subComponentId = Some(x)))
        .validate(validateSubComponentId)
        .text("Get the status of specific subcomponent of the system")

    opt[Int]("smrtlink-port")
        .action((x, c) => c.copy(smrtLinkPort = x))
        .text(s"SmrtLink Analysis Services port ${toM(defaults.smrtLinkPort.toString)}")

    opt[Int]("smrtview-port")
        .action((x, c) => c.copy(smrtLinkPort = x))
        .text(s"SmrtView port ${toM(defaults.smrtViewPort.toString)}")

    opt[Int]("tomcat-port")
        .action((x, c) => c.copy(smrtLinkPort = x))
        .text(s"SmrtLink Tomcat port ${toM(defaults.tomcatPort.toString)}")

    opt[Int]("wso2-port")
        .action((x, c) => c.copy(smrtLinkPort = x))
        .text(s"WSO2 publisher port ${toM(defaults.wso2Port.toString)}")

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show Options and exit"

    opt[Unit]("version") action { (x, c) =>
      showVersion
      sys.exit(0)
    } text "Show tool version and exit"

    // add the shared `--debug` and logging options
    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])

  }

  private def getSystemIdToPort(opts: GetSystemStatusToolOptions): Map[String, Int] =
    Map(
      SubComponentIds.SLA -> opts.smrtLinkPort,
      SubComponentIds.SVIEW -> opts.smrtViewPort,
      SubComponentIds.TOMCAT -> opts.tomcatPort,
      SubComponentIds.WSO2 -> opts.wso2Port)


  /**
    * Util to block and run tool
    * @param fx func to run
    * @param timeOut timeout for the blocking call
    * @return
    */
  def runAndBlock(fx: => Future[String], timeOut: Duration): Try[String] =
    Try { Await.result(fx, timeOut) }

  override def runTool(opts: GetSystemStatusToolOptions): Try[String] = {

    val timeOutSec = (opts.maxRetries + 1) * opts.sleepTimeSec
    val timeOut = timeOutSec.seconds
    val retryDelay: FiniteDuration = opts.sleepTimeSec.seconds

    // get a map of the system to port for the case where -i was given
    val systemPort = opts.subComponentId
        .flatMap(ix => getSystemIdToPort(opts).get(ix)
            .map(port => (ix, port)))

    implicit val actorSystem = ActorSystem("tool")

    val result = systemPort match {
      case Some(Tuple2(ix, port)) =>
        runAndBlock(GetSubSystemStatus.getSubComponentSystemStatusById(ix, opts.host, port, opts.maxRetries, retryDelay), timeOut)
      case _ =>
        runAndBlock(GetSubSystemStatus.getSystemStatus(opts.host, opts.smrtLinkPort, opts.smrtViewPort, opts.tomcatPort, opts.wso2Port, opts.maxRetries,retryDelay), timeOut)
    }

    logger.debug("Shutting down actor system")
    actorSystem.shutdown()

    result
  }

  // for backward compat with old model
  def run(config: GetSystemStatusToolOptions) =
    Left(ToolFailure(toolId, 0, "NOT SUPPORTED"))
}

object GetSystemStatusToolApp extends App {
  import GetSystemStatusTool._
  runnerWithArgsAndExit(args)
}