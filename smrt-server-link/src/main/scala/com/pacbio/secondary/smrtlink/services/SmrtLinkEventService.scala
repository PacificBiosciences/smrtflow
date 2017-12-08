package com.pacbio.secondary.smrtlink.services

import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json._
import akka.http.scaladsl.server._
import DefaultJsonProtocol._
import com.pacbio.secondary.smrtlink.models.PacBioComponentManifest
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.actors.EventManagerActorProvider

class SmrtLinkEventService(eventManagerActor: ActorRef)
    extends SmrtLinkBaseMicroService {

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._
  import com.pacbio.secondary.smrtlink.actors.EventManagerActor._

  val ROUTE_PREFIX = "events"

  val manifest = PacBioComponentManifest(
    toServiceId("events"),
    "SL Event Service",
    "0.1.0",
    "SMRT Link Event Service. Forwards messages to EventManager")

  def eventRoutes: Route =
    pathPrefix(ROUTE_PREFIX) {
      pathEndOrSingleSlash {
        post {
          entity(as[SmrtLinkEvent]) { event =>
            complete {
              created {
                (eventManagerActor ? CreateEvent(event))
                  .mapTo[SmrtLinkSystemEvent]
              }
            }
          }
        }
      }
    }

  def routes = eventRoutes

}

trait SmrtLinkEventServiceProvider {
  this: ServiceComposer with EventManagerActorProvider =>

  val smrtLinkEventService: Singleton[SmrtLinkEventService] =
    Singleton(() => new SmrtLinkEventService(eventManagerActor()))

  addService(smrtLinkEventService)
}
