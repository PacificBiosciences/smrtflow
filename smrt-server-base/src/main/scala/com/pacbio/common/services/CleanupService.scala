package com.pacbio.common.services

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.pacbio.common.actors.{CleanupServiceActorRefProvider, CleanupServiceActor}
import com.pacbio.common.auth.{AuthenticatorProvider, BaseRoles, Authenticator}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import spray.http.MediaTypes
import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._

// TODO(smcclellan): add scaladoc, unittests, .rst docs

class CleanupService(cleanupActor: ActorRef, authenticator: Authenticator)
  extends BaseSmrtService
  with DefaultJsonProtocol {

  import BaseRoles._
  import CleanupServiceActor._
  import PacBioJsonProtocol._

  implicit val timeout = Timeout(10.seconds)

  val manifest = PacBioComponentManifest(
    toServiceId("cleanup"),
    "Subsystem Cleanup Service",
    "0.2.0", "Subsystem Cleanup Service", None)

  val routes =
    pathPrefix("cleanup" / "jobs") {
      authenticate(authenticator.jwtAuth) { authInfo =>
        pathEnd {
          get {
            complete {
              (cleanupActor ? GetAllJobs).mapTo[Set[CleanupJobResponse]]
            }
          } ~
          post {
            authorize(authInfo.hasPermission(CLEANUP_ADMIN)) {
              entity(as[ApiCleanupJobCreate]) { c =>
                complete {
                  created {
                    (cleanupActor ? CreateJob(c)).mapTo[CleanupJobResponse]
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
                  (cleanupActor ? GetJob(id)).mapTo[CleanupJobResponse]
                }
              }
            } ~
            delete {
              authorize(authInfo.hasPermission(CLEANUP_ADMIN)) {
                respondWithMediaType(MediaTypes.`application/json`) {
                  complete {
                    ok {
                      (cleanupActor ? DeleteJob(id)).mapTo[String]
                    }
                  }
                }
              }
            }
          } ~
          path("start") {
            post {
              complete {
                ok {
                  (cleanupActor ? StartJob(id)).mapTo[CleanupJobResponse]
                }
              }
            }
          } ~
          path("pause") {
            post {
              complete {
                ok {
                  (cleanupActor ? PauseJob(id)).mapTo[CleanupJobResponse]
                }
              }
            }
          }
        }
      }
    }
}

trait CleanupServiceProvider {
  this: CleanupServiceActorRefProvider with AuthenticatorProvider =>

  final val cleanupService: Singleton[CleanupService] =
    Singleton(() => new CleanupService(cleanupServiceActorRef(), authenticator())).bindToSet(AllServices)
}

trait CleanupServiceProviderx {
  this: CleanupServiceActorRefProvider
      with AuthenticatorProvider
      with ServiceComposer =>

  final val cleanupService: Singleton[CleanupService] =
    Singleton(() => new CleanupService(cleanupServiceActorRef(), authenticator()))

  addService(cleanupService)
}
