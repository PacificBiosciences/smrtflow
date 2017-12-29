package com.pacbio.secondary.smrtlink.app

import java.lang.management.ManagementFactory
import java.net.BindException

import akka.actor.{ActorRefFactory, Props}
import akka.http.scaladsl.Http
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.auth.{
  AuthenticatorImplProvider,
  JwtUtilsImplProvider
}
import com.pacbio.secondary.smrtlink.dependency.{
  DefaultConfigProvider,
  SetBindings,
  Singleton
}
import com.pacbio.secondary.smrtlink.file.JavaFileSystemUtilProvider
import com.pacbio.common.models.Constants
import com.pacbio.secondary.smrtlink.services.utils.StatusGeneratorProvider
import com.pacbio.secondary.smrtlink.time.SystemClockProvider
import com.pacbio.common.utils.OSUtils
import com.pacbio.common.logging.LoggerOptions
import com.pacbio.secondary.smrtlink.actors.ActorSystemProvider
import com.pacbio.secondary.smrtlink.analysis.configloaders.ConfigLoader
import com.pacbio.secondary.smrtlink.models.MimeTypeDetectors
import com.pacbio.secondary.smrtlink.services._
import com.typesafe.scalalogging.LazyLogging

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.ControlThrowable

object BaseSmrtServerApp

class StartupFailedException(cause: Throwable)
    extends RuntimeException("Startup failed", cause)
    with ControlThrowable

// TODO(smcclellan): This is getting too monolithic, break it up into modules
trait CoreProviders
    extends ActorSystemProvider
    with SetBindings
    with DefaultConfigProvider
    with ServiceRoutesProvider
    with ServiceManifestsProvider
    with ManifestServiceProvider
    with StatusServiceProvider
    with StatusGeneratorProvider
    with UserServiceProvider
    with CommonFilesServiceProvider
    with DiskSpaceServiceProvider
    with MimeTypeDetectors
    with JwtUtilsImplProvider
    with AuthenticatorImplProvider
    with JavaFileSystemUtilProvider
    with SystemClockProvider
    with ConfigLoader {

  val serverPort: Singleton[Int] = Singleton(
    () => conf.getInt("smrtflow.server.port"))
  val serverHost: Singleton[String] = Singleton(
    () => conf.getString("smrtflow.server.host"))

  override val actorSystemName = Some("base-smrt-server")

  override val buildPackage: Singleton[Package] = Singleton(
    getClass.getPackage)

  override val baseServiceId: Singleton[String] = Singleton("smrtlink_common")

}

trait AuthenticatedCoreProviders
    extends ActorSystemProvider
    with SetBindings
    with DefaultConfigProvider
    with ServiceComposer
    with ManifestServiceProviderx
    with StatusServiceProviderx
    with StatusGeneratorProvider
    with UserServiceProviderx
    with CommonFilesServiceProviderx
    with DiskSpaceServiceProviderx
    with MimeTypeDetectors
    with JwtUtilsImplProvider
    with AuthenticatorImplProvider
    with JavaFileSystemUtilProvider
    with SystemClockProvider
    with ConfigLoader {

  val serverPort: Singleton[Int] = Singleton(
    () => conf.getInt("smrtflow.server.port"))
  val serverHost: Singleton[String] = Singleton(
    () => conf.getString("smrtflow.server.host"))

  override val actorSystemName = Some("base-smrt-server")

  override val buildPackage: Singleton[Package] = Singleton(
    getClass.getPackage)

  override val baseServiceId: Singleton[String] = Singleton("smrtlink_common")
}

trait BaseApi {
  val providers: ActorSystemProvider with RouteProvider

  // Override these with custom startup and shutdown logic
  def startup(): Unit = ()
  def shutdown(): Unit = ()

  implicit lazy val system = providers.actorSystem()
  implicit lazy val materializer = ActorMaterializer()(system)
  lazy val routes: Route = providers.routes()

  // This is needed with Mixin routes from traits that only extend HttpService
  def actorRefFactory: ActorRefFactory = system

  sys.addShutdownHook(system.terminate())
}

trait BaseServer extends LazyLogging with OSUtils { this: BaseApi =>

  implicit val timeout = Timeout(10.seconds)

  val host: String
  val port: Int

  def start = {
    logger.info(s"Starting App using smrtflow ${Constants.SMRTFLOW_VERSION}")
    logger.info(s"Running on OS ${getOsVersion()}")
    logger.info(
      s"Number of Available Processors ${Runtime.getRuntime().availableProcessors()}")
    logger.info("Java Version: " + System.getProperty("java.version"))
    logger.info("Java Home: " + System.getProperty("java.home"))
    val runtimeMxBean = ManagementFactory.getRuntimeMXBean
    val arguments = runtimeMxBean.getInputArguments
    logger.info("Java Args: " + arguments.asScala.mkString(" "))

    val startF = Http().bindAndHandle(routes, host, port)

    startF.failed.foreach { ex =>
      logger.error(ex.getMessage)
      System.err.println(ex.getMessage)
      system.terminate()
    }
  } // End of Start
}

/**
  * This is used for spray-can http server which can be started via 'sbt run'
  */
object BaseSmrtServer extends App with BaseServer with BaseApi {
  override val providers: CoreProviders = new CoreProviders {}
  override val host = providers.serverHost()
  override val port = providers.serverPort()

  LoggerOptions.parseAddDebug(args)

  start
}
