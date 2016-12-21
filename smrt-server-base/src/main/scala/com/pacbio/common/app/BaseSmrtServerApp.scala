package com.pacbio.common.app

import java.lang.management.ManagementFactory
import java.net.BindException

import akka.actor.{ActorRefFactory, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.pacbio.common.actors._
import com.pacbio.common.auth.{AuthenticatorImplProvider, JwtUtilsImplProvider}
import com.pacbio.common.cleanup.CleanupSchedulerProvider
import com.pacbio.common.database._
import com.pacbio.common.dependency.{DefaultConfigProvider, SetBindings, Singleton, TypesafeSingletonReader}
import com.pacbio.common.logging.LoggerFactoryImplProvider
import com.pacbio.common.models.MimeTypeDetectors
import com.pacbio.common.services._
import com.pacbio.common.services.utils.StatusGeneratorProvider
import com.pacbio.common.time.SystemClockProvider
import com.pacbio.logging.LoggerOptions
import com.pacbio.secondary.analysis.configloaders.ConfigLoader
import com.typesafe.scalalogging.LazyLogging
import spray.can.Http
import spray.routing.Route
import spray.servlet.WebBoot

import scala.collection.JavaConversions._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import scala.util.control.ControlThrowable

object BaseSmrtServerApp

class StartupFailedException(cause: Throwable)
  extends RuntimeException("Startup failed", cause)
  with ControlThrowable

// TODO(smcclellan): This is getting too monolithic, break it up into modules
trait CoreProviders extends
  SetBindings with
  DefaultConfigProvider with
  ServiceRoutesProvider with
  ServiceManifestsProvider with
  ManifestServiceProvider with
  HealthServiceProvider with
  InMemoryHealthDaoProvider with
  LogServiceProvider with
  DatabaseLogDaoProvider with
  CleanupServiceProvider with
  CleanupServiceActorRefProvider with
  InMemoryCleanupDaoProvider with
  CleanupSchedulerProvider with
  StatusServiceProvider with
  StatusGeneratorProvider with
  UserServiceProvider with
  ConfigServiceProvider with
  CommonFilesServiceProvider with
  DiskSpaceServiceProvider with
  MimeTypeDetectors with
  SubSystemComponentServiceProvider with
  SubSystemResourceServiceProvider with
  ActorSystemProvider with
  BaseSmrtServerDatabaseConfigProviders with
  JwtUtilsImplProvider with
  AuthenticatorImplProvider with
  LoggerFactoryImplProvider with
  SystemClockProvider with ConfigLoader{

  val serverPort: Singleton[Int] = Singleton(() => conf.getInt("smrtflow.server.port"))
  val serverHost: Singleton[String] = Singleton(() => conf.getString("smrtflow.server.host"))

  override val actorSystemName = Some("base-smrt-server")

  override val buildPackage: Singleton[Package] = Singleton(getClass.getPackage)

  override val baseServiceId: Singleton[String] = Singleton("smrtlink_common")

  override val logDaoDatabaseConfigProvider: DatabaseConfigProvider = new TypesafeDatabaseConfigProvider {
    override val databaseConfigPath = Singleton("log")
  }

  override val logback: Singleton[Boolean] = Singleton(true)
}

trait AuthenticatedCoreProviders extends
  SetBindings with
  DefaultConfigProvider with
  ServiceComposer with
  ManifestServiceProviderx with
  HealthServiceProviderx with
  InMemoryHealthDaoProvider with
  LogServiceProviderx with
  DatabaseLogDaoProvider with
  CleanupServiceProviderx with
  CleanupServiceActorRefProvider with
  InMemoryCleanupDaoProvider with
  CleanupSchedulerProvider with
  StatusServiceProviderx with
  StatusGeneratorProvider with
  UserServiceProviderx with
  ConfigServiceProviderx with
  CommonFilesServiceProviderx with
  DiskSpaceServiceProviderx with
  MimeTypeDetectors with
  SubSystemComponentServiceProviderx with
  SubSystemResourceServiceProviderx with
  ActorSystemProvider with
  BaseSmrtServerDatabaseConfigProviders with
  JwtUtilsImplProvider with
  AuthenticatorImplProvider with
  LoggerFactoryImplProvider with
  SystemClockProvider with ConfigLoader{

  val serverPort: Singleton[Int] = Singleton(() => conf.getInt("smrtflow.server.port"))
  val serverHost: Singleton[String] = Singleton(() => conf.getString("smrtflow.server.host"))

  override val actorSystemName = Some("base-smrt-server")

  override val buildPackage: Singleton[Package] = Singleton(getClass.getPackage)

  override val baseServiceId: Singleton[String] = Singleton("smrtlink_common")

  override val logDaoDatabaseConfigProvider: DatabaseConfigProvider = new TypesafeDatabaseConfigProvider {
    override val databaseConfigPath = Singleton("log")
  }

  override val logback: Singleton[Boolean] = Singleton(true)
}

trait BaseApi {
  val providers: ActorSystemProvider with RouteProvider

  // Override these with custom startup and shutdown logic
  def startup(): Unit = ()
  def shutdown(): Unit = ()

  class ServiceActor(route: Route) extends RoutedHttpService(route: Route) {
    override def preStart(): Unit = startup()
    override def postStop(): Unit = shutdown()
  }

  lazy val system = providers.actorSystem()
  lazy val routes = providers.routes()
  lazy val rootService = system.actorOf(Props(new ServiceActor(routes)), name = "ServiceActor")

    // This is needed with Mixin routes from traits that only extend HttpService
  def actorRefFactory: ActorRefFactory = system

  sys.addShutdownHook(system.shutdown())
}

trait BaseServer extends LazyLogging {
  this: BaseApi =>

  implicit val timeout = Timeout(10.seconds)

  val host: String
  val port: Int

  def start = {
    logger.info("Starting App")
    logger.info("Java Version: " + System.getProperty("java.version"))
    logger.info("Java Home: " + System.getProperty("java.home"))
    val runtimeMxBean = ManagementFactory.getRuntimeMXBean
    val arguments = runtimeMxBean.getInputArguments
    logger.info("Java Args: " + arguments.mkString(" "))

    val f: Future[Option[BindException]] = (IO(Http)(system) ? Http.Bind(rootService, host, port = port)) map {
      case r: Http.CommandFailed => Some(new BindException(s"Failed to bind to $host:$port"))
      case r => None
    }

    Await.result(f, 10.seconds) map { e =>
      IO(Http)(system) ! Http.CloseAll
      system.shutdown()
      throw new StartupFailedException(e)
    }
  }
}

/**
 * This is used for spray-can http server which can be started via 'sbt run'
 */
object BaseSmrtServer extends App with BaseServer with BaseApi {
  override val providers: CoreProviders = new CoreProviders {}
  override val host = providers.serverHost()
  override val port = providers.serverPort()

  override def startup(): Unit = providers.cleanupScheduler().scheduleAll()

  LoggerOptions.parseAddDebug(args)

  start
}

/**
 * Build our servlet app using tomcat
 *
 * <p> Note that the port used here is set in build.sbt when running locally
 * Used for running within tomcat via 'container:start'
 */
class BaseSmrtServerServlet extends WebBoot with BaseApi {
  override val providers: CoreProviders = new CoreProviders {}
  override val serviceActor = rootService
}
