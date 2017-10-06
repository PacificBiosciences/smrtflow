package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.JobConstants.GENERAL_PROJECT_ID
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  InvalidJobOptionError,
  JobResultsWriter
}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.ConvertImportFastaBarcodesOptions
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import spray.json._

//FIXME(mpkocher)(8-17-2017) There's a giant issue with the job "name" versus "name" used in job options.
case class ImportBarcodeFastaJobOptions(path: Path,
                                        name: Option[String],
                                        description: Option[String],
                                        projectId: Option[Int] = Some(
                                          JobConstants.GENERAL_PROJECT_ID))
    extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.CONVERT_FASTA_BARCODES
  override def toJob() = new ImportBarcodeFastaJob(this)

  //(mpkocher)(8-31-2017) This validation needs to be improved.
  override def validate(
      dao: JobsDao,
      config: SystemJobConfig): Option[InvalidJobOptionError] = {
    if (Files.exists(path)) None
    else Some(InvalidJobOptionError(s"Unable to find $path"))
  }
}

class ImportBarcodeFastaJob(opts: ImportBarcodeFastaJobOptions)
    extends ServiceCoreJob(opts) {
  type Out = PacBioDataStore
  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultsWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {
    // Shim layer
    val name = opts.name.getOrElse("Fasta-Barcodes")
    val projectId = opts.projectId.getOrElse(GENERAL_PROJECT_ID)
    val oldOpts = ConvertImportFastaBarcodesOptions(
      opts.path.toAbsolutePath.toString,
      name,
      projectId)
    val job = oldOpts.toJob
    job.run(resources, resultsWriter)
  }
}
