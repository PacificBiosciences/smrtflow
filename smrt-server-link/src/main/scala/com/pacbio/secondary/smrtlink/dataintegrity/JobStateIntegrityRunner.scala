package com.pacbio.secondary.smrtlink.dataintegrity

import org.joda.time.{DateTime => JodaDateTime}

import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.EngineJob
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.common.models.CommonModelImplicits._



/**
  * Created by mkocher on 4/27/17.
  */
class JobStateIntegrityRunner(dao: JobsDao, smrtLinkVersion: Option[String]) extends BaseDataIntegrity with LazyLogging{

  override val runnerId = "smrtflow.dataintegrity.jobstate"

  final val STUCK_STATES = Seq(AnalysisJobStates.CREATED, AnalysisJobStates.RUNNING)

  /**
    * Only Interested in Jobs that are "stuck" in the Created, or Running state
    * @param job SL Engine Job
    * @return
    */
  def filterByState(job: EngineJob): Boolean =
    STUCK_STATES contains job.state

  /**
    * Jobs that are not the same version as the current SL System version
    * are assumed to be from a previous install and should be marked as FAILED.
    *
    * @param expectedVersion SMRT Link System Version
    * @param job             Engine Job
    * @return
    */
  def filterByNotEqualVersion(expectedVersion: String, job: EngineJob): Boolean = {
    job.smrtlinkVersion
        .map(v => v != expectedVersion)
        .getOrElse(false)
  }


  /**
    * Core Job filter interface for detecting "STUCK" jobs.
    *
    * @param job Engine Job
    * @return
    */
  def jobFilter(job: EngineJob): Boolean = {
    def filterWithVersion(j: EngineJob): Boolean = {
      smrtLinkVersion
          .map(v => filterByNotEqualVersion(v, j))
          .getOrElse(false)
    }

    filterWithVersion(job) && filterByState(job)

  }

  /**
    * Update the Job State to Failed and append the errorMessage of the job
    *
    * Should this append the stderr in the job directory?
    *
    * @param engineJob Engine Job that should be marked as failed.
    * @return
    */
  def updateJobStateToFail(engineJob: EngineJob): Future[EngineJob] = {
    val m = s"Detected STUCK job ${engineJob.id} Type:${engineJob.jobTypeId} in state ${engineJob.state}. Marking as FAILED"
    val errorMessage = engineJob.errorMessage.getOrElse("") + m
    logger.info(m)
    for {
      _ <- dao.updateJobState(engineJob.uuid, AnalysisJobStates.FAILED, errorMessage)
      updatedJob <- dao.getJobById(engineJob.id)
    } yield updatedJob
  }

  def toSummary(jobs: Seq[EngineJob]) = {
    val m = s"Detected ${jobs.length} 'STUCK' job in states $STUCK_STATES."
    val msg = if (jobs.isEmpty) m else m + "Updated state of jobs to FAILED"
    logger.info(msg)
    msg
  }

  override def run(): Future[MessageResponse] = {
    for {
      jobs <- dao.getEngineCoreJobs(includeInactive = true)
      potentialJobs <- Future.successful(jobs.filter(jobFilter))
      _ <- Future {logger.info(s"Detected ${potentialJobs.length} potentially STUCK jobs.")}
      updatedJobs <- Future.sequence(potentialJobs.map(j => updateJobStateToFail(j)))
      m <- Future.successful(MessageResponse(toSummary(updatedJobs)))
    } yield m
  }

}
