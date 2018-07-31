package com.pacbio.secondary.smrtlink.jobtypes

import java.net.{URI, URL}
import java.nio.file.Path
import java.util.UUID

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Try, Failure, Success}
import org.apache.commons.io.FileUtils
import org.joda.time.{DateTime => JodaDateTime}
import spray.json._

import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.externaltools.{
  ExternalCmdFailure,
  ExternalToolsUtils
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  CoreJobUtils,
  InvalidJobOptionError,
  JobResultsWriter
}
import com.pacbio.secondary.smrtlink.analysis.pbsmrtpipe._
import com.pacbio.secondary.smrtlink.analysis.reports.{
  ReportJsonProtocol,
  ReportModels
}
import com.pacbio.secondary.smrtlink.models.{
  BoundServiceEntryPoint,
  EngineJobEntryPointRecord
}
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig

/**
  * Created by mkocher on 8/17/17.
  */
case class PbsmrtpipeJobOptions(
    name: Option[String],
    description: Option[String],
    pipelineId: String,
    entryPoints: Seq[BoundServiceEntryPoint],
    taskOptions: Seq[ServiceTaskOptionBase],
    workflowOptions: Seq[ServiceTaskOptionBase],
    projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID),
    submit: Option[Boolean] = Some(JobConstants.SUBMIT_DEFAULT_CORE_JOB),
    tags: Option[String] = None)
    extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.PBSMRTPIPE

  override def subJobTypeId: Option[String] = Some(pipelineId)

  override def resolveEntryPoints(
      dao: JobsDao): Seq[EngineJobEntryPointRecord] = {
    val fx = resolver(entryPoints, dao).map(_.map(_._1))
    Await.result(fx, DEFAULT_TIMEOUT)
  }

  override def validate(
      dao: JobsDao,
      config: SystemJobConfig): Option[InvalidJobOptionError] = {
    Try(resolveEntryPoints(dao)) match {
      case Success(_) => None
      case Failure(ex) =>
        Some(InvalidJobOptionError(s"Invalid options. ${ex.getMessage}"))
    }
  }

  override def toJob() = new PbsmrtpipeJob(this)
}

object PbsmrtpipeJobUtils {

  final val PBSMRTPIPE_PID_KILL_FILE_SCRIPT = ".pbsmrtpipe-terminate.sh"

  private def resolveTerminateScript(jobDir: Path): Path =
    jobDir.resolve(PBSMRTPIPE_PID_KILL_FILE_SCRIPT)

  /**
    * This needs a better error handling.
    *
    * @param jobDir
    * @return
    */
  def terminateJobFromDir(jobDir: Path): Option[ExternalCmdFailure] = {
    val cmd =
      Seq("bash", resolveTerminateScript(jobDir).toAbsolutePath.toString)
    ExternalToolsUtils.runCheckCall(cmd)
  }
}

/** Core functionality for running pbsmrtpipe on the command line.  This is
  * also used by the convert-fasta-reference service.
  *
  */
