package com.pacbio.common.services

import akka.util.Timeout
import com.pacbio.common.actors.{HealthDaoProvider, HealthDao}
import com.pacbio.common.auth.{AuthenticatorProvider, Authenticator, BaseRoles}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import spray.http.MediaTypes
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._

class HealthService(healthDao: HealthDao, authenticator: Authenticator)
  extends BaseSmrtService
  with DefaultJsonProtocol {

  import BaseRoles._
  import PacBioJsonProtocol._

  implicit val timeout = Timeout(10.seconds)

  val manifest = PacBioComponentManifest(
    toServiceId("health"),
    "Health Service",
    "1.0.0", "Subsystem Health Service")

  val healthServiceName = "health"

  val routes =
    pathPrefix(healthServiceName) {
      authenticate(authenticator.jwtAuth) { authInfo =>
        pathEnd {
          get {
            complete {
              ok {
                healthDao.getUnhealthyMetrics
              }
            }
          }
        } ~
        pathPrefix("metrics") {
          pathEnd {
            get {
              complete {
                ok {
                  healthDao.getAllHealthMetrics
                }
              }
            } ~
            post {
              entity(as[HealthMetricCreateMessage]) { m =>
                authorize(authInfo.hasPermission(HEALTH_AND_LOGS_ADMIN)) {
                  respondWithMediaType(MediaTypes.`application/json`) {
                    complete {
                      created {
                        healthDao.createHealthMetric(m)
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
                    healthDao.getHealthMetric(id)
                  }
                }
              }
            } ~
            path("updates") {
              get {
                complete {
                  ok {
                    healthDao.getMetricUpdates(id)
                  }
                }
              }
            }
          }
        } ~
        path("updates") {
          get {
            complete {
              ok {
                healthDao.getAllUpdates
              }
            }
          } ~
          post {
            authorize(authInfo.hasPermission(HEALTH_AND_LOGS_WRITE)) {
              entity(as[HealthMetricUpdateMessage]) { m =>
                complete {
                  created {
                    healthDao.update(m)
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