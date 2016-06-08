package com.pacbio.secondary.smrtserver.services.jobtypes

import java.net.{URI, URL}
import java.util.UUID

import akka.actor.ActorRef
import akka.pattern._
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.logging.{LoggerFactory, LoggerFactoryProvider}
import com.pacbio.common.models.LogMessageRecord
import com.pacbio.secondary.analysis.engine.EngineConfig
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobs.{CoreJob, SecondaryJobProtocols}
import com.pacbio.secondary.analysis.jobtypes.PbSmrtPipeJobOptions
import com.pacbio.secondary.analysis.pbsmrtpipe.{PbsmrtpipeEngineOptions, _}
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.{EngineManagerActorProvider, JobsDaoActorProvider}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.pacbio.secondary.smrtlink.services.jobtypes.JobTypeService
import com.pacbio.secondary.smrtserver.SmrtServerConstants
import com.pacbio.secondary.smrtserver.models.SecondaryAnalysisJsonProtocols
import com.typesafe.scalalogging.LazyLogging
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global


class DirectPbsmrtpipeJobType(
    dbActor: ActorRef,
    engineManagerActor: ActorRef,
    authenticator: Authenticator,
    loggerFactory: LoggerFactory,
    engineConfig: EngineConfig,
    pbsmrtpipeEngineOptions: PbsmrtpipeEngineOptions,
    serviceStatusHost: String,
    port: Int,
    commandTemplate: Option[CommandTemplate] = None)
  extends JobTypeService with LazyLogging {

  logger.info(s"Pbsmrtpipe job type with Pbsmrtpipe engine options $pbsmrtpipeEngineOptions")

  import SecondaryAnalysisJsonProtocols._
  import SecondaryJobProtocols.directPbsmrtpipeJobOptionsFormat
  import SmrtServerConstants._

  // Not thrilled with this name
  val endpoint = "pbsmrtpipe-direct"
  val description =
    """
      |Run a secondary analysis pbsmrtpipe job and by passing the File Resolver. Assumes files are on the Shared
      |FileSystem.
    """.stripMargin

  val rootUpdateURL = new URL(s"http://$serviceStatusHost:$port/$ROOT_SERVICE_PREFIX/$SERVICE_PREFIX/jobs/pbsmrtpipe")

  def toURL(baseURL: URL, uuid: UUID): URI = {
    // there has to be a cleaner way to do this
    new URI(s"${baseURL.getProtocol}://${baseURL.getHost}:${baseURL.getPort}${baseURL.getPath}/${uuid.toString}")
  }

  val routes =
    pathPrefix(endpoint) {
      pathEndOrSingleSlash {
        get {
          complete {
            jobList(dbActor, endpoint)
          }
        } ~
          post {
            optionalAuthenticate(authenticator.jwtAuth) { authInfo =>
              entity(as[PbsmrtpipeDirectJobOptions]) { ropts =>

                val uuid = UUID.randomUUID()
                logger.info(s"Attempting to create pbsmrtpipe Job ${uuid.toString} from service options $ropts")

                logger.info(s"Pbsmrtpipe Service Opts $ropts")
                val jsonSettings = ropts.toJson
                val envPath = ""
                val serviceUri = toURL(rootUpdateURL, uuid)

                val opts = PbSmrtPipeJobOptions(
                  ropts.pipelineId,
                  ropts.entryPoints,
                  ropts.taskOptions,
                  ropts.workflowOptions,
                  envPath,
                  Option(serviceUri))

                val coreJob = CoreJob(uuid, opts)
                // Should this be exposed via POST ?
                val name = "Direct Pbsmrtpipe Job"
                // It might be useful for this to try to look up the files by path. For now this is fine.
                val entryPoints: Option[Seq[EngineJobEntryPointRecord]] = None

                val fx = (dbActor ? CreateJobType(
                  uuid,
                  name,
                  s"pbsmrtpipe ${ropts.pipelineId}",
                  endpoint,
                  coreJob,
                  entryPoints,
                  jsonSettings.toString(),
                  authInfo.map(_.login)
                )).mapTo[EngineJob]

                complete {
                  created {
                    fx
                  }
                }
              }
            }
          }
      } ~
        sharedJobRoutes(dbActor)
    } ~
      path(endpoint / IntNumber / LOG_PREFIX) { id =>
        post {
          entity(as[LogMessageRecord]) { m =>
            respondWithMediaType(MediaTypes.`application/json`) {
              complete {
                created {
                  val sourceId = s"job::$id::${m.sourceId}"
                  loggerFactory.getLogger(LOG_PB_SMRTPIPE_RESOURCE_ID, sourceId).log(m.message, m.level)
                  Map("message" -> s"Successfully logged. $sourceId -> ${m.message}")
                }
              }
            }
          }
        }
      } ~
      path(endpoint / JavaUUID / LOG_PREFIX) { id =>
        post {
          entity(as[LogMessageRecord]) { m =>
            respondWithMediaType(MediaTypes.`application/json`) {
              complete {
                created {
                  (dbActor ? GetJobByUUID(id)).mapTo[EngineJob].map { engineJob =>
                    val sourceId = s"job::${engineJob.id}::${m.sourceId}"
                    loggerFactory.getLogger(LOG_PB_SMRTPIPE_RESOURCE_ID, sourceId).log(m.message, m.level)
                    // an "ok" message should
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
    with EngineManagerActorProvider
    with LoggerFactoryProvider
    with SmrtLinkConfigProvider
    with JobManagerServiceProvider =>
  val pbsmrtpipeDirectServiceJobType: Singleton[DirectPbsmrtpipeJobType] =
    Singleton(() => new DirectPbsmrtpipeJobType(
      jobsDaoActor(),
      engineManagerActor(),
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
      cmdTemplate())).bindToSet(JobTypes)
}
