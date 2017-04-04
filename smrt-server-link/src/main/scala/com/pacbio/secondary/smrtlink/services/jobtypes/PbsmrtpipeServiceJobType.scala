package com.pacbio.secondary.smrtlink.services.jobtypes

import java.net.{URI, URL}
import java.nio.file.Paths
import java.util.UUID

import akka.actor.ActorRef
import akka.pattern._
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.logging.{LoggerFactory, LoggerFactoryProvider}
import com.pacbio.common.models.CommonModelSpraySupport
import com.pacbio.common.models.CommonModels.{UUIDIdAble, IntIdAble}
import com.pacbio.common.models.{LogMessageRecord, UserRecord}
import com.pacbio.common.services.PacBioServiceErrors._
import com.pacbio.secondary.analysis.engine.CommonMessages.MessageResponse
import com.pacbio.secondary.analysis.engine.EngineConfig
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobs.{AnalysisJobStates, CoreJob}
import com.pacbio.secondary.analysis.jobtypes.{PbSmrtPipeJobOptions, PbsmrtpipeJobUtils}
import com.pacbio.secondary.analysis.pbsmrtpipe.{PbsmrtpipeEngineOptions, _}
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.SmrtLinkJsonProtocols._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.typesafe.scalalogging.LazyLogging
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class PbsmrtpipeServiceJobType(
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
    override val endpoint = JobTypeIds.PBSMRTPIPE.id
    override val description = "Run a secondary analysis pbsmrtpipe job."
  } with JobTypeService[PbSmrtPipeServiceOptions](dbActor, authenticator) with LazyLogging {

  import CommonModelSpraySupport._

  logger.info(s"Pbsmrtpipe job type with Pbsmrtpipe engine options $pbsmrtpipeEngineOptions")

  val rootUpdateURL = new URL(s"http://$serviceStatusHost:$port/$ROOT_SERVICE_PREFIX/$SERVICE_PREFIX/jobs/pbsmrtpipe")

  private def toURL(baseURL: URL, uuid: UUID): URI = {
    // there has to be a cleaner way to do this
    new URI(s"${baseURL.getProtocol}://${baseURL.getHost}:${baseURL.getPort}${baseURL.getPath}/${uuid.toString}")
  }

  /**
   * Util for resolving Entry Points into create an Engine Job
   * @param e BoundServiceEntryPoint
   */
  private def resolveEntry(e: BoundServiceEntryPoint): Future[(EngineJobEntryPointRecord, BoundEntryPoint)] = {
    ValidateImportDataSetUtils.resolveDataSetByAny(e.fileTypeId, e.datasetId, dbActor).map { d =>
      (EngineJobEntryPointRecord(d.uuid, e.fileTypeId), BoundEntryPoint(e.entryId, d.path))
    }
  }

  private def failIfNotRunning(engineJob: EngineJob): Future[EngineJob] = {
    if (engineJob.isRunning) Future { engineJob }
    else Future.failed(new UnprocessableEntityError(s"Only terminating ${AnalysisJobStates.RUNNING} is supported"))
  }

  /**
   * Hacky Workaround for terminating a job.
   *
   * Only supports jobs in the Running state where pbsmrtpipe has already started.
   */
  private def terminatePbsmrtpipeJob(engineJob: EngineJob): Future[MessageResponse] = {
    for {
      runningJob <- failIfNotRunning(engineJob)
      _ <- Future { PbsmrtpipeJobUtils.terminateJobFromDir(Paths.get(runningJob.path))} // FIXME Handle failure in better well defined model
    } yield MessageResponse(s"Attempting to terminate analysis job ${runningJob.id} in ${runningJob.path}")
  }

  override def createJob(ropts: PbSmrtPipeServiceOptions, user: Option[UserRecord]): Future[CreateJobType] =
    Future.sequence(ropts.entryPoints.map(resolveEntry)).map { xs =>
      val uuid = UUID.randomUUID()
      logger.info(s"Attempting to create pbsmrtpipe Job ${uuid.toString} from service options $ropts")
      val opts = PbSmrtPipeJobOptions(
        ropts.pipelineId,
        xs.map(_._2),
        ropts.taskOptions,
        pbsmrtpipeEngineOptions.toPipelineOptions.map(_.asServiceOption),
        engineConfig.pbToolsEnv,
        Some(toURL(rootUpdateURL, uuid)),
        commandTemplate,
        ropts.projectId)
      CreateJobType(
        uuid,
        ropts.name,
        s"pbsmrtpipe ${ropts.pipelineId}",
        endpoint,
        CoreJob(uuid, opts),
        Some(xs.map(_._1)),
        ropts.toJson.toString(),
        user.map(_.userId),
        smrtLinkVersion)
    }

  override def extraRoutes(dbActor: ActorRef, authenticator: Authenticator) =
    pathPrefix(IdAbleMatcher) { jobId =>
      path(LOG_PREFIX) {
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
      path("terminate") {
        post {
          complete {
            ok {
              for {
                engineJob <- (dbActor ? GetJobByIdAble(jobId)).mapTo[EngineJob]
                message <- terminatePbsmrtpipeJob(engineJob)
              } yield message
            }
          }
        }
      }
    }
}

trait PbsmrtpipeServiceJobTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with LoggerFactoryProvider
    with SmrtLinkConfigProvider
    with JobManagerServiceProvider =>
  val pbsmrtpipeServiceJobType: Singleton[PbsmrtpipeServiceJobType] =
    Singleton(() => new PbsmrtpipeServiceJobType(
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
      cmdTemplate(), smrtLinkVersion())).bindToSet(JobTypes)
}
