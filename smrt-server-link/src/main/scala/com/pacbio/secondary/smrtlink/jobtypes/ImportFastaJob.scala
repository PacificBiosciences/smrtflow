package com.pacbio.secondary.smrtlink.jobtypes

import java.net.{URI, URL}
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import com.pacbio.secondary.smrtlink.analysis.jobtypes.{
  MockJobUtils,
  PbSmrtPipeJobOptions
}
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.converters.{
  FastaToReferenceConverter,
  PacBioFastaValidator
}
import com.pacbio.secondary.smrtlink.analysis.datasets.{
  DataSetMetaTypes,
  DataSetIO
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.JobConstants
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  InvalidJobOptionError,
  JobResultsWriter
}
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import org.joda.time.{DateTime => JodaDateTime}

import scala.util.{Failure, Success, Try}

trait ImportFastaBaseJobOptions extends ServiceJobOptions {
  val path: String
  val ploidy: String
  val organism: String
  val name: Option[String]
  val description: Option[String]
  val projectId: Option[Int]

  /**
    * Minimal lightweight validation.
    */
  override def validate(
      dao: JobsDao,
      config: SystemJobConfig): Option[InvalidJobOptionError] = {
    if (Files.exists(Paths.get(path))) None
    else Some(InvalidJobOptionError(s"Unable to find $path"))
  }
}

// See comments on Job "name" vs Job option scoped "name" used to assign DataSet name.
// This should have been "datasetName" to avoid confusion
case class ImportFastaJobOptions(path: String,
                                 ploidy: String,
                                 organism: String,
                                 name: Option[String],
                                 description: Option[String],
                                 projectId: Option[Int] = Some(
                                   JobConstants.GENERAL_PROJECT_ID))
    extends ImportFastaBaseJobOptions {
  override def jobTypeId = JobTypeIds.CONVERT_FASTA_REFERENCE

  override def toJob() = new ImportFastaJob(this)
}

