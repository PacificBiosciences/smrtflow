package com.pacbio.secondary.smrtserverbundle.app

import java.net.{BindException, URL}
import java.nio.file.{Files, Path, Paths}

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.io.IO
import akka.util.Timeout
import akka.pattern._
import com.pacbio.common.models.Constants
import com.pacbio.secondary.smrtlink.services.utils.StatusGenerator
import com.pacbio.secondary.smrtlink.services._
import com.pacbio.secondary.smrtlink.time.SystemClock
import com.pacbio.common.logging.LoggerOptions
import com.pacbio.common.semver.SemVersion
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.actors.{
  DaoFutureUtils,
  EventManagerActor,
  PacBioBundleDaoActor,
  PacBioDataBundlePollExternalActor,
  PacBioBundleDao => LegacyPacBioBundleDao
}
import com.pacbio.secondary.smrtlink.io.PacBioDataBundleIOUtils
import com.pacbio.secondary.smrtlink.app.{
  ActorSystemCakeProvider,
  BaseServiceConfigCakeProvider,
  ServiceLoggingUtils
}
import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.models.{
  PacBioComponentManifest,
  PacBioDataBundleIO
}
import com.pacbio.secondary.smrtserverbundle.dao.BundleUpdateDao
import com.typesafe.scalalogging.LazyLogging
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.{
  ContentDispositionTypes,
  `Content-Disposition`
}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.FileAndResourceDirectives
import akka.http.scaladsl.settings.RoutingSettings

import concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

trait SmrtBundleBaseMicroService extends PacBioService with LazyLogging {

  implicit val timeout = Timeout(30.seconds)

  // Note, Using a single prefix of "api/v1" will not work as "expected"
  override def prefixedRoutes = pathPrefix("api" / "v2") {
    super.prefixedRoutes
  }
}

/**
  * Original (5.0.X) Legacy Update Bundle Service.
  *
  * This mirrored the SMRT Link Server bundle API.
  *
  * Note, this should only be used with 5.0.X bundles.
  *
  */
class LegacyChemistryUpdateBundleService(daoActor: ActorRef,
                                         rootBundle: Path,
                                         externalPollActor: ActorRef,
                                         eventManagerActor: ActorRef)(
    implicit override val actorSystem: ActorSystem)
    extends PacBioBundleService(daoActor,
                                rootBundle,
                                externalPollActor,
                                eventManagerActor)(actorSystem) {

  /**
    * Removed any PUT, POST routes yield only GET
    *
    * And remove the Remote bundle Status routes (this system should never be configured with an external
    * bundle service to update from.
    */
  override val routes = bundleRoutes
}

class BundleUpdateService(dao: BundleUpdateDao)(
    implicit actorSystem: ActorSystem)
    extends SmrtBundleBaseMicroService
    with DaoFutureUtils
    with PacBioDataBundleIOUtils
    with FileAndResourceDirectives {

  // Json Serialization
  import SmrtLinkJsonProtocols._
  // for getFromFile to work
  implicit val routing = RoutingSettings.default

  val ROUTE_PREFIX = "updates"
  val BUNDLE_PREFIX = "bundles"

  val manifest = PacBioComponentManifest(
    toServiceId("pacbio_bundles"),
    "PacBio Bundle Service",
    "2.0.0",
    "PacBio Update Service for PacBio Data Bundles"
  )

  def routeGetAllBundles: Route = pathPrefix(ROUTE_PREFIX) {
    pathEndOrSingleSlash {
      get {
        complete {
          dao.allBundles().map(bs => bs.map(_.bundle))
        }
      }
    }
  }

  def routeGetBySystemVersion =
    pathPrefix(ROUTE_PREFIX / Segment / BUNDLE_PREFIX) { systemVersion =>
      pathEndOrSingleSlash {
        get {
          complete {
            dao
              .getBundlesBySystem(systemVersion)
              .map(bs => bs.map(_.bundle))
          }
        }
      }
    }

  def routeGetBySystemVersionAndBundleType: Route =
    pathPrefix(ROUTE_PREFIX / Segment / BUNDLE_PREFIX / Segment) {
      (systemVersion, bundleType) =>
        pathEndOrSingleSlash {
          get {
            complete {
              dao
                .getBundlesBySystemAndBundleType(systemVersion, bundleType)
                .map(bs => bs.map(_.bundle))
            }
          }
        }
    }

  def routeGetBySystemVersionAndBundleTypeAndVersion: Route =
    pathPrefix(ROUTE_PREFIX / Segment / BUNDLE_PREFIX / Segment / Segment) {
      (systemVersion, bundleType, bundleVersion) =>
        pathEndOrSingleSlash {
          get {
            complete {
              dao
                .getBundleByVersion(systemVersion, bundleType, bundleVersion)
                .map(_.bundle)
            }
          }
        }
    }

  def routeDownloadBundle: Route =
    pathPrefix(
      ROUTE_PREFIX / Segment / BUNDLE_PREFIX / Segment / Segment / "download") {
      (systemVersion, bundleTypeId, bundleVersion) =>
        pathEndOrSingleSlash {
          get {
            onSuccess(
              dao.getBundleByVersion(systemVersion,
                                     bundleTypeId,
                                     bundleVersion)) {
              case b: PacBioDataBundleIO =>
                val fileName = s"$bundleTypeId-$bundleVersion.tar.gz"
                logger.info(s"Downloading bundle $b to $fileName")
                val params: Map[String, String] = Map("filename" -> fileName)
                val customHeader: HttpHeader =
                  `Content-Disposition`(ContentDispositionTypes.attachment,
                                        params)

                respondWithHeader(customHeader) {
                  getFromFile(b.tarGzPath.toFile)
                }
            }
          }
        }
    }

  def swaggerRoute: Route =
    pathPrefix("swagger") {
      pathEndOrSingleSlash {
        get {
          getFromResource("bundleserver_swagger.json")
        }
      }
    }

  override def routes: Route =
    routeGetAllBundles ~ routeGetBySystemVersion ~ routeGetBySystemVersionAndBundleType ~ routeGetBySystemVersionAndBundleTypeAndVersion ~ routeDownloadBundle ~ swaggerRoute
}

/**
  * Thin PacBio Data Bundle Only Server
  *
  */
trait PacBioDataBundleConfigCakeProvider
    extends BaseServiceConfigCakeProvider
    with LazyLogging {
  override lazy val systemName = "bundle-server"

  lazy val pacBioBundleRoot =
    Paths.get(conf.getString("smrtflow.server.bundleDir")).toAbsolutePath()

  lazy val dnsName = Try { conf.getString("smrtflow.server.dnsName") }.toOption

  /**
    * This will load the key and convert to URL.
    * Any errors will *only* be logged. This is probably not the best model.
    *
    * @return
    */
  private def loadUrl(key: String): Option[URL] = {
    Try { new URL(conf.getString(key)) } match {
      case Success(url) =>
        logger.info(s"Converted $key to URL $url")
        Some(url)
      case Failure(ex) =>
        logger.error(s"Failed to load URL from key '$key' ${ex.getMessage}")
        None
    }
  }
}

trait PacBioDataBundleServicesCakeProvider {
  this: ActorSystemCakeProvider with PacBioDataBundleConfigCakeProvider =>

  lazy val statusGenerator = new StatusGenerator(new SystemClock(),
                                                 systemName,
                                                 systemUUID,
                                                 Constants.SMRTFLOW_VERSION)

  def isValidSemVer(sx: String): Boolean =
    Try(SemVersion.fromString(sx)).isSuccess

