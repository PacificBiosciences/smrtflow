package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.{Files, Path, Paths}
import java.io.File
import java.util.UUID

import scala.collection.JavaConverters._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

import org.joda.time.{DateTime => JodaDateTime}

import com.pacbio.common.models.CommonModels._
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.io._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  InvalidJobOptionError,
  JobResultsWriter
}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.MockJobUtils
import com.pacbio.secondary.smrtlink.analysis.reports.ReportUtils
import com.pacbio.secondary.smrtlink.analysis.reports.ReportModels.Report
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.io.DeleteResourcesUtils
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.models.{
  EngineJobEntryPointRecord,
  ServiceDataSetMetadata
}
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.smrtlink.validators.ValidateServiceDataSetUtils
import com.pacificbiosciences.pacbiodatasets._
import com.pacificbiosciences.pacbiobasedatamodel.{
  InputOutputDataType,
  ExternalResources
}

/**
  * Created by mkocher on 8/17/17.
  */
case class DeleteDataSetJobOptions(
    ids: Seq[IdAble],
    datasetType: DataSetMetaTypes.DataSetMetaType,
    removeFiles: Boolean = true,
    name: Option[String],
    description: Option[String],
    projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID))
    extends ServiceJobOptions {

  import com.pacbio.common.models.CommonModelImplicits._

  override def jobTypeId = JobTypeIds.DELETE_DATASETS
  override def toJob() = new DeleteDataSetJob(this)

  private def validateSubreadSetType(
      datasetType: DataSetMetaTypes.DataSetMetaType)
    : Future[DataSetMetaTypes.DataSetMetaType] = {
    datasetType match {
      case DataSetMetaTypes.Subread => Future.successful(datasetType)
      case x =>
        Future.failed(new UnprocessableEntityError(
          s"DataSetMetaType $x is not support. Only dataset type ${DataSetMetaTypes.Subread} is supported."))
    }
  }

  /**
    * Get a list of ALL datasets to be deleted from the system.
    */
  def getAllDataSets(dao: JobsDao): Future[Seq[ServiceDataSetMetadata]] = {
    for {
      datasets <- getDataSets(dao)
      upstreamDataSets <- getUpstreamDataSets(datasets.map(_.jobId),
                                              DataSetMetaTypes.Subread.dsId,
                                              dao)
      allDataSets <- Future.successful(datasets ++ upstreamDataSets)
    } yield allDataSets
  }

  def getAllDataSetsAndDelete(
      dao: JobsDao): Future[(String, Seq[ServiceDataSetMetadata])] = {
    for {
      datasets <- getAllDataSets(dao)
      msgs <- Future.sequence(datasets.map(d => dao.deleteDataSetById(d.uuid)))
    } yield
      (msgs
         .map(_.message)
         .reduceLeftOption(_ + "\n" + _)
         .getOrElse("No datasets found to delete."),
       datasets)
  }

  private def getUpstreamDataSets(
      jobIds: Seq[Int],
      dsMetaType: String,
      dao: JobsDao): Future[Seq[ServiceDataSetMetadata]] = {
    for {
      jobs <- Future.sequence { jobIds.map(j => dao.getJobById(j)) }
      mergeJobs <- Future.successful(
        jobs.filter(_.jobTypeId == JobTypeIds.MERGE_DATASETS.id))
      entryPoints <- Future
        .sequence { mergeJobs.map(j => dao.getJobEntryPoints(j.id)) }
        .map(_.flatten)
      datasets <- Future.sequence {
        entryPoints.map(
          ep =>
            ValidateServiceDataSetUtils.resolveDataSet(
              DataSetMetaTypes.fromString(ep.datasetType).get,
              ep.datasetUUID,
              dao))
      }
    } yield datasets
  }

  /**
    * Get the original entry points used for each job provided.
    *
    */
  def getDataSets(dao: JobsDao): Future[Seq[ServiceDataSetMetadata]] = {
    for {
      _ <- validateSubreadSetType(datasetType)
      datasets <- Future.sequence(
        ids.map(x =>
          ValidateServiceDataSetUtils
            .resolveDataSet(DataSetMetaTypes.Subread, x, dao)))
    } yield datasets
  }

  override def resolveEntryPoints(
      dao: JobsDao): Seq[EngineJobEntryPointRecord] = {
    Await.result(getDataSets(dao).map(_.map(x =>
                   EngineJobEntryPointRecord(x.uuid, datasetType.toString))),
                 DEFAULT_TIMEOUT)
  }

  override def validate(
      dao: JobsDao,
      config: SystemJobConfig): Option[InvalidJobOptionError] = {
    Try(Await.result(getDataSets(dao), DEFAULT_TIMEOUT)) match {
      case Success(_) => None
      case Failure(ex) =>
        Some(InvalidJobOptionError(s"Invalid options. ${ex.getMessage}"))
    }
  }

}

trait DeleteResourcesCoreJob extends DeleteResourcesUtils {
  this: ServiceCoreJob =>

  type Out = PacBioDataStore
  val resourceType = "Unknown Path"

  protected def runDelete(job: JobResourceBase,
                          resultsWriter: JobResultsWriter,
                          dao: JobsDao,
                          config: SystemJobConfig): Try[Report]

  protected def runJob(job: JobResourceBase,
                       resultsWriter: JobResultsWriter,
                       dao: JobsDao,
                       config: SystemJobConfig): Either[ResultFailed, Out] = {
    val startedAt = JodaDateTime.now()
    //resultsWriter.writeLine(s"Starting cleanup of ${opts.path} at ${startedAt.toString}")
    val logPath = job.path.resolve(JobConstants.JOB_STDOUT)
    val logFile = toSmrtLinkJobLog(
      logPath,
      Some(
        s"${JobConstants.DATASTORE_FILE_MASTER_DESC} of the details of the delete resources job"))
    val reportPath = job.path.resolve("delete_report.json")

    runDelete(job, resultsWriter, dao, config) match {
      case Success(report) =>
        val now = JodaDateTime.now()
        ReportUtils.writeReport(report, reportPath)
        val rptFile = DataStoreFile(
          report.uuid,
          s"${jobTypeId}::delete-report",
          FileTypes.REPORT.fileTypeId.toString,
          reportPath.toFile.length(),
          now,
          now,
          reportPath.toAbsolutePath.toString,
          isChunked = false,
          s"${jobTypeId} Delete Report",
          s"Report for ${resourceType} deletion"
        )
        Right(PacBioDataStore(now, now, "0.2.1", Seq(logFile, rptFile)))
      case Failure(err) =>
        val runTimeSec = computeTimeDeltaFromNow(startedAt)
        Left(
          ResultFailed(
            job.jobId,
            jobTypeId.toString,
            s"Delete job ${job.jobId} of ${resourceType} failed with error '${err.getMessage}",
            runTimeSec,
            AnalysisJobStates.FAILED,
            host
          ))
    }
  }
}

class DeleteDataSetJob(opts: DeleteDataSetJobOptions)
    extends ServiceCoreJob(opts)
    with DeleteResourcesCoreJob {
  override val resourceType = "Dataset"

  override def runDelete(job: JobResourceBase,
                         resultsWriter: JobResultsWriter,
                         dao: JobsDao,
                         config: SystemJobConfig): Try[Report] = Try {
    // THIS needs to be fixed. It should delete from the file system and update the db as a computation single unit
    // to make sure there's a
    val results =
      Await.result(opts.getAllDataSetsAndDelete(dao), opts.DEFAULT_TIMEOUT)
    val paths: Seq[Path] = results._2.map(p => Paths.get(p.path))
    if (paths.isEmpty) throw new Exception("No paths specified")
    deleteDataSetFiles(paths, opts.removeFiles)
  }

  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultsWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] =
    runJob(resources, resultsWriter, dao, config)
}
