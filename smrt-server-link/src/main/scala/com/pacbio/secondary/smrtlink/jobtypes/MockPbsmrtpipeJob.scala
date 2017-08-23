
package com.pacbio.secondary.smrtlink.jobtypes

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, JobResultWriter}
import com.pacbio.secondary.smrtlink.models.BoundServiceEntryPoint

/**
  * Created by mkocher on 8/17/17.
  */
case class MockPbsmrtpipeJobOptions(name: Option[String],
                                    description: Option[String],
                                    entryPoints: Seq[BoundServiceEntryPoint],
                                    taskOptions: Seq[ServiceTaskOptionBase],
                                    workflowOptions: Seq[ServiceTaskOptionBase],
                                    projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID)) extends ServiceJobOptions {
  override val jobTypeId: JobTypeId = JobTypeIds.MOCK_PBSMRTPIPE
  override def validate() = None
  override def toJob() = new MockPbsmrtpipeJob(this)
}

class MockPbsmrtpipeJob(opts: MockPbsmrtpipeJobOptions) extends ServiceCoreJob(opts){
  type Out = PacBioDataStore
  override def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao): Either[ResultFailed, PacBioDataStore] = {
    Left(ResultFailed(resources.jobId, jobTypeId.id, "Failed because of X", 1, AnalysisJobStates.FAILED, host))
  }
}
