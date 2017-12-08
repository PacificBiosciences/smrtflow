package com.pacbio.secondary.smrtlink.services

import akka.actor.ActorSystem
import com.pacbio.secondary.smrtlink.actors.ActorSystemProvider
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.models.PacBioComponentManifest
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import spray.json._
import akka.http.scaladsl.server._
import DefaultJsonProtocol._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.directives.FileAndResourceDirectives
import akka.http.scaladsl.settings.RoutingSettings

class SwaggerFileService(swaggerResourceName: String)(
    implicit actorSystem: ActorSystem)
    extends SmrtLinkBaseMicroService
    with FileAndResourceDirectives {

  // for getFromFile to work
  implicit val routing = RoutingSettings.default

  val manifest = PacBioComponentManifest(toServiceId("swagger_file"),
                                         "Swagger JSON file Service",
                                         "0.1.0",
                                         "Swagger Service Endpoints")

  val routes =
    pathPrefix("swagger") {
      pathEndOrSingleSlash {
        get {
          getFromResource(swaggerResourceName)
        }
      }
    }

}

trait SwaggerFileServiceProvider {
  this: ActorSystemProvider with SmrtLinkConfigProvider with ServiceComposer =>

  val swaggerFileService: Singleton[SwaggerFileService] =
    Singleton { () =>
      implicit val system = actorSystem()
      new SwaggerFileService(swaggerResource())(system)
    }

  addService(swaggerFileService)
}
