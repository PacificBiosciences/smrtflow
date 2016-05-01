package com.pacbio.common.services

import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.{PacBioJsonProtocol, PacBioComponent, PacBioComponentManifest}

import scala.collection.mutable

import spray.httpx.SprayJsonSupport._

class SubSystemComponentService extends PacBioService with AppConfig {

  import PacBioJsonProtocol._

  val manifest = PacBioComponentManifest(
    toServiceId("components"),
    "Subsystem Component Service",
    "0.2.0", "Subsystem Component Service", None)

  val componentServiceName = "components"

  var _componentManifests = mutable.Map(manifest.id -> manifest)

  def manifestToComponents(m: Seq[PacBioComponentManifest]): Seq[PacBioComponent] = {
    m.map(x => PacBioComponent(x.id, x.version))
  }

  def toComponents =
    manifestToComponents(_componentManifests.values.toList)

  val routes = {
    path(componentServiceName) {
      get {
        complete {
          toComponents
        }
      }
    }
  }
}

/**
 * Provides a singleton SubSystemComponentService, and also binds it to the set of total services.
 */
trait SubSystemComponentServiceProvider {
  final val subSystemComponentService: Singleton[SubSystemComponentService] =
    Singleton(() => new SubSystemComponentService()).bindToSet(AllServices)
}

trait SubSystemComponentServiceProviderx {
  this: ServiceComposer =>
  final val subSystemComponentService: Singleton[SubSystemComponentService] =
    Singleton(() => new SubSystemComponentService())

  addService(subSystemComponentService)
}
