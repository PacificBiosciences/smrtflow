package com.pacbio.secondary.smrtserver.services.jobtypes

import java.net.{URI, URL}
import java.util.UUID

import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout
import com.pacbio.common.auth.{AuthenticatorProvider, Authenticator}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.logging.{LoggerFactoryProvider, LoggerFactory}
import com.pacbio.common.models.LogMessageRecord
import com.pacbio.secondary.analysis.engine.EngineConfig
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobtypes.PbSmrtPipeJobOptions
import com.pacbio.secondary.analysis.pbsmrtpipe.{PbsmrtpipeEngineOptions, _}
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.jobtypes.{JobTypeService, ValidateImportDataSetUtils}
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.pacbio.secondary.smrtserver.SmrtServerConstants
import com.pacbio.secondary.smrtserver.models.SecondaryAnalysisJsonProtocols
import com.typesafe.scalalogging.LazyLogging

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._


// For serialization magic. This is required for any serialization in spray to work.
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.json._


class PbsmrtpipeServiceJobType(
    dbActor: ActorRef,
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
  import SmrtServerConstants._

  val endpoint = "pbsmrtpipe"
  val description = "Run a secondary analysis pbsmrtpipe job."

  val rootUpdateURL = new URL(s"http://$serviceStatusHost:$port/$ROOT_SERVICE_PREFIX/$SERVICE_PREFIX/jobs/pbsmrtpipe")

  def toURL(baseURL: URL, uuid: UUID): URI = {
    // there has to be a cleaner way to do this
    new URI(s"${baseURL.getProtocol}://${baseURL.getHost}:${baseURL.getPort}${baseURL.getPath}/${uuid.toString}")
  }

  /**
    * Util for resolving Entry Points into create an Engine Job
    * @param e BoundServiceEntryPoint
    * @return
    */
  def resolveEntry(e: BoundServiceEntryPoint): Future[(EngineJobEntryPointRecord, BoundEntryPoint)] = {
    ValidateImportDataSetUtils.resolveDataSetByAny(e.fileTypeId, e.datasetId, dbActor).map { d =>
      (EngineJobEntryPointRecord(d.uuid, e.fileTypeId), BoundEntryPoint(e.entryId, d.path))
    }
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
            entity(as[PbSmrtPipeServiceOptions]) { ropts =>

              val uuid = UUID.randomUUID()
              val serviceUri = toURL(rootUpdateURL, uuid)
              logger.info(s"Attempting to create pbsmrtpipe Job ${uuid.toString} from service options $ropts")

              val fx = for {
                taskOptions <- Future { ropts.taskOptions.map(x => PipelineStrOption(x.id, s"Name ${x.id}", x.value.toString, s"Description ${x.id}")) }
                xs <- Future.sequence(ropts.entryPoints.map(resolveEntry))
                boundEntryPoints <- Future { xs.map(_._2) }
                engineJobPoints <- Future { xs.map(_._1) }
                workflowOptions <- Future { pbsmrtpipeEngineOptions.toPipelineOptions }
                opts <- Future { PbSmrtPipeJobOptions(ropts.pipelineId, boundEntryPoints, taskOptions, workflowOptions, engineConfig.pbToolsEnv, Some(serviceUri), commandTemplate)}
                coreJob <- Future { CoreJob(uuid, opts)}
                engineJob <- (dbActor ? CreateJobType(uuid, ropts.name, s"pbsmrtpipe ${opts.pipelineId}", endpoint, coreJob, Some(engineJobPoints), ropts.toJson.toString(), authInfo.map(_.login))).mapTo[EngineJob]
              } yield engineJob

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
      cmdTemplate())).bindToSet(JobTypes)
}
