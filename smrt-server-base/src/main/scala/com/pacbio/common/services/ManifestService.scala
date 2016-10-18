package com.pacbio.common.services

import com.pacbio.common.dependency.{ConfigProvider, Singleton}
import com.pacbio.common.loaders.ManifestLoader
import com.pacbio.common.models.{PacBioComponentManifest, PacBioJsonProtocol}
import com.pacbio.common.services.PacBioServiceErrors.ResourceNotFoundError
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ManifestService(manifests: Set[PacBioComponentManifest]) extends PacBioService with DefaultJsonProtocol {

  import PacBioJsonProtocol._

  val manifest = PacBioComponentManifest(
    toServiceId("service_manifests"),
    "Status Service",
    "0.2.0", "Subsystem Manifest Service")

  val allManifests = (manifests + manifest).toList

  def getById(manifestId: String): Future[PacBioComponentManifest] = {
    allManifests.find(_.id == manifestId) match {
      case Some(m) => Future { m }
      case _ => Future.failed(new ResourceNotFoundError(s"Unable to find manifest id $manifestId"))
    }
  }

  val routes =
    path("services" / "manifests") {
      get {
        complete {
          allManifests
        }
      }
    } ~
        path("services" / "manifests" / Segment) { manifestId =>
          get {
            complete {
              getById(manifestId)
            }
          }
        }
}

/**
 * Provides a singleton ManifestService, and also binds it to the set of total services. Concrete providers must mixin
 * {{{ServiceManifestsProvider}}}.
 */
trait ManifestServiceProvider {
  this: ServiceManifestsProvider with ConfigProvider =>

  // Load a Manifest defined in the application.conf (e.g., SL version) and add it to the list
  final val manifestService: Singleton[ManifestService] =
    Singleton(() => new ManifestService(manifests() ++ ManifestLoader.loadFromConfig(config()))).bindToSet(NoManifestServices)
}


class ManifestServicex(services: ServiceComposer) extends PacBioService with DefaultJsonProtocol {

  import PacBioJsonProtocol._

  val manifest = PacBioComponentManifest(
    toServiceId("service_manifests"),
    "Component Manifest Service",
    "0.2.0", "Subsystem Component Manifest/Version Service")

  val routes =
    path("services" / "manifests") {
      get {
        complete {
          services.manifests()
        }
      }
    }
}

trait ManifestServiceProviderx {
  this: ServiceComposer with ConfigProvider =>

  final val manifestService: Singleton[ManifestServicex] =
    Singleton(() => new ManifestServicex(this))

  // Really need to rethink these entire model
  addManifests(ManifestLoader.loadFromConfig(config()).toSet)
  addService(manifestService)
}
