package com.pacbio.secondary.smrtlink.analysis.jobtypes

import java.net.URI
import java.nio.file.Path

import com.pacbio.secondary.smrtlink.analysis.externaltools.ExternalToolsUtils
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.JobConstants.GENERAL_PROJECT_ID
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  BaseCoreJob,
  BaseJobOptions,
  _
}
import com.pacbio.secondary.smrtlink.analysis.pbsmrtpipe._
import org.apache.commons.io.FileUtils
import org.joda.time.{DateTime => JodaDateTime}
import spray.json._

import scala.util.Try

/**
  * Contains all the options for an Analysis/Pbsmrtpipe task
  *
  * @param pipelineId      The pbsmrtpipe pipeline Id
  * @param entryPoints     The entry points to the pipeline (these will be validated from pbsmrtpipe on startup)
  * @param taskOptions     The task options for the specific pipeline
  * @param workflowOptions the Workflow Engine level options
  * @param serviceUri      Complete URI to the Update endpoint for the specific job by IdAble
  *                        (e.g., http://my-host:9876/smrt-link/job-manager/jobs/[UUID]
  * @param envPath         No longer supported, should be removed
  * @param commandTemplate No longer supported, should be removed
  * @param projectId       Project Id to assign the output results to
  */
case class PbSmrtPipeJobOptions(
    pipelineId: String,
    entryPoints: Seq[BoundEntryPoint],
    taskOptions: Seq[ServiceTaskOptionBase],
    workflowOptions: Seq[ServiceTaskOptionBase],
    envPath: Option[Path],
    serviceUri: Option[URI],
    commandTemplate: Option[CommandTemplate] = None,
    override val projectId: Int = GENERAL_PROJECT_ID)
    extends BaseJobOptions {

  def toJob = new PbSmrtPipeJob(this)

  override def validate = {

    None
  }
}

object PbsmrtpipeJobUtils {

  final val PBSMRTPIPE_PID_KILL_FILE_SCRIPT = ".pbsmrtpipe-terminate.sh"

  private def resolveTerminateScript(jobDir: Path): Path =
    jobDir.resolve(PBSMRTPIPE_PID_KILL_FILE_SCRIPT)

  def terminateJobFromDir(jobDir: Path) = {
    val cmd =
      Seq("bash", resolveTerminateScript(jobDir).toAbsolutePath.toString)
    ExternalToolsUtils.runCmd(cmd)
  }
}

class PbSmrtPipeJob(opts: PbSmrtPipeJobOptions)
    extends BaseCoreJob(opts: PbSmrtPipeJobOptions)
    with ExternalToolsUtils {

  //FIXME(mpkocher)(2016-10-4) Push these hardcoded values back to a constants layer
  val DEFAULT_STDERR = "job.stderr"
  val DEFAULT_STDOUT = "job.stdout"
  val DEFAULT_JOB_SH = "pbscala-job.sh"

  type Out = PacBioDataStore
  val jobTypeId = JobTypeIds.PBSMRTPIPE

  // For datastore de-serialization

  import SecondaryJobProtocols._

  def run(job: JobResourceBase,
          resultsWriter: JobResultsWriter): Either[ResultFailed, Out] = {
    val startedAt = JodaDateTime.now()

    def writeOptions(opts: Seq[ServiceTaskOptionBase], msg: String): Unit = {
      resultsWriter.writeLine(msg)
      opts
        .map(x => s"${x.id} -> ${x.value}")
        .foreach(resultsWriter.writeLine)
    }

    resultsWriter.writeLine(
      opts.serviceUri
        .map(x => s"Update URL:$x")
        .getOrElse("Updating URL is not configured"))

    writeOptions(opts.workflowOptions, s"PbSmrtPipe job with Engine opts:")
    writeOptions(opts.taskOptions, s"PbSmrtPipe task options:")

    val engineOpts = PbsmrtpipeEngineOptions(opts.workflowOptions)

    // 'Raw' pbsmrtpipe Command without stderr/stdout
    // And will write the preset.json
    val cmd = IOUtils.toCmd(opts.entryPoints,
                            opts.pipelineId,
                            job.path,
                            opts.taskOptions,
                            opts.workflowOptions,
                            opts.serviceUri)

    resultsWriter.writeLine(s"pbsmrtpipe command '$cmd'")

    val wrappedCmd = opts.commandTemplate.map { tp =>
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
                                            opts.envPath)
      resultsWriter.writeLine(
        s"Writing custom wrapper to ${sh.toAbsolutePath.toString}'")
      Seq("bash", sh.toAbsolutePath.toString)
    } getOrElse {
      val sh = IOUtils.writeJobShellWrapper(job.path.resolve(DEFAULT_JOB_SH),
                                            cmd,
                                            opts.envPath)
      Seq("bash", sh.toAbsolutePath.toString)
    }

    val stdoutP = job.path.resolve(DEFAULT_STDOUT)
    val stderrP = job.path.resolve(DEFAULT_STDERR)
    resultsWriter.writeLine(s"Running $wrappedCmd")
    val (exitCode, errorMessage) = runUnixCmd(wrappedCmd, stdoutP, stderrP)
    val runTimeSec = computeTimeDeltaFromNow(startedAt)

    val datastorePath = job.path.resolve("workflow/datastore.json")

    val ds = Try {
      val contents = FileUtils.readFileToString(datastorePath.toFile)
      contents.parseJson.convertTo[PacBioDataStore]
    } getOrElse {
      resultsWriter.writeLine(
        s"[WARNING] Unable to find Datastore from ${datastorePath.toAbsolutePath.toString}")
      PacBioDataStore(startedAt, startedAt, "0.2.1", Seq.empty[DataStoreFile])
    }

    //FIXME(mpkocher)(1-27-2017) These error messages are not great. Try to parse the pbsmrtpipe LOG (or a structure
    // data of the output to get a better error message)
    exitCode match {
      case 0 => Right(ds)
      case 7 =>
        Left(
          ResultFailed(
            job.jobId,
            jobTypeId.toString,
            s"Pbsmrtpipe job ${job.path} failed with exit code 7 (terminated by user). $errorMessage",
            runTimeSec,
            AnalysisJobStates.TERMINATED,
            host
          ))
      case x =>
        Left(ResultFailed(
          job.jobId,
          jobTypeId.toString,
          s"Pbsmrtpipe job ${job.path} failed with exit code $x. $errorMessage",
          runTimeSec,
          AnalysisJobStates.FAILED,
          host))
    }
  }
}
