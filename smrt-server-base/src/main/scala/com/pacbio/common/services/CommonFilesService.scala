package com.pacbio.common.services

import java.nio.file.Paths

import akka.actor.ActorSystem
import com.pacbio.common.actors.ActorSystemProvider
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._

import scala.concurrent.ExecutionContext

// TODO(smcclellan): Add specs, docs
class CommonFilesService(mimeTypes: MimeTypes)(
    override implicit val actorSystem: ActorSystem,
    override implicit val ec: ExecutionContext)
  extends SimpleFilesService(mimeTypes) with BaseSmrtService {

  override val serviceBaseId = "files"
  override val serviceName = "Common Files Service"
  override val serviceVersion = "0.1.0"
  override val serviceDescription = "Serves files from the root directory of the filesystem."

  // TODO(smcclellan): THIS IS SUPER-DUPER INSECURE
  override val rootDir = Paths.get("/")
}

trait CommonFilesServiceProvider {
  this: MimeTypeDetectors with ActorSystemProvider =>

  val commonFilesService: Singleton[CommonFilesService] = Singleton { () =>
    implicit val system = actorSystem()
    implicit val ec = scala.concurrent.ExecutionContext.global
    new CommonFilesService(mimeTypes())
  }.bindToSet(AllServices)
}

trait CommonFilesServiceProviderx {
  this: MimeTypeDetectors with ActorSystemProvider with ServiceComposer =>

  val commonFilesService: Singleton[CommonFilesService] = Singleton { () =>
    implicit val system = actorSystem()
    implicit val ec = scala.concurrent.ExecutionContext.global
    new CommonFilesService(mimeTypes())
  }

  addService(commonFilesService)
}