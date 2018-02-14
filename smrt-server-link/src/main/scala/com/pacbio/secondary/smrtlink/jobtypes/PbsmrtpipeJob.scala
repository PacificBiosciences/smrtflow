package com.pacbio.secondary.smrtlink.jobtypes

import java.net.{URI, URL}
import java.nio.file.Path
import java.util.UUID

import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  JobResultsWriter,
  CoreJobUtils
}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.{
  PbSmrtPipeJobOptions => OldPbSmrtPipeJobOptions
}
import com.pacbio.secondary.smrtlink.models.{
  BoundServiceEntryPoint,
  EngineJobEntryPointRecord
}
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by mkocher on 8/17/17.
  */
case class PbsmrtpipeJobOptions(name: Option[String],
                                description: Option[String],
                                pipelineId: String,
                                entryPoints: Seq[BoundServiceEntryPoint],
                                taskOptions: Seq[ServiceTaskOptionBase],
                                workflowOptions: Seq[ServiceTaskOptionBase],
                                projectId: Option[Int] = Some(
                                  JobConstants.GENERAL_PROJECT_ID))
    extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.PBSMRTPIPE

  override def resolveEntryPoints(
      dao: JobsDao): Seq[EngineJobEntryPointRecord] = {
    val fx = resolver(entryPoints, dao).map(_.map(_._1))
    Await.result(fx, DEFAULT_TIMEOUT)
  }

  override def validate(dao: JobsDao, config: SystemJobConfig) = None
  override def toJob() = new PbsmrtpipeJob(this)
}

class PbsmrtpipeJob(opts: PbsmrtpipeJobOptions)
    extends ServiceCoreJob(opts)
    with JobServiceConstants
    with CoreJobUtils {
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

    val logFile = toSmrtLinkJobLog(logPath)

    val rootUpdateURL = new URL(
      s"http://${config.host}:${config.port}/$ROOT_SA_PREFIX/$JOB_MANAGER_PREFIX/jobs/pbsmrtpipe")

    // These need to be pulled from the System config
    val envPath: Option[Path] = None

    // This needs to be cleaned up
    val serviceURI: Option[URI] = Some(toURL(rootUpdateURL, resources.jobId))

    // Proactively add the datastore file to communicate
    // Resolve Entry Points (with updated paths for SubreadSets)
    val fx: Future[Seq[BoundEntryPoint]] = for {
      _ <- dao.importDataStoreFile(logFile, resources.jobId)
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

    //MK. This isn't the greatest idea to have two different processes writing to the same file handle
    val oldOpts = OldPbSmrtPipeJobOptions(opts.pipelineId,
                                          entryPoints,
                                          opts.taskOptions,
                                          workflowLevelOptions,
                                          envPath,
                                          serviceURI,
                                          None,
                                          Some(logPath),
                                          Some(stdErr),
                                          opts.getProjectId())
    val job = oldOpts.toJob
    job.run(resources, resultsWriter)
  }
}
