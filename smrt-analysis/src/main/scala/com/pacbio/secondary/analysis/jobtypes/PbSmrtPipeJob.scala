package com.pacbio.secondary.analysis.jobtypes

import java.net.URI
import java.nio.file.Path

import com.pacbio.secondary.analysis.externaltools.ExternalToolsUtils

import scala.util.Try
import org.joda.time.{DateTime => JodaDateTime}
import spray.json._
import com.pacbio.secondary.analysis.jobs.{AnalysisJobStates, BaseCoreJob, BaseJobOptions}
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobs._
import com.pacbio.secondary.analysis.pbsmrtpipe._


// Contain for all SmrtpipeJob 'type' options
case class PbSmrtPipeJobOptions(
    pipelineId: String,
    entryPoints: Seq[BoundEntryPoint],
    taskOptions: Seq[ServiceTaskOptionBase],
    workflowOptions: Seq[ServiceTaskOptionBase],
    envPath: Option[Path], serviceUri: Option[URI],
    commandTemplate: Option[CommandTemplate] = None) extends BaseJobOptions {

  def toJob = new PbSmrtPipeJob(this)

  override def validate = {


    None
  }
}

object PbsmrtpipeJobUtils {

  final val PBSMRTPIPE_PID_KILL_FILE_SCRIPT = ".pbsmrtpipe-terminate.sh"

  private def resolveTerminateScript(jobDir: Path): Path = jobDir.resolve(PBSMRTPIPE_PID_KILL_FILE_SCRIPT)

  def terminateJobFromDir(jobDir: Path) = {
    val cmd = Seq("bash", resolveTerminateScript(jobDir).toAbsolutePath.toString)
    ExternalToolsUtils.runCmd(cmd)
  }
}


class PbSmrtPipeJob(opts: PbSmrtPipeJobOptions) extends BaseCoreJob(opts: PbSmrtPipeJobOptions)
with ExternalToolsUtils {

  //FIXME(mpkocher)(2016-10-4) Push these hardcoded values back to a constants layer
  val DEFAULT_STDERR = "job.stderr"
  val DEFAULT_STDOUT = "job.stdout"
  val DEFAULT_JOB_SH = "pbscala-job.sh"

  type Out = PacBioDataStore
  val jobTypeId = JobTypeId("pbsmrtpipe")

  // For datastore de-serialization

  import SecondaryJobProtocols._


  def run(job: JobResourceBase, resultsWriter: JobResultWriter): Either[ResultFailed, Out] = {
    val startedAt = JodaDateTime.now()

    def writer(s: String): Unit = {
      resultsWriter.writeLineStdout(s)
      logger.info(s)
    }

    resultsWriter.writeLineStdout(s"pbsmrtpipe job with Engine opts:")
    opts.workflowOptions.foreach { x => resultsWriter.writeLineStdout(s"${x.id} -> ${x.value}")}

    val engineOpts = PbsmrtpipeEngineOptions(opts.workflowOptions)

    // 'Raw' pbsmrtpipe Command without stderr/stdout
    // And will write the preset.json
    val cmd = IOUtils.toCmd(
      opts.entryPoints,
      opts.pipelineId,
      job.path,
      opts.taskOptions,
      opts.workflowOptions,
      opts.serviceUri)

    writer(s"pbsmrtpipe command '$cmd'")

    val wrappedCmd = opts.commandTemplate.map { tp =>
      val commandJob = CommandTemplateJob(
        s"j${job.jobId.toString}",
        engineOpts.maxNproc,
        job.path.resolve(DEFAULT_STDOUT),
        job.path.resolve(DEFAULT_STDERR), cmd)

      // This resulting string will be exec'ed
      val customCmd = tp.render(commandJob)
      // This should probably use 'exec'
      val execCustomCmd = "eval \"" + customCmd + "\""
      writer(s"Custom command Job $commandJob")
      writer(s"Resolved Custom command template 'pb-cmd-template' to '$execCustomCmd'")
      val sh = IOUtils.writeJobShellWrapper(job.path.resolve(DEFAULT_JOB_SH), execCustomCmd, opts.envPath)
      writer(s"Writing custom wrapper to ${sh.toAbsolutePath.toString}'")
      Seq("bash", sh.toAbsolutePath.toString)
    } getOrElse {
      val sh = IOUtils.writeJobShellWrapper(job.path.resolve(DEFAULT_JOB_SH), cmd, opts.envPath)
      Seq("bash", sh.toAbsolutePath.toString)
    }

    val stdoutP = job.path.resolve(DEFAULT_STDOUT)
    val stderrP = job.path.resolve(DEFAULT_STDERR)
    writer(s"Running $wrappedCmd")
    val (exitCode, errorMessage) = runUnixCmd(wrappedCmd, stdoutP, stderrP)
    val runTimeSec = computeTimeDeltaFromNow(startedAt)

    val datastorePath = job.path.resolve("workflow/datastore.json")

    val ds = Try {
      val source = scala.io.Source.fromFile(datastorePath.toFile)
      val contents = source.mkString("")
      val xs = contents.parseJson
      xs.convertTo[PacBioDataStore]
    } getOrElse {
      writer(s"[WARNING] Unable to find Datastore from ${datastorePath.toAbsolutePath.toString}")
      PacBioDataStore(startedAt, startedAt, "0.2.1", Seq.empty[DataStoreFile])
    }

    //FIXME(mpkocher)(1-27-2017) These error messages are not great. Try to parse the pbsmrtpipe LOG (or a structure
    // data of the output to get a better error message)
    exitCode match {
      case 0 => Right(ds)
      case 7 => Left(ResultFailed(job.jobId, jobTypeId.toString, s"Pbsmrtpipe job ${job.path} failed with exit code 7 (terminated by user). $errorMessage", runTimeSec, AnalysisJobStates.TERMINATED, host))
      case x => Left(ResultFailed(job.jobId, jobTypeId.toString, s"Pbsmrtpipe job ${job.path} failed with exit code $x. $errorMessage", runTimeSec, AnalysisJobStates.FAILED, host))
    }
  }
}

