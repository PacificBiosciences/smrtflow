package com.pacbio.common.services

import java.io.File
import java.nio.file.{FileSystem, FileSystems}

import akka.util.Timeout
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import spray.httpx.SprayJsonSupport._
import spray.json._
import DefaultJsonProtocol._

import scala.concurrent.duration._

// TODO(smcclellan): Unit tests

class DiskSpaceService extends BaseSmrtService {

  import PacBioJsonProtocol._

  implicit val timeout = Timeout(10.seconds)

  val manifest = PacBioComponentManifest(
    toServiceId("disk_space"),
    "Disk Space Service",
    "0.1.0", "Disk Space Service")

  val fileSystem: FileSystem = FileSystems.getDefault

  val routes =
    path("disk-space") {
      parameters('path.?) { path: Option[String] =>
        get {
          complete {
            ok {
              val dir = new File(path.getOrElse("/"))
              DiskSpaceResource(dir.getTotalSpace, dir.getUsableSpace, dir.getFreeSpace)
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