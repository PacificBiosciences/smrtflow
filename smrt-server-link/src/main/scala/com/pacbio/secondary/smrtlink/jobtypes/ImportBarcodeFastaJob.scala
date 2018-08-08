package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import org.apache.commons.io.FileUtils
import org.joda.time.{DateTime => JodaDateTime}
import spray.json._

import scala.util.{Failure, Success, Try}

import com.pacificbiosciences.pacbiodatasets.BarcodeSet
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.converters.{
  DatasetConvertError,
  FastaBarcodesConverter,
  PacBioFastaValidator
}
import com.pacbio.secondary.smrtlink.analysis.datasets.{
  DataSetMetaTypes,
  BarcodeSetIO
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs._
import com.pacbio.secondary.smrtlink.analysis.jobs.CoreJobUtils
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig

//FIXME(mpkocher)(8-17-2017) There's a giant issue with the job "name" versus "name" used in job options.
case class ImportBarcodeFastaJobOptions(
    path: Path,
    name: Option[String],
    description: Option[String],
    projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID),
    submit: Option[Boolean] = Some(JobConstants.SUBMIT_DEFAULT_CORE_JOB),
    tags: Option[String] = None)
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
    extends ServiceCoreJob(opts)
    with ImportFastaUtils
    with CoreJobUtils
    with timeUtils {

  val DS_METATYPE = DataSetMetaTypes.Barcode
  val SOURCE_ID = s"pbscala::${jobTypeId.id}"
  type Out = PacBioDataStore

  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultsWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {
    def w(sx: String): Unit = {
      logger.debug(sx)
      resultsWriter.writeLine(sx)
    }
    // Shim layer
    val name = opts.name.getOrElse("Fasta-Barcodes")
    val startedAt = JodaDateTime.now()
    val logFile = getStdOutLog(resources, dao)

    w(s"Converting Fasta to dataset ${opts.path}")
    w(s"Job Options $opts")

    val outputDir = resources.path.resolve("pacbio-barcodes")

    def validateAndRun(path: Path): Either[DatasetConvertError, BarcodeSetIO] = {
      PacBioFastaValidator(path) match {
        case Left(x) => Left(DatasetConvertError(x.msg))
        case Right(refMetaData) =>
          FastaBarcodesConverter(name, path, outputDir, mkdir = true)
      }
    }
    val projectId = opts.projectId.getOrElse(JobConstants.GENERAL_PROJECT_ID)
    val datastoreJson = resources.path.resolve("datastore.json")

    val result = Try { validateAndRun(opts.path) }

    val runTime = computeTimeDeltaFromNow(startedAt)
    result match {
      case Success(x) =>
        x match {
          case Right(barcodeDatasetFileIO) =>
            w(s"completed running conversion in $runTime sec")
            val ds = writeFiles(barcodeDatasetFileIO, logFile, resources, w)
            Right(ds)
          case Left(a) =>
            val msg =
              s"Failed to convert fasta file ${opts.path} ${a.msg} in $runTime sec"
            w(msg)
            Left(
              ResultFailed(resources.jobId,
                           opts.jobTypeId.toString,
                           msg,
                           runTime,
                           AnalysisJobStates.FAILED,
                           host))
        }
      case Failure(ex) =>
        val emsg =
          s"Failed to convert fasta file ${opts.path} ${ex.getMessage}"
        logger.error(emsg)
        w(emsg)
        Left(
          ResultFailed(resources.jobId,
                       opts.jobTypeId.toString,
                       emsg,
                       runTime,
                       AnalysisJobStates.FAILED,
                       host))
    }
  }
}
