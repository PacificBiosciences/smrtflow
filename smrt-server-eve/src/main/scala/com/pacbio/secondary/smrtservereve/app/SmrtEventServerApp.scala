package com.pacbio.secondary.smrtservereve.app

import java.io._
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}
import org.joda.time.format.DateTimeFormat
import org.apache.commons.io.FileUtils
import akka.actor.ActorSystem
import akka.util.Timeout
import akka.pattern._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server._
import akka.http.scaladsl.model.{
  HttpHeader,
  MediaTypes,
  Multipart,
  StatusCodes
}
import akka.http.scaladsl.Http
import spray.json._
import DefaultJsonProtocol._
import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.server.directives.FileInfo
import akka.http.scaladsl.settings.RoutingSettings
import akka.stream.ActorMaterializer
import com.pacbio.secondary.smrtlink.file.FileSizeFormatterUtil
import com.pacbio.common.models.Constants
import com.pacbio.secondary.smrtlink.services.utils.StatusGenerator
import com.pacbio.secondary.smrtlink.services.{
  PacBioService,
  PacBioServiceErrors,
  StatusService
}
import com.pacbio.secondary.smrtlink.time.SystemClock
import com.pacbio.secondary.smrtlink.models.{
  PacBioComponentManifest,
  SmrtLinkSystemEvent
}
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.TsSystemStatusManifest
import com.pacbio.secondary.smrtlink.analysis.techsupport.TechSupportConstants
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.common.utils.TarGzUtils
import com.pacbio.common.logging.LoggerOptions
import com.pacbio.secondary.smrtlink.app.{
  ActorSystemCakeProvider,
  BaseServiceConfigCakeProvider,
  ServiceLoggingUtils
}
import com.pacbio.secondary.smrtlink.auth.hmac.Signer

// Jam All the Event Server Components to create a pure Cake (i.e., not Singleton) app
// in here for first draft.

/**
  * This could be made more configurable, currently keep the
  * defaults hardcoded in this trait.
  */
trait HmacDirectives {

  val pattern = """^hmac (\S+):(\S+)$"""
  val authHeader = "Authentication"
  val regex = pattern.r

  /**
    * Auth Directive for validating the Hmac Authentication
    *
    * Trying to keep this as simple as possible.
    *
    * @param hmacSecret API secret key
    * @return
    */
  def validateHmac(hmacSecret: String): Directive0 = Directive[Unit] {
    inner => ctx =>
      {
        ctx.request.headers
          .find(_.is(authHeader.toLowerCase))
          .map(_.value()) match {
          case Some(rawHeaderValue) =>
            rawHeaderValue match {
              case regex(_, hash) =>
                if (Signer.valid(hash,
                                 hmacSecret,
                                 ctx.request.uri.path.toString())) {
                  inner(Unit)(ctx)
                } else {
                  ctx.reject(
                    AuthenticationFailedRejection(
                      CredentialsRejected,
                      HttpChallenge(ctx.request.uri.scheme, realm = None)))
                }
              case _ =>
                ctx.reject(
                  MalformedHeaderRejection(
                    authHeader,
                    s"Malformed header $rawHeaderValue"))
            }
            inner(Unit)(ctx)
          case _ =>
            ctx.reject(MissingHeaderRejection(s"Missing $authHeader"))
        }
      }
  }
}

// Push this back to FileSystemUtils in common
trait EveFileUtils extends LazyLogging {
  def createDirIfNotExists(p: Path): Path = {
    if (!Files.exists(p)) {
      logger.info(s"Creating dir(s) $p")
      Files.createDirectories(p)
    }
    p
  }

  def writeToFile(sx: String, path: Path) = {
    val bw = new BufferedWriter(new FileWriter(path.toFile))
    bw.write(sx)
    bw.close()
    path
  }
}

/**
  * Base Interface for Processing an Event
  *
  * This could log an event, send the event to ElasticSearch, or Kafka, etc...
  *
  *
  */
trait EventProcessor {

  /**
    * Name of the processor
    */
  val name: String

  /**
    * Process the event. If the processing of the event has failed, the
    * future should be failed.
    * @param event SL System event
    * @return
    */
  def process(event: SmrtLinkSystemEvent): Future[SmrtLinkSystemEvent]
}

/**
  * Logging of the Event
  */
class EventLoggingProcessor extends EventProcessor with LazyLogging {

