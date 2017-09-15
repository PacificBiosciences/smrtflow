package com.pacbio.secondary.smrtlink.services

import java.nio.file.{Path, Paths}

import akka.util.Timeout
import com.pacbio.secondary.smrtlink.dependency.{ConfigProvider, Singleton}
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.analysis.configloaders.{
  EngineCoreConfigConstants,
  EngineCoreConfigLoader
}
import com.pacbio.secondary.smrtlink.file.{
  FileSystemUtil,
  FileSystemUtilProvider
}
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.ResourceNotFoundError
import com.typesafe.config.Config
import spray.httpx.SprayJsonSupport._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

// TODO(smcclellan): Unit tests

class DiskSpaceService(config: Config, fileSystemUtil: FileSystemUtil)
    extends BaseSmrtService
    with EngineCoreConfigLoader {

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  implicit val timeout = Timeout(10.seconds)

  override val manifest = PacBioComponentManifest(toServiceId("disk_space"),
                                                  "Disk Space Service",
                                                  "0.1.0",
                                                  "Disk Space Service")

  // We use the injected config here, instead of engineConfig.pbRootJobDir, so tests can use a custom job dir
  val jobRootDir = loadJobRoot(
    config.getString(EngineCoreConfigConstants.PB_ROOT_JOB_DIR))

  // TODO(smcclellan): Make this a constant, maybe in EngineCoreConfigConstants?
  val tmpDirConfig = "pacBioSystem.tmpDir"
  val tmpDirProp = "java.io.tmpdir"
  val tmpDir =
    if (config.hasPath(tmpDirConfig)) config.getString(tmpDirConfig)
    else System.getProperty(tmpDirProp)

  private val idsToPaths: Map[String, Path] = Map(
    "smrtlink.resources.root" -> Paths.get("/"),
    "smrtlink.resources.jobs_root" -> jobRootDir,
    "smrtlink.resources.tmpdir" -> Paths.get(tmpDir)
  )

  private def toResource(id: String): Future[DiskSpaceResource] = Future {
    idsToPaths
      .get(id)
      .map { p =>
        DiskSpaceResource(p.toString,
                          fileSystemUtil.getTotalSpace(p),
                          fileSystemUtil.getFreeSpace(p))
      }
      .getOrElse {
        val ids = idsToPaths.keySet.map(i => s"'$i'").reduce(_ + ", " + _)
        throw new ResourceNotFoundError(
          s"Could not find resource with id $id. Available resources are: $ids.")
      }
  }

  override val routes =
    pathPrefix("disk-space") {
      pathEndOrSingleSlash {
        get {
          complete {
            ok {
              Future.sequence(idsToPaths.keySet.map(toResource))
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
  this: ConfigProvider with FileSystemUtilProvider =>

  val diskSpaceService: Singleton[DiskSpaceService] =
    Singleton(() => new DiskSpaceService(config(), fileSystemUtil()))
      .bindToSet(AllServices)
}

trait DiskSpaceServiceProviderx {
  this: ConfigProvider with FileSystemUtilProvider with ServiceComposer =>

  val diskSpaceService: Singleton[DiskSpaceService] = Singleton(
    () => new DiskSpaceService(config(), fileSystemUtil()))

  addService(diskSpaceService)
}
