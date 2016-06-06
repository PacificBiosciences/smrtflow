package com.pacbio.common.services

import akka.util.Timeout
import com.pacbio.common.actors._
import com.pacbio.common.auth.{AuthenticatorProvider, BaseRoles, Authenticator}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import org.joda.time.{DateTime => JodaDateTime}
import spray.http.MediaTypes
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._

class LogService(logDao: LogDao, authenticator: Authenticator)
  extends BaseSmrtService
  with DefaultJsonProtocol {

  import BaseRoles._
  import PacBioJsonProtocol._
  import language.implicitConversions

  implicit val timeout = Timeout(10.seconds)

  implicit def longOptionToJodaDateTimeOption(t: Option[Long]): Option[JodaDateTime] = t.map(new JodaDateTime(_))

  val manifest = PacBioComponentManifest(
    toServiceId("log"),
    "Subsystem Logging Service",
    "0.3.0", "Subsystem Logging Service")

  val logServiceName = "loggers"

  val routes =
    pathPrefix(logServiceName) {
      authenticate(authenticator.jwtAuth) { authInfo =>
        pathEnd {
          get {
            complete {
              ok {
                logDao.getAllLogResources
              }
            }
          } ~
          post {
            authorize(authInfo.hasPermission(HEALTH_AND_LOGS_ADMIN)) {
              entity(as[LogResourceRecord]) { m =>
                respondWithMediaType(MediaTypes.`application/json`) {
                  complete {
                    created {
                      logDao.createLogResource(m)
                    }
                  }
                }
              }
            }
          }
        } ~
        pathPrefix("system") {
          path("messages") {
            get {
              complete {
                ok {
                  logDao.getSystemLogMessages
                }
              }
            }
          } ~
          path("search") {
            get {
              parameters('substring.?, 'sourceId.?, 'startTime.?.as[Option[Long]], 'endTime.?.as[Option[Long]])
                .as(SearchCriteria) { criteria =>
                complete {
                  ok {
                    logDao.searchSystemLogMessages(criteria)
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
                  logDao.getLogResource(id)
                }
              }
            }
          } ~
          path("messages") {
            get {
              complete {
                ok {
                  logDao.getLogMessages(id)
                }
              }
            } ~
            post {
              authorize(authInfo.hasPermission(HEALTH_AND_LOGS_WRITE)) {
                entity(as[LogMessageRecord]) { m =>
                  complete {
                    created {
                      logDao.createLogMessage(id, m)
                    }
                  }
                }
              }
            }
          } ~
          path("search") {
            get {
              parameters('substring.?, 'sourceId.?, 'startTime.?.as[Option[Long]], 'endTime.?.as[Option[Long]])
                .as(SearchCriteria) { criteria =>
                complete {
                  ok {
                    logDao.searchLogMessages(id, criteria)
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
 * Provides a singleton LogService, and also binds it to the set of total services. Concrete providers must mixin a
 * {{{LogDaoProvider}}} and an {{{AuthenticatorProvider}}}.
 */
trait LogServiceProvider {
  this: LogDaoProvider with AuthenticatorProvider =>

  final val logService: Singleton[LogService] =
    Singleton(() => new LogService(logDao(), authenticator())).bindToSet(AllServices)
}

trait LogServiceProviderx {
<<<<<<< HEAD
  this: LogDaoProvider
      with AuthenticatorProvider
      with ServiceComposer =>
=======
  this: LogServiceActorRefProvider
    with AuthenticatorProvider
    with ServiceComposer =>
>>>>>>> migration

  final val logService: Singleton[LogService] =
    Singleton(() => new LogService(logDao(), authenticator()))

  addService(logService)
}