  val name = "Logging Processor"

  def process(event: SmrtLinkSystemEvent) = Future {
    logger.info(s"Event $event")
    event
  }
}

/**
  * Write the event to a directory. a Directory within the root SL instance will
  * be created and events will be written to that dir with the UUID of the message
  * as the name.
  *
  * root-dir/{SL-SYSTEM-UUID}/{YYYY}/{MM}/{DD}/{EVENT-UUID}.json
  *
  * @param rootDir
  */
class EventFileWriterProcessor(rootDir: Path)
    extends EventProcessor
    with LazyLogging
    with EveFileUtils {

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  val name = s"File Writer Event Processor to Dir $rootDir"

  private def createSmrtLinkSystemDir(uuid: UUID): Path =
    createDirIfNotExists(rootDir.resolve(uuid.toString))

  private def createDateTimeSubDir(root: Path): Path = {

    def pad2(i: Int): String = {
      if (i < 10) { s"0$i" } else i.toString
    }

    val now = JodaDateTime.now()
    val px = root
      .resolve(now.getYear.toString)
      .resolve(pad2(now.getMonthOfYear))
      .resolve(pad2(now.getDayOfMonth))
    createDirIfNotExists(px)
  }

  def writeEvent(e: SmrtLinkSystemEvent): SmrtLinkSystemEvent = {
    val smrtLinkSystemDir = createSmrtLinkSystemDir(e.smrtLinkId)
    val dateTimeDir = createDateTimeSubDir(smrtLinkSystemDir)
    val eventPath = dateTimeDir.resolve(s"${e.uuid}.json")
    // logstash requires the json string to be on one line and has a newline at the end
    writeToFile(e.toJson.toString + "\n", eventPath)
    e
  }

  def process(event: SmrtLinkSystemEvent) = Future {
    blocking {
      writeEvent(event)
    }
  }
}

trait EventServiceBaseMicroService extends PacBioService {

  // Note, Using a single prefix of "api/v1" will not work as "expected"
  override def prefixedRoutes = pathPrefix("api" / "v1") {
    super.prefixedRoutes
  }
}

/**
  * Event Service for processing SmrtLinkSystemEvents and File uploads
  *
  * @param eventProcessor Event Processor, will write, or log system events
  * @param rootOutputDir root output dir for events
  * @param rootUploadFilesDir root output dir for files
  */
