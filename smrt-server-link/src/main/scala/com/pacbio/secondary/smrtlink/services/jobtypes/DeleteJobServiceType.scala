package com.pacbio.secondary.smrtlink.services.jobtypes

import java.nio.file.Paths
import java.util.UUID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import spray.httpx.SprayJsonSupport._
import spray.json._
import akka.actor.ActorRef
import akka.pattern.ask

import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.{CommonModelImplicits, UserRecord}
import com.pacbio.common.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobtypes.DeleteResourcesOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.SecondaryAnalysisJsonProtocols._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider

class DeleteJobServiceType(dbActor: ActorRef,
                           authenticator: Authenticator,
                           smrtLinkVersion: Option[String])
    extends {
      override val endpoint = JobTypeIds.DELETE_JOB.id
      override val description = "Delete a services job and remove files"
    } with JobTypeService[DeleteJobServiceOptions](dbActor, authenticator) {

  import CommonModelImplicits._

  private def confirmIsDeletable(jobId: UUID, force: Boolean = false): Future[EngineJob] = {
    (dbActor ? GetJobByIdAble(jobId)).mapTo[EngineJob].flatMap { job =>
      if (job.isComplete || force) {
        (dbActor ? GetJobChildrenById(jobId)).mapTo[Seq[EngineJob]].map {
          jobs => if (jobs.isEmpty || force) job else
            throw new UnprocessableEntityError("Can't delete this job because it has active children")
        }
      } else throw new UnprocessableEntityError("Can't delete this job because it hasn't completed")
    }
  }

  override def createJob(sopts: DeleteJobServiceOptions, user: Option[UserRecord]): Future[CreateJobType] = {
    val uuid = UUID.randomUUID()
    val force = sopts.force.getOrElse(false)
    // XXX This is not really a good idea, but that's what marketing and TS want
    val removeFiles = sopts.removeFiles //&& !force

    for {
      targetJob <- confirmIsDeletable(sopts.jobId, force)
      opts <- Future { DeleteResourcesOptions(Paths.get(targetJob.path), removeFiles, targetJob.projectId) }
      _ <- dbActor ? DeleteJobById(targetJob.uuid)
    } yield CreateJobType(
        uuid,
        s"Job $endpoint",
        s"Deleting job ${sopts.jobId}",
        endpoint,
        CoreJob(uuid, opts),
        None,
        sopts.toJson.toString(),
        user.map(_.userId),
        user.flatMap(_.userEmail),
        smrtLinkVersion)
  }
  override def createEngineJob(dbActor: ActorRef,
                               sopts: DeleteJobServiceOptions,
                               user: Option[UserRecord]): Future[EngineJob] =
    if (sopts.dryRun.getOrElse(false)) confirmIsDeletable(sopts.jobId) else super.createEngineJob(dbActor, sopts, user)
}

trait DeleteJobServiceTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with JobManagerServiceProvider with SmrtLinkConfigProvider =>

  val deleteJobServiceType: Singleton[DeleteJobServiceType] =
    Singleton(() => new DeleteJobServiceType(jobsDaoActor(), authenticator(), smrtLinkVersion())).bindToSet(JobTypes)
}
