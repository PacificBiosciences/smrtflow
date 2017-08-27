package com.pacbio.secondary.smrtlink.jobtypes

import java.io.FileWriter
import java.nio.file.Paths
import java.util.UUID

import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, FileJobResultsWriter, JobResultWriter}
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, ExecutionContext, Future}


/**
  *
  * Interface for running jobs
  *
  * @param dao Persistence layer for interfacing with the db
  * @param config System Job Config
  */
class ServiceJobRunner(dao: JobsDao, config: SystemJobConfig) extends timeUtils with LazyLogging {
  import CommonModelImplicits._

  def host = config.host

  private def importDataStoreFile(ds: DataStoreFile, jobUUID: UUID):Future[MessageResponse] = {
    if (ds.isChunked) {
      Future.successful(MessageResponse(s"skipping import of intermediate chunked file $ds"))
    } else {
      dao.insertDataStoreFileById(ds, jobUUID)
    }
  }

  private def importDataStore(dataStore: PacBioDataStore, jobUUID:UUID)(implicit ec: ExecutionContext): Future[Seq[MessageResponse]] = {
    // Filter out non-chunked files. The are presumed to be intermediate files
    val nonChunked = dataStore.files.filter(!_.isChunked)
    Future.sequence(nonChunked.map(x => importDataStoreFile(x, jobUUID)))
  }

  private def importAbleFile(x: ImportAble, jobUUID: UUID)(implicit ec: ExecutionContext ): Future[Seq[MessageResponse]] = {
    x match {
      case x: DataStoreFile => importDataStoreFile(x, jobUUID).map(List(_))
      case x: PacBioDataStore => importDataStore(x, jobUUID)
    }
  }

  private def validate(opts: ServiceJobOptions, uuid: UUID, writer: JobResultWriter): Option[ResultFailed] = {
    opts.validate(dao, config) match {
      case Some(errors) =>
        val msg = s"Failed to validate Job options $opts Error $errors"
        writer.writeLineError(msg)
        logger.error(msg)
        Some(ResultFailed(uuid, opts.jobTypeId.id, msg, 1, AnalysisJobStates.FAILED, host))
      case None =>
        val msg = s"Successfully validated Job Options $opts"
        writer.writeLine(msg)
        logger.info(msg)
        None
    }
  }

  private def updateJobState(uuid: UUID, state: AnalysisJobStates.JobStates, message: Option[String]): Future[EngineJob] = {
    dao.updateJobState(uuid, state, message.getOrElse(s"Updating Job $uuid state to $state"))
  }

  /**
    * Central interface for running jobs.
    *
    * This single place is responsible for handling ALL job state and importing results (e.g., import datastore files)
    *
    * @param engineJob Engine Job to run
    * @param timeout Timeout for interfacing with the jobs dao
    */
  def run(engineJob: EngineJob)(implicit timeout: Duration = 30.seconds): Either[ResultFailed, ResultSuccess] = {
    val startedAt = JodaDateTime.now()

    val opts = Converters.convertEngineToOptions(engineJob)
    val uuid = engineJob.uuid
    val output = Paths.get(engineJob.path)

    // This abstraction needs to be fixed.
    val resource = JobResource(uuid, output, AnalysisJobStates.RUNNING)

    val stderrFw = new FileWriter(output.resolve("pbscala-job.stderr").toAbsolutePath.toString, true)
    val stdoutFw = new FileWriter(output.resolve("pbscala-job.stdout").toAbsolutePath.toString, true)

    val writer = new FileJobResultsWriter(stdoutFw, stderrFw)

    val smsg = s"Validating job-type ${opts.jobTypeId.id} ${opts.toString} in ${resource.path.toString}"

    logger.info(smsg)
    writer.writeLine(smsg)

    validate(opts, uuid, writer) match {
      case None =>
        // Validation was successful. Let's start running the job

        // Need to clarify who is responsible for updating the task state
        // updateStateToRunning(resource.jobId) // This needs to be blocking
        opts.toJob().run(resource, writer, dao, config) match {
          case Left(a) =>
            val result = Await.result(updateJobState(resource.jobId, AnalysisJobStates.FAILED, Some(a.message)), timeout)
            Left(a)
          case Right(xs:ImportAble) =>
            // Need to add dataset importing
            val runTime = computeTimeDelta(JodaDateTime.now(), startedAt)
            val msg = s"Successfully completed job ${resource.jobId} in $runTime sec"
            writer.writeLine(msg)
            val importResult = Await.result(importAbleFile(xs, uuid), timeout)
            val result = Await.result(updateJobState(resource.jobId, AnalysisJobStates.SUCCESSFUL, Some(msg)), timeout)
            Right(ResultSuccess(resource.jobId, opts.jobTypeId.id, msg, runTime, AnalysisJobStates.SUCCESSFUL, host))
          case Right(_) =>
            // Need to add dataset importing
            val runTime = computeTimeDelta(JodaDateTime.now(), startedAt)
            val msg = s"Successfully completed job ${resource.jobId} in $runTime sec"
            writer.writeLineError(msg)
            val result = Await.result(updateJobState(resource.jobId, AnalysisJobStates.SUCCESSFUL, Some(msg)), timeout)
            Right(ResultSuccess(resource.jobId, opts.jobTypeId.id, msg, runTime, AnalysisJobStates.SUCCESSFUL, host))
        }

      case Some(r) =>
        // Job Option Validation Failed
        val result = Await.result(updateJobState(resource.jobId, r.state, Some(r.message)), timeout)
        writer.writeError(r.message)
        Left(r)
    }
  }
}
