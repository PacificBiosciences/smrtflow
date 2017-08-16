package com.pacbio.secondary.smrtlink.services

import com.pacbio.secondary.smrtlink.dependency.{ConfigProvider, Singleton}
import com.pacbio.secondary.smrtlink.models.PacBioComponentManifest
import com.pacbio.secondary.smrtlink.loaders.ManifestLoader
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.ResourceNotFoundError
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// MK. There's duplication between these two implementations due to the service composer model versus the old model.
class ManifestService(manifests: Set[PacBioComponentManifest]) extends PacBioService with DefaultJsonProtocol {

  import com.pacbio.secondary.smrtlink.models.PacBioJsonProtocol._

  val manifest = PacBioComponentManifest(
    toServiceId("service_manifests"),
    "Status Service",
    "0.2.0", "Subsystem Manifest Service")

  val allManifests = (manifests + manifest).toList

  def getById(ms: Seq[PacBioComponentManifest], manifestId: String): Future[PacBioComponentManifest] = {
    ms.find(_.id == manifestId) match {
      case Some(m) => Future.successful(m)
      case _ => Future.failed(new ResourceNotFoundError(s"Unable to find manifest id $manifestId"))
    }
  }

  val manifestRoutes =
    path("manifests") {
      get {
        complete {
          allManifests
        }
      }
    } ~
    path("manifests" / Segment) { manifestId =>
      get {
        complete {
          getById(allManifests.toSeq, manifestId)
        }
      }
    }

  val servicesManifests = pathPrefix("services") { manifestRoutes }
  val smrtLinkManifests = pathPrefix("smrt-link") { manifestRoutes }

  // BackWard Migration model to push the routes into a consistent single prefix "smrt-link"
  val routes = servicesManifests ~ smrtLinkManifests
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

  import com.pacbio.secondary.smrtlink.models.PacBioJsonProtocol._

  val manifest = PacBioComponentManifest(
    toServiceId("service_manifests"),
    "Component Manifest Service",
    "0.2.0", "Subsystem Component Manifest/Version Service")

  def getById(ms: Seq[PacBioComponentManifest], manifestId: String): Future[PacBioComponentManifest] = {
    ms.find(_.id == manifestId) match {
      case Some(m) => Future.successful(m)
      case _ => Future.failed(new ResourceNotFoundError(s"Unable to find manifest id $manifestId"))
    }
  }

  val manifestRoutes =
    path("manifests") {
      get {
        complete {
          services.manifests()
        }
      }
    } ~
    path("manifests" / Segment) { manifestId =>
      get {
        complete {
          getById(services.manifests().toSeq, manifestId)
        }
      }
    }

  val servicesManifests = pathPrefix("services") { manifestRoutes }
  val smrtLinkManifests = pathPrefix("smrt-link") { manifestRoutes }

  // BackWard Migration model to push the routes into a consistent single prefix "smrt-link"
  val routes = servicesManifests ~ smrtLinkManifests
}

trait ManifestServiceProviderx {
  this: ServiceComposer with ConfigProvider =>

  final val manifestService: Singleton[ManifestServicex] =
    Singleton(() => new ManifestServicex(this))

  // Really need to rethink these entire model
  addManifests(ManifestLoader.loadFromConfig(config()).toSet)
  addService(manifestService)
}