trait PbsmrtpipeCoreJob
    extends CoreJobUtils
    with ExternalToolsUtils
    with ReportJsonProtocol { this: ServiceCoreJob =>

  import PbsmrtpipeConstants._
  import ReportModels._

  private def parseErrorMessageFromReport(tasksRpt: Path,
                                          errorMessage: String): String = {
    Try {
      val logOut = FileUtils.readFileToString(tasksRpt.toFile, "UTF-8")
      val rpt = logOut.parseJson.convertTo[Report]
      rpt
        .getAttributeValue("pbsmrtpipe.error_message")
        .map(_.asInstanceOf[String])
        .getOrElse(errorMessage)
    }.getOrElse(errorMessage)
  }

  private def loadDataStore(
      datastorePath: Path,
      resultsWriter: JobResultsWriter): PacBioDataStore = {
    Try {
      val contents = FileUtils.readFileToString(datastorePath.toFile)
      contents.parseJson.convertTo[PacBioDataStore]
    } getOrElse {
      resultsWriter.writeLine(
        s"[WARNING] Unable to find Datastore from ${datastorePath.toAbsolutePath.toString}")
      PacBioDataStore.fromFiles(Seq.empty[DataStoreFile])
    }
  }

  protected def runPbsmrtpipe(
      job: JobResourceBase,
      resultsWriter: JobResultsWriter,
      pipelineId: String,
      entryPoints: Seq[BoundEntryPoint],
      taskOptions: Seq[ServiceTaskOptionBase],
      workflowOptions: Seq[ServiceTaskOptionBase],
      envPath: Option[Path],
      serviceUri: Option[URI],
      commandTemplate: Option[CommandTemplate] = None,
      stdOut: Option[Path] = None,
      stdErr: Option[Path] = None): Either[ResultFailed, PacBioDataStore] = {
    val startedAt = JodaDateTime.now()

    def writeOptions(opts: Seq[ServiceTaskOptionBase], msg: String): Unit = {
      val m = opts match {
        case Nil => s"$msg (No Options)"
        case _ =>
          val m1 = opts
            .map(x => s"${x.id} -> ${x.value}")
            .reduce(_ ++ "\n" ++ _)
          s"$msg (${opts.length} Options)\n$m1"
      }
      resultsWriter.writeLine(m)
    }

    resultsWriter.writeLine(
      serviceUri
        .map(x => s"Update URL:$x")
        .getOrElse("Updating URL is not configured"))

    writeOptions(workflowOptions, s"PbSmrtPipe job with Engine opts:")
    writeOptions(taskOptions, s"PbSmrtPipe task options:")

    val engineOpts = PbsmrtpipeEngineOptions(workflowOptions)

    // 'Raw' pbsmrtpipe Command without stderr/stdout
    // And will write the preset.json
    val cmd = IOUtils.toCmd(entryPoints,
                            pipelineId,
                            job.path,
                            taskOptions,
                            workflowOptions,
                            serviceUri)

    resultsWriter.writeLine(s"pbsmrtpipe command '$cmd'")

    val wrappedCmd = commandTemplate.map { tp =>
      val commandJob = CommandTemplateJob(s"j${job.jobId.toString}",
                                          engineOpts.maxNproc,
                                          job.path.resolve(DEFAULT_STDOUT),
                                          job.path.resolve(DEFAULT_STDERR),
                                          cmd)
      // This resulting string will be exec'ed
      val customCmd = tp.render(commandJob)
      // This should probably use 'exec'
      val execCustomCmd = "eval \"" + customCmd + "\""
      resultsWriter.writeLine(s"Custom command Job $commandJob")
      resultsWriter.writeLine(
        s"Resolved Custom command template 'pb-cmd-template' to '$execCustomCmd'")
      val sh = IOUtils.writeJobShellWrapper(job.path.resolve(DEFAULT_JOB_SH),
                                            execCustomCmd,
                                            envPath)
      resultsWriter.writeLine(
        s"Writing custom wrapper to ${sh.toAbsolutePath.toString}'")
      Seq("bash", sh.toAbsolutePath.toString)
    } getOrElse {
      val sh = IOUtils.writeJobShellWrapper(job.path.resolve(DEFAULT_JOB_SH),
                                            cmd,
                                            envPath)
      Seq("bash", sh.toAbsolutePath.toString)
    }

    val stdoutP = stdOut.getOrElse(job.path.resolve(DEFAULT_STDOUT))
    val stderrP = stdErr.getOrElse(job.path.resolve(DEFAULT_STDERR))

    resultsWriter.writeLine(s"Running $wrappedCmd")

    // The errors will be logged at this level, not in subprocess layer
    val cmdResult = runUnixCmd(wrappedCmd, stdoutP, stderrP, logErrors = false)

    cmdResult match {
      case Right(_) =>
        val datastorePath = job.path.resolve("workflow/datastore.json")
        val ds = loadDataStore(datastorePath, resultsWriter)
        Right(ds)
      case Left(cmdFailure) =>
        val failedState: AnalysisJobStates.JobStates =
          cmdFailure.exitCode match {
            case 7 => AnalysisJobStates.TERMINATED
            case _ => AnalysisJobStates.FAILED
          }

        val customFailureMessage = if (cmdFailure.exitCode == 7) {
          s"Pbsmrtpipe job ${job.path} failed with exit code 7 (terminated by user). ${cmdFailure.msg}"
        } else {
          val taskReport = job.path.resolve("workflow/report-tasks.json")
          val pbsmrtpipeError =
            parseErrorMessageFromReport(taskReport, cmdFailure.msg)
          s"Pbsmrtpipe job ${job.path} failed with exit code ${cmdFailure.exitCode}. $pbsmrtpipeError"
        }

        Left(
          ResultFailed(
            job.jobId,
            jobTypeId.toString,
            customFailureMessage,
            cmdFailure.runTime.toInt,
            failedState,
            host
          ))
    }
  }
}

