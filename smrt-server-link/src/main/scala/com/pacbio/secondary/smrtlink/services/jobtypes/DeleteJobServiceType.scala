package com.pacbio.secondary.smrtlink.services.jobtypes

import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import spray.http.MediaTypes
import spray.json._
import spray.httpx.SprayJsonSupport
import SprayJsonSupport._
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobtypes.DeleteResourcesOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.{EngineManagerActorProvider, JobsDaoActorProvider}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider


class DeleteJobServiceType(dbActor: ActorRef,
                           authenticator: Authenticator,
                           smrtLinkVersion: Option[String],
                           smrtLinkToolsVersion: Option[String])
    extends JobTypeService {

  import SmrtLinkJsonProtocols._

  override val endpoint = "delete-job"
  override val description = "Delete a services job and remove files"

  private def confirmIsDeletable(jobId: UUID): Future[Boolean] = {
    ((dbActor ? GetJobChildrenByUUID(jobId)).mapTo[Seq[EngineJob]]).map {
      jobs => if (jobs.isEmpty) true else throw new Exception("Can't delete this job because it has active children")
    }
  }

  def createJob(sopts: DeleteJobServiceOptions, createdBy: Option[String]): Future[EngineJob] = {
    val uuid = UUID.randomUUID()
    val desc = s"Deleting job ${sopts.jobId}"
    val name = s"Job $endpoint"

    val fx = for {
      targetJob <- (dbActor ? GetJobByUUID(sopts.jobId)).mapTo[EngineJob]
      _ <- confirmIsDeletable(targetJob.uuid)
      opts <- Future { DeleteResourcesOptions(Paths.get(targetJob.path), sopts.removeFiles) }
      _ <- (dbActor ? DeleteJobByUUID(targetJob.uuid))
      engineJob <- (dbActor ? CreateJobType(uuid, name, desc, endpoint, CoreJob(uuid, opts), None, sopts.toJson.toString(), createdBy, smrtLinkVersion, smrtLinkToolsVersion)).mapTo[EngineJob]
    } yield engineJob

    fx
  }

  override val routes =
    pathPrefix(endpoint) {
      pathEndOrSingleSlash {
        get {
          complete {
            jobList(dbActor, endpoint)
          }
        } ~
        post {
          optionalAuthenticate(authenticator.wso2Auth) { user =>
            entity(as[DeleteJobServiceOptions]) { sopts =>
              complete {
                created {
                  createJob(sopts, user.map(_.userId))
                }
              }
            }
          }
        }
      } ~
      sharedJobRoutes(dbActor)
    }

}

trait DeleteJobServiceTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with JobManagerServiceProvider with SmrtLinkConfigProvider =>

  val deleteJobServiceType: Singleton[DeleteJobServiceType] =
    Singleton(() => new DeleteJobServiceType(jobsDaoActor(), authenticator(), smrtLinkVersion(), None)).bindToSet(JobTypes)
}