abstract class ImportFastaBaseJob(opts: ImportFastaBaseJobOptions)
    extends ServiceCoreJob(opts)
    with MockJobUtils
    with timeUtils {
  type Out = PacBioDataStore

  val PIPELINE_ID: String
  val DS_METATYPE: DataSetMetaTypes.DataSetMetaType

  def dsTypeName = DS_METATYPE.fileType.fileTypeId.split(".").last

  // Max size for a fasta file to converted locally, versus being converted to a pbsmrtpipe cluster task
  // This value probably needs to be tweaked a bit
  final val LOCAL_MAX_SIZE_MB = 50 // this takes about 2.5 minutes

  final val PIPELINE_ENTRY_POINT_ID = "eid_ref_fasta"

  // Accessible via pbsmrtpipe show-task-details pbcoretools.tasks.fasta_to_reference
  final val OPT_NAME = "pbcoretools.task_options.reference_name"
  final val OPT_ORGANISM = "pbcoretools.task_options.organism"
  final val OPT_PLOIDY = "pbcoretools.task_options.ploidy"
  final val DEFAULT_REFERENCE_SET_NAME = "Fasta-Convert"

  private def toPbsmrtPipeJobOptions(opts: ImportFastaBaseJobOptions,
                                     config: SystemJobConfig,
                                     jobUUID: UUID): PbSmrtPipeJobOptions = {

    // There's some common code that needs to be pulled out
    val updateUrl = new URL(
      s"http://${config.host}:${config.port}/smrt-link/job-manager/jobs/pbsmrtpipe/${jobUUID.toString}")

    def toPipelineOption(id: String, value: String) =
      ServiceTaskStrOption(id, value)

    val name = opts.name.getOrElse(DEFAULT_REFERENCE_SET_NAME)

    val tOpts: Seq[(String, String)] = Seq((OPT_NAME, name),
                                           (OPT_ORGANISM, opts.organism),
                                           (OPT_PLOIDY, opts.ploidy))

    val entryPoints = Seq(
      BoundEntryPoint(PIPELINE_ENTRY_POINT_ID, Paths.get(opts.path)))
    val taskOptions = tOpts.map(x => toPipelineOption(x._1, x._2))

    // FIXME. this should be Option[Path] or Option[Map[String, String]]
    val envPath: Option[Path] = None
    PbSmrtPipeJobOptions(
      PIPELINE_ID,
      entryPoints,
      taskOptions,
      config.pbSmrtPipeEngineOptions.toPipelineOptions.map(_.asServiceOption),
      envPath,
      Some(updateUrl.toURI),
      projectId = opts.getProjectId()
    )

  }

  private def toDataStoreFile(uuid: UUID, name: String, path: Path) = {
    val importedAt = JodaDateTime.now()
    DataStoreFile(
      uuid,
      s"pbscala::${jobTypeId.id}",
      DS_METATYPE.toString,
      path.toFile.length(),
      importedAt,
      importedAt,
      path.toAbsolutePath.toString,
      isChunked = false,
      s"${dsTypeName} $name",
      s"Converted Fasta and Imported ${dsTypeName} $name"
    )
  }

  private def writeDatastoreToJobDir(dsFiles: Seq[DataStoreFile],
                                     jobDir: Path) = {
    // Keep the pbsmrtpipe jobOptions directory structure for now. But this needs to change
    val resources = setupJobResourcesAndCreateDirs(jobDir)
    val ds = toDatastore(resources, dsFiles)
    writeDataStore(ds, resources.datastoreJson)
    ds
  }

  protected def runConverter(opts: ImportFastaBaseJobOptions,
                             outputDir: Path): Try[DataSetIO]

  /**
    * Run locally (don't submit to the cluster resources)
    */
  private def runLocal(
      dao: JobsDao,
      opts: ImportFastaBaseJobOptions,
      job: JobResourceBase,
      resultsWriter: JobResultsWriter): Try[PacBioDataStore] = {

    val logPath = job.path.resolve(JobConstants.JOB_STDOUT)
    val logFile = toSmrtLinkJobLog(logPath)
    val outputDir = job.path.resolve("pacbio-reference")

    def w(sx: String): Unit = {
      logger.debug(sx)
      resultsWriter.writeLine(sx)
    }

    def writeFiles(rio: DataSetIO): PacBioDataStore = {
      w(s"Successfully wrote DataSet uuid:${rio.dataset.getUniqueId} name:${rio.dataset.getName} to path:${rio.path}")
      val dsFile =
        toDataStoreFile(UUID.fromString(rio.dataset.getUniqueId),
                        rio.dataset.getName,
                        rio.path)
      val datastore =
        writeDatastoreToJobDir(Seq(dsFile, logFile), job.path)
      w(s"successfully generated datastore with ${datastore.files.length} files")
      datastore
    }

    w(s"Attempting to converting Fasta to ${dsTypeName} ${opts.path}")
    w(s"Job Options $opts")

    // Proactively add the log file, so the datastore file will show up in
    // SL and can be accessible from the UI
    for {
      _ <- runAndBlock(dao.importDataStoreFile(logFile, job.jobId),
                       opts.DEFAULT_TIMEOUT)
      _ <- PacBioFastaValidator.toTry(Paths.get(opts.path))
      _ <- Success(w(s"Successfully validated fasta file ${opts.path}"))
      r <- runConverter(opts, outputDir)
      results <- Try(writeFiles(r))
    } yield results
  }

  /**
    * Run With pbsmrtpipe for large references
    *
    */
  private def runNonLocal(opts: ImportFastaBaseJobOptions,
                          job: JobResourceBase,
                          resultsWriter: JobResultsWriter,
                          config: SystemJobConfig): Try[PacBioDataStore] = {

    val pbOpts = toPbsmrtPipeJobOptions(opts, config, job.jobId)

    pbOpts.toJob.run(job, resultsWriter) match {
      case Right(x) => Success(x)
      case Left(e) => Failure(new Exception(s"Failed to run job ${e.message}"))
    }
  }

  private def shouldRunLocal(opts: ImportFastaBaseJobOptions,
                             job: JobResourceBase,
                             resultsWriter: JobResultsWriter): Boolean = {
    val fileSizeMB = Paths.get(opts.path).toFile.length / 1024 / 1024
    fileSizeMB <= LOCAL_MAX_SIZE_MB
  }

  /**
    * Run and dispatch to correct computation resources
    */
  def runner(dao: JobsDao,
             opts: ImportFastaBaseJobOptions,
             job: JobResourceBase,
             resultsWriter: JobResultsWriter,
             config: SystemJobConfig): Try[PacBioDataStore] = {
    if (shouldRunLocal(opts, job, resultsWriter)) {
      runLocal(dao, opts, job, resultsWriter)
    } else {
      // pre-Validation must be encapsulated completely within in this layer
      runNonLocal(opts, job, resultsWriter, config)
    }
  }

  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultsWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {

    val startedAt = JodaDateTime.now()

    def toLeft(msg: String): Either[ResultFailed, PacBioDataStore] = {
      val runTime = computeTimeDeltaFromNow(startedAt)
      Left(
        ResultFailed(resources.jobId,
                     jobTypeId.toString,
                     msg,
                     runTime,
                     AnalysisJobStates.FAILED,
                     host))
    }

    // Wrapping layer to compose with the current API
    val tx = runner(dao, opts, resources, resultsWriter, config)
      .map(result => Right(result))

    val tr = tx.recover { case ex => toLeft(ex.getMessage) }

    tr match {
      case Success(x) => x
      case Failure(ex) => toLeft(ex.getMessage)
    }
  }

}

class ImportFastaJob(opts: ImportFastaJobOptions)
    extends ImportFastaBaseJob(opts) {
  override val PIPELINE_ID = "pbsmrtpipe.pipelines.sa3_ds_fasta_to_reference"
  override val DS_METATYPE = DataSetMetaTypes.Reference

  override def runConverter(opts: ImportFastaBaseJobOptions,
                            outputDir: Path): Try[DataSetIO] =
    FastaToReferenceConverter
      .toTry(opts.name.getOrElse(DEFAULT_REFERENCE_SET_NAME),
             Option(opts.organism),
             Option(opts.ploidy),
             Paths.get(opts.path),
             outputDir,
             mkdir = true)
}
