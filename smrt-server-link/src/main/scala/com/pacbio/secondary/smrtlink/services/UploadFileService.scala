package com.pacbio.secondary.smrtlink.services

import java.io.File
import java.nio.file.Path
import java.util.UUID

import akka.actor.ActorSystem
import com.pacbio.secondary.smrtlink.actors.ActorSystemProvider
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.models.PacBioComponentManifest
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import spray.json._
import akka.http.scaladsl.server._
import DefaultJsonProtocol._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.directives.{
  FileAndResourceDirectives,
  FileInfo
}
import akka.http.scaladsl.settings.RoutingSettings

class UploadFileService(rootOutputDir: Path)(implicit actorSystem: ActorSystem)
    extends SmrtLinkBaseMicroService
    with FileAndResourceDirectives {

  // for getFromFile to work
  implicit val routing = RoutingSettings.default

  val manifest = PacBioComponentManifest(toServiceId("uploader"),
                                         "Upload file Service",
                                         "0.1.0",
                                         "Upload FileService Endpoints")

  private def resolveDestination(fileInfo: FileInfo): File = {
    logger.info(s"File Info $fileInfo")
    val fileName = fileInfo.fileName

    val i = UUID.randomUUID()
    val f = rootOutputDir.resolve(s"$i-$fileName").toFile
    logger.info(s"Resolved $fileName to local output $f")
    f
  }

  val routes =
    pathPrefix("uploader") {
      pathEndOrSingleSlash {
        post {
          storeUploadedFile("upload_file", resolveDestination) {
            case (fileInfo, file) =>
              complete(
                StatusCodes.Created -> Map("path" -> file.toPath.toString))
          }
        }
      }
    }

}

trait UploadFileServiceProvider {
  this: ActorSystemProvider with SmrtLinkConfigProvider with ServiceComposer =>

  val uploadFileService: Singleton[UploadFileService] =
    Singleton { () =>
      implicit val system = actorSystem()
      new UploadFileService(smrtLinkTempDir())(system)
    }

  addService(uploadFileService)
}
