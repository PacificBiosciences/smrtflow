package com.pacbio.secondary.smrtlink.jobtypes

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, JobResultWriter}
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig

/**
  * Created by mkocher on 8/17/17.
  */
case class ExportDataSetsJobOptions(datasetType: String,
                                    ids: Seq[Int],
                                    outputPath: String,
                                    name: Option[String],
                                    description: Option[String],
                                    projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID)) extends ServiceJobOptions {
  // Need to think about how this is set from the EngineJob or if it's even necessary
  override def jobTypeId = JobTypeIds.EXPORT_DATASETS
  override def validate(dao: JobsDao, config: SystemJobConfig) = None
  override def toJob() = new ExportDataSetJob(this)

}

class ExportDataSetJob(opts:ExportDataSetsJobOptions) extends ServiceCoreJob(opts){
  type Out = PacBioDataStore
  override def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao, config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {
    Left(ResultFailed(resources.jobId, jobTypeId.id, "Failed because of X", 1, AnalysisJobStates.FAILED, host))
  }
}