package com.pacbio.common.services

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.pacbio.common.actors.{LogServiceActorRefProvider, SearchCriteria, LogServiceActor}
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

class LogService(logActor: ActorRef, authenticator: Authenticator)
  extends BaseSmrtService
  with DefaultJsonProtocol {

  import BaseRoles._
  import LogServiceActor._
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
              (logActor ? GetAllResources).mapTo[Seq[LogResource]]
            }
          } ~
          post {
            authorize(authInfo.hasPermission(HEALTH_AND_LOGS_ADMIN)) {
              entity(as[LogResourceRecord]) { m =>
                respondWithMediaType(MediaTypes.`application/json`) {
                  complete {
                    created {
                      (logActor ? CreateResource(m)).mapTo[String]
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
                (logActor ? GetSystemMessages).mapTo[Seq[LogMessage]]
              }
            }
          } ~
          path("search") {
            get {
              parameters('substring.?, 'sourceId.?, 'startTime.?.as[Option[Long]], 'endTime.?.as[Option[Long]])
                  .as(SearchCriteria) { criteria =>
                complete {
                  (logActor ? SearchSystemMessages(criteria)).mapTo[Seq[LogMessage]]
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
                  (logActor ? GetResource(id)).mapTo[LogResource]
                }
              }
            }
          } ~
          path("messages") {
            get {
              complete {
                (logActor ? GetMessages(id)).mapTo[Seq[LogMessage]]
              }
            } ~
            post {
              authorize(authInfo.hasPermission(HEALTH_AND_LOGS_WRITE)) {
                entity(as[LogMessageRecord]) { m =>
                  complete {
                    created {
                      (logActor ? CreateMessage(id, m)).mapTo[LogMessage]
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
                  (logActor ? SearchMessages(id, criteria)).mapTo[Seq[LogMessage]]
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
  this: LogServiceActorRefProvider with AuthenticatorProvider =>

  final val logService: Singleton[LogService] =
    Singleton(() => new LogService(logServiceActorRef(), authenticator())).bindToSet(AllServices)
}

trait LogServiceProviderx {
  this: LogServiceActorRefProvider
      with AuthenticatorProvider
      with ServiceComposer =>

  final val logService: Singleton[LogService] =
    Singleton(() => new LogService(logServiceActorRef(), authenticator()))

  addService(logService)
}
