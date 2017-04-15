package com.pacbio.secondary.smrtlink.services

import akka.actor.ActorSystem
import com.pacbio.common.actors.ActorSystemProvider
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import com.pacbio.common.services.ServiceComposer
import com.pacbio.secondary.smrtlink.models._
import spray.json._
import spray.routing._
import DefaultJsonProtocol._
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import spray.httpx.SprayJsonSupport._
import spray.routing.RoutingSettings
import spray.routing.directives.FileAndResourceDirectives


class SwaggerFileService(swaggerResourceName: String)(implicit actorSystem: ActorSystem) extends SmrtLinkBaseMicroService with FileAndResourceDirectives{

  // for getFromFile to work
  implicit val routing = RoutingSettings.default

  val manifest = PacBioComponentManifest(
    toServiceId("swagger_file"),
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