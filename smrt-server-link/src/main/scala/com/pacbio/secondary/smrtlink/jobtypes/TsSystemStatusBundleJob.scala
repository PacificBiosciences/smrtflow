
package com.pacbio.secondary.smrtlink.jobtypes

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, JobResultWriter}
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig


case class TsSystemStatusBundleJobOptions(path: String,
                                          name: Option[String],
                                          description: Option[String],
                                          projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID)) extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.TS_SYSTEM_STATUS
  override def validate(dao: JobsDao, config: SystemJobConfig) = None
  override def toJob() = new TsSystemStatusBundleJob(this)
}

class TsSystemStatusBundleJob(opts: TsSystemStatusBundleJobOptions) extends ServiceCoreJob(opts){
  type Out = PacBioDataStore
  override def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao, config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {
    Left(ResultFailed(resources.jobId, jobTypeId.id, "Failed because of X", 1, AnalysisJobStates.FAILED, host))
  }
}