class PbsmrtpipeJob(opts: PbsmrtpipeJobOptions)
    extends ServiceCoreJob(opts)
    with JobServiceConstants
    with PbsmrtpipeCoreJob {
  type Out = PacBioDataStore

  private def toURL(baseURL: URL, uuid: UUID): URI = {
    // there has to be a cleaner way to do this
    new URI(
      s"${baseURL.getProtocol}://${baseURL.getHost}:${baseURL.getPort}${baseURL.getPath}/${uuid.toString}")
  }

  override def run(
      resources: JobResourceBase,
      resultsWriter: JobResultsWriter,
      dao: JobsDao,
      config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {

    val logPath = resources.path.resolve(JobConstants.JOB_STDOUT)
    val stdErr = resources.path.resolve(JobConstants.JOB_STDERR)

    resultsWriter.writeLine(
      s"Starting to run Analysis/pbsmrtpipe Job ${resources.jobId}")

    val rootUpdateURL = new URL(
      s"http://localhost:${config.port}/$ROOT_SA_PREFIX/$JOB_MANAGER_PREFIX/jobs/pbsmrtpipe")

    // These need to be pulled from the System config
    val envPath: Option[Path] = None

    // This needs to be cleaned up
    val serviceURI: Option[URI] = Some(toURL(rootUpdateURL, resources.jobId))

    // Proactively add the datastore file to communicate
    // Resolve Entry Points (with updated paths for SubreadSets)
    val fx: Future[Seq[BoundEntryPoint]] = for {
      logFile <- addStdOutLogToDataStore(resources, dao, opts.projectId)
      entryPoints <- opts.resolver(opts.entryPoints, dao).map(_.map(_._2))
      epUpdated <- Future.sequence {
        entryPoints.map { ep =>
          updateDataSetandWriteToEntryPointsDir(ep.path, resources.path, dao)
            .map(path => ep.copy(path = path))
        }
      }
    } yield epUpdated

    val entryPoints: Seq[BoundEntryPoint] =
      Await.result(fx, opts.DEFAULT_TIMEOUT)

    val workflowLevelOptions =
      config.pbSmrtPipeEngineOptions.toPipelineOptions.map(_.asServiceOption)

    // This is a bit odd of an interface. We currently don't allow users to set system configuration parameters on a
    // per job basis.
    if (opts.workflowOptions.nonEmpty) {
      val msg =
        """WARNING Supplied Workflow level options are not supported on a per job basis.
          |Using system configured workflow level options for workflow engine.
        """.stripMargin
      resultsWriter.writeLine(msg)
    }
    runPbsmrtpipe(resources,
                  resultsWriter,
                  opts.pipelineId,
                  entryPoints,
                  opts.taskOptions,
                  workflowLevelOptions,
                  envPath,
                  serviceURI,
                  None,
                  Some(logPath),
                  Some(stdErr))
  }
}
