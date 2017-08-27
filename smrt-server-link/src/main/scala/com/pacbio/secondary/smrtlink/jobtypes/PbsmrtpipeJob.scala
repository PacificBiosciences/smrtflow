
package com.pacbio.secondary.smrtlink.jobtypes

import java.net.{URI, URL}
import java.nio.file.Path
import java.util.UUID

import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.actors.JobsDao
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobResultWriter
import com.pacbio.secondary.smrtlink.analysis.jobtypes.{PbSmrtPipeJobOptions => OldPbSmrtPipeJobOptions}
import com.pacbio.secondary.smrtlink.models.{BoundServiceEntryPoint, EngineJobEntryPointRecord}
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
                                projectId: Option[Int] = Some(JobConstants.GENERAL_PROJECT_ID)) extends ServiceJobOptions {
  override def jobTypeId = JobTypeIds.PBSMRTPIPE

  override def resolveEntryPoints(dao: JobsDao): Seq[EngineJobEntryPointRecord] = {
    val fx = resolver(entryPoints, dao).map(_.map(_._1))
    Await.result(fx, 5.seconds)
  }

  override def validate(dao: JobsDao, config: SystemJobConfig) = None
  override def toJob() = new PbsmrtpipeJob(this)
}

class PbsmrtpipeJob(opts: PbsmrtpipeJobOptions) extends ServiceCoreJob(opts) with JobServiceConstants {
  type Out = PacBioDataStore

  private def toURL(baseURL: URL, uuid: UUID): URI = {
    // there has to be a cleaner way to do this
    new URI(s"${baseURL.getProtocol}://${baseURL.getHost}:${baseURL.getPort}${baseURL.getPath}/${uuid.toString}")
  }

  override def run(resources: JobResourceBase, resultsWriter: JobResultWriter, dao: JobsDao, config: SystemJobConfig): Either[ResultFailed, PacBioDataStore] = {

    val rootUpdateURL = new URL(s"http://${config.host}:${config.port}/$ROOT_SERVICE_PREFIX/$SERVICE_PREFIX/jobs/pbsmrtpipe")

    // These need to be pulled from the System config
    val envPath: Option[Path] = None

    // This needs to be cleaned up
    val serviceURI: Option[URI] = Some(toURL(rootUpdateURL, resources.jobId))

    // Resolve Entry Points
    val fx:Future[Seq[BoundEntryPoint]] = opts.resolver(opts.entryPoints, dao).map(_.map(_._2))
    val entryPoints: Seq[BoundEntryPoint] = Await.result(fx, 5.seconds)

    val oldOpts = OldPbSmrtPipeJobOptions(opts.pipelineId, entryPoints, opts.taskOptions, opts.workflowOptions, envPath, serviceURI, None, opts.getProjectId())
    val job = oldOpts.toJob
    job.run(resources, resultsWriter)
  }
}
