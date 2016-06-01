package com.pacbio.common.services

import akka.util.Timeout
import com.pacbio.common.actors.{HealthDao, HealthDaoProvider, HealthServiceActor}
import com.pacbio.common.auth.{AuthenticatorProvider, Authenticator, BaseRoles}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import spray.http.MediaTypes
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._

class HealthService(dao: HealthDao, authenticator: Authenticator)
  extends BaseSmrtService
  with DefaultJsonProtocol {

  import BaseRoles._
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
              ok {
                dao.getSevereHealthGauges
              }
            }
          }
        } ~
        pathPrefix("gauges") {
          pathEnd {
            get {
              complete {
                ok {
                  dao.getAllHealthGauges
                }
              }
            } ~
            post {
              entity(as[HealthGaugeRecord]) { m =>
                authorize(authInfo.hasPermission(HEALTH_AND_LOGS_ADMIN)) {
                  respondWithMediaType(MediaTypes.`application/json`) {
                    complete {
                      created {
                        dao.createHealthGauge(m)
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
                    dao.getHealthGauge(id)
                  }
                }
              }
            } ~
            path("messages") {
              get {
                complete {
                  ok {
                    dao.getAllHealthMessages(id)
                  }
                }
              } ~
              post {
                authorize(authInfo.hasPermission(HEALTH_AND_LOGS_WRITE)) {
                  entity(as[HealthGaugeMessageRecord]) { m =>
                    complete {
                      created {
                        dao.createHealthMessage(id, m)
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
 * {{{HealthDaoProvider}}} and an {{{AuthenticatorProvider}}}.
 */
trait HealthServiceProvider {
  this: HealthDaoProvider with AuthenticatorProvider =>

  final val healthService: Singleton[HealthService] =
    Singleton(() => new HealthService(healthDao(), authenticator())).bindToSet(AllServices)
}

trait HealthServiceProviderx {
  this: HealthDaoProvider
      with AuthenticatorProvider
      with ServiceComposer =>

  final val healthService: Singleton[HealthService] =
    Singleton(() => new HealthService(healthDao(), authenticator()))

  addService(healthService)
}
