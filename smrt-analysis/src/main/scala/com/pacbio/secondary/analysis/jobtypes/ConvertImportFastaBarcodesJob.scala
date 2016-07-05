package com.pacbio.secondary.analysis.jobtypes

import java.nio.file.{Files, Path, Paths}
import java.util.UUID

//import com.pacbio.secondary.analysis.converters.FastaConverter._
import com.pacbio.secondary.analysis.converters.{DatasetConvertError, FastaBarcodesConverter, PacBioFastaValidator}
import com.pacbio.secondary.analysis.datasets.{DataSetMetaTypes, BarcodeSetIO}
import com.pacbio.secondary.analysis.jobs._
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.tools.timeUtils
import com.pacificbiosciences.pacbiodatasets.BarcodeSet
import org.apache.commons.io.FileUtils
import org.joda.time.{DateTime => JodaDateTime}

import scala.util.{Failure, Success, Try}

// Import & Convert Fasta -> Barcode Dataset
case class ConvertImportFastaBarcodesOptions(path: String, name: String) extends BaseJobOptions {
  def toJob = new ConvertImportFastaBarcodesJob(this)

  override def validate = {
    val p = Paths.get(path)
    if (Files.exists(p)) None else Some(InvalidJobOptionError(s"Unable to find $path"))
  }
}

class ConvertImportFastaBarcodesJob(opts: ConvertImportFastaBarcodesOptions) extends BaseCoreJob(opts: ConvertImportFastaBarcodesOptions)
with MockJobUtils
with timeUtils {

  type Out = PacBioDataStore
  val jobTypeId = JobTypeId("convert_fasta_to_barcodeset")

  private def toDataStoreFile(uuid: UUID, path: Path) = {
    val importedAt = JodaDateTime.now()
    DataStoreFile(uuid,
      s"pbscala::${jobTypeId.id}",
      DataSetMetaTypes.Barcode.toString,
      path.toFile.length(),
      importedAt,
      importedAt,
      path.toAbsolutePath.toString,
      isChunked = false,
      s"BarcodeSet ${opts.name}",
      s"Converted Fasta and Imported BarcodeSet ${opts.name}")
  }

  private def writeDatastoreToJobDir(dsFiles: Seq[DataStoreFile], jobDir: Path) = {
    // Keep the pbsmrtpipe jobOptions directory structure for now. But this needs to change
    val resources = setupJobResourcesAndCreateDirs(jobDir)
    val ds = toDatastore(resources, dsFiles)
    writeDataStore(ds, resources.datastoreJson)
    ds
  }

  def run(job: JobResourceBase, resultsWriter: JobResultWriter): Either[ResultFailed, Out] = {

    def w(sx: String): Unit = {
      logger.debug(sx)
      resultsWriter.writeLineStdout(sx)
    }
    val startedAt = JodaDateTime.now()

    w(s"Converting Fasta to dataset ${opts.path}")
    w(s"Job Options $opts")

    val fastaPath = Paths.get(opts.path)
    val outputDir = job.path.resolve("pacbio-barcodes")

    def validateAndRun(path: Path): Either[DatasetConvertError, BarcodeSetIO] = {
      PacBioFastaValidator(path) match {
        case Some(x) => Left(DatasetConvertError(x.msg))
        case _ => FastaBarcodesConverter(opts.name, path, outputDir, mkdir=true)
      }
    }

    val result = Try { validateAndRun(fastaPath )}

    val runTime = computeTimeDeltaFromNow(startedAt)
    result match {
      case Success(x) =>
        x match {
          case Right(barcodeDatasetFileIO) =>
            w(s"completed running conversion in $runTime sec")
            val dsFile = toDataStoreFile(UUID.fromString(barcodeDatasetFileIO.dataset.getUniqueId), barcodeDatasetFileIO.path)
            val datastore = writeDatastoreToJobDir(Seq(dsFile), job.path)
            w(s"successfully generated datastore with ${datastore.files.length} files in $runTime sec.")
            Right(datastore)
          case Left(a) =>
            Left(ResultFailed(job.jobId, jobTypeId.toString, s"Failed to convert fasta file ${opts.path} ${a.msg} in $runTime sec", runTime, AnalysisJobStates.FAILED, host))
        }
      case Failure(ex) =>
        val emsg = s"Failed to convert fasta file ${opts.path} ${ex.getMessage}"
        logger.error(emsg)
        Left(ResultFailed(job.jobId, jobTypeId.toString, emsg, runTime, AnalysisJobStates.FAILED, host))
    }
  }
}
