
package com.pacbio.secondary.smrtlink.jobtypes

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, JobResultWriter}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.SimpleDevJobOptions

/**
  * Created by mkocher on 8/17/17.
  */
case class SimpleJobOptions(path: String,
                            name: Option[String],
                            description: Option[String],
                            projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID)) extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.SIMPLE
  override def validate() = None
  override def toJob() = new SimpleJob(this)
}

class SimpleJob(opts: SimpleJobOptions) extends ServiceCoreJob(opts){
  type Out = PacBioDataStore
  override def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao): Either[ResultFailed, PacBioDataStore] = {
    // shim
    val oldOpts = SimpleDevJobOptions(1, 2, opts.getProjectId())
    val job = oldOpts.toJob
    job.run(resources, resultsWriter)
  }
}
