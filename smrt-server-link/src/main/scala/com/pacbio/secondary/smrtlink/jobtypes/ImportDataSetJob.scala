package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.{Path, Paths}
import java.util.UUID

import util.{Failure, Success, Try}
import org.joda.time.{DateTime => JodaDateTime}
import com.pacificbiosciences.pacbiodatasets.DataSetType
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.datasets.{
  DataSetFileUtils,
  DataSetMetaTypes
}
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs._
import com.pacbio.secondary.smrtlink.analysis.jobs.CoreJobUtils
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
    projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID),
    submit: Option[Boolean] = Some(JobConstants.SUBMIT_DEFAULT_CORE_JOB),
    tags: Option[String] = None)
    extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.IMPORT_DATASET
  override def validate(
      dao: JobsDao,
      config: SystemJobConfig): Option[InvalidJobOptionError] = {

    def failIfInValid(dst: DataSetMetaTypes.DataSetMetaType)
      : Try[DataSetMetaTypes.DataSetMetaType] = {
      val msg =
        s"Incompatible DataSetMetaType provided=${datasetType.fileType.fileTypeId} expected=${dst.fileType.fileTypeId} from $path"
      if (dst == datasetType) Success(dst)
      else Failure(new Exception(msg))
    }

    val tx = for {
      mini <- Try(DataSetFileUtils.getDataSetMiniMeta(path))
      _ <- failIfInValid(mini.metatype)
    } yield mini

    tx match {
      case Success(_) => None
      case Failure(ex) => Some(InvalidJobOptionError(ex.getMessage))
    }

  }

  override def toJob() = new ImportDataSetJob(this)
}

class ImportDataSetJob(opts: ImportDataSetJobOptions)
    extends ServiceCoreJob(opts)
    with CoreJobUtils
    with timeUtils {
  type Out = PacBioDataStore
  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultsWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {

    val startedAt = JodaDateTime.now()
    val createdAt = startedAt

    val fileSize = opts.path.toFile.length

    // The required Metadata field should be removed from the interface,
    // it doesn't provide any useful value
    def validateCorrectMetaType(
        fileTypeId: String): Try[DataSetMetaTypes.DataSetMetaType] = {
      val msg =
        s"Incompatible DataSet type actual=$fileTypeId provided=${opts.datasetType}"

      DataSetMetaTypes
        .fromString(fileTypeId)
        .map(f => Success(f))
        .getOrElse(Failure(new Exception(msg)))
    }

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
      resultsWriter.writeLine(
        s"Loaded dataset and convert to DataStoreFile $dsFile")

      val logFile = getStdOutLog(resources, dao)
      // This should never stop a dataset from being imported
      val reportDataStoreFiles = DataSetReports.runAllIgnoreErrors(
        opts.path,
        dst,
        resources.path,
        opts.jobTypeId,
        resultsWriter)

      val dsFiles = Seq(dsFile, logFile) ++ reportDataStoreFiles
      val datastore = PacBioDataStore.fromFiles(dsFiles)
      val datastorePath =
        resources.path.resolve(JobConstants.OUTPUT_DATASTORE_JSON)
      writeDataStore(datastore, datastorePath)
      resultsWriter.writeLine(
        s"Successfully wrote datastore with ${datastore.files.length} files to $datastorePath")
      datastore
    }

    val tx = for {
      dsFile <- Try {
        toDataStoreFile(DataSetLoader.loadType(opts.datasetType, opts.path))
      }
      _ <- validateCorrectMetaType(dsFile.fileTypeId)
      dstore <- Try { writeJobDataStore(dsFile, opts.datasetType) }
    } yield dstore

    convertTry[PacBioDataStore](tx, resultsWriter, startedAt, resources.jobId)

  }
}
