package com.pacbio.secondary.smrtlink.services.jobtypes

import java.net.{URI, URL}
import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.actor.ActorRef
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.UserRecord
import com.pacbio.common.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobtypes.{ConvertImportFastaOptions, PbSmrtPipeJobOptions}
import com.pacbio.secondary.analysis.pbsmrtpipe.PbsmrtpipeEngineOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.SecondaryAnalysisJsonProtocols._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.typesafe.scalalogging.LazyLogging
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ImportFastaServiceType(
    dbActor: ActorRef,
    authenticator: Authenticator,
    pbsmrtpipeEngineOptions: PbsmrtpipeEngineOptions,
    serviceStatusHost: String,
    port: Int,
    smrtLinkVersion: Option[String])
  extends {
    override val endpoint = JobTypeIds.CONVERT_FASTA_REFERENCE.id
    override val description = "Import fasta reference and create a generated a Reference DataSet XML file."
  } with JobTypeService[ConvertImportFastaOptions](dbActor, authenticator) with LazyLogging {

  // Max size for a fasta file to converted locally, versus being converted to a pbsmrtpipe cluster task
  // This value probably needs to be tweaked a bit
  final val LOCAL_MAX_SIZE_MB = 50 // this takes about 2.5 minutes

  final val PIPELINE_ID = "pbsmrtpipe.pipelines.sa3_ds_fasta_to_reference"
  final val PIPELINE_ENTRY_POINT_ID = "eid_ref_fasta"

  // Accessible via pbsmrtpipe show-task-details pbcoretools.tasks.fasta_to_reference
  final val OPT_NAME = "pbcoretools.task_options.reference_name"
  final val OPT_ORGANISM = "pbcoretools.task_options.organism"
  final val OPT_PLOIDY = "pbcoretools.task_options.ploidy"

  // There's some common code that needs to be pulled out
  private val rootUpdateURL = new URL(s"http://$serviceStatusHost:$port/$ROOT_SERVICE_PREFIX/$SERVICE_PREFIX/jobs/pbsmrtpipe")

  private def toURI(baseURL: URL, uuid: UUID): URI = {
    // there has to be a cleaner way to do this
    new URI(s"${baseURL.getProtocol}://${baseURL.getHost}:${baseURL.getPort}${baseURL.getPath}/${uuid.toString}")
  }

  private def toPbsmrtPipeJobOptions(opts: ConvertImportFastaOptions, serviceURI: Option[URI]): PbSmrtPipeJobOptions = {

    def toPipelineOption(id: String, value: String) = ServiceTaskStrOption(id, value)

    val tOpts = Seq((OPT_NAME, opts.name), (OPT_ORGANISM, opts.organism), (OPT_PLOIDY, opts.ploidy))

    val entryPoints = Seq(BoundEntryPoint(PIPELINE_ENTRY_POINT_ID, opts.path))
    val taskOptions = tOpts.map(x => toPipelineOption(x._1, x._2))

    // FIXME. this should be Option[Path] or Option[Map[String, String]]
    val envPath: Option[Path] = None
    PbSmrtPipeJobOptions(PIPELINE_ID, entryPoints, taskOptions, pbsmrtpipeEngineOptions.toPipelineOptions.map(_.asServiceOption), envPath, serviceURI, projectId = opts.projectId)

  }

  private def toCoreJob(sopts: ConvertImportFastaOptions, uuid: UUID): CoreJob = {
    val fileSizeMB = Paths.get(sopts.path).toFile.length / 1024 / 1024
    if (fileSizeMB <= LOCAL_MAX_SIZE_MB) CoreJob(uuid, sopts)
    else CoreJob(uuid, toPbsmrtPipeJobOptions(sopts, Option(toURI(rootUpdateURL, uuid))))
  }

  override def createJob(sopts: ConvertImportFastaOptions, user: Option[UserRecord]): Future[CreateJobType] = Future {
    sopts.validate match {
      case Some(e) => throw new UnprocessableEntityError(s"Failed to validate: $e")
      case _ =>
        val uuid = UUID.randomUUID()
        CreateJobType(
          uuid,
          s"Job $endpoint",
          "Import/Convert Fasta File to DataSet",
          endpoint,
          toCoreJob(sopts, uuid),
          None,
          sopts.toJson.toString(),
          user.map(_.userId),
          user.flatMap(_.userEmail),
          smrtLinkVersion)
    }
  }
}

trait ImportFastaServiceTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with SmrtLinkConfigProvider
    with JobManagerServiceProvider =>

  val importFastaServiceType: Singleton[ImportFastaServiceType] =
    Singleton(() => new ImportFastaServiceType(jobsDaoActor(), authenticator(), pbsmrtpipeEngineOptions(), if (host() != "0.0.0.0") host() else java.net.InetAddress.getLocalHost.getCanonicalHostName,
      port(), smrtLinkVersion()))
      .bindToSet(JobTypes)
}
