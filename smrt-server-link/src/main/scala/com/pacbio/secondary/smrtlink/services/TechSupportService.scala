package com.pacbio.secondary.smrtlink.services

import java.net.URL
import java.util.UUID

import akka.actor.ActorRef
import akka.pattern._
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import com.pacbio.common.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.common.services.ServiceComposer
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models._
import spray.httpx.SprayJsonSupport._

import concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import org.joda.time.{DateTime => JodaDateTime}
import spray.json.JsObject



class TechSupportService(eventServerUrl: Option[URL]) extends SmrtLinkBaseMicroService with SmrtLinkJsonProtocols{

  val manifest = PacBioComponentManifest(
    toServiceId("tech_support"),
    "Tech Support Service",
    "0.1.0",
    "Tech Support service for creating bundles and Pushing TechSupport TGZ Bundles to PacBio")

  val TS_ROUTE_PREFIX = "tech-support"

  def toMockEvent(): Future[SmrtLinkSystemEvent] = {
    eventServerUrl.map { url =>
      Future.successful(SmrtLinkSystemEvent(UUID.randomUUID(), "event_type", 1, UUID.randomUUID(), JodaDateTime.now(), JsObject.empty))
    }.getOrElse(Future.failed(throw new UnprocessableEntityError("System is not configured with an external TechSupport event server")))
  }

  val routes =
    pathPrefix(TS_ROUTE_PREFIX) {
      pathPrefix("system-status") {
        pathEndOrSingleSlash {
          post {
            entity(as[TechSupportSystemStatusRecord]) { record =>
              complete {
                created {
                  toMockEvent()
                }
              }
            }
          }
        }
      } ~
      pathPrefix("job") {
        pathEndOrSingleSlash {
          post {
            entity(as[TechSupportJobRecord]) { record =>
              complete {
                created {
                  toMockEvent()
                }
              }
            }
          }
        }
      }
  }
}

trait TechSupportServiceProvider {
  this: SmrtLinkConfigProvider with ServiceComposer =>

  val techSupportService: Singleton[TechSupportService] =
    Singleton(() => new TechSupportService(externalEventHost().map(_.toUrl())))

  addService(techSupportService)
}