package com.pacbio.secondary.smrtlink.jobtypes

import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.blocking
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

import org.joda.time.{DateTime => JodaDateTime}

import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.datasets.{
  DataSetMetaTypes,
  DataSetFilterUtils,
  DataSetFilterProperty
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  InvalidJobOptionError,
  JobResultsWriter
}
import com.pacbio.secondary.smrtlink.analysis.jobs.CoreJobUtils
import com.pacbio.secondary.smrtlink.analysis.reports.DataSetReports
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig

case class CopyDataSetJobOptions(datasetId: IdAble,
                                 filters: Seq[Seq[DataSetFilterProperty]],
                                 name: Option[String],
                                 description: Option[String],
                                 projectId: Option[Int] = Some(
                                   JobConstants.GENERAL_PROJECT_ID))
    extends ServiceJobOptions {
  import CommonModelImplicits._

  override def jobTypeId = JobTypeIds.DS_COPY
  override def toJob() = new CopyDataSetJob(this)

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
    validateAndResolveEntryPoints(dao,
                                  DataSetMetaTypes.Subread,
                                  Seq(datasetId))
}

class CopyDataSetJob(opts: CopyDataSetJobOptions)
    extends ServiceCoreJob(opts)
    with CoreJobUtils
    with DataSetFilterUtils
    with timeUtils {
  type Out = PacBioDataStore

  import CommonModelImplicits._

  val SOURCE_ID = "pbscala::filter_dataset"

  def runner(job: JobResourceBase,
             resultsWriter: JobResultsWriter,
             dao: JobsDao,
             config: SystemJobConfig): Future[PacBioDataStore] = {
    val logPath = job.path.resolve(JobConstants.JOB_STDOUT)
    val outputFile = job.path.resolve("filtered.subreadset.xml")
    val datastoreJson = job.path.resolve("datastore.json")

    def writer(sx: String): Future[String] = {
      logger.debug(sx)
      resultsWriter.writeLine(sx)
      Future.successful(sx)
    }

    for {
      logFile <- addStdOutLogToDataStore(job.jobId,
                                         dao,
                                         logPath,
                                         opts.projectId)
      paths <- resolvePathsAndWriteEntryPoints(dao,
                                               job.path,
                                               DataSetMetaTypes.Subread,
                                               Seq(opts.datasetId))
      _ <- writer(s"Successfully resolved dataset ${opts.datasetId.toString}")
      ds <- Future.successful(
        applyFilters(paths.head, outputFile, opts.filters, opts.name))
      dsFile <- Future.successful(
        toDataStoreFile(ds, outputFile, "Filtered dataset", SOURCE_ID))
      dataStore <- Future.successful(
        PacBioDataStore.fromFiles(Seq(logFile, dsFile)))
      dataStore <- Future.successful(dataStore.copy(files = dataStore.files))
      _ <- Future.successful(writeDataStore(dataStore, datastoreJson))
      _ <- writer(
        s"Successfully wrote datastore to ${datastoreJson.toAbsolutePath}")
    } yield dataStore
  }

  /**
    * No Reports are Generated for Copied datasets
    *
    * @param resources     Resources for the Job to use (e.g., job id, root path) This needs to be expanded to include the system config.
    *                      This be renamed for clarity
    * @param resultsWriter Writer to write to job stdout and stderr
    * @param dao           interface to the DB. See above comments on suggested use and responsibility
    * @param config        System Job config. Any specific config for any job type needs to be pushed into this layer
    * @return
    */
  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultsWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {
    val startedAt = JodaDateTime.now()
    val fx = runner(resources, resultsWriter, dao, config)
    convertTry(runAndBlock(fx, opts.DEFAULT_TIMEOUT),
               resultsWriter,
               startedAt,
               resources.jobId)
  }
}
