package com.pacbio.common.services

import java.util.UUID
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import org.joda.time.{DateTime => JodaDateTime}
import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport._

import scala.collection.mutable
import scala.util.Try

class SubSystemResourceService extends PacBioService {

  import PacBioJsonProtocol._

  val serviceName = "subsystem_resources"
  val manifest = PacBioComponentManifest(
    toServiceId("subsystem_resources"),
    "Subsystem Resources Service",
    "0.2.0", "Subsystem Resources Service")

  val exampleResource = SubsystemResource(UUID.randomUUID, "MyDisplayName", "0.1.2",
    "/subsystem_resources", "/docs/user/subsystem", "/docs/api/subsystem", JodaDateTime.now, JodaDateTime.now)

  var _subsystemResources = {
    mutable.Map(exampleResource.uuid -> exampleResource)
  }

  def _findResourceOrError(uuid: java.util.UUID): Try[SubsystemResource] = Try {
    if (_subsystemResources.contains(uuid))
      _subsystemResources(uuid)
    else
      throw new NoSuchElementException(s"Unable to find resource $uuid")
  }

  def _deleteResourceOrError(uuid: java.util.UUID): Try[String] = Try {
    if (_subsystemResources.contains(uuid)) {
      _subsystemResources -= uuid
      s"Successfully deleted resource $uuid"
    } else
      throw new NoSuchElementException(s"Unable to find resource $uuid")
  }

  val routes =
    pathPrefix(serviceName) {
      pathEnd {
        get {
          complete {
            _subsystemResources.values.toList
          }
        }
      } ~
      path("create") {
        post {
          entity(as[SubsystemResourceRecord]) { r =>
            val sresource = SubsystemResource(UUID.randomUUID, r.name, r.version, r.url, r.userDocs, r.apiDocs, JodaDateTime.now, JodaDateTime.now)
            _subsystemResources(sresource.uuid) = sresource
            complete {
              StatusCodes.Created -> sresource
            }
          }
        }
      } ~
      path(JavaUUID) { _uuid =>
        get {
          complete {
            ok {
              _findResourceOrError(_uuid)
            }
          }
        } ~
        delete {
          complete {
            ok {
              _deleteResourceOrError(_uuid)
            }
          }
        }
      }
    }
}

/**
 * Provides a singleton SubSystemResourceService, and also binds it to the set of total services.
 */
trait SubSystemResourceServiceProvider {
  final val subSystemResourceService: Singleton[SubSystemResourceService] =
    Singleton(() => new SubSystemResourceService()).bindToSet(AllServices)
}

trait SubSystemResourceServiceProviderx {
  this: ServiceComposer =>
  final val subSystemResourceService: Singleton[SubSystemResourceService] =
    Singleton(() => new SubSystemResourceService())

  addService(subSystemResourceService)
}
