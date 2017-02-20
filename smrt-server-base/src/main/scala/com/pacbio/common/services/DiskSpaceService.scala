package com.pacbio.common.services

import java.nio.file.{Paths, Path}

import akka.util.Timeout
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.file.{FileSystemUtilProvider, FileSystemUtil}
import com.pacbio.common.models._
import com.pacbio.common.services.PacBioServiceErrors.ResourceNotFoundError
import com.pacbio.secondary.analysis.configloaders.EngineCoreConfigLoader
import spray.httpx.SprayJsonSupport._
import spray.json._
import DefaultJsonProtocol._

import scala.concurrent.duration._

// TODO(smcclellan): Unit tests

class DiskSpaceService(fileSystemUtil: FileSystemUtil) extends BaseSmrtService with EngineCoreConfigLoader {

  import PacBioJsonProtocol._

  implicit val timeout = Timeout(10.seconds)

  override val manifest = PacBioComponentManifest(
    toServiceId("disk_space"),
    "Disk Space Service",
    "0.1.0", "Disk Space Service")

  private val idsToPaths: Map[String, Path] = Map(
    "smrtlink.resources.root" -> Paths.get("/"),
    "smrtlink.resources.jobs_root" -> engineConfig.pbRootJobDir
  )

  private def toResource(id: String): DiskSpaceResource = {
    idsToPaths.get(id).map { p =>
      DiskSpaceResource(
        fileSystemUtil.getFile(p).getAbsolutePath,
        fileSystemUtil.getTotalSpace(p),
        fileSystemUtil.getFreeSpace(p))
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
  this: FileSystemUtilProvider =>

  val diskSpaceService: Singleton[DiskSpaceService] =
    Singleton(() => new DiskSpaceService(fileSystemUtil())).bindToSet(AllServices)
}

trait DiskSpaceServiceProviderx {
  this: FileSystemUtilProvider with ServiceComposer =>

  val diskSpaceService: Singleton[DiskSpaceService] = Singleton(() => new DiskSpaceService(fileSystemUtil()))

  addService(diskSpaceService)
}