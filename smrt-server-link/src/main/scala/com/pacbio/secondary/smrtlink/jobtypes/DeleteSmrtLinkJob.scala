package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.Paths
import org.joda.time.{DateTime => JodaDateTime}

import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.common.models.CommonModelImplicits._
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  InvalidJobOptionError,
  JobResultsWriter
}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.DeleteResourcesOptions
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.UnprocessableEntityError

import scala.util.{Failure, Success, Try}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

case class DeleteSmrtLinkJobOptions(jobId: IdAble,
                                    name: Option[String],
                                    description: Option[String],
                                    removeFiles: Boolean = false,
                                    dryRun: Option[Boolean] = None,
                                    force: Option[Boolean] = None,
                                    projectId: Option[Int] = Some(
                                      JobConstants.GENERAL_PROJECT_ID))
    extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.DELETE_JOB
  override def validate(
      dao: JobsDao,
      config: SystemJobConfig): Option[InvalidJobOptionError] = {
    validateOptionsAndBlock(
      confirmIsDeletable(dao, jobId, force.getOrElse(false)),
      5.seconds)
  }

  override def toJob() = new DeleteSmrtLinkJob(this)

  def confirmIsDeletable(dao: JobsDao,
                         jobId: IdAble,
                         force: Boolean = false): Future[EngineJob] = {
    dao.getJobById(jobId).flatMap { job =>
      if (job.isComplete || force) {
        dao.getJobChildrenByJobId(job.id).flatMap { jobs =>
          if (jobs.isEmpty || force) Future.successful(job)
          else
            Future.failed(throw new UnprocessableEntityError(
              s"Can't delete this job because it has active children. Job Ids: ${jobs
                .map(_.id)}"))
        }
      } else {
        Future.failed(
          throw new UnprocessableEntityError(
            "Can't delete this job because it hasn't completed"))
      }
    }
  }

}

/**
  * If dryRun is provided and true, then only the confirmation that the job can be deleted will
  * be performed. The provide options, force and removeFiles will be removed.
  *
  * Otherwise the model is:
  * 1. check and verify that a job is in a completed state
  * 2.
  *
  * @param opts Job Options
  */
class DeleteSmrtLinkJob(opts: DeleteSmrtLinkJobOptions)
    extends ServiceCoreJob(opts)
    with timeUtils {
  type Out = PacBioDataStore

  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultsWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {

    val startedAt = JodaDateTime.now()
    // DB interaction timeout
    val timeOut = 10.seconds

    // Job Id to delete
    val jobId = opts.jobId
    //

    // If Running in Dry Mode, we ignore the user provided options and set both, force and remove files to false
    val (force, removeFiles, dryMode) = opts.dryRun match {
      case Some(true) => (false, false, true)
      case _ => (opts.force.getOrElse(false), opts.removeFiles, false)
    }

    val f1: Future[DeleteResourcesOptions] = for {
      targetJob <- opts.confirmIsDeletable(dao, jobId, force)
      oldOpts <- Future.successful(
        DeleteResourcesOptions(Paths.get(targetJob.path),
                               removeFiles,
                               targetJob.projectId))
    } yield oldOpts

    //FIXME(mpkocher)(8-31-2017) The order of this should be clearer. And perhaps handle a rollback if possible.
    def f2: Future[String] =
      for {
        _ <- dao.deleteJobById(jobId)
        updatedJob <- dao.getJobById(jobId)
        files <- dao.getDataStoreFilesByJobId(updatedJob.uuid)
        _ <- Future.sequence(
          files.map(f => dao.deleteDataStoreJobFile(f.dataStoreFile.uniqueId)))
      } yield
        s"Updated job ${updatedJob.id}. isActive=${updatedJob.isActive} ${files.length} files updated as isActive=false"

    // If running in dryMode, don't call the db updating
    val updater =
      if (dryMode)
        Future.successful(
          s"Running in drymode. Skipping DB updating of $jobId")
      else f2

    // There's a bit of clumsy composition here between the Either and Try
    val tx: Try[Either[ResultFailed, PacBioDataStore]] = for {
      oldOpts <- runAndBlock(f1, timeOut)
      result <- Try(oldOpts.toJob.run(resources, resultsWriter)) // Because removeFalse and force are false, this should be a null operation
      msgUpdate <- runAndBlock(updater, timeOut)
      _ <- Try(resultsWriter.writeLine(msgUpdate))
    } yield result

    tx match {
      case Success(result) => result
      case Failure(ex) =>
        val runTime = computeTimeDeltaFromNow(startedAt)
        val emsg =
          s"Failed to run files in DeleteSmrtLinkJob $jobId ${ex.getMessage}"
        resultsWriter.writeError(emsg)
        Left(
          ResultFailed(resources.jobId,
                       jobTypeId.id,
                       emsg,
                       runTime,
                       AnalysisJobStates.FAILED,
                       host))
    }
  }
}
