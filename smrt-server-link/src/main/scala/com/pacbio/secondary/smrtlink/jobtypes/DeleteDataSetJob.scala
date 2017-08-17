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
                              description: Option[String]) extends ServiceJobOptions{
  override val projectId: Int = 1 // Need to think about how this is set from the EngineJob or if it's even necessary
  override val jobTypeId: JobTypeId = JobTypeIds.DELETE_DATASETS
  override def validate() = None
  override def toJob() = new DeleteDataSetJob(this)
}

class DeleteDataSetJob(opts: DeleteDataSetJobOptions) extends ServiceCoreJob(opts) {
  type Out = PacBioDataStore
  override def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao): Either[ResultFailed, PacBioDataStore] = {
    Left(ResultFailed(resources.jobId, jobTypeId.id, "Failed because of X", 1, AnalysisJobStates.FAILED, host))
  }
}
