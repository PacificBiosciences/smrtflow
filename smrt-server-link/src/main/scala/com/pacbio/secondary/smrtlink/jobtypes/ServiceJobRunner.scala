package com.pacbio.secondary.smrtlink.jobtypes

import java.io.{File, FileNotFoundException, IOError}
import java.net.URL
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  FileJobResultsWriter,
  JobResultsWriter,
  LogJobResultsWriter
}
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.joda.time.{DateTime => JodaDateTime}
import spray.json._
import DefaultJsonProtocol._
import com.pacbio.secondary.smrtlink.io.ModelIOUtils
import com.pacbio.secondary.smrtlink.mail.PbMailer

import scala.concurrent.duration._
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
class ServiceJobRunner(dao: JobsDao, config: SystemJobConfig)(
    implicit ec: ExecutionContext)
    extends timeUtils
    with LazyLogging
    with JobRunnerUtils
    with PbMailer
    with ModelIOUtils {
  import CommonModelImplicits._
  import com.pacbio.secondary.smrtlink.analysis.jobs.SecondaryJobProtocols._

  def host: String = config.host

  private def validateDsFile(
      dataStoreFile: DataStoreFile): Future[DataStoreFile] = {
    if (Files.exists(Paths.get(dataStoreFile.path)))
      Future.successful(dataStoreFile)
    else
      Future.failed(new FileNotFoundException(
        s"DatastoreFile ${dataStoreFile.uniqueId} name:${dataStoreFile.name} Unable to find path: ${dataStoreFile.path}"))
  }

  private def importDataStore(datastoreFiles: Seq[DataStoreFile],
                              jobUUID: UUID): Future[Seq[MessageResponse]] = {

    for {
      validFiles <- Future.sequence(datastoreFiles.map(validateDsFile))(
        implicitly,
        ec)
      results <- dao.importDataStoreFiles(validFiles, jobUUID)
    } yield results
  }

  /**
    * Import non-chunked Files
    *
    * When a datastore instance is provided, the files must all be resolved to absolute paths.
    *
    * Filter out non-chunked files. The are presumed to be intermediate files
    *
    */
  private def importAbleFile(x: ImportAble,
                             jobUUID: UUID): Future[Seq[MessageResponse]] = {
    x match {
      case x: DataStoreFile => importDataStore(Seq(x), jobUUID)
      case x: PacBioDataStore =>
        val dataStoreFiles = loadFiles(x.files, None)
        val nonChunkedFiles = dataStoreFiles.filter(f => !f.isChunked)
        logger.info(
          s"Job $jobUUID Loaded ${dataStoreFiles.length} raw files, ${nonChunkedFiles.length} Non-Chunked files")
        importDataStore(nonChunkedFiles, jobUUID)
    }
  }

  private def updateJobState(
      uuid: UUID,
      state: AnalysisJobStates.JobStates,
      message: Option[String],
      errorMessage: Option[String]): Future[EngineJob] = {
    dao.updateJobState(
      uuid,
      state,
      message.getOrElse(s"Updating Job $uuid state to $state"),
      errorMessage
    )
  }

  // Returns the Updated EngineJob
  private def updateJobStateBlock(
      uuid: UUID,
      state: AnalysisJobStates.JobStates,
      message: Option[String],
      timeout: FiniteDuration,
      errorMessage: Option[String] = None): Try[EngineJob] = {
    Try {
      Await.result(updateJobState(uuid, state, message, errorMessage), timeout)
    }
  }

  /**
    * Import the result from a Job
    *
    * @param jobId   Job Id
    * @param x       This is the OutType from a job (This should be improved to use this abuse of "any"
    * @param timeout timeout for the entire importing process (file IO + db insert)
    * @return
    */
  def importer(jobId: UUID, x: Any, timeout: FiniteDuration): Try[String] = {

    def processSummary(messages: Seq[MessageResponse]): String = {
      messages
        .map(_.message)
        .reduceLeftOption(_ + "\n" + _)
        .getOrElse("WARNING. No Files to be imported")
    }

    x match {
      case ds: ImportAble =>
        Try(
          Await.result(importAbleFile(ds, jobId).map(processSummary), timeout))
      case _ => Success("No ImportAble. Skipping importing")
    }
  }

  /**
    *  This takes a lot of args and is a bit clumsy, however it's explicit and straightforward
    *
    *  Emits a status message of success.
    */
  private def runJobAndImport[T <: ServiceJobOptions](
      jobIntId: Int,
      opts: T,
      resource: JobResourceBase,
      writer: JobResultsWriter,
      dao: JobsDao,
      config: SystemJobConfig,
      startedAt: JodaDateTime,
      timeout: FiniteDuration): Try[String] = {

    val jobId = resource.jobId

    def andWrite(msg: String) = Try(writer.writeLine(msg))

    val tx = for {
      results <- opts
        .toJob()
        .runTry(resource, writer, dao, config) // Returns Try[#Out] of the job type
      _ <- andWrite(s"Successfully completed running core job id:$jobIntId")
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
        s"Successfully completed Job id:${updatedEngineJob.id} job-type:${opts.jobTypeId.id} in ${computeTimeDeltaFromNow(startedAt)} sec")
    } yield msg

    // This is a little clumsy to get the error message written to the correct place
    // This is also potentially duplicated with the Either[ResultsFailed,ResultsSuccess] in the old core job level.
    tx.recoverWith(
      writeError(writer, Some(s"Job $jobIntId Failed to execute or import")))
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
      writer: JobResultsWriter): Try[T] = {
    Try { opts.validate(dao, config) }.flatMap {
      case Some(errors) =>
        // Write to both out and err to make sure the err is communicated to out
        val msg = s"Failed to validate Job options $opts Error $errors"
        writer.writeLineError(msg)
        writer.writeLine(msg)
        Failure(new IllegalArgumentException(msg))
      case None =>
        val msg = s"Successfully validated Job Options $opts"
        writer.writeLine(msg)
        Success(opts)
    }
  }

  /**
    * General util to update the job state. If the state can't be
    * update successfully in the db, this can potentially leave this
    * job in an incorrect state.
    *
    * @param jobId   : Job UUID
    * @param timeout Max timeout for the database operation.
    */
  private def recoverAndUpdateToFailed[T](
      jobId: UUID,
      timeout: FiniteDuration): PartialFunction[Throwable, Try[T]] = {
    case NonFatal(ex) =>
      // This has lost the details of the Failed state. This is why
      // pbsmrtpipe doesn't propagate the TERMINAL state correctly
      updateJobStateBlock(jobId,
                          AnalysisJobStates.FAILED,
                          Some(s"Failed to Run Job $jobId"),
                          timeout,
                          Some(ex.getMessage))
        .flatMap(engineJob =>
          Failure(new Exception(
            s"${ex.getMessage}. Successfully updated job ${engineJob.id} state to ${engineJob.state}.")))
  }

  /**
    *
    * Core function for running the job and updating the State in the db.
    * This needs to be completely self-contained and handle errors such
    * that the db state is updated successfully with the failed successful
    * or failed state.
    *
    */
  private def runner(engineJob: EngineJob,
                     writer: JobResultsWriter,
                     resource: JobResource,
                     startedAt: JodaDateTime)(
      implicit timeout: FiniteDuration): Try[String] = {

    val tx = for {
      opts <- Try(Converters.convertServiceCoreJobOption(engineJob))
      _ <- validateOpts(opts, writer)
      results <- runJobAndImport(engineJob.id,
                                 opts,
                                 resource,
                                 writer,
                                 dao,
                                 config,
                                 startedAt,
                                 timeout)
    } yield results

    tx.recoverWith(recoverAndUpdateToFailed(engineJob.uuid, timeout))
  }

  private def sendMail(jobId: Int): Future[String] = {

    for {
      updatedJob <- dao.getJobById(jobId)
      msg <- config.mail
        .map(c => sendCoreJobEmail(updatedJob, config.baseJobsUrl, c))
        .getOrElse(Future.successful(
          s"Skipping Sending Email for Job $jobId state:${updatedJob.state}. type:${updatedJob.jobTypeId} userEmail:${updatedJob.createdByEmail}, System is not configured for Email sending"))
    } yield msg
  }

  // Black hole, we don't fail the job if an exception is raised
  // during the emailing. Only log to the system log, not locally
  private def sendMailTry(jobId: Int,
                          writer: JobResultsWriter,
                          timeout: FiniteDuration): Try[String] =
    Try(Await.result(sendMail(jobId), timeout)) match {
      case Success(msg) =>
        writer.writeLine(msg)
        Success(msg)
      case Failure(ex) =>
        val msg = s"Failed to send email for job id $jobId ${ex.getMessage}"
        writer.writeError(msg)
        Success(msg)
    }

  // Propagate the original success message
  def sendMailOnSuccess(jobId: Int,
                        writer: JobResultsWriter,
                        timeout: FiniteDuration)(msg: String): Try[String] =
    sendMailTry(jobId, writer, timeout).map(_ => msg)

  // Propagate the original error message
  def sendMailOnFailure(jobId: Int,
                        writer: JobResultsWriter,
                        timeout: FiniteDuration)(ex: Throwable): Try[String] =
    sendMailTry(jobId, writer, timeout).flatMap(_ => Failure(ex))

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
    * 6. Return an Success status message (as a string)
    *
    *
    *
    * @param engineJob Engine Job to Run
    * @param timeout The Total timeout to process/parse the output files and import them into the system. If
    *                a pipeline produces N files and M different jobs complete at around the same time,
    *                the this will impact the timeout (the M jobs will be competing for import processing). Specifically,
    *                for the case when N is > 100.
    *
    *                In the job log, we need to add some diagnostics to see how long the total import takes. However,
    *                in the meantime, here's a back of the envelop approximation based on these two factors.
    *
    *                1. Average per file process time of ~ 0.5 sec. Processing, is parsing NFS hosted file and import into db.
    *                In practice this is a bimodal distribution of a raw DataStoreFile (which has no parsing overhead)
    *                and a DataSet which will hit the disk and parse the XML. Note, the parse IO is completely decoupled from
    *                the db insertion.
    *                2. Approximate upper bound of files in a pipeline to be 300-400 datastore files.
    *
    *                This roughly translates to 3 total minutes for a single job to import successfully provided that
    *                it is not competing with other importing or db processes.
    *
    */
  def runCore(engineJob: EngineJob,
              writer: JobResultsWriter,
              resources: JobResource,
              startedAt: Option[JodaDateTime] = None)(
      implicit timeout: FiniteDuration = 3.minutes): Try[String] = {

    // Enable logging to the System and to the Job log
    class FileAndLogWriter extends JobResultsWriter {
      override def write(msg: String): Unit = {
        logger.info(msg)
        writer.write(msg)
      }

      override def writeError(msg: String): Unit = {
        logger.error(msg)
        writer.writeError(msg)
      }
    }

    val localAndSystemWriter = new FileAndLogWriter()

    // Run the main, update state in db on success or failure
    // This will handled all error logging
    val tryRunner =
      runner(engineJob,
             localAndSystemWriter,
             resources,
             startedAt.getOrElse(JodaDateTime.now()))

    tryRunner.transform(
      sendMailOnSuccess(engineJob.id, localAndSystemWriter, timeout),
      sendMailOnFailure(engineJob.id, localAndSystemWriter, timeout))
  }

  /**
    * There's a little bit of gymnastics going on here to get the necessary components
    * to compose AND also have context to the necessary JobResults Writer that will enable writing
    * locally to the job log, as well to the system log.
    *
    */
  def run(engineJob: EngineJob, startedAt: Option[JodaDateTime] = None)(
      implicit timeout: FiniteDuration = 3.minutes): Try[String] = {

    // Because the job isn't "setup" yet, we only log to the system
    // After the job is successfully setup, a new log writer will be used
    val systemOnlyLogWriter = new LogJobResultsWriter()

    // If the resource failed to setup, the mark the job as failed
    val tx: Try[(FileJobResultsWriter, JobResource)] =
      Try(setupCoreJobResources(engineJob))
        .recoverWith(recoverAndUpdateToFailed(engineJob.uuid, timeout))

    // to adhere to the transform interface
    def runCoreJob(x: (FileJobResultsWriter, JobResource)): Try[String] =
      runCore(engineJob, x._1, x._2, startedAt)

    // If the setup was successful, then call the runCore func, otherwise, just
    // send an email and only log to the system log.
    tx.transform[String](
      runCoreJob,
      sendMailOnFailure(engineJob.id, systemOnlyLogWriter, timeout))

  }

}
