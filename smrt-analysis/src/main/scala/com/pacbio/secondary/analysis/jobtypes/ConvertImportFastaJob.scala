package com.pacbio.secondary.analysis.jobtypes

import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import com.pacbio.secondary.analysis.converters.{DatasetConvertError, FastaToReferenceConverter, PacBioFastaValidator}
import com.pacbio.secondary.analysis.datasets.{DataSetMetaTypes, ReferenceDatasetFileIO, ReferenceSetIO}
import com.pacbio.secondary.analysis.jobs._
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.tools.timeUtils
import com.pacificbiosciences.pacbiodatasets.ReferenceSet
import org.apache.commons.io.FileUtils
import org.joda.time.{DateTime => JodaDateTime}

import scala.util.{Failure, Success, Try}

// Import & Convert Fasta -> Reference Dataset (need to add standard Organism, ploidy options)
case class ConvertImportFastaOptions(path: String, name: String, ploidy: String, organism: String) extends BaseJobOptions {
  def toJob = new ConvertImportFastaJob(this)

  override def validate = {
    val p = Paths.get(path)
    if (Files.exists(p)) None else Some(InvalidJobOptionError(s"Unable to find $path"))
  }
}

/**
 * 1. Copy fasta file to local jobOptions dir
 * 2. Create fasta index
 * 3. Call sawriter
 * 2. Write Reference dataset XML
 *
 * Created by mkocher on 5/1/15.
 */
class ConvertImportFastaJob(opts: ConvertImportFastaOptions) extends BaseCoreJob(opts: ConvertImportFastaOptions)
with MockJobUtils
with timeUtils {

  type Out = PacBioDataStore
  val jobTypeId = JobTypeId("convert_fasta_to_dataset")

  private def toDataStoreFile(uuid: UUID, path: Path) = {
    val importedAt = JodaDateTime.now()
    DataStoreFile(uuid,
      s"pbscala::${jobTypeId.id}",
      DataSetMetaTypes.Reference.toString,
      path.toFile.length(),
      importedAt,
      importedAt,
      path.toAbsolutePath.toString,
      isChunked = false,
      s"ReferenceSet ${opts.name}",
      s"Converted Fasta and Imported ReferenceSet ${opts.name}")
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
    val outputDir = job.path.resolve("pacbio-reference")

    def validateAndRun(path: Path): Either[DatasetConvertError, ReferenceSetIO] = {
      PacBioFastaValidator(path) match {
        case Left(x) => Left(DatasetConvertError(x.msg))
        case Right(refMetaData) => FastaToReferenceConverter(opts.name, Option(opts.organism), Option(opts.ploidy), path, outputDir, mkdir=true)
      }
    }

    // Initial pure scala version. This was replaced by the need to generate the SMRT View required index files
    //createReferenceFromFasta(fastaDest, job.path, Option("Converted-fasta"), organism, ploidy)
    val result = Try { validateAndRun(fastaPath )}

    val runTime = computeTimeDeltaFromNow(startedAt)
    result match {
      case Success(x) =>
        x match {
          case Right(referenceDatasetFileIO) =>
            w(s"completed running conversion in $runTime sec")
            val dsFile = toDataStoreFile(UUID.fromString(referenceDatasetFileIO.dataset.getUniqueId), referenceDatasetFileIO.path)
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
