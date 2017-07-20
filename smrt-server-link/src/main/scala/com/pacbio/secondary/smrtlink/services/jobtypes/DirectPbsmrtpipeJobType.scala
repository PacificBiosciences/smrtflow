package com.pacbio.secondary.smrtlink.services.jobtypes

import java.net.{URI, URL}
import java.nio.file.Path
import java.util.UUID

import akka.actor.ActorRef
import akka.pattern._
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.logging.{LoggerFactory, LoggerFactoryProvider}
import com.pacbio.common.models.CommonModels.{UUIDIdAble, IntIdAble}
import com.pacbio.common.models.{CommonModelSpraySupport, LogMessageRecord, UserRecord}
import com.pacbio.secondary.analysis.engine.EngineConfig
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobs.SecondaryJobProtocols._
import com.pacbio.secondary.analysis.jobtypes.PbSmrtPipeJobOptions
import com.pacbio.secondary.analysis.pbsmrtpipe.{PbsmrtpipeEngineOptions, _}
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.SecondaryAnalysisJsonProtocols._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.typesafe.scalalogging.LazyLogging
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DirectPbsmrtpipeJobType(
    dbActor: ActorRef,
    authenticator: Authenticator,
    loggerFactory: LoggerFactory,
    engineConfig: EngineConfig,
    pbsmrtpipeEngineOptions: PbsmrtpipeEngineOptions,
    serviceStatusHost: String,
    port: Int,
    commandTemplate: Option[CommandTemplate] = None,
    smrtLinkVersion: Option[String])
  extends {
    override val endpoint = JobTypeIds.PBSMRTPIPE_DIRECT.id
    override val description =
      """
        |Run a secondary analysis pbsmrtpipe job and by passing the File Resolver. Assumes files are on the Shared
        |FileSystem.
      """.stripMargin
  } with JobTypeService[PbsmrtpipeDirectJobOptions](dbActor, authenticator) with DefaultJsonProtocol with LazyLogging {

  import CommonModelSpraySupport._

  logger.info(s"Pbsmrtpipe job type with Pbsmrtpipe engine options $pbsmrtpipeEngineOptions")

  private val rootUpdateURL = new URL(s"http://$serviceStatusHost:$port/$ROOT_SERVICE_PREFIX/$SERVICE_PREFIX/jobs/pbsmrtpipe")
  private def toURL(baseURL: URL, uuid: UUID): URI = {
    // there has to be a cleaner way to do this
    new URI(s"${baseURL.getProtocol}://${baseURL.getHost}:${baseURL.getPort}${baseURL.getPath}/${uuid.toString}")
  }

  override def createJob(ropts: PbsmrtpipeDirectJobOptions, user: Option[UserRecord]): Future[CreateJobType] = Future {
    val uuid = UUID.randomUUID()
    logger.info(s"Attempting to create pbsmrtpipe Job ${uuid.toString} from service options $ropts")

    logger.info(s"Pbsmrtpipe Service Opts $ropts")
    val jsonSettings = ropts.toJson
    val envPath:Option[Path] = None
    val serviceUri = toURL(rootUpdateURL, uuid)

    val opts = PbSmrtPipeJobOptions(
      ropts.pipelineId,
      ropts.entryPoints,
      ropts.taskOptions,
      ropts.workflowOptions,
      envPath,
      Option(serviceUri),
      projectId = ropts.projectId)

    val coreJob = CoreJob(uuid, opts)
    // Should this be exposed via POST ?
    val name = "Direct Pbsmrtpipe Job"
    // It might be useful for this to try to look up the files by path. For now this is fine.
    val entryPoints: Option[Seq[EngineJobEntryPointRecord]] = None

    CreateJobType(
      uuid,
      name,
      s"pbsmrtpipe ${ropts.pipelineId}",
      endpoint,
      coreJob,
      entryPoints,
      jsonSettings.toString(),
      user.map(_.userId),
      user.flatMap(_.userEmail),
      smrtLinkVersion)
  }

  override def extraRoutes(dbActor: ActorRef, authenticator: Authenticator) =
    path(IdAbleMatcher / LOG_PREFIX) { jobId =>
      post {
        entity(as[LogMessageRecord]) { m =>
          respondWithMediaType(MediaTypes.`application/json`) {
            complete {
              created {
                val f = jobId match {
                  case IntIdAble(n) => Future.successful(n)
                  case UUIDIdAble(_) => (dbActor ? GetJobByIdAble(jobId)).mapTo[EngineJob].map(_.id)
                }
                f.map { intId =>
                  val sourceId = s"job::$intId::${m.sourceId}"
                  loggerFactory.getLogger(LOG_PB_SMRTPIPE_RESOURCE_ID, sourceId).log(m.message, m.level)
                  Map("message" -> s"Successfully logged. $sourceId -> ${m.message}")
                }
              }
            }
          }
        }
      }
    }
}

trait DirectPbsmrtpipeJobTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with LoggerFactoryProvider
    with SmrtLinkConfigProvider
    with JobManagerServiceProvider =>
  val pbsmrtpipeDirectServiceJobType: Singleton[DirectPbsmrtpipeJobType] =
    Singleton(() => new DirectPbsmrtpipeJobType(
      jobsDaoActor(),
      authenticator(),
      loggerFactory(),
      jobEngineConfig(),
      pbsmrtpipeEngineOptions(),
      // When the host is "0.0.0.0", we need to try to resolve the analysis host so that jobs submitted to cluster
      // resources have an endpoint to communicate back with. Note this is not complete, for other cases, such as
      // localhost, they get what they get. For pbsmrtpipe tasks, the error should be clear (enough) in the services-uri
      // in the pbscala.sh
      //
      // Note, that by design, the subprocess or cluster job doesn't need
      // to communicate back to the host (the wrapper Actor will handle updating the final state). However, we want
      // for status messages to be sent back to the Server
      if (host() != "0.0.0.0") host() else java.net.InetAddress.getLocalHost.getCanonicalHostName,
      port(),
      cmdTemplate(),
      smrtLinkVersion()))
      .bindToSet(JobTypes)
}
