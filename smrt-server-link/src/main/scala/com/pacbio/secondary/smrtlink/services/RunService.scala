package com.pacbio.secondary.smrtlink.services

import akka.actor.ActorRef
import akka.pattern.ask
import com.pacbio.common.auth.{AuthenticatorProvider, Authenticator}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models._
import com.pacbio.common.services.ServiceComposer
import com.pacbio.secondary.analysis.engine.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.{RunServiceActorRefProvider, SearchCriteria, RunServiceActor}
import com.pacbio.secondary.smrtlink.auth.SmrtLinkRoles
import com.pacbio.secondary.smrtlink.models._
import spray.httpx.SprayJsonSupport._

import scala.concurrent.ExecutionContext.Implicits._

// TODO(smcclellan): Add documentation

class RunService(runActor: ActorRef, authenticator: Authenticator)
  extends SmrtLinkBaseMicroService
  with SmrtLinkJsonProtocols {

  import RunServiceActor._
  import SmrtLinkRoles._

  val manifest = PacBioComponentManifest(
    toServiceId("runs"),
    "Run Service",
    "0.1.0",
    "Database-backed CRUD operations for Runs")

  val routes =
    //authenticate(authenticator.jwtAuth) { authInfo =>
      pathPrefix("runs") {
        pathEnd {
          get {
            parameters('name.?, 'substring.?, 'createdBy.?, 'reserved.?.as[Option[Boolean]]).as(SearchCriteria) { criteria =>
              complete {
                (runActor ? GetRuns(criteria)).mapTo[Set[RunSummary]]
              }
            }
          } ~
          post {
            entity(as[RunCreate]) { create =>
              //authorize(authInfo.hasPermission(RUN_DESIGN_WRITE)) {
                complete {
                  created {
                    //(runActor ? CreateRun(authInfo.login, create)).mapTo[RunMetadata]
                    (runActor ? CreateRun(create)).mapTo[RunSummary]
                  }
                }
              //}
            }
          }
        } ~
        pathPrefix(JavaUUID) { id =>
          pathEnd {
            get {
              complete {
                ok {
                  (runActor ? GetRun(id)).mapTo[Run]
                }
              }
            } ~
            post {
              entity(as[RunUpdate]) { update =>
                //authorize(authInfo.hasPermission(RUN_DESIGN_WRITE)) {
                  complete {
                    ok {
                      (runActor ? UpdateRun(id, update)).mapTo[RunSummary]
                    }
                  }
                //}
              }
            } ~
            delete {
              //authorize(authInfo.hasPermission(RUN_DESIGN_WRITE)) {
                complete {
                  ok {
                    (runActor ? DeleteRun(id)).mapTo[MessageResponse]
                  }
                }
              //}
            }
          } ~
          pathPrefix("collections") {
            get {
              pathEnd {
                complete {
                  ok {
                    (runActor ? GetCollections(id)).mapTo[Seq[CollectionMetadata]]
                  }
                }
              } ~
              path(JavaUUID) { collectionId =>
                complete {
                  ok {
                    (runActor ? GetCollection(id, collectionId)).mapTo[CollectionMetadata]
                  }
                }
              }
            }
          }
        }
      }
    //}
}

/**
 * Provides a singleton RunService, and also binds it to the set of total services. Concrete providers must mixin
 * a {{{RunServiceActorRefProvider}}} and an {{{AuthenticatorProvider}}}.
 */
trait RunServiceProvider {
  this: RunServiceActorRefProvider
    with AuthenticatorProvider
    with ServiceComposer =>

  final val runService: Singleton[RunService] =
    Singleton(() => new RunService(runServiceActorRef(), authenticator()))

  addService(runService)
}
