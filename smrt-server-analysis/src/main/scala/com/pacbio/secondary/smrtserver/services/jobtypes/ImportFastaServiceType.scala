package com.pacbio.secondary.smrtserver.services.jobtypes

import java.net.{URI, URL}
import java.nio.file.Paths
import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask
import com.pacbio.common.actors.{MetricsProvider, Metrics}
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobtypes.{ConvertImportFastaOptions, PbSmrtPipeJobOptions}
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.{EngineManagerActorProvider, JobsDaoActorProvider}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.pacbio.secondary.smrtlink.services.jobtypes.JobTypeService
import com.typesafe.scalalogging.LazyLogging
import spray.json._
import spray.httpx.SprayJsonSupport._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class ImportFastaServiceType(
    dbActor: ActorRef,
    engineManagerActor: ActorRef,
    authenticator: Authenticator,
    metrics: Metrics,
    serviceStatusHost: String,
    port: Int)
  extends JobTypeService with LazyLogging {

  import SmrtLinkJsonProtocols._

  // Max size for a fasta file to converted locally, versus being converted to a pbsmrtpipe cluster task
  // This value probably needs to be tweaked a bit
  final val LOCAL_MAX_SIZE_MB = 50 // this takes about 2.5 minutes

  final val PIPELINE_ID = "pbsmrtpipe.pipelines.sa3_ds_fasta_to_reference"
  final val PIPELINE_ENTRY_POINT_ID = "eid_ref_fasta"

  // Accessible via pbsmrtpipe show-task-details pbcoretools.tasks.fasta_to_reference
  final val OPT_NAME = "pbcoretools.task_options.reference_name"
  final val OPT_ORGANISM = "pbcoretools.task_options.organism"
  final val OPT_PLOIDY = "pbcoretools.task_options.ploidy"

  override val endpoint = "convert-fasta-reference"
  override val description = "Import fasta reference and create a generated a Reference DataSet XML file."

  // There's some common code that needs to be pulled out
  val rootUpdateURL = new URL(s"http://$serviceStatusHost:$port/$ROOT_SERVICE_PREFIX/$SERVICE_PREFIX/jobs/pbsmrtpipe")

  def toURI(baseURL: URL, uuid: UUID): URI = {
    // there has to be a cleaner way to do this
    new URI(s"${baseURL.getProtocol}://${baseURL.getHost}:${baseURL.getPort}${baseURL.getPath}/${uuid.toString}")
  }

  def toPbsmrtPipeJobOptions(opts: ConvertImportFastaOptions, serviceURI: Option[URI]): PbSmrtPipeJobOptions = {

    def toPipelineOption(id: String, value: String) = PipelineStrOption(id, id, value, s"$id description $value")

    val tOpts = Seq((OPT_NAME, opts.name), (OPT_ORGANISM, opts.organism), (OPT_PLOIDY, opts.ploidy))

    val entryPoints = Seq(BoundEntryPoint(PIPELINE_ENTRY_POINT_ID, opts.path))
    val taskOptions = tOpts.map(x => toPipelineOption(x._1, x._2))

    // FIXME. this should be Option[Path] or Option[Map[String, String]]
    val envPath = ""
    PbSmrtPipeJobOptions(PIPELINE_ID, entryPoints, taskOptions, Seq.empty[PipelineBaseOption], envPath, serviceURI)

  }

  def toCoreJob(sopts: ConvertImportFastaOptions, uuid: UUID): CoreJob = {
    val fileSizeMB = Paths.get(sopts.path).toFile.length / 1024 / 1024
    if (fileSizeMB <= LOCAL_MAX_SIZE_MB) CoreJob(uuid, sopts)
    else CoreJob(uuid, toPbsmrtPipeJobOptions(sopts, Option(toURI(rootUpdateURL, uuid))))
  }

  override val routes =
    pathPrefix(endpoint) {
      pathEndOrSingleSlash {
        get {
          complete {
            jobList(dbActor, endpoint, metrics)
          }
        } ~
        post {
          optionalAuthenticate(authenticator.jwtAuth) { authInfo =>
            entity(as[ConvertImportFastaOptions]) { sopts =>
              val uuid = UUID.randomUUID()
              val coreJob = CoreJob(uuid, sopts)
              val comment = s"Import/Convert Fasta File to DataSet"

              val fx = Future {sopts.validate}.flatMap {
                case Some(e) => Future { throw new UnprocessableEntityError(s"Failed to validate: $e") }
                case _ => metrics(dbActor ? CreateJobType(
                  uuid,
                  s"Job $endpoint",
                  comment,
                  endpoint,
                  toCoreJob(sopts, uuid),
                  None,
                  sopts.toJson.toString(),
                  authInfo.map(_.login))).mapTo[EngineJob]
              }

              complete {
                created {
                  fx
                }
              }
            }
          }
        }
      } ~
      sharedJobRoutes(dbActor, metrics)
    }
}

trait ImportFastaServiceTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with MetricsProvider
    with EngineManagerActorProvider
    with SmrtLinkConfigProvider
    with JobManagerServiceProvider =>

  val importFastaServiceType: Singleton[ImportFastaServiceType] =
    Singleton(() => new ImportFastaServiceType(
      jobsDaoActor(),
      engineManagerActor(),
      authenticator(),
      metrics(),
      if (host() != "0.0.0.0") host() else java.net.InetAddress.getLocalHost.getCanonicalHostName,
      port())).bindToSet(JobTypes)
}
