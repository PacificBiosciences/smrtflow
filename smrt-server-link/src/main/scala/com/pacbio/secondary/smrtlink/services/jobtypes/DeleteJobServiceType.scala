package com.pacbio.secondary.smrtlink.services.jobtypes

import java.nio.file.Paths
import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import spray.json._
import spray.httpx.SprayJsonSupport
import SprayJsonSupport._
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobtypes.DeleteResourcesOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider


class DeleteJobServiceType(dbActor: ActorRef,
                           authenticator: Authenticator,
                           smrtLinkVersion: Option[String],
                           smrtLinkToolsVersion: Option[String])
    extends JobTypeService {

  import SmrtLinkJsonProtocols._
  import CommonModelImplicits._

  override val endpoint = JobTypeIds.DELETE_JOB.id
  override val description = "Delete a services job and remove files"

  private def confirmIsDeletable(jobId: UUID): Future[EngineJob] = {
    (dbActor ? GetJobByIdAble(jobId)).mapTo[EngineJob].flatMap { job =>
      if (job.isComplete) {
        (dbActor ? GetJobChildrenByUUID(jobId)).mapTo[Seq[EngineJob]].map {
          jobs => if (jobs.isEmpty) job else
            throw new UnprocessableEntityError("Can't delete this job because it has active children")
        }
      } else throw new UnprocessableEntityError("Can't delete this job because it hasn't completed")
    }
  }

  def createJob(sopts: DeleteJobServiceOptions, createdBy: Option[String]): Future[EngineJob] = {
    val uuid = UUID.randomUUID()
    val desc = s"Deleting job ${sopts.jobId}"
    val name = s"Job $endpoint"

    val fx = for {
      targetJob <- confirmIsDeletable(sopts.jobId)
      opts <- Future { DeleteResourcesOptions(Paths.get(targetJob.path), sopts.removeFiles, targetJob.projectId) }
      _ <- dbActor ? DeleteJobByUUID(targetJob.uuid)
      engineJob <- (dbActor ? CreateJobType(uuid, name, desc, endpoint, CoreJob(uuid, opts), None, sopts.toJson.toString(), createdBy, smrtLinkVersion, smrtLinkToolsVersion)).mapTo[EngineJob]
    } yield engineJob

    fx
  }

  def dryRun(sopts: DeleteJobServiceOptions): Future[EngineJob] = confirmIsDeletable(sopts.jobId)

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
                  if (sopts.dryRun.getOrElse(false))
                    dryRun(sopts)
                  else
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
