package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.{Path, Paths}
import java.util.UUID

import util.{Success, Failure, Try}

import org.joda.time.{DateTime => JodaDateTime}

import com.pacificbiosciences.pacbiodatasets.DataSetType
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs._
import com.pacbio.secondary.smrtlink.analysis.jobs.MockJobUtils
import com.pacbio.secondary.smrtlink.analysis.reports.DataSetReports
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.typesafe.scalalogging.LazyLogging

/**
  * Created by mkocher on 8/17/17.
  */
case class ImportDataSetJobOptions(
    path: Path,
    datasetType: DataSetMetaTypes.DataSetMetaType,
    name: Option[String],
    description: Option[String],
    projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID))
    extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.IMPORT_DATASET
  override def validate(
      dao: JobsDao,
      config: SystemJobConfig): Option[InvalidJobOptionError] = {
    // Spray serialization errors will be raised here.
    //logger.warn(s"Job ${jobTypeId.id} Validation is disabled")
    None
  }

  override def toJob() = new ImportDataSetJob(this)
}

class ImportDataSetJob(opts: ImportDataSetJobOptions)
    extends ServiceCoreJob(opts)
    with MockJobUtils
    with timeUtils {
  type Out = PacBioDataStore
  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultsWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {
    val startedAt = JodaDateTime.now()
    val createdAt = JodaDateTime.now()

    val fileSize = opts.path.toFile.length

    def toDataStoreFile(ds: DataSetType) =
      DataStoreFile(
        UUID.fromString(ds.getUniqueId),
        s"pbscala::import_dataset",
        ds.getMetaType,
        fileSize,
        createdAt,
        createdAt,
        opts.path.toString,
        isChunked = false,
        Option(ds.getName).getOrElse(s"PacBio DataSet"),
        s"Imported DataSet on $startedAt"
      )

    def writeJobDataStore(
        dsFile: DataStoreFile,
        dst: DataSetMetaTypes.DataSetMetaType): PacBioDataStore = {
      logger.info(s"Loaded dataset and convert to DataStoreFile $dsFile")

      // This should never stop a dataset from being imported
      val reports = Try {
        DataSetReports.runAll(opts.path,
                              dst,
                              resources.path,
                              opts.jobTypeId,
                              resultsWriter)
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

      val logPath = resources.path.resolve(JobConstants.JOB_STDOUT)
      val logFile =
        toSmrtLinkJobLog(
          logPath,
          Some(
            s"${JobConstants.DATASTORE_FILE_MASTER_DESC} of the Import Dataset job"))

      val dsFiles = Seq(dsFile, logFile) ++ reportFiles
      val datastore = PacBioDataStore(startedAt, createdAt, "0.1.0", dsFiles)
      val datastorePath = resources.path.resolve("datastore.json")
      writeDataStore(datastore, datastorePath)
      logger.info(
        s"Successfully wrote datastore with ${datastore.files.length} files to $datastorePath")
      datastore
    }

    val tx = for {
      dsFile <- Try {
        toDataStoreFile(DataSetLoader.loadType(opts.datasetType, opts.path))
      }
      dstore <- Try { writeJobDataStore(dsFile, opts.datasetType) }
    } yield dstore

    tx match {
      case Success(datastore) =>
        logger.info(
          s"${opts.jobTypeId.id} was successful. Generated datastore with ${datastore.files.length} files.")
        Right(datastore)
      case Failure(ex) =>
        val runTime = computeTimeDeltaFromNow(startedAt)
        val msg =
          s"Failed to import dataset ${opts.path} in $runTime sec. Error ${ex.getMessage}\n ${ex.getStackTrace
            .mkString("\n")}"
        logger.error(msg)
        Left(
          ResultFailed(resources.jobId,
                       opts.jobTypeId.id,
                       msg,
                       computeTimeDeltaFromNow(startedAt),
                       AnalysisJobStates.FAILED,
                       host))
    }
  }
}
