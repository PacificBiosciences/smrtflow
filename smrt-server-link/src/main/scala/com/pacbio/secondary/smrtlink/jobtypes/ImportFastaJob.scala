package com.pacbio.secondary.smrtlink.jobtypes

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, JobResultWriter}

// See comments on Job "name" vs Job option scoped "name" used to assign DataSet name.
// This should have been "datasetName" to avoid confusion
case class ImportFastaJobOptions(path: String,
                                 ploidy: String,
                                 organism: String,
                                 name: Option[String],
                                 description: Option[String],
                                 projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID)) extends ServiceJobOptions {
  override val jobTypeId: JobTypeId = JobTypeIds.HELLO_WORLD
  override def validate() = None
  override def toJob() = new ImportFastaJob(this)
}

class ImportFastaJob(opts: ImportFastaJobOptions) extends ServiceCoreJob(opts){
  type Out = PacBioDataStore
  override def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao): Either[ResultFailed, PacBioDataStore] = {
    Left(ResultFailed(resources.jobId, jobTypeId.id, "Failed because of X", 1, AnalysisJobStates.FAILED, host))
  }
}
