package com.pacbio.common.services

import com.pacbio.common.dependency.{ConfigProvider, Singleton}
import com.pacbio.common.loaders.ManifestLoader
import com.pacbio.common.models.{PacBioComponentManifest, PacBioJsonProtocol}
import spray.httpx.SprayJsonSupport._
import spray.json._

class ManifestService(manifests: Set[PacBioComponentManifest]) extends PacBioService with DefaultJsonProtocol {

  import PacBioJsonProtocol._

  val manifest = PacBioComponentManifest(
    toServiceId("service_manifests"),
    "Status Service",
    "0.2.0", "Subsystem Manifest Service")

  val routes =
    path("services" / "manifests") {
      get {
        complete {
          (manifests + manifest).toList
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
