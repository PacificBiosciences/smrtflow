package com.pacbio.common.services

import akka.util.Timeout
import com.pacbio.common.actors.{CleanupDaoProvider, CleanupDao}
import com.pacbio.common.auth.{AuthenticatorProvider, BaseRoles, Authenticator}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import spray.http.MediaTypes
import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._

// TODO(smcclellan): add scaladoc, unittests, .rst docs

class CleanupService(cleanupDao: CleanupDao, authenticator: Authenticator)
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
              ok {
                cleanupDao.getAllJobs
              }
            }
          } ~
          post {
            authorize(authInfo.hasPermission(CLEANUP_ADMIN)) {
              entity(as[ApiCleanupJobCreate]) { c =>
                complete {
                  created {
                    cleanupDao.createJob(c)
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
                  cleanupDao.getJob(id)
                }
              }
            } ~
            delete {
              authorize(authInfo.hasPermission(CLEANUP_ADMIN)) {
                respondWithMediaType(MediaTypes.`application/json`) {
                  complete {
                    ok {
                      cleanupDao.deleteJob(id)
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
                  cleanupDao.startJob(id)
                }
              }
            }
          } ~
          path("pause") {
            post {
              complete {
                ok {
                  cleanupDao.pauseJob(id)
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
  this: CleanupDaoProvider
    with AuthenticatorProvider
    with ServiceComposer =>

  final val cleanupService: Singleton[CleanupService] =
    Singleton(() => new CleanupService(cleanupDao(), authenticator()))

  addService(cleanupService)
}
