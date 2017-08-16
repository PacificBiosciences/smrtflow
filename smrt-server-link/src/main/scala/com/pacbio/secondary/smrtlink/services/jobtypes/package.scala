package com.pacbio.secondary.smrtlink.services

import akka.actor.ActorRef
import akka.pattern.ask
import com.pacbio.secondary.smrtlink.auth.Authenticator
import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.secondary.smrtlink.models.UserRecord
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.EngineJob
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor.{CreateJobType, GetJobByIdAble}
import com.pacbio.secondary.smrtlink.{JobServiceConstants, SmrtLinkConstants}
import com.pacbio.secondary.smrtlink.models.SmrtLinkJsonProtocols._
import spray.httpx.SprayJsonSupport._
import spray.httpx.unmarshalling._
import spray.json._
import spray.routing.Route

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

package object jobtypes {
  trait ProjectIdJoiner extends SmrtLinkConstants {
    def joinProjectIds(projectIds: Seq[Int]): Int = projectIds.distinct match {
      case ids if ids.size == 1 => ids.head
      case _ => GENERAL_PROJECT_ID
    }
  }

  abstract class JobTypeService[O](dbActor: ActorRef, authenticator: Authenticator)(implicit um: Unmarshaller[O])
    extends JobService with JobServiceConstants {

    // Subclasses must override early
    val endpoint: String

    // Subclasses must override early
    val description: String

    // Subclasses must override
    protected def createJob(opts: O, user: Option[UserRecord]): Future[CreateJobType]

    // Subclasses may override to add custom logic
    protected def createEngineJob(dbActor: ActorRef,
                                  opts: O,
                                  user: Option[UserRecord]): Future[EngineJob] =
      createJob(opts, user).flatMap { c => (dbActor ? c).mapTo[EngineJob] }

    // Subclasses may override to add custom job routes
    protected def extraRoutes(dbActor: ActorRef, authenticator: Authenticator): Route = reject

    def getJob(i: IdAble): Future[EngineJob] =
      (dbActor ? GetJobByIdAble(i)).mapTo[EngineJob]

    val routes =
      pathPrefix(endpoint) {
        extraRoutes(dbActor, authenticator) ~
        pathEndOrSingleSlash {
          get {
            parameters('showAll.?, 'projectId.?.as[Option[Int]]) { (showAll, projectId) =>
              complete {
                ok {
                  jobList(dbActor, endpoint, showAll.isDefined, projectId)
                }
              }
            }
          } ~
          post {
            optionalAuthenticate(authenticator.wso2Auth) { user =>
              entity(as[O]) { opts =>
                complete {
                  created {
                    createEngineJob(dbActor, opts, user)
                  }
                }
              }
            }
          }
        } ~
        sharedJobRoutes(dbActor)
      }
  }
}