class EventService(eventProcessor: EventProcessor,
                   rootOutputDir: Path,
                   rootUploadFilesDir: Path,
                   apiSecret: String,
                   swaggerResourceName: String)(
    implicit val actorSystem: ActorSystem,
    implicit val materializer: ActorMaterializer)
    extends EventServiceBaseMicroService
    with HmacDirectives
    with LazyLogging
    with timeUtils
    with FileSizeFormatterUtil {

  // for getFromFile to work
  implicit val routing = RoutingSettings.default

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  val PREFIX_EVENTS = "events"
  val PREFIX_FILES = "files"

  val manifest = PacBioComponentManifest(
    "events",
    "Event Services",
    "0.1.0",
    "SMRT Server Event and general Messages service")

  logger.info(s"Creating Service with Event Processor ${eventProcessor.name}")

  def failIfNone[T](message: String): (Option[T] => Future[T]) = {
    case Some(value) => Future.successful(value)
    case _ => Future.failed(UnprocessableEntityError(message))
  }

  def eventRoutes: Route =
    pathPrefix(PREFIX_EVENTS) {
      pathEndOrSingleSlash {
        validateHmac(apiSecret) {
          post {
            entity(as[SmrtLinkSystemEvent]) { event =>
              complete(StatusCodes.Created -> eventProcessor.process(event))
            }
          }
        }
      }
    }

  def swaggerFile: Route =
    pathPrefix("swagger") {
      pathEndOrSingleSlash {
        get {
          getFromResource(swaggerResourceName)
        }
      }
    }

  private def resolveDestination(fileInfo: FileInfo): File = {
    logger.info(s"File Info $fileInfo")
    resolveOutputFile(fileInfo.fileName)
  }

  def uploadFileRoute: Route =
    pathPrefix(PREFIX_FILES) {
      pathEndOrSingleSlash {
        validateHmac(apiSecret) {
          post {
            storeUploadedFile("techsupport_tgz", resolveDestination) {
              case (fileInfo, file) =>
                complete(StatusCodes.Created -> createImportEvent(file))
            }
          }
        }
      }
    }

  def routes = eventRoutes ~ uploadFileRoute ~ swaggerFile

  /**
    * Unzip the .tar.gz or .tgz file next to the tar-zipped file. Then extract the TS Bundle manifest from the
    * tech-support-manifest.json in the root directory. The TS manifest json file must adhere to the
    * base data model.
    *
    * The general model is to treat the TS Manifest schema as a pass through layer and add the minimal necessary
    * data to the "message" body. Specfically,
    *
    * An SmrtLink System Event will be created by:
    * - passing through the TS Manifest as the "message"
    * - adding "uploadedBundlePath" to the root level of the "message"
    *
    * @param file Path to the TS manifest JSON file
    * @return
    */
  def createImportEvent(file: File): SmrtLinkSystemEvent = {

    val parent: Path = file.toPath.toAbsolutePath.getParent

    // this is kinda sloppy
    val name = file.getName.replace(".tar.gz", "").replace(".tgz", "")
    val outputDir = parent.resolve(name)

    logger.info(s"Uncompressing $file")
    // This will create the output dir
    TarGzUtils.uncompressTarGZ(file, outputDir.toFile)

    val manifestPath =
      outputDir.resolve(TechSupportConstants.DEFAULT_TS_MANIFEST_JSON)

    val mxStr = FileUtils.readFileToString(manifestPath.toFile, "UTF-8")

    val manifestJson = mxStr.parseJson

    // Casting to TsSystem because this is the "base" concrete type.
    val manifest = manifestJson.convertTo[TsSystemStatusManifest]

    // The model here is to try to be a pass-through layer. There's chances for collisions here
    // depending on models defined by users. Here we create the event and
    // add the unzipped bundle path to the event. See comments above
    val eventJson = manifestJson.asJsObject

    val bundleJx: Map[String, JsValue] = Map(
      "uploadedBundlePath" -> JsString(outputDir.toAbsolutePath.toString))

    val bundleJson = JsObject(eventJson.fields ++ bundleJx)

    val eventId = UUID.randomUUID()

    SmrtLinkSystemEvent(manifest.smrtLinkSystemId,
                        manifest.bundleTypeId,
                        manifest.bundleTypeVersion,
                        eventId,
                        JodaDateTime.now(),
                        bundleJson,
                        manifest.dnsName)
  }

  def isSupportedFileExt(exts: Seq[String], fileName: String): Boolean =
    exts.map(ext => fileName.endsWith(ext)).reduce(_ || _)

  def onlyAllowTarGz(fileName: String): Future[String] = {
    val supportedExts = Seq(".tgz", ".tar.gz")
    val errorMessage =
      s"Invalid file '$fileName'. Supported types ${supportedExts.reduce(_ + ", " + _)}"
    if (isSupportedFileExt(supportedExts, fileName)) Future(fileName)
    else Future.failed(UnprocessableEntityError(errorMessage))
  }

  /**
    * Resolve Output files to a directory structure by date YYYY/MM/DD/UUID-filename relative to the root directory
    *
    * @param fileName file name (not path) of the tgz file
    * @return
    */
  def resolveOutputFile(fileName: String): File = {
    val now = JodaDateTime.now()

    val fm = DateTimeFormat.forPattern("MM")
    val fd = DateTimeFormat.forPattern("dd")

    val outputDir = rootUploadFilesDir.resolve(
      s"${now.getYear}/${fm.print(now)}/${fd.print(now)}")

    logger.info(s"Resolved to output dir $outputDir")

    if (!Files.exists(outputDir)) {
      Files.createDirectories(outputDir)
    }

    val i = UUID.randomUUID()
    val f = outputDir.resolve(s"$i-$fileName").toFile
    logger.info(s"Resolved $fileName to local output $f")
    f
  }
}

trait EventServiceConfigCakeProvider extends BaseServiceConfigCakeProvider {

  override lazy val systemName = "smrt-eve"
  // This should be loaded from the application.conf with an ENV var mapping
  lazy val eventMessageDir: Path =
    Paths.get(conf.getString("smrtflow.event.eventRootDir")).toAbsolutePath
  // Make this independently configurable
  lazy val eventUploadFilesDir: Path = eventMessageDir.resolve("files")
  lazy val swaggerJson = "eventserver_swagger.json"
}

