package com.pacbio.secondary.smrtlink.jobtypes

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, JobResultWriter}

/**
  * Created by mkocher on 8/17/17.
  */
case class ImportDataSetJobOptions(path: String,
                                   datasetType: DataSetMetaTypes.DataSetMetaType,
                                   name: Option[String],
                                   description: Option[String],
                                   projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID)
                                  ) extends ServiceJobOptions {
  override val jobTypeId: JobTypeId = JobTypeIds.HELLO_WORLD
  override def validate() = None

  override def toJob() = new ImportDataSetJob(this)
}

class ImportDataSetJob(opts: ImportDataSetJobOptions) extends ServiceCoreJob(opts){
  type Out = PacBioDataStore
  override def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao): Either[ResultFailed, PacBioDataStore] = {
    Left(ResultFailed(resources.jobId, jobTypeId.id, "Failed because of X", 1, AnalysisJobStates.FAILED, host))
  }
}