  /**
    * Load all bundles with a system version in the relative dir
    *
    *
    * @param rootDir Root level bundle directory
    * @return
    */
  def loadSystemBundlesByVersion(
      rootDir: Path): Map[String, Seq[PacBioDataBundleIO]] = {

    if (Files.exists(rootDir)) {
      rootDir.toFile
        .listFiles()
        .filter(_.isDirectory)
        .filter(x => isValidSemVer(x.getName))
        .map { p =>
          val bundles = PacBioDataBundleIOUtils.loadBundlesFromRoot(p.toPath)
          (SemVersion.fromString(p.getName).toSemVerString(), bundles)
        }
        .toMap
    } else {
      logger.error(
        s"Directory $rootDir does not exists. Unable to load bundles.")
      Map.empty[String, Seq[PacBioDataBundleIO]]
    }
  }

  // Load Legacy 5.0.X Bundles and warn if a non 5.0.X version is loaded
  def loadLegacy500(path: Path): Seq[PacBioDataBundleIO] = {
    if (Files.exists(legacy500Dir)) {
      PacBioDataBundleIOUtils.loadBundlesFromRoot(path)
    } else {
      logger.warn(s"UNABLE TO LOAD LEGACY 5.0.0 bundles from $path")
      Nil
    }
  }

  lazy val legacy500Dir = pacBioBundleRoot.resolve("5.0.0")

  // V2 Bundles with the system version
  lazy val bundleDao = new BundleUpdateDao(
    loadSystemBundlesByVersion(pacBioBundleRoot))

  lazy val dao = new LegacyPacBioBundleDao(loadLegacy500(legacy500Dir))
  lazy val daoActor =
    actorSystem.actorOf(Props(new PacBioBundleDaoActor(dao, pacBioBundleRoot)))

  // This is not the greatest model. If the URL is None, then none of the calls will be made
  lazy val externalUpdateUrl: Option[URL] = None

  lazy val externalPollActor = actorSystem.actorOf(
    Props(
      new PacBioDataBundlePollExternalActor(pacBioBundleRoot,
                                            externalUpdateUrl,
                                            12.hours,
                                            daoActor,
                                            "bundle-unknown-type",
                                            None)))
  // V2 Bundle API service.

  // Events (this was to have the interface comply with the SMRT Link Server interface
  val externalEveUrl: Option[URL] = None
  lazy val eventManagerActor = actorSystem.actorOf(
    Props(
      new EventManagerActor(systemUUID, dnsName, externalEveUrl, apiSecret)),
    "EventManagerActor")

  lazy val services: Seq[PacBioService] = Seq(
    new LegacyChemistryUpdateBundleService(daoActor,
                                           pacBioBundleRoot,
                                           externalPollActor,
                                           eventManagerActor),
    new BundleUpdateService(bundleDao)(actorSystem),
    new StatusService(statusGenerator)
  )
}

trait RootPacBioDataBundleServerCakeProvider extends RouteConcatenation {
  this: ActorSystemCakeProvider with PacBioDataBundleServicesCakeProvider =>

  lazy val allRoutes: Route = services.map(_.prefixedRoutes).reduce(_ ~ _)
}

trait PacBioDataBundleServerCakeProvider
    extends LazyLogging
    with timeUtils
    with ServiceLoggingUtils {
  this: RootPacBioDataBundleServerCakeProvider
    with PacBioDataBundleConfigCakeProvider
    with ActorSystemCakeProvider =>

  implicit val timeout = Timeout(10.seconds)

  //FIXME(mpkocher)(2017-4-11) Add validation on startup. Add explicit unbind call
  def startServices(): Future[String] =
    Http()
      .bindAndHandle(logResponseTimeRoutes(allRoutes), systemHost, systemPort)
      .map { result =>
        logger.info(result.toString)
        val msg = s"Successfully Started Services on $systemHost:$systemPort"
        logger.info(msg)
        msg
      }

}

object PacBioDataBundleServer
    extends PacBioDataBundleConfigCakeProvider
    with ActorSystemCakeProvider
    with PacBioDataBundleServicesCakeProvider
    with RootPacBioDataBundleServerCakeProvider
    with PacBioDataBundleServerCakeProvider {}

object BundleUpdateServerApp extends App {
  import PacBioDataBundleServer._
  LoggerOptions.parseAddDebug(args)
  startServices()
}
