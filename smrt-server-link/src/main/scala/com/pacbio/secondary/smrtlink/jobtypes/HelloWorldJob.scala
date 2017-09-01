package com.pacbio.secondary.smrtlink.jobtypes

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, JobResultWriter}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.SimpleDevJobOptions
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig


case class HelloWorldJobOptions(x: Int,
                                name: Option[String],
                                description: Option[String],
                                projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID)) extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.HELLO_WORLD
  override def validate(dao: JobsDao, config: SystemJobConfig) = None
  override def toJob() = new HelloWorldServiceJob(this)
}

class HelloWorldServiceJob(opts: HelloWorldJobOptions) extends ServiceCoreJob(opts){
  type Out = PacBioDataStore
  override def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao, config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {
    // shim
    val oldOpts = SimpleDevJobOptions(1, 2, opts.getProjectId())
    val job = oldOpts.toJob
    job.run(resources, resultsWriter)
  }
}
