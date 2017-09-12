package com.pacbio.secondary.smrtlink.jobtypes


import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobResultWriter
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.models.DeferredJob


case class MultiAnalysisJobOptions(jobs: Seq[DeferredJob],
                                   name: Option[String],
                                   description: Option[String],
                                   projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID)) extends ServiceJobOptions {

  override def jobTypeId = JobTypeIds.MJOB_MULTI_ANALYSIS

  override def validate(dao: JobsDao, config: SystemJobConfig) = None

  override def toJob() = new MultiAnalysisJob(this)

}

class MultiAnalysisJob(opts:MultiAnalysisJobOptions) extends ServiceCoreJob(opts) {
  type Out = Int

  override def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao, config: SystemJobConfig) = {
    Right(1)
  }
}


