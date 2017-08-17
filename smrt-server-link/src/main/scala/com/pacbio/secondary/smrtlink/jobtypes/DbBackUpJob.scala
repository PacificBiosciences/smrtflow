package com.pacbio.secondary.smrtlink.jobtypes

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, JobResultWriter}


case class DbBackUpJobOptions(user: String, comment: String, name: Option[String], description: Option[String]) extends ServiceJobOptions {
  override val projectId: Int = 1
  override val jobTypeId: JobTypeId = JobTypeIds.DB_BACKUP
  override def validate() = None
  override def toJob() = new DbBackUpJob(this)

}

class DbBackUpJob(opts: DbBackUpJobOptions) extends ServiceCoreJob(opts){
  type Out = PacBioDataStore
  override def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao) = {
    Left(ResultFailed(resources.jobId, jobTypeId.id, "Failed because of X", 1, AnalysisJobStates.FAILED, host))
  }
}
