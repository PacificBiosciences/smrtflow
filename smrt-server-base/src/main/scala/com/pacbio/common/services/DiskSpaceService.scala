package com.pacbio.common.services

import java.io.File

import akka.util.Timeout
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import com.pacbio.common.services.PacBioServiceErrors.ResourceNotFoundError
import spray.httpx.SprayJsonSupport._
import spray.json._
import DefaultJsonProtocol._

import scala.concurrent.duration._

// TODO(smcclellan): Unit tests

class DiskSpaceService extends BaseSmrtService {

  import PacBioJsonProtocol._

  implicit val timeout = Timeout(10.seconds)

  override val manifest = PacBioComponentManifest(
    toServiceId("disk_space"),
    "Disk Space Service",
    "0.1.0", "Disk Space Service")

  private val idsToPaths: Map[String, String] = Map("smrtlink.root" -> "/")
  
  private def toResource(id: String): DiskSpaceResource = {
    idsToPaths.get(id).map { p =>
      val dir = new File(p)
      DiskSpaceResource(id, p, dir.getTotalSpace, dir.getUsableSpace, dir.getFreeSpace)
    }.getOrElse {
      val ids = idsToPaths.keySet.map(i => s"'$i'").reduce(_ + ", " + _)
      throw new ResourceNotFoundError(s"Could not find resource with id $id. Available resources are: $ids.")
    }
  }

  override val routes =
    pathPrefix("disk-space") {
      pathEndOrSingleSlash {
        get {
          complete {
            ok {
              idsToPaths.keySet.map(toResource)
            }
          }
        }
      } ~
      path(Segment) { id =>
        get {
          complete {
            ok {
              toResource(id)
            }
          }
        }
      }
    }
}

/**
 * Provides a singleton SpaceService, and also binds it to the set of total services.
 */
trait DiskSpaceServiceProvider {
  val diskSpaceService: Singleton[DiskSpaceService] = Singleton(() => new DiskSpaceService).bindToSet(AllServices)
}

trait DiskSpaceServiceProviderx {
  this: ServiceComposer =>

  val diskSpaceService: Singleton[DiskSpaceService] = Singleton(() => new DiskSpaceService)

  addService(diskSpaceService)
}