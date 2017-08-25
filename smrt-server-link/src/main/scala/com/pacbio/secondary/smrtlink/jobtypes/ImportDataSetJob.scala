package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.Path

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{InvalidJobOptionError, JobResultWriter}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.ImportDataSetOptions
import com.typesafe.scalalogging.LazyLogging

/**
  * Created by mkocher on 8/17/17.
  */
case class ImportDataSetJobOptions(path: Path,
                                   datasetType: DataSetMetaTypes.DataSetMetaType,
                                   name: Option[String],
                                   description: Option[String],
                                   projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID)
                                  ) extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.IMPORT_DATASET
  override def validate(): Option[InvalidJobOptionError] = {
    // Spray serialization errors will be raised here.
    //logger.warn(s"Job ${jobTypeId.id} Validation is disabled")
    None
  }

  override def toJob() = new ImportDataSetJob(this)
}

class ImportDataSetJob(opts: ImportDataSetJobOptions) extends ServiceCoreJob(opts){
  type Out = PacBioDataStore
  override def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao): Either[ResultFailed, PacBioDataStore] = {
    // shim layer
    val oldOpts = ImportDataSetOptions(opts.path.toAbsolutePath.toString, opts.datasetType, opts.getProjectId())
    val job = oldOpts.toJob
    job.run(resources, resultsWriter)
  }
}


