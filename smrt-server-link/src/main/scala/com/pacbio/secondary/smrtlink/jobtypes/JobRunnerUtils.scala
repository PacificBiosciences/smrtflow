package com.pacbio.secondary.smrtlink.jobtypes

import java.io.{FileWriter, PrintWriter, StringWriter}
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  FileJobResultsWriter,
  JobResultsWriter
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Try}
import scala.util.control.NonFatal

/**
  * Created by mkocher on 10/9/17.
  */
trait JobRunnerUtils {

  private def setupResources(engineJob: EngineJob)
    : (FileJobResultsWriter, JobResource, Boolean, Path) = {

    val output = Paths.get(engineJob.path)

    val stderr = output.resolve(JobConstants.JOB_STDERR).toAbsolutePath
    val stdout = output.resolve(JobConstants.JOB_STDOUT).toAbsolutePath

    val wasSetup = Files.exists(stdout)

    // This abstraction needs to be fixed. This is for legacy interface
    val resource = JobResource(engineJob.uuid, output)

    val stderrFw = new FileWriter(stderr.toString, true)
    val stdoutFw = new FileWriter(stdout.toString, true)

    val writer = new FileJobResultsWriter(stdoutFw, stderrFw)

    if (!wasSetup) {

      val multiJobMsg = engineJob.parentMultiJobId
        .map(i => s"Parent MultiJobId:$i")
        .getOrElse("")

      writer.writeLine(
        s"Initial Setup of resources. Starting to run engine job id:${engineJob.id} type:${engineJob.jobTypeId} $multiJobMsg")

    }

    (writer, resource, wasSetup, stdout)
  }

  def setupCoreJobResources(
      engineJob: EngineJob): (FileJobResultsWriter, JobResource) = {
    val (writer, resource, wasSetup, logPath) = setupResources(engineJob)
    (writer, resource)
  }

  /**
    * Note, this can be called multiple times from the same job.
    */
  def setupMultiJobResources(engineJob: EngineJob,
                             dao: JobsDao,
                             timeout: FiniteDuration) = {
    val (writer, resource, wasSetup, logPath) = setupResources(engineJob)
    if (!wasSetup) {
      val now = JodaDateTime.now()
      val dataStoreFile = DataStoreFile(
        UUID.randomUUID,
        JobConstants.DATASTORE_FILE_MASTER_LOG_ID,
        FileTypes.LOG.fileTypeId,
        0L,
        now,
        now,
        logPath.toAbsolutePath.toString,
        isChunked = false,
        "Log File",
        "Job Master Log/Stdout File"
      )
      val dataStoreJobFile = DataStoreJobFile(engineJob.uuid, dataStoreFile)

      val result =
        Await.result(dao.addDataStoreFile(dataStoreJobFile), timeout)

      writer.writeLine(
        s"Successfully added DataStore log/stdout file uuid:${dataStoreFile.uniqueId} path:$logPath")

    }

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
