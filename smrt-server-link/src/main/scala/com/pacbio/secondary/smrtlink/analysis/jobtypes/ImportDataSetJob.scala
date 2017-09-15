package com.pacbio.secondary.smrtlink.analysis.jobtypes

import java.nio.file.{Path, Paths, Files}
import java.util.UUID
import util.{Success, Failure, Try}

import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.reports.DataSetReports
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacificbiosciences.pacbiodatasets.DataSetType
import org.joda.time.{DateTime => JodaDateTime}
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.smrtlink.analysis.externaltools.PbReports
import com.pacbio.secondary.smrtlink.analysis.jobs._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.JobConstants.GENERAL_PROJECT_ID

/**
  * Import DataSet job options
  * @param path Path to input dataSet
  * @param datasetType DataSet type (must be consistent with the resource in `path`
  */
case class ImportDataSetOptions(path: String,
                                datasetType: DataSetMetaTypes.DataSetMetaType,
                                override val projectId: Int =
                                  GENERAL_PROJECT_ID)
    extends BaseJobOptions {
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
class ImportDataSetJob(opts: ImportDataSetOptions)
    extends BaseCoreJob(opts: ImportDataSetOptions)
    with MockJobUtils
    with timeUtils {

  type Out = PacBioDataStore
  // Note, this is inconsistent with the id defined in JobTypeIds "import-dataset"
  // Changing this will break backward compatibility with the source id in the datastore
  val jobTypeId = JobTypeIds.IMPORT_DATASET

  def run(job: JobResourceBase,
          resultsWriter: JobResultWriter): Either[ResultFailed, Out] = {

    val startedAt = JodaDateTime.now()
    val createdAt = JodaDateTime.now()

    val srcP = Paths.get(opts.path)
    val fileSize = srcP.toFile.length

    def toDataStoreFile(ds: DataSetType) =
      DataStoreFile(
        UUID.fromString(ds.getUniqueId),
        s"pbscala::import_dataset",
        ds.getMetaType,
        fileSize,
        createdAt,
        createdAt,
        opts.path,
        isChunked = false,
        Option(ds.getName).getOrElse(s"PacBio DataSet"),
        s"Imported DataSet on $startedAt"
      )

    def writeJobDataStore(
        dsFile: DataStoreFile,
        dst: DataSetMetaTypes.DataSetMetaType): PacBioDataStore = {
      logger.info(s"Loaded dataset and convert to DataStoreFile $dsFile")
      val resources = setupJobResourcesAndCreateDirs(job.path)

      // This should never stop a dataset from being imported
      val reports = Try {
        DataSetReports.runAll(srcP, dst, job.path, jobTypeId, resultsWriter)
      }

      val reportFiles: Seq[DataStoreFile] = reports match {
        case Success(rpts) => rpts
        case Failure(ex) =>
          val errorMsg =
            s"Error ${ex.getMessage}\n ${ex.getStackTrace.mkString("\n")}"
          logger.error(errorMsg)
          resultsWriter.writeLineError(errorMsg)
          // Might want to consider adding a report attribute that has this warning message
          Nil
      }

      val logPath = job.path.resolve(JobConstants.JOB_STDOUT)
      val logFile = toMasterDataStoreFile(
        logPath,
        "Job Master log of the Import Dataset job")

      val dsFiles = Seq(dsFile, logFile) ++ reportFiles
      val datastore = toDatastore(resources, dsFiles)
      val datastorePath = job.path.resolve("datastore.json")
      writeDataStore(datastore, datastorePath)
      logger.info(
        s"Successfully wrote datastore with ${datastore.files.length} files to $datastorePath")
      datastore
    }

    val tx = for {
      dsFile <- Try {
        toDataStoreFile(DataSetLoader.loadType(opts.datasetType, srcP))
      }
      dstore <- Try { writeJobDataStore(dsFile, opts.datasetType) }
    } yield dstore

    tx match {
      case Success(datastore) =>
        logger.info(
          s"${jobTypeId.id} was successful. Generated datastore with ${datastore.files.length} files.")
        Right(datastore)
      case Failure(ex) =>
        val runTime = computeTimeDeltaFromNow(startedAt)
        val msg =
          s"Failed to import dataset ${opts.path} in $runTime sec. Error ${ex.getMessage}\n ${ex.getStackTrace
            .mkString("\n")}"
        logger.error(msg)
        Left(
          ResultFailed(job.jobId,
                       jobTypeId.id,
                       msg,
                       computeTimeDeltaFromNow(startedAt),
                       AnalysisJobStates.FAILED,
                       host))
    }
  }
}
