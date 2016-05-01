package com.pacbio.secondary.analysis.jobtypes

import java.nio.file.{Path, Paths, Files}
import java.util.UUID
import util.{Success, Failure, Try}

import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.reports.DataSetReports
import com.pacbio.secondary.analysis.tools.timeUtils
import com.pacificbiosciences.pacbiodatasets.DataSetType
import org.joda.time.{DateTime => JodaDateTime}
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.analysis.externaltools.PbReports
import com.pacbio.secondary.analysis.jobs._
import com.pacbio.secondary.analysis.jobs.JobModels._


/**
 * Import DataSet job options
 * @param path Path to input dataSet
 * @param datasetType DataSet type (must be consistent with the resource in `path`
 */
case class ImportDataSetOptions(path: String, datasetType: DataSetMetaTypes.DataSetMetaType) extends BaseJobOptions {
  def toJob = new ImportDataSetJob(this)

  override def validate = {
    Validators.fileExists(path)
  }
}


/**
 * Generic Job for Importing DataSets into the DataStore
 *
 * 1. Validate DataSetType
 * 2. Validate File Path
 * 3. Validate DataSet
 *
 * Created by mkocher on 5/1/15.
 */
class ImportDataSetJob(opts: ImportDataSetOptions) extends BaseCoreJob(opts: ImportDataSetOptions)
with MockJobUtils with timeUtils {

  type Out = PacBioDataStore
  val jobTypeId = JobTypeId("import_dataset")

  def run(job: JobResourceBase, resultsWriter: JobResultWriter): Either[ResultFailed, Out] = {

    val startedAt = JodaDateTime.now()
    val createdAt = JodaDateTime.now()

    val srcP = Paths.get(opts.path)
    val fileSize = srcP.toFile.length

    def toDataStoreFile(ds: DataSetType) =
      DataStoreFile(
        UUID.fromString(ds.getUniqueId),
        s"pbscala::${jobTypeId.id}",
        ds.getMetaType,
        fileSize,
        createdAt,
        createdAt,
        opts.path,
        isChunked = false,
        Option(ds.getName).getOrElse(s"PacBio DataSet"),
        s"Imported DataSet on $startedAt")

    def writeJobDataStore(dsFile: DataStoreFile, dst: DataSetMetaTypes.DataSetMetaType): PacBioDataStore = {
      logger.info(s"Loaded dataset and convert to DataStoreFile $dsFile")
      val resources = setupJobResourcesAndCreateDirs(job.path)

      val logPath = job.path.resolve("pbscala-job.stdout")

      val reportFiles = DataSetReports.runAll(srcP, dst, job.path, jobTypeId, resultsWriter)

      val logFile = DataStoreFile(
        UUID.randomUUID(),
        s"master.log",
        FileTypes.LOG.fileTypeId,
        // probably wrong; the file isn't closed yet.  But it won't get
        // closed until after this method runs.
        logPath.toFile.length,
        createdAt,
        startedAt,
        logPath.toString,
        isChunked = false,
        "Master Log",
        "Log file of the details of the import dataset job")

      val datastore = toDatastore(resources, Seq(dsFile, logFile) ++ reportFiles)
      val datastorePath = job.path.resolve("datastore.json")
      writeDataStore(datastore, datastorePath)
      logger.info(s"Successfully wrote datastore to $datastorePath")
      datastore
    }

    val tx = for {
      dsFile <- Try { toDataStoreFile(DataSetLoader.loadType(opts.datasetType, srcP)) }
      dstore <- Try { writeJobDataStore(dsFile, opts.datasetType) }
    } yield dstore

    tx match{
      case Success(datastore) => Right(datastore)
      case Failure(ex) =>
        logger.error(s"Failed to import dataset ${opts.path} ${ex.getMessage}")
        Left(ResultFailed(job.jobId, jobTypeId.id, s"Failed to import $opts ${ex.getMessage}", computeTimeDeltaFromNow(startedAt), AnalysisJobStates.FAILED, host))
    }
  }
}
