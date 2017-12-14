package com.pacbio.secondary.smrtlink.services

import akka.actor.ActorRef
import akka.pattern.ask

import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.common.models._
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.{
  RunServiceActor,
  RunServiceActorRefProvider,
  SearchCriteria
}
import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.models._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import scala.concurrent.ExecutionContext.Implicits._

// TODO(smcclellan): Add documentation

class RunService(runActor: ActorRef)
    extends SmrtLinkBaseMicroService
    with SmrtLinkJsonProtocols {

  import RunServiceActor._

  val manifest = PacBioComponentManifest(
    toServiceId("runs"),
    "Run Service",
    "0.1.0",
    "Database-backed CRUD operations for Runs")

  val routes =
    //authenticate(authenticator.wso2Auth) { user =>
    pathPrefix("runs") {
      pathEnd {
        get {
          parameters('name.?,
                     'substring.?,
                     'createdBy.?,
                     'reserved.?.as[Option[Boolean]]).as(SearchCriteria) {
            criteria =>
              complete {
                (runActor ? GetRuns(criteria)).mapTo[Set[RunSummary]]
              }
          }
        } ~
          post {
            entity(as[RunCreate]) { create =>
              complete {
                created {
                  //(runActor ? CreateRun(user.userId, create)).mapTo[RunMetadata]
                  (runActor ? CreateRun(create)).mapTo[RunSummary]
                }
              }
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
                  complete {
                    ok {
                      (runActor ? UpdateRun(id, update)).mapTo[RunSummary]
                    }
                  }
                }
              } ~
              delete {
                complete {
                  ok {
                    (runActor ? DeleteRun(id)).mapTo[MessageResponse]
                  }
                }
              }
          } ~
            pathPrefix("collections") {
              get {
                pathEnd {
                  complete {
                    ok {
                      (runActor ? GetCollections(id))
                        .mapTo[Seq[CollectionMetadata]]
                    }
                  }
                } ~
                  path(JavaUUID) { collectionId =>
                    complete {
                      ok {
                        (runActor ? GetCollection(id, collectionId))
                          .mapTo[CollectionMetadata]
                      }
                    }
                  }
              }
            } ~
            pathPrefix("datamodel") {
              get {
                pathEndOrSingleSlash {
                  complete {
                    ok { // added as a sugar layer to avoid getting the XML from within the JSON of Run
                      (runActor ? GetRun(id))
                        .mapTo[Run]
                        .map(f => scala.xml.XML.loadString(f.dataModel))
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
  this: RunServiceActorRefProvider with ServiceComposer =>

  final val runService: Singleton[RunService] =
    Singleton(() => new RunService(runServiceActorRef()))

  addService(runService)
}
