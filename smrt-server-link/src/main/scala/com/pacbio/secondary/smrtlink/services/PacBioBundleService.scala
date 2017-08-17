package com.pacbio.secondary.smrtlink.services

import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import collection.JavaConversions._
import collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent._
import scala.util.{Failure, Success, Try}
import spray.http.{HttpHeaders, Uri}
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing._
import spray.routing.directives.FileAndResourceDirectives
import org.joda.time.{DateTime => JodaDateTime}
import DefaultJsonProtocol._
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern._

import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.{ResourceNotFoundError, UnprocessableEntityError}
import com.pacbio.secondary.smrtlink.services._
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.{ActorSystemProvider, _}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.models.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.io.PacBioDataBundleIOUtils


class PacBioBundleService(daoActor: ActorRef, rootBundle: Path, externalPollActor: ActorRef, eventManagerActor: ActorRef)(implicit val actorSystem: ActorSystem) extends SmrtLinkBaseMicroService
    with DaoFutureUtils
    with PacBioDataBundleIOUtils
    with FileAndResourceDirectives{

  import SmrtLinkJsonProtocols._
  // Message protocols to communicate with the Dao
  import PacBioBundleDaoActor._

  import EventManagerActor._

  // for getFromFile to work
  implicit val routing = RoutingSettings.default


  val ROUTE_PREFIX = "bundles"

  // When the system is downloading bundles, they'll be placed temporarily here, the moved to
  // under the bundle root if they are valid
  val tmpRootBundleDir = Files.createTempDirectory("tmp-bundles")

  val manifest = PacBioComponentManifest(
    toServiceId("pacbio_bundles"),
    "PacBio Bundle Service",
    "0.1.0", "PacBio Service for registering Bundles of config files for extending the SL Core system")

  // Create a more terse error message
  def fromTry[T](errorMessage: String, t: Try[T]): Future[T] = {
    t match {
      case Success(r) => Future.successful(r)
      case Failure(ex) =>
        Future.failed(throw new UnprocessableEntityError(s"$errorMessage ${ex.getMessage}"))
    }
  }

  private def resolveToRoot(path: Uri.Path, rootPath: Path): Future[Path] = {
    val realPath = java.net.URLDecoder.decode(path.toString(), "UTF-8")
    val absPath = rootPath.resolve(Paths.get(realPath))
    if (Files.exists(absPath)) Future.successful(absPath)
    else Future.failed(throw new ResourceNotFoundError(s"Unable to resolve path: ${path.toString()}"))
  }

  def getBundleIOByTypeAndVersion(bundleTypeId: String, bundleVersion: String): Future[PacBioDataBundleIO] =
    (daoActor ? GetBundleIOByTypeAndVersion(bundleTypeId, bundleVersion))
        .mapTo[Option[PacBioDataBundleIO]]
        .flatMap(failIfNone(s"Unable to find Bundle Type '$bundleTypeId' and version '$bundleVersion'"))

  def getActiveBundle(bundleTypeId: String): Future[PacBioDataBundleIO] =
    (daoActor ? GetActiveIOBundle(bundleTypeId))
        .mapTo[Option[PacBioDataBundleIO]]
        .flatMap(failIfNone(s"Unable to find Active Data Bundle for type '$bundleTypeId'"))

  def getFileInBundle(bundleTypeId: String, rest: Uri.Path):Future[Path] = {
    for {
      b <-  getActiveBundle(bundleTypeId)
      resolvedPath <- resolveToRoot(rest, b.path)
    } yield resolvedPath
  }

  def activateBundle(bundleType: String, version: String): Future[PacBioDataBundleIO] = {
    val errorMessage = s"Unable to upgrade bundle $bundleType and version $version"
    val f = (daoActor ? ActivateBundle(bundleType: String, version: String))
        .mapTo[Try[PacBioDataBundleIO]]
        .flatMap(t => fromTry[PacBioDataBundleIO](errorMessage, t))

    f onSuccess {
      case bunIO: PacBioDataBundleIO => {
        logger.info(s"Activated ${bundleType} bundle version ${version}")

        val slEvent = SmrtLinkEvent(eventTypeId = EventTypes.CHEMISTRY_ACTIVATE_BUNDLE,
                                    eventTypeVersion = 1,
                                    uuid = UUID.randomUUID(),
                                    createdAt = JodaDateTime.now(),
                                    message = JsObject(
                                      "bundleType" -> JsString(bundleType),
                                      "version" -> JsString(version)
                                    ))
        (eventManagerActor ! CreateEvent(slEvent))
      }
    }

    f
  }

  val remoteBundleServerStatusRoute:Route = {
    pathPrefix("bundle-upgrader") {
      path("check") {
        pathEndOrSingleSlash {
          get {
            complete {
              ok {
                (externalPollActor ? PacBioDataBundlePollExternalActor.CheckForUpdates).mapTo[MessageResponse]
              }
            }
          }
        }
      } ~
      path("status") {
        pathEndOrSingleSlash {
          get {
            complete {
              ok {
                (externalPollActor ? PacBioDataBundlePollExternalActor.CheckStatus).mapTo[ExternalServerStatus]
              }
            }
          }
        }
      }
    }
  }

  val bundleImportRoute: Route =
    pathPrefix(ROUTE_PREFIX) {
      pathEndOrSingleSlash {
        post  {
          entity(as[PacBioBundleRecord]) { record =>
            complete {
              created {
                for {
                  pathBundle <- fromTry[(Path, PacBioDataBundle)](s"Failed to process $record", Try { downloadAndParseBundle(record.url, tmpRootBundleDir) } )
                  validBundle <- fromTry[PacBioDataBundleIO](s"Bundle Already exists.", Try { copyBundleTo(pathBundle._1, pathBundle._2, rootBundle)})
                  addedBundle <- (daoActor ? AddBundleIO(validBundle)).mapTo[PacBioDataBundleIO]
                } yield addedBundle.bundle
              }
            }
          }
        }
      }
    }

  // The naming here is terrible. It should be "activate" because that exactly what its doing
  // Adding the new route here. This is what should be used POST 5.0.0
  // MK. I couldn't figure out the path(Segment / Segment / "upgrade") or path(Segment / Segment / "activate") semantics
  val bundleActivateRoute: Route =
    pathPrefix(ROUTE_PREFIX) {
      (path(Segment / Segment / "upgrade") | path(Segment / Segment / "activate")) { (bundleTypeId, bundleVersion) =>
        post {
          complete {
            ok {
              for {
                b <- getBundleIOByTypeAndVersion(bundleTypeId, bundleVersion)
                bio <- activateBundle(b.bundle.typeId, b.bundle.version)
              } yield bio.bundle
            }
          }
        }
      }
    }

    val bundleRoutes:Route = {
    pathPrefix(ROUTE_PREFIX) {
      pathEndOrSingleSlash {
        get {
          complete {
            ok {
              (daoActor ? GetAllBundles).mapTo[Seq[PacBioDataBundle]]
            }
          }
        }
      } ~
      path(Segment) { bundleTypeId =>
        get {
          complete {
            ok {
              (daoActor ? GetAllBundlesByType(bundleTypeId)).mapTo[Seq[PacBioDataBundle]]
            }
          }
        }
      } ~
      path(Segment / "latest") { bundleTypeId =>
        get {
          complete {
            ok {
              (daoActor ? GetNewestBundle(bundleTypeId))
                  .mapTo[Option[PacBioDataBundle]].flatMap(failIfNone(s"Unable to find Newest Data Bundle for type '$bundleTypeId'"))
            }
          }
        }
      } ~
      path(Segment / "active") { bundleTypeId =>
        get {
          complete {
            ok {
              (daoActor ? GetActiveIOBundle(bundleTypeId))
                  .mapTo[Option[PacBioDataBundleIO]]
                  .flatMap(failIfNone(s"Unable to find Active Data Bundle for type '$bundleTypeId'"))
                  .map(_.bundle)
            }
          }
        }
      } ~
      path(Segment / "active" / "files" / RestPath) { (bundleTypeId, uriPath) =>
        onSuccess(getFileInBundle(bundleTypeId, uriPath)) { rPath => getFromFile(rPath.toFile) }
      } ~
      path(Segment / "upgrade") { bundleTypeId =>
        get {
          complete {
            ok {
                (daoActor ? GetUpgradableBundle(bundleTypeId)).mapTo[PacBioDataBundleUpgrade]
            }
          }
        }
      } ~
       path(Segment / Segment / "download") { (bundleTypeId, bundleVersion) =>
        pathEndOrSingleSlash {
          get {
            onSuccess(getBundleIOByTypeAndVersion(bundleTypeId, bundleVersion)) { case b: PacBioDataBundleIO =>
              val fileName = s"$bundleTypeId-$bundleVersion.tar.gz"
              logger.info(s"Downloading bundle $b to $fileName")
              respondWithHeader(HttpHeaders.`Content-Disposition`("attachment; filename=" + fileName)) {
                getFromFile(b.tarGzPath.toFile)
              }
            }
          }
        }
      } ~
      path(Segment / Segment) { (bundleTypeId, bundleVersion) =>
        get {
          complete {
            ok {
              getBundleIOByTypeAndVersion(bundleTypeId, bundleVersion).map(_.bundle)
            }
          }
        }
      }
    }
  }

  // The order is here is very important
  val routes = bundleRoutes ~ remoteBundleServerStatusRoute ~ bundleImportRoute ~ bundleActivateRoute
}

trait PacBioBundleServiceProvider {
  this: SmrtLinkConfigProvider
      with ServiceComposer
      with ActorSystemProvider
      with PacBioBundleDaoActorProvider
      with PacBioDataBundlePollExternalActorProvider
      with EventManagerActorProvider =>

  val pacBioBundleService: Singleton[PacBioBundleService] =
    Singleton { () =>
      implicit val system = actorSystem()
      new PacBioBundleService(pacBioBundleDaoActor(), pacBioBundleRoot(), externalBundleUpgraderActor(), eventManagerActor())
    }

  addService(pacBioBundleService)
}


