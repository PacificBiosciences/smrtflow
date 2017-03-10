package com.pacbio.common.services

import com.pacbio.common.actors._
import com.pacbio.common.auth.{AuthenticatorProvider, Authenticator}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import com.pacbio.common.time.PacBioDateTimeFormat
import org.joda.time.{DateTime => JodaDateTime}
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing._

import scala.concurrent.ExecutionContext.Implicits._

class LogService(logDao: LogDao, authenticator: Authenticator)
  extends BaseSmrtService
  with DefaultJsonProtocol {

  import PacBioDateTimeFormat.TIME_ZONE
  import PacBioJsonProtocol._
  import language.implicitConversions

  implicit def longOptionToJodaDateTimeOption(t: Option[Long]): Option[JodaDateTime] =
    t.map(new JodaDateTime(_, TIME_ZONE))

  val manifest = PacBioComponentManifest(
    toServiceId("log"),
    "Subsystem Logging Service",
    "0.3.0", "Subsystem Logging Service")

  val logServiceName = "loggers"

  val routes =
    pathPrefix(logServiceName) {
      pathEnd {
        get {
          complete {
            ok {
              logDao.getAllLogResources
            }
          }
        } ~
        post {
          authenticate(authenticator.wso2Auth) { user =>
            entity(as[LogResourceRecord]) { m =>
              complete {
                created {
                  logDao.createLogResource(m)
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
            authenticate(authenticator.wso2Auth) { user =>
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

/**
 * Provides a singleton LogService, and also binds it to the set of total services. Concrete providers must mixin a
 * {{{LogServiceActorRefProvider}}} and an {{{AuthenticatorProvider}}}.
 */
trait LogServiceProvider {
  this: LogDaoProvider with AuthenticatorProvider =>

  final val logService: Singleton[LogService] =
    Singleton(() => new LogService(logDao(), authenticator())).bindToSet(AllServices)
}

trait LogServiceProviderx {
  this: LogDaoProvider
    with AuthenticatorProvider
    with ServiceComposer =>

  final val logService: Singleton[LogService] =
    Singleton(() => new LogService(logDao(), authenticator()))

  addService(logService)
}
