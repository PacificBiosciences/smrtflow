package com.pacbio.common.services

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.pacbio.common.actors.{HealthServiceActorRefProvider, HealthServiceActor}
import com.pacbio.common.auth.{AuthenticatorProvider, Authenticator, BaseRoles}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import spray.http.MediaTypes
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._

class HealthService(healthActor: ActorRef, authenticator: Authenticator)
  extends BaseSmrtService
  with DefaultJsonProtocol {

  import BaseRoles._
  import HealthServiceActor._
  import PacBioJsonProtocol._

  implicit val timeout = Timeout(10.seconds)

  val components = Seq(PacBioComponent(toServiceId("logging"), "0.1.0"))

  val manifest = PacBioComponentManifest(
    toServiceId("health"),
    "Subsystem Health Service",
    "0.2.0", "Subsystem Health Service", components)

  val healthServiceName = "health"

  val routes =
    pathPrefix(healthServiceName) {
      authenticate(authenticator.jwtAuth) { authInfo =>
        pathEnd {
          get {
            complete {
              (healthActor ? GetSevereGauges).mapTo[Seq[HealthGauge]]
            }
          }
        } ~
        pathPrefix("gauges") {
          pathEnd {
            get {
              complete {
                (healthActor ? GetAllGauges).mapTo[Seq[HealthGauge]]
              }
            } ~
            post {
              entity(as[HealthGaugeRecord]) { m =>
                authorize(authInfo.hasPermission(HEALTH_AND_LOGS_ADMIN)) {
                  respondWithMediaType(MediaTypes.`application/json`) {
                    complete {
                      created {
                        (healthActor ? CreateGauge(m)).mapTo[String]
                      }
                    }
                  }
                }
              }
            }
          } ~
          pathPrefix(Segment) { id =>
            pathEnd {
              get {
                complete {
                  ok {
                    (healthActor ? GetGauge(id)).mapTo[HealthGauge]
                  }
                }
              }
            } ~
            path("messages") {
              get {
                complete {
                  (healthActor ? GetAllMessages(id)).mapTo[Seq[HealthGaugeMessage]]
                }
              } ~
              post {
                authorize(authInfo.hasPermission(HEALTH_AND_LOGS_WRITE)) {
                  entity(as[HealthGaugeMessageRecord]) { m =>
                    complete {
                      created {
                        (healthActor ? CreateMessage(id, m)).mapTo[HealthGaugeMessage]
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
}

/**
 * Provides a singleton HealthService, and also binds it to the set of total services. Concrete providers must mixin a
 * {{{HealthServiceActorRefProvider}}} and an {{{AuthenticatorProvider}}}.
 */
trait HealthServiceProvider {
  this: HealthServiceActorRefProvider with AuthenticatorProvider =>

  final val healthService: Singleton[HealthService] =
    Singleton(() => new HealthService(healthServiceActorRef(), authenticator())).bindToSet(AllServices)
}

trait HealthServiceProviderx {
  this: HealthServiceActorRefProvider
    with AuthenticatorProvider
    with ServiceComposer =>

  final val healthService: Singleton[HealthService] =
    Singleton(() => new HealthService(healthServiceActorRef(), authenticator()))

  addService(healthService)
}