trait EventServicesCakeProvider {
  this: ActorSystemCakeProvider with EventServiceConfigCakeProvider =>

  lazy val statusGenerator = new StatusGenerator(new SystemClock(),
                                                 systemName,
                                                 systemUUID,
                                                 Constants.SMRTFLOW_VERSION)

  lazy val eventProcessor = new EventFileWriterProcessor(eventMessageDir)
  // All Service instances go here
  lazy val services: Seq[PacBioService] = Seq(
    new EventService(eventProcessor,
                     eventMessageDir,
                     eventUploadFilesDir,
                     apiSecret,
                     swaggerJson)(actorSystem, materializer),
    new StatusService(statusGenerator)
  )
}

trait RootEventServerCakeProvider extends RouteConcatenation {
  this: ActorSystemCakeProvider with EventServicesCakeProvider =>

  lazy val allRoutes: Route = services.map(_.prefixedRoutes).reduce(_ ~ _)

}

trait EventServerCakeProvider
    extends LazyLogging
    with timeUtils
    with EveFileUtils
    with PacBioServiceErrors
    with ServiceLoggingUtils {
  this: RootEventServerCakeProvider
    with EventServiceConfigCakeProvider
    with ActorSystemCakeProvider =>

  implicit val timeout = Timeout(30.seconds)

  implicit val customExceptionHandler = pacbioExceptionHandler
  implicit val customRejectionHandler = pacBioRejectionHandler

  lazy val startupTimeOut = 30.seconds

  // Mocked out. Fail the future if any invalid option is detected
  def validateOption(): Future[String] =
    Future.successful(s"Successfully validated System options.")

  /**
    * Pre System Startup
    *
    * - validate config
    * - create output directory to write events/messages to
    *
    * @return
    */
  private def preStartUpHook(): Future[String] =
    for {
      validMsg <- validateOption()
      createdDir <- Future { createDirIfNotExists(eventMessageDir) }
      createdFilesDir <- Future { createDirIfNotExists(eventUploadFilesDir) }
      message <- Future {
        s"Successfully created $createdDir and $createdFilesDir"
      }
    } yield
      Seq(validMsg, message, "Successfully executed preStartUpHook")
        .mkString("\n")

  private def startServices(): Future[String] = {
    Http()
      .bindAndHandle(logResponseTimeRoutes(allRoutes),
                     systemHost,
                     port = systemPort)
      .map(_ => s"Successfully started up on $systemHost:$systemPort")
  }

  /**
    * Post Services Startup Hook
    * @return
    */
  private def postStartUpHook(): Future[String] =
    Future.successful("Successfully ran PostStartup Hook")

  /**
    * Public method for starting the Entire System
    *
    * 1. Call PreStart Hook
    * 2. Start Services
    * 3. Call PostStart Hook
    *
    * Wrapped in a Try and will shutdown if the system has a failure on startup.
    *
    */
  def startSystem(): Unit = {
    val startedAt = JodaDateTime.now()
    logger.info(
      s"smrtflow ${Constants.SMRTFLOW_VERSION} Attempting to bind to $systemHost on port:$systemPort")
    val fx = for {
      preMessage <- preStartUpHook()
      startMessage <- startServices()
      postStartMessage <- postStartUpHook()
    } yield
      Seq(
        preMessage,
        startMessage,
        postStartMessage,
        s"Successfully Started System in ${computeTimeDeltaFromNow(startedAt)} sec")
        .reduce(_ + "\n" + _)

    val result = Try(Await.result(fx, startupTimeOut))

    result match {
      case Success(msg) =>
        logger.info(msg)
        println(s"Successfully started up System")
      case Failure(ex) =>
        // This should call unbind?
        // flatMap(_.unbind()) // trigger unbinding from the port
        val msg = s"Failed to start ${ex.getMessage}"
        logger.error(msg)
        actorSystem.terminate()
        System.err.println(msg)
        System.exit(1)
    }

  }
}

// Construct the Applications from Components. This will be used in the Tests as well
object SmrtEventServer
    extends EventServiceConfigCakeProvider
    with ActorSystemCakeProvider
    with EventServicesCakeProvider
    with RootEventServerCakeProvider
    with EventServerCakeProvider {}

object SmrtEventServerApp extends App {

  import SmrtEventServer._

  LoggerOptions.parseAddDebug(args)
  startSystem()
}
