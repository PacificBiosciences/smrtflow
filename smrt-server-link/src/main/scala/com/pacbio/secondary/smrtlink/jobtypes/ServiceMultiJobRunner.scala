package com.pacbio.secondary.smrtlink.jobtypes

import com.pacbio.common.models._
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  FileJobResultsWriter,
  JobResultsWriter
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.mail.PbMailer
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

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
      engineJob: EngineJob,
      dao: JobsDao): (T, FileJobResultsWriter, JobResource) = {
    val opts = Converters.convertServiceMultiJobOption(engineJob)
    val (writer, resource) =
      setupMultiJobResources(engineJob, dao, opts.DEFAULT_TIMEOUT)
    (opts, writer, resource)
  }

  private def sendMailIfConfigured(job: EngineJob,
                                   dao: JobsDao): Future[String] = {
    if (job.state.isCompleted) {
      config.mail
        .map(m => sendMultiAnalysisJobMail(job.id, dao, m, config.baseJobsUrl))
        .getOrElse(Future.successful(NOT_CONFIGURED_FOR_MAIL_MSG))
    } else {
      Future.successful("")
    }
  }

  def andLog(sx: String, writer: JobResultsWriter): Future[String] = Future {
    writer.writeLine(sx)
    logger.info(sx)
    sx
  }

  def andLogIfNonEmpty(sx: String, writer: JobResultsWriter) =
    if (sx.isEmpty) Future.successful(sx) else andLog(sx, writer)

  def runner(engineJob: EngineJob): Future[MessageResponse] = {

    val fx = for {
      (opts, writer, resources) <- Future.fromTry(
        Try(setupAndConvert(engineJob, dao)))
      job <- Future.successful(opts.toMultiJob())
      msg <- job.runWorkflow(engineJob, resources, writer, dao, config)
      updatedJob <- dao.getJobById(engineJob.id)
      emailMessage <- sendMailIfConfigured(updatedJob, dao)
      _ <- andLogIfNonEmpty(emailMessage, writer)
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
          updatedJob <- dao.getJobById(engineJob.id)
          _ <- sendMailIfConfigured(updatedJob, dao)
          _ <- Future.failed(ex)
        } yield MessageResponse(msg) // Never get here
    }
  }

  /**
    * Core routine for running a multi-analysis job.
    *
    * 0. Check if job state is completed
    * 1. convert EngineJob opts to MultiJobOpts instance
    * 2. convert to Job (val job = opts.toJob)
    * 3. then run workflow
    * 4. update workflow state
    *
    * Need to fix the EngineJob vs CoreEngineJob confusion
    *
    * @param engineJob (Multi-Analysis Job Type) Engine Job to run
    */
  def runWorkflow(engineJob: EngineJob): Future[MessageResponse] = {
    val msg =
      s"MultiAnalysis Job ${engineJob.id} is completed (state: ${engineJob.state}), no workflow to run."
    if (engineJob.state.isCompleted) Future.successful(MessageResponse(msg))
    else runner(engineJob)
  }

}
