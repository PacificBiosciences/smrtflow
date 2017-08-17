package com.pacbio.secondary.smrtlink.jobtypes

import java.io.FileWriter
import java.net.InetAddress
import java.nio.file.Path
import java.util.UUID

import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, FileJobResultsWriter, JobResultWriter}
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


/**
  *
  * Interface for running jobs
  *
  * @param dao Persistence layer for interfacing with the db
  */
class ServiceJobRunner(dao: JobsDao) extends timeUtils with LazyLogging {
  import CommonModelImplicits._

  def host = InetAddress.getLocalHost.getHostName

  private def validate(opts: ServiceJobOptions, uuid: UUID, writer: JobResultWriter): Option[ResultFailed] = {
    opts.validate() match {
      case Some(errors) =>
        val msg = s"Failed to validate Job options $opts Error $errors"
        writer.writeLineStderr(msg)
        logger.error(msg)
        Some(ResultFailed(uuid, opts.jobTypeId.id, msg, 1, AnalysisJobStates.FAILED, host))
      case _ =>
        val msg = s"Successfully validated Job Options $opts"
        writer.writeLineStdout(msg)
        logger.info(msg)
        None
    }
  }

  private def updateJobState(uuid: UUID, state: AnalysisJobStates.JobStates, message: Option[String]): Future[EngineJob] = {
    dao.updateJobState(uuid, state, message.getOrElse(s"Updating Job $uuid state to $state"))
  }



  def run(opts: ServiceJobOptions, uuid: UUID, output: Path)(implicit timeout: Duration = 30.seconds): Either[ResultFailed, ResultSuccess] = {
    val startedAt = JodaDateTime.now()

    val resource = JobResource(uuid, output, AnalysisJobStates.RUNNING)
    val stderrFw = new FileWriter(output.resolve("pbscala-job.stderr").toAbsolutePath.toString, true)
    val stdoutFw = new FileWriter(output.resolve("pbscala-job.stdout").toAbsolutePath.toString, true)
    val writer = new FileJobResultsWriter(stdoutFw, stderrFw)

    val smsg = s"Validating job-type ${opts.jobTypeId.id} ${opts.toString} in ${resource.path.toString}"
    logger.info(smsg)

    validate(opts, uuid, writer) match {
      case Some(r) => Left(r)
      case _ =>
        // For now, the EngineWorker is responsible for setting the state so other Workers don't take the work
        // The job should really be set to SUBMITTED or similar to indicate it's "checked-out"
        // updateStateToRunning(resource.jobId) // This needs to be blocking
        opts.toJob().run(resource, writer, dao) match {
          case Left(a) => Left(a)
          case Right(_) =>
            val runTime = computeTimeDelta(JodaDateTime.now(), startedAt)
            val msg = s"Successfully completed job ${resource.jobId}"
            val result = Await.result(updateJobState(resource.jobId, AnalysisJobStates.SUCCESSFUL, Some(msg)), timeout)
            Right(ResultSuccess(resource.jobId, opts.jobTypeId.id, msg, runTime, AnalysisJobStates.FAILED, host))
        }
    }
  }
}
