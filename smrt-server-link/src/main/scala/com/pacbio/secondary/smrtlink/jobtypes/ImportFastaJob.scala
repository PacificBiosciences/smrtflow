package com.pacbio.secondary.smrtlink.jobtypes

import com.pacbio.secondary.smrtlink.analysis.jobtypes.ConvertImportFastaOptions
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.JobConstants.GENERAL_PROJECT_ID
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  JobResultWriter
}
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig

// See comments on Job "name" vs Job option scoped "name" used to assign DataSet name.
// This should have been "datasetName" to avoid confusion
case class ImportFastaJobOptions(path: String,
                                 ploidy: String,
                                 organism: String,
                                 name: Option[String],
                                 description: Option[String],
                                 projectId: Option[Int] = Some(
                                   JobConstants.GENERAL_PROJECT_ID))
    extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.CONVERT_FASTA_REFERENCE
  override def validate(dao: JobsDao, config: SystemJobConfig) = None
  override def toJob() = new ImportFastaJob(this)
}

class ImportFastaJob(opts: ImportFastaJobOptions)
    extends ServiceCoreJob(opts) {
  type Out = PacBioDataStore
  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {
    // Shim layer
    val name = opts.name.getOrElse("Fasta-Convert")
    val projectId = opts.projectId.getOrElse(GENERAL_PROJECT_ID)
    val oldOpts = ConvertImportFastaOptions(opts.path,
                                            name,
                                            opts.ploidy,
                                            opts.organism,
                                            projectId)
    val job = oldOpts.toJob
    job.run(resources, resultsWriter)
  }
}
