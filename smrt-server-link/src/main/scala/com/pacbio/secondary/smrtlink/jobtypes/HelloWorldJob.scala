package com.pacbio.secondary.smrtlink.jobtypes

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{JobResourceBase, JobTypeId, PacBioDataStore, ResultFailed}
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, JobResultWriter}
import com.pacbio.secondary.smrtlink.engine.{ServiceCoreJob, ServiceJobOptions}


case class HelloWorldJobOptions(x: Int, override val projectId: Int = 1)(jobType: String) extends ServiceJobOptions {
  override val jobTypeId: JobTypeId = JobTypeId(jobType)
  override def validate() = None
  override def toJob = new HelloWorldServiceJob(this)
}

class HelloWorldServiceJob(opts: HelloWorldJobOptions) extends ServiceCoreJob(opts){
  type Out = PacBioDataStore
  override def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao): Either[ResultFailed, PacBioDataStore] = {
    Left(ResultFailed(resources.jobId, jobTypeId.id, "Failed because of X", 1, AnalysisJobStates.FAILED, host))
  }
}
