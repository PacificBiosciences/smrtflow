package com.pacbio.secondary.smrtlink.services

import java.nio.file.Paths

import akka.actor.ActorSystem
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.common.models._
import com.pacbio.secondary.smrtlink.actors.ActorSystemProvider
import com.pacbio.secondary.smrtlink.file.{FileSystemUtil, FileSystemUtilProvider}
import com.pacbio.secondary.smrtlink.models.{MimeTypeDetectors, MimeTypes}

import scala.concurrent.ExecutionContext

// TODO(smcclellan): Add specs, docs
class CommonFilesService(mimeTypes: MimeTypes, fileSystemUtil: FileSystemUtil)(
    override implicit val actorSystem: ActorSystem,
    override implicit val ec: ExecutionContext)
  extends SimpleFilesService(mimeTypes, fileSystemUtil) with BaseSmrtService {

  override val serviceBaseId = "files"
  override val serviceName = "Common Files Service"
  override val serviceVersion = "0.1.1"
  override val serviceDescription = "Serves files from the root directory of the filesystem."

  // TODO(smcclellan): THIS IS SUPER-DUPER INSECURE
  override val rootDir = Paths.get("/")
}

trait CommonFilesServiceProvider {
  this: MimeTypeDetectors with FileSystemUtilProvider with ActorSystemProvider =>

  val commonFilesService: Singleton[CommonFilesService] = Singleton { () =>
    implicit val system = actorSystem()
    implicit val ec = scala.concurrent.ExecutionContext.global
    new CommonFilesService(mimeTypes(), fileSystemUtil())
  }.bindToSet(AllServices)
}

trait CommonFilesServiceProviderx {
  this: MimeTypeDetectors with FileSystemUtilProvider with ActorSystemProvider with ServiceComposer =>

  val commonFilesService: Singleton[CommonFilesService] = Singleton { () =>
    implicit val system = actorSystem()
    implicit val ec = scala.concurrent.ExecutionContext.global
    new CommonFilesService(mimeTypes(), fileSystemUtil())
  }

  addService(commonFilesService)
}