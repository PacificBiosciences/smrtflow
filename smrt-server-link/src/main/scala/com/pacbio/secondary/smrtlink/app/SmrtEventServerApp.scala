package com.pacbio.secondary.smrtlink.app

import java.net.BindException
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}
import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.util.Timeout
import akka.pattern._
import com.pacbio.common.app.StartupFailedException
import com.pacbio.common.models.{Constants, PacBioComponentManifest}
import com.pacbio.common.services.utils.StatusGenerator
import com.pacbio.common.services.{PacBioService, RoutedHttpService, StatusService}
import com.pacbio.common.time.SystemClock
import com.pacbio.secondary.analysis.configloaders.ConfigLoader
import com.pacbio.secondary.smrtlink.client.EventServerClient
import com.pacbio.secondary.smrtlink.models.{SmrtLinkJsonProtocols, SmrtLinkSystemEvent}
import com.typesafe.scalalogging.LazyLogging
import spray.can.Http
import spray.routing.{Route, RouteConcatenation}
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing._
import DefaultJsonProtocol._
import com.pacbio.logging.LoggerOptions
import com.pacbio.secondary.analysis.tools.timeUtils

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

// Jam All the Event Server Components to create a pure Cake (i.e., not Singleton) app
// in here for first draft.

trait EventServiceBaseMicroService extends PacBioService {

  // Note, Using a single prefix of "api/v1" will not work as "expected"
  override def prefixedRoutes = pathPrefix("api" / "v1") { super.prefixedRoutes }
}

class EventService extends EventServiceBaseMicroService with LazyLogging{

  import SmrtLinkJsonProtocols._

  val PREFIX_EVENTS = "events"

  val manifest = PacBioComponentManifest("events", "Event Services", "0.1.0",
    "SMRT Server Event and general Messages service")

  def eventRoutes: Route =
    pathPrefix(PREFIX_EVENTS) {
      pathEndOrSingleSlash {
        get {
          complete {
            Map("status" -> "OK", "comment" -> "Mocked out Get").toJson
          }
        } ~ post {
            entity(as[SmrtLinkSystemEvent]) { event =>
            complete {
              created {
                logger.info(s"Logged Event $event")
                event
              }
            }
          }
        }
      }
    }

  def routes = eventRoutes
}

/**
  * Build the App using the "Cake" Patten leveraging "self" traits.
  * This method defined a model to build the app in type-safe way.
  *
  * Adding "Cake" to the naming to avoid name collisions with the
  * Singleton Providers approach.
  *
  * Note that every definition must use a lazy val or def to use the
  * cake pattern correctly.
  */
trait EventServiceConfigCakeProvider extends ConfigLoader{
  // This should be loaded from the application.conf with an ENV var mapping
  lazy val eventMessageDir: Path = Paths.get("/tmp/events")

  lazy val systemName = "smrt-events"
  lazy val systemPort = conf.getInt("smrtflow.server.port")
  lazy val systemHost = "0.0.0.0"
  lazy val systemUUID = UUID.randomUUID()

}

trait ActorSystemCakeProvider {
  this: EventServiceConfigCakeProvider =>
  implicit lazy val actorSystem = ActorSystem(systemName)
}

trait EventServicesCakeProvider {
  this: ActorSystemCakeProvider with EventServiceConfigCakeProvider =>

  lazy val statusGenerator = new StatusGenerator(new SystemClock(), systemName, systemUUID, Constants.SMRTFLOW_VERSION)

  // All Service instances go here
  lazy val services: Seq[PacBioService] = Seq(new EventService, new StatusService(statusGenerator))
}

trait RootEventServerCakeProvider extends RouteConcatenation{
  this: ActorSystemCakeProvider with EventServicesCakeProvider =>

  lazy val allRoutes:Route = services.map(_.prefixedRoutes).reduce(_ ~ _)

  lazy val rootService = actorSystem.actorOf(Props(new RoutedHttpService(allRoutes)))
}

trait EventServerCakeProvider extends LazyLogging with timeUtils{
  this: RootEventServerCakeProvider
      with EventServiceConfigCakeProvider
      with ActorSystemCakeProvider =>

  implicit val timeout = Timeout(10.seconds)

  lazy val startupTimeOut = 10.seconds
  lazy val eventServiceClient = new EventServerClient(systemHost, systemPort)

  def createDirIfNotExists(p: Path): Path = {
    if (!Files.exists(p)) {
      Files.createDirectories(p)
    }
    p
  }

  // Mocked out. Fail the future if any invalid option is detected
  def validateOption(): Future[String] =
    Future {"Successfully validated System options."}

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
      createdDir <- Future {createDirIfNotExists(eventMessageDir)}
      message <- Future { s"Successfully created $createdDir" }
    } yield s"$validMsg\n$message\nSuccessfully executed preStartUpHook"


  private def startServices(): Future[String] = {
    (IO(Http)(actorSystem) ? Http.Bind(rootService, systemHost, port = systemPort)) flatMap  {
      case r: Http.CommandFailed => Future.failed(new BindException(s"Failed to bind to $systemHost:$systemPort"))
      case _ => Future {s"Successfully started up on $systemHost:$systemPort" }
    }
  }

  /**
    * Run a Sanity Check from out Client to make sure the client lib
    * and Server can successfully communicate.
    * @return
    */
  private def postStartUpHook(): Future[String] = {
    val startUpEventMessage =
      SmrtLinkSystemEvent(systemUUID, "smrt_server_startup",
        UUID.randomUUID(),
        JodaDateTime.now, JsObject.empty)

    for {
      status <- eventServiceClient.getStatus
      m <- Future {s"Client Successfully got Status ${status.version} ${status.message}"}
      sentEvent <- eventServiceClient.sendSmrtLinkSystemEvent(startUpEventMessage)
      msgEvent <- Future {s"Successfully sent message $sentEvent"}
    } yield s"$m\nSuccessfully ran PostStartup Hook"
  }


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
  def startSystem() = {
    val startedAt = JodaDateTime.now()
    val fx = for {
      preMessage <- preStartUpHook()
      startMessage <- startServices()
      postStartMessage <- postStartUpHook()
    } yield Seq(preMessage, startMessage, postStartMessage, s"Successfully Started System in ${computeTimeDeltaFromNow(startedAt)} sec").reduce(_ + "\n" + _)


    val result = Try { Await.result(fx, startupTimeOut) }

    result match {
      case Success(msg) =>
        logger.info(msg)
        println("Successfully started up System")
      case Failure(ex) =>
        IO(Http)(actorSystem) ! Http.CloseAll
        actorSystem.shutdown()
        throw new StartupFailedException(ex)
    }

  }

}

// Construct the Applications from Components
object SmrtEventServerApp extends EventServiceConfigCakeProvider
    with ActorSystemCakeProvider
    with EventServicesCakeProvider
    with RootEventServerCakeProvider
    with EventServerCakeProvider with App {


  LoggerOptions.parseAddDebug(args)
  startSystem()
}


