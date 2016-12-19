package com.pacbio.common.app

import java.lang.management.ManagementFactory
import java.net.BindException

import akka.actor.{ActorRefFactory, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.pacbio.common.actors._
import com.pacbio.common.auth.{BaseRolesInit, JwtUtilsImplProvider, FakeAuthenticatorProvider, AuthenticatorImplProvider}
import com.pacbio.common.cleanup.CleanupSchedulerProvider
import com.pacbio.common.database._
import com.pacbio.common.dependency.{DefaultConfigProvider, TypesafeSingletonReader, Singleton, SetBindings}
import com.pacbio.common.logging.LoggerFactoryImplProvider
import com.pacbio.common.models.MimeTypeDetectors
import com.pacbio.common.services._
import com.pacbio.common.time.SystemClockProvider
import com.pacbio.logging.LoggerOptions
import com.typesafe.scalalogging.LazyLogging
import spray.can.Http
import spray.routing.Route
import spray.servlet.WebBoot

import scala.collection.JavaConversions._
import scala.concurrent.{Future, Await}
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import scala.util.control.ControlThrowable

object BaseSmrtServerApp

// TODO(smcclellan): This is getting too monolithic, break it up into modules
// avm - removing dependencies related to cleanup and logging, they are causing pa-xfer to freeze due to singleton errors
trait CoreProviders extends
    SetBindings with
    DefaultConfigProvider with
    ServiceRoutesProvider with
    ServiceManifestsProvider with
    ManifestServiceProvider with
    HealthServiceProvider with
    HealthServiceActorRefProvider with
    InMemoryHealthDaoProvider with
    /*LogServiceProvider with
    LogServiceActorRefProvider with
    DatabaseLogDaoProvider with*/
    UserServiceProvider with
    UserServiceActorRefProvider with
    LdapUserDaoProvider with
   /* CleanupServiceProvider with
    CleanupServiceActorRefProvider with
    InMemoryCleanupDaoProvider with
    CleanupSchedulerProvider with*/
    StatusServiceProvider with
    StatusServiceActorRefProvider with
    ConfigServiceProvider with
    CommonFilesServiceProvider with
    MimeTypeDetectors with
    SubSystemComponentServiceProvider with
    SubSystemResourceServiceProvider with
    ActorSystemProvider with
    BaseSmrtServerDatabaseConfigProviders with
    TypesafeLdapUserDaoConfigProvider with
    JwtUtilsImplProvider with
    // TODO(smcclellan): Switch to AuthenticatorImplProvider when clients are ready to provide credentials
    FakeAuthenticatorProvider with
    //LoggerFactoryImplProvider with
    SystemClockProvider {
  val serverHost: Singleton[String] = TypesafeSingletonReader.fromConfig().getString("host").orElse("0.0.0.0")
  val serverPort: Singleton[Int] = TypesafeSingletonReader.fromConfig().getInt("port").orElse(8080)

  override val actorSystemName = Some("base-smrt-server")

  override val buildPackage: Singleton[Package] = Singleton(getClass.getPackage)

  override val baseServiceId: Singleton[String] = Singleton("smrtlink_common")

 /* override val logDaoDatabaseConfigProvider: DatabaseConfigProvider = new TypesafeDatabaseConfigProvider {
    override val databaseConfigPath = Singleton("log")
  }

  override val logback: Singleton[Boolean] = Singleton(true)*/
}

trait AuthenticatedCoreProviders extends
    SetBindings with
    DefaultConfigProvider with
    ServiceComposer with
    ManifestServiceProviderx with
    HealthServiceProviderx with
    HealthServiceActorRefProvider with
    InMemoryHealthDaoProvider with
    LogServiceProviderx with
    LogServiceActorRefProvider with
    DatabaseLogDaoProvider with
    UserServiceProviderx with
    UserServiceActorRefProvider with
    LdapUserDaoProvider with
    CleanupServiceProviderx with
    CleanupServiceActorRefProvider with
    InMemoryCleanupDaoProvider with
    CleanupSchedulerProvider with
    StatusServiceProviderx with
    StatusServiceActorRefProvider with
    ConfigServiceProviderx with
    CommonFilesServiceProviderx with
    MimeTypeDetectors with
    SubSystemComponentServiceProviderx with
    SubSystemResourceServiceProviderx with
    ActorSystemProvider with
    BaseSmrtServerDatabaseConfigProviders with
    TypesafeLdapUserDaoConfigProvider with
    JwtUtilsImplProvider with
    AuthenticatorImplProvider with
    LoggerFactoryImplProvider with
    SystemClockProvider {
  val serverHost: Singleton[String] = TypesafeSingletonReader.fromConfig().getString("host").orElse("0.0.0.0")
  val serverPort: Singleton[Int] = TypesafeSingletonReader.fromConfig().getInt("port").orElse(8080)

  override val actorSystemName = Some("base-smrt-server")

  override val buildPackage: Singleton[Package] = Singleton(getClass.getPackage)

  override val baseServiceId: Singleton[String] = Singleton("smrtlink_common")

  override val logDaoDatabaseConfigProvider: DatabaseConfigProvider = new TypesafeDatabaseConfigProvider {
    override val databaseConfigPath = Singleton("log")
  }

  override val logback: Singleton[Boolean] = Singleton(true)
}

trait BaseApi extends BaseRolesInit {
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
  lazy val rootService = system.actorOf(Props(new ServiceActor(routes)))

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
    logger.info("Java Version: " + System.getProperty("java.version"));
    logger.info("Java Home: " + System.getProperty("java.home"));
    val runtimeMxBean = ManagementFactory.getRuntimeMXBean();
    val arguments = runtimeMxBean.getInputArguments();
    logger.info("Java Args: " + arguments.mkString(" "));

    val f: Future[Option[BindException]] = (IO(Http)(system) ? Http.Bind(rootService, host, port = port)) map {
      case r: Http.CommandFailed => Some(new BindException(s"Failed to bind to $host:$port"))
      case r => None
    }

    class StartupFailedException(cause: Throwable)
        extends RuntimeException("Startup failed", cause) with ControlThrowable

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
