package com.pacbio.secondary.smrtlink.jobtypes

import java.io.File
import java.nio.file.{Path, Paths}
import java.util.UUID

import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  FileJobResultsWriter,
  JobResultWriter
}
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.joda.time.{DateTime => JodaDateTime}
import spray.json._
import DefaultJsonProtocol._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
  *
  * Interface for running jobs
  *
  * @param dao Persistence layer for interfacing with the db
  * @param config System Job Config
  */
class ServiceJobRunner(dao: JobsDao, config: SystemJobConfig)
    extends timeUtils
    with LazyLogging
    with JobRunnerUtils {
  import CommonModelImplicits._
  import com.pacbio.secondary.smrtlink.analysis.jobs.SecondaryJobProtocols._

  def host = config.host

  def resolvePath(f: Path, root: Path): Path = {
    if (f.isAbsolute) f
    else root.resolve(f)
  }

  def resolve(root: Path, files: Seq[DataStoreFile]): Seq[DataStoreFile] =
    files.map(f =>
      f.copy(path = resolvePath(Paths.get(f.path), root).toString))

  def loadFiles(files: Seq[DataStoreFile],
                root: Option[Path]): Seq[DataStoreFile] = {
    val resolvedFiles = root.map(r => resolve(r, files)).getOrElse(files)
    resolvedFiles
      .filter(_.fileTypeId == FileTypes.DATASTORE.fileTypeId)
      .foldLeft(resolvedFiles) { (f, dsf) =>
        f ++ loadDataStoreFilesFromDataStore(Paths.get(dsf.path).toFile)
      }
  }

  def loadDataStoreFilesFromDataStore(file: File): Seq[DataStoreFile] = {
    //logger.info(s"Loading raw DataStore from $file")
    val sx = FileUtils.readFileToString(file, "UTF-8")
    val ds = sx.parseJson.convertTo[PacBioDataStore]
    loadFiles(ds.files, Some(file.toPath.getParent))
      .map(f => f.uniqueId -> f)
      .toMap
      .values
      .toSeq
  }

  private def importDataStoreFile(ds: DataStoreFile,
                                  jobUUID: UUID): Future[MessageResponse] = {
    if (ds.isChunked) {
      Future.successful(
        MessageResponse(s"skipping import of intermediate chunked file $ds"))
    } else {
      dao.insertDataStoreFileById(ds, jobUUID)
    }
  }

  private def importDataStore(dataStore: PacBioDataStore, jobUUID: UUID)(
      implicit ec: ExecutionContext): Future[Seq[MessageResponse]] = {
    // When a datastore instance is provided, the files must all be resolved to
    // absolute paths.
    // Filter out non-chunked files. The are presumed to be intermediate files
    val files = loadFiles(dataStore.files, None).filter(!_.isChunked)
    Future.sequence(files.map(x => importDataStoreFile(x, jobUUID)))
  }

  private def importAbleFile(x: ImportAble, jobUUID: UUID)(
      implicit ec: ExecutionContext): Future[Seq[MessageResponse]] = {
    x match {
      case x: DataStoreFile => importDataStoreFile(x, jobUUID).map(List(_))
      case x: PacBioDataStore => importDataStore(x, jobUUID)
    }
  }

  private def updateJobState(uuid: UUID,
                             state: AnalysisJobStates.JobStates,
                             message: Option[String]): Future[EngineJob] = {
    dao.updateJobState(
      uuid,
      state,
      message.getOrElse(s"Updating Job $uuid state to $state"))
  }

  private def convertAndSetup[T >: ServiceJobOptions](
      engineJob: EngineJob): Try[(T, FileJobResultsWriter, JobResource)] = {
    Try {
      val opts = Converters.convertServiceCoreJobOption(engineJob)
      val (writer, resource) = setupResources(engineJob)
      (opts, writer, resource)
    }
  }

  // Returns the Updated EngineJob
  private def updateJobStateBlock(uuid: UUID,
                                  state: AnalysisJobStates.JobStates,
                                  message: Option[String],
                                  timeout: FiniteDuration): Try[EngineJob] = {
    Try { Await.result(updateJobState(uuid, state, message), timeout) }
  }

  /**
    * Import the result from a Job
    *
    * @param jobId   Job Id
    * @param x       This is the OutType from a job (This should be improved to use this abuse of "any"
    * @param timeout timeout for the entire importing process (file IO + db insert)
    * @return
    */
  private def importer(jobId: UUID,
                       x: Any,
                       timeout: FiniteDuration): Try[String] = {
    x match {
      case ds: ImportAble =>
        Try(
          Await.result(importAbleFile(ds, jobId).map(messages =>
                         messages.map(_.message).reduce(_ + "\n" + _)),
                       timeout))
      case _ => Success("No ImportAble. Skipping importing")
    }
  }

  // This takes a lot of args and is a bit clumsy, however it's explicit and straightforward
  private def runJobAndImport[T <: ServiceJobOptions](
      jobIntId: Int,
      opts: T,
      resource: JobResourceBase,
      writer: JobResultWriter,
      dao: JobsDao,
      config: SystemJobConfig,
      startedAt: JodaDateTime,
      timeout: FiniteDuration): Try[ResultSuccess] = {

    val jobId = resource.jobId

    def toSuccess(msg: String) =
      ResultSuccess(jobId,
                    opts.jobTypeId.id,
                    msg,
                    computeTimeDeltaFromNow(startedAt),
                    AnalysisJobStates.SUCCESSFUL,
                    host)

    def andWrite(msg: String) = Try(writer.writeLine(msg))

    val tx = for {
      results <- opts
        .toJob()
        .runTry(resource, writer, dao, config) // Returns Try[#Out] of the job type
      _ <- andWrite(s"Successfully completed running core job. $results")
      msg <- importer(jobId, results, timeout)
      _ <- andWrite(msg)
      updatedEngineJob <- updateJobStateBlock(
        jobId,
        AnalysisJobStates.SUCCESSFUL,
        Some(s"Successfully run job $jobId"),
        timeout)
      _ <- andWrite(
        s"Updated job ${updatedEngineJob.id} state to ${updatedEngineJob.state}")
      _ <- andWrite(
        s"Successfully completed job-type:${opts.jobTypeId.id} id:${updatedEngineJob.id} in ${computeTimeDeltaFromNow(startedAt)} sec")
    } yield toSuccess(msg)

    // This is a little clumsy to get the error message written to the correct place
    // This is also potentially duplicated with the Either[ResultsFailed,ResultsSuccess] in the old core job level.
    tx.recoverWith(
      writeError(writer, Some(s"Failed to run and import $jobIntId")))
  }

  /**
    * Validation of Job options
    *
    * @param opts Job Service Options
    * @param writer output writer
    * @return
    */
  private def validateOpts[T <: ServiceJobOptions](
      opts: T,
      writer: JobResultWriter): Try[T] = {
    Try { opts.validate(dao, config) }.flatMap {
      case Some(errors) =>
        val msg = s"Failed to validate Job options $opts Error $errors"
        writer.writeLineError(msg)
        logger.error(msg)
        Failure(new IllegalArgumentException(msg))
      case None =>
        val msg = s"Successfully validated Job Options $opts"
        writer.writeLine(msg)
        logger.info(msg)
        Success(opts)
    }
  }

  // This should probably have some re-try mechanism
  def recoverAndUpdateToFailed(jobId: UUID,
                               timeout: FiniteDuration,
                               toFailed: String => ResultFailed)
    : PartialFunction[Throwable, Try[Either[ResultFailed, ResultSuccess]]] = {
    case NonFatal(ex) =>
      updateJobStateBlock(jobId,
                          AnalysisJobStates.FAILED,
                          Some(ex.getMessage),
                          timeout)
        .map(engineJob =>
          Left(toFailed(
            s"${ex.getMessage}. Successfully updated job ${engineJob.id} state to ${engineJob.state}.")))
  }

  /**
    * This single place is responsible for handling ALL job state and importing results (e.g., import datastore files)
    *
    * Break this into a few steps
    *
    * 1. convert the EngineJob settings to a ServiceJobOptions
    * 2. Setup directories and results writers
    * 3. Run Job
    * 4. Import datastore (if necessary)
    * 5. Update db state of job
    * 6. Return an Either[ResultFailed,ResultSuccess]
    *
    * @param engineJob Engine Job to Run
    * @return
    */
  def run(engineJob: EngineJob)(implicit timeout: FiniteDuration = 30.seconds)
    : Either[ResultFailed, ResultSuccess] = {

    val startedAt = JodaDateTime.now()

    def toFailed(msg: String) =
      ResultFailed(engineJob.uuid,
                   engineJob.jobTypeId,
                   msg,
                   computeTimeDeltaFromNow(startedAt),
                   AnalysisJobStates.FAILED,
                   host)

    val tx = for {
      rxs <- convertAndSetup(engineJob)
      _ <- validateOpts(rxs._1, rxs._2)
      results <- runJobAndImport(engineJob.id,
                                 rxs._1,
                                 rxs._3,
                                 rxs._2,
                                 dao,
                                 config,
                                 startedAt,
                                 timeout)
    } yield Right(results)

    tx.recoverWith(recoverAndUpdateToFailed(engineJob.uuid, timeout, toFailed)) match {
      case Success(either) => either
      case Failure(ex) =>
        logger.error(
          s"FAILED to update db state for ${engineJob.id} ${ex.getMessage}")
        // this should log the stacktrace. This needs access to the job writer
        Left(toFailed(ex.getMessage))
    }
  }

}
