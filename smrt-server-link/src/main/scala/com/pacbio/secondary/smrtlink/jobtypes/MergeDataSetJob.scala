package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetMerger
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  InvalidJobOptionError,
  JobResultsWriter
}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.MockJobUtils

import com.pacbio.secondary.smrtlink.analysis.reports.DataSetReports
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.models.{
  EngineJobEntryPointRecord,
  ServiceDataSetMetadata
}
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.smrtlink.validators.ValidateServiceDataSetUtils
import com.pacificbiosciences.pacbiodatasets.DataSetType

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.blocking
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

/**
  * Created by mkocher on 8/17/17.
  */
case class MergeDataSetJobOptions(
    datasetType: DataSetMetaTypes.DataSetMetaType,
    ids: Seq[IdAble],
    name: Option[String],
    description: Option[String],
    projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID))
    extends ServiceJobOptions {
  import CommonModelImplicits._

  override def jobTypeId = JobTypeIds.MERGE_DATASETS
  override def toJob() = new MergeDataSetJob(this)

  override def validate(
      dao: JobsDao,
      config: SystemJobConfig): Option[InvalidJobOptionError] = {
    Try(resolveEntryPoints(dao)) match {
      case Success(_) => None
      case Failure(ex) =>
        Some(InvalidJobOptionError(s"Invalid options. ${ex.getMessage}"))
    }
  }

  override def resolveEntryPoints(dao: JobsDao) =
    validateAndResolveEntryPoints(dao, datasetType, ids)

}

class MergeDataSetJob(opts: MergeDataSetJobOptions)
    extends ServiceCoreJob(opts)
    with MockJobUtils
    with timeUtils {
  type Out = PacBioDataStore

  import CommonModelImplicits._

  val SOURCE_ID = s"pbscala::merge_dataset"

  // There's a bit of duplication here. I can't get the types to work correctly.
  def mergeDataSets[T <: DataSetType](
      jobRoot: Path,
      resultsWriter: JobResultsWriter,
      dataSetType: DataSetMetaTypes.DataSetMetaType,
      paths: Seq[Path],
      outputDataSetPath: Path,
      name: String,
      description: String): Try[Seq[DataStoreFile]] = {
    dataSetType match {
      case DataSetMetaTypes.Subread =>
        for {
          dataset <- Try(
            DataSetMerger
              .mergeSubreadSetPathsTo(paths, name, outputDataSetPath))
          dsFile <- Success(
            toDataStoreFile(dataset,
                            outputDataSetPath,
                            description,
                            SOURCE_ID))
          reportFiles <- Try(
            DataSetReports.runAll(outputDataSetPath,
                                  DataSetMetaTypes.Subread,
                                  jobRoot,
                                  jobTypeId,
                                  resultsWriter))
        } yield Seq(dsFile) ++ reportFiles
      case DataSetMetaTypes.HdfSubread =>
        for {
          dataset <- Try(
            DataSetMerger
              .mergeHdfSubreadSetPathsTo(paths, name, outputDataSetPath))
          dsFile <- Success(
            toDataStoreFile(dataset,
                            outputDataSetPath,
                            description,
                            SOURCE_ID))
          reportFiles <- Try(
            DataSetReports.runAll(outputDataSetPath,
                                  DataSetMetaTypes.HdfSubread,
                                  jobRoot,
                                  jobTypeId,
                                  resultsWriter))
        } yield Seq(dsFile) ++ reportFiles
      case x =>
        Failure(new Exception(
          s"Unsupported dataset type $x. Only SubreadSet and HdfSubreadSet are supported."))
    }
  }

  def runner(job: JobResourceBase,
             resultsWriter: JobResultsWriter,
             dao: JobsDao,
             config: SystemJobConfig): Try[PacBioDataStore] = {

    val startedAt = JodaDateTime.now()

    // Job Resources
    val logPath = job.path.resolve(JobConstants.JOB_STDOUT)
    val logFile = toSmrtLinkJobLog(logPath)
    val outputDataSetPath = job.path.resolve("merged.dataset.xml")
    val datastoreJson = job.path.resolve("datastore.json")

    val name = opts.name.getOrElse("MergedDataSet")
    val description = s"Merged PacBio DataSet from ${opts.ids.length} files"

    def writer(sx: String): String = {
      logger.debug(sx)
      resultsWriter.writeLine(sx)
      sx
    }

    def writeInitSummary(): Unit = {
      writer(
        s"Starting dataset merging of ${opts.ids.length} ${opts.datasetType} Files at ${startedAt.toString}")
      resultsWriter.writeLine(s"DataSet Merging options: $opts")
    }

    val timeout: FiniteDuration = opts.ids.length * opts.TIMEOUT_PER_RECORD

    // add datastore file so the errors are propagated on failure
    for {
      _ <- runAndBlock(dao.importDataStoreFile(logFile, job.jobId),
                       opts.DEFAULT_TIMEOUT)
      _ <- Success(writeInitSummary())
      paths <- runAndBlock(resolvePathsAndWriteEntryPoints(dao,
                                                           job.path,
                                                           opts.datasetType,
                                                           opts.ids),
                           opts.DEFAULT_TIMEOUT)
      _ <- Success(writer(s"Successfully resolved ${opts.ids.length} files"))
      dsFiles <- mergeDataSets(job.path,
                               resultsWriter,
                               opts.datasetType,
                               paths,
                               outputDataSetPath,
                               name,
                               description)
      dataStore <- Success(PacBioDataStore.fromFiles(dsFiles ++ Seq(logFile)))
      _ <- Try(writeDataStore(dataStore, datastoreJson))
      _ <- Try(
        writer(
          s"Successfully wrote datastore to ${datastoreJson.toAbsolutePath}"))
    } yield dataStore

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
    val tx = runner(resources, resultsWriter, dao, config)
      .map(result => Right(result))

    val tr = tx.recover { case ex => toLeft(ex.getMessage) }

    tr match {
      case Success(x) => x
      case Failure(ex) => toLeft(ex.getMessage)
    }
  }
}
