package com.pacbio.secondary.smrtlink.jobtypes

import java.io.{FileWriter, PrintWriter, StringWriter}
import java.nio.file.Paths

import com.pacbio.common.models._
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  FileJobResultsWriter,
  JobResultWriter
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{
  EngineJob,
  JobResource,
  JobResourceBase,
  ResultSuccess
}
import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
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
                          writer: JobResultWriter,
                          msg: Option[String]): Unit = {
    writer.writeLineError(s"${msg.getOrElse("")} ${ex.getMessage}")
    val sw = new StringWriter
    ex.printStackTrace(new PrintWriter(sw))
    writer.writeLineError(sw.toString)
  }

  def writeError(
      writer: JobResultWriter,
      msg: Option[String]): PartialFunction[Throwable, Try[ResultSuccess]] = {
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
    with LazyLogging {

  import CommonModelImplicits._

  def setupAndConvert[T >: ServiceMultiJobOptions](
      engineJob: EngineJob): (T, FileJobResultsWriter, JobResource) = {
    val opts = Converters.convertServiceMultiJobOption(engineJob)
    val (writer, resource) = setupResources(engineJob)
    (opts, writer, resource)
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
          _ <- Future.failed(ex)
        } yield MessageResponse(msg) // Never get here
    }
  }

}
