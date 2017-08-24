package com.pacbio.secondary.smrtlink.jobtypes

import java.util.UUID

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, JobResultWriter}

/**
  * Created by mkocher on 8/17/17.
  */
case class DeleteDataSetJobOptions(jobId: UUID,
                                   removeFiles: Boolean = false,
                                   dryRun: Option[Boolean] = None,
                                   force: Option[Boolean] = None,
                                   name: Option[String],
                                   description: Option[String],
                                   projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID)) extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.DELETE_DATASETS
  override def validate() = None
  override def toJob() = new DeleteDataSetJob(this)
}

class DeleteDataSetJob(opts: DeleteDataSetJobOptions) extends ServiceCoreJob(opts) {
  type Out = PacBioDataStore

  override def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao): Either[ResultFailed, PacBioDataStore] = {
    Left(ResultFailed(resources.jobId, jobTypeId.id, "Failed because of X", 1, AnalysisJobStates.FAILED, host))
  }
}
