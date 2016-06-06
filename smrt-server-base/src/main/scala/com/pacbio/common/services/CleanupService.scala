package com.pacbio.common.services

import akka.util.Timeout
import com.pacbio.common.actors.{CleanupDao, CleanupDaoProvider}
import com.pacbio.common.auth.{AuthenticatorProvider, BaseRoles, Authenticator}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import spray.http.MediaTypes
import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._

// TODO(smcclellan): add scaladoc, unittests, .rst docs

class CleanupService(dao: CleanupDao, authenticator: Authenticator)
  extends BaseSmrtService
  with DefaultJsonProtocol {

  import BaseRoles._
  import PacBioJsonProtocol._

  implicit val timeout = Timeout(10.seconds)

  val manifest = PacBioComponentManifest(
    toServiceId("cleanup"),
    "Subsystem Cleanup Service",
    "0.2.0", "Subsystem Cleanup Service")

  val routes =
    pathPrefix("cleanup" / "jobs") {
      authenticate(authenticator.jwtAuth) { authInfo =>
        pathEnd {
          get {
            complete {
              dao.getAllJobs()
            }
          } ~
          post {
            authorize(authInfo.hasPermission(CLEANUP_ADMIN)) {
              entity(as[ApiCleanupJobCreate]) { c =>
                complete {
                  created {
                    dao.createJob(c)
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
                  dao.getJob(id)
                }
              }
            } ~
            delete {
              authorize(authInfo.hasPermission(CLEANUP_ADMIN)) {
                respondWithMediaType(MediaTypes.`application/json`) {
                  complete {
                    ok {
                      dao.deleteJob(id)
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
                  dao.startJob(id)
                }
              }
            }
          } ~
          path("pause") {
            post {
              complete {
                ok {
                  dao.startJob(id)
                }
              }
            }
          }
        }
      }
    }
}

trait CleanupServiceProvider {
  this: CleanupDaoProvider with AuthenticatorProvider =>

  final val cleanupService: Singleton[CleanupService] =
    Singleton(() => new CleanupService(cleanupDao(), authenticator())).bindToSet(AllServices)
}

trait CleanupServiceProviderx {
<<<<<<< HEAD
  this: CleanupDaoProvider
      with AuthenticatorProvider
      with ServiceComposer =>
=======
  this: CleanupServiceActorRefProvider
    with AuthenticatorProvider
    with ServiceComposer =>
>>>>>>> migration

  final val cleanupService: Singleton[CleanupService] =
    Singleton(() => new CleanupService(cleanupDao(), authenticator()))

  addService(cleanupService)
}
