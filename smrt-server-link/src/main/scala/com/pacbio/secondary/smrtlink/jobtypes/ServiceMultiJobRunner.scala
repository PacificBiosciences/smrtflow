package com.pacbio.secondary.smrtlink.jobtypes

import java.io.{FileWriter, PrintWriter, StringWriter}
import java.nio.file.Paths

import com.pacbio.common.models._
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  FileJobResultsWriter,
  JobResultsWriter
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{
  EngineJob,
  JobResource
}
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.mail.PbMailer
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Try}
import scala.util.control.NonFatal

trait JobRunnerUtils {

  def setupResources(
      engineJob: EngineJob): (FileJobResultsWriter, JobResource) = {
    val output = Paths.get(engineJob.path)

    // This abstraction needs to be fixed. This is for legacy interface
    val resource = JobResource(engineJob.uuid, output)

    val stderrFw = new FileWriter(
      output.resolve("pbscala-job.stderr").toAbsolutePath.toString,
      true)
    val stdoutFw = new FileWriter(
      output.resolve("pbscala-job.stdout").toAbsolutePath.toString,
      true)

    val writer = new FileJobResultsWriter(stdoutFw, stderrFw)
    writer.writeLine(s"Starting to run engine job ${engineJob.id}")

    (writer, resource)
  }

  def writeErrorToResults(ex: Throwable,
                          writer: JobResultsWriter,
                          msg: Option[String]): Unit = {
    writer.writeLineError(s"${msg.getOrElse("")} ${ex.getMessage}")
    val sw = new StringWriter
    ex.printStackTrace(new PrintWriter(sw))
    writer.writeLineError(sw.toString)
  }

  def writeError(
      writer: JobResultsWriter,
      msg: Option[String]): PartialFunction[Throwable, Try[String]] = {
    case NonFatal(ex) =>
      writeErrorToResults(ex, writer, msg)
      Failure(ex)
  }

}

/**
  * Created by mkocher on 9/11/17.
  */
class ServiceMultiJobRunner(dao: JobsDao, config: SystemJobConfig)
    extends JobRunnerUtils
    with timeUtils
    with LazyLogging
    with PbMailer {

  import CommonModelImplicits._

  def setupAndConvert[T >: ServiceMultiJobOptions](
      engineJob: EngineJob): (T, FileJobResultsWriter, JobResource) = {
    val opts = Converters.convertServiceMultiJobOption(engineJob)
    val (writer, resource) = setupResources(engineJob)
    (opts, writer, resource)
  }

  private def sendMailIfConfigured(jobId: Int, dao: JobsDao): Future[String] = {
    config.mail
      .map(m => sendMultiAnalysisJobMail(jobId, dao, m, config.baseJobsUrl))
      .getOrElse(Future.successful(NOT_CONFIGURED_FOR_MAIL_MSG))
  }

  def andLog(sx: String): Future[String] = Future {
    logger.info(sx)
    sx
  }

  /**
    * Need to fix the EngineJob vs CoreEngineJob confusion
    *
    * 1. convert EngineJob opts to MultiJobOpts instance
    * 2. convert to Job (val job = opts.toJob)
    * 3. then run workflow, job.runWorkflow(dao // What should this return?
    * 4. update workflow state
    *
    * @param engineJob
    */
  def runWorkflow(engineJob: EngineJob): Future[MessageResponse] = {

    val fx = for {
      (opts, writer, resources) <- Future.fromTry(
        Try(setupAndConvert(engineJob)))
      job <- Future.successful(opts.toMultiJob())
      msg <- job.runWorkflow(engineJob, resources, writer, dao, config)
      emailMessage <- sendMailIfConfigured(engineJob.id, dao)
      _ <- andLog(emailMessage)
      _ <- Future.successful(writer.writeLine(emailMessage))
    } yield msg

    fx.recoverWith {
      case ex =>
        for {
          msg <- Future.successful(s"MultiJob failure ${ex.getMessage}")
          //_ <- Future.successful(writeErrorToResults(ex, writer, Some(msg)))
          _ <- dao.updateJobState(engineJob.id,
                                  AnalysisJobStates.FAILED,
                                  msg,
                                  Some(ex.getMessage))
          _ <- sendMailIfConfigured(engineJob.id, dao)
          _ <- Future.failed(ex)
        } yield MessageResponse(msg) // Never get here
    }
  }

}
