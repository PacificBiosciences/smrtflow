package com.pacbio.secondary.smrtserver.services.jobtypes

import java.io.File
import java.net.{URI, URL}
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout
import com.pacbio.common.actors.{UserServiceActorRefProvider, UserServiceActor}
import com.pacbio.common.auth.{AuthenticatorProvider, Authenticator}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.logging.{LoggerFactoryProvider, LoggerFactory}
import com.pacbio.common.models.LogMessageRecord
import com.pacbio.common.services.PacBioServiceErrors.ResourceNotFoundError
import com.pacbio.secondary.analysis.engine.CommonMessages.{CheckForRunnableJob, ImportDataStoreFile, ImportDataStoreFileByJobId}
import com.pacbio.secondary.analysis.engine.EngineConfig
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobtypes.PbSmrtPipeJobOptions
import com.pacbio.secondary.analysis.pbsmrtpipe.{PbsmrtpipeEngineOptions, _}
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.{EngineManagerActorProvider, JobsDaoActorProvider}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.jobtypes.{JobTypeService, ValidateImportDataSetUtils}
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.pacbio.secondary.smrtserver.SmrtServerConstants
import com.pacbio.secondary.smrtserver.models.SecondaryAnalysisJsonProtocols
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.{FileUtils, FilenameUtils}

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal

// For serialization magic. This is required for any serialization in spray to work.

import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.json._


class PbsmrtpipeServiceJobType(dbActor: ActorRef,
                               userActor: ActorRef,
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
  import SmrtServerConstants._

  val endpoint = "pbsmrtpipe"
  val description = "Run a secondary analysis pbsmrtpipe job."

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
            jobList(dbActor, userActor, endpoint)
          }
        } ~
        post {
          optionalAuthenticate(authenticator.jwtAuth) { authInfo =>
            entity(as[PbSmrtPipeServiceOptions]) { ropts =>
              // 0. Validate Pipeline template and entry points are consistent.
              // 1. Resolve Service Entry Points
              //   {
              //     "entryId" : "e_01",
              //     "entryType" : "Pacbio.DataSets.Subreads",
              //     "id" : 1234
              //   } -> {"entryId" : "e_01", "path" : "/path/to/file"}
              // 2. Create a new job in db
              // 3. Create a new CoreJob instance
              // 4. Submit CoreJob to manager
              // FIXME. This should be POST/PUT/GET-able via /jobs/{JOB_TYPE_ID}/settings
              // val envPath = "/Users/mkocher/.virtualenvs/dev_pbsmrtpipe_test/bin/activate"

              val uuid = UUID.randomUUID()
              logger.info(s"Attempting to create pbsmrtpipe Job ${uuid.toString} from service options $ropts")

              val fsx = ropts.entryPoints.map(x => ValidateImportDataSetUtils.resolveDataSet(x.fileTypeId, x.datasetId, dbActor))

              val pathsFs = Future sequence fsx
              val rs = Await.result(pathsFs, 4.seconds)

              val boundEntryPoints = ropts.entryPoints.zip(rs).map(x => BoundEntryPoint(x._1.entryId, x._2.path))
              val engineEntryPts = ropts.entryPoints.zip(rs).map(x => EngineJobEntryPointRecord(x._2.uuid, x._1.fileTypeId))

              logger.info(s"Task options ${ropts.taskOptions}")
              // FIXME This casting issues. Currently it's pushed down to pbsmrtpipe level
              val taskOptions = ropts.taskOptions.map(x => PipelineStrOption(x.id, s"Name ${x.id}", x.value.toString, s"Description ${x.id}"))
              val workflowOptions = pbsmrtpipeEngineOptions.toPipelineOptions

              val serviceUri = toURL(rootUpdateURL, uuid)
              val opts = PbSmrtPipeJobOptions(
                ropts.pipelineId,
                boundEntryPoints,
                taskOptions,
                workflowOptions,
                engineConfig.pbToolsEnv,
                Some(serviceUri),
                commandTemplate)
              val coreJob = CoreJob(uuid, opts)
              val jopts = ropts.toJson

              logger.info("Pbsmrtpipe Service Opts:")
              logger.info(jopts.prettyPrint)
              logger.info(s"Resolved options to $opts")

              val fx = (dbActor ? CreateJobType(
                uuid,
                ropts.name,
                s"pbsmrtpipe ${opts.pipelineId}",
                endpoint,
                coreJob,
                Some(engineEntryPts),
                jopts.toString(),
                authInfo.map(_.login)
              )).mapTo[EngineJob]

              fx.foreach(_ => engineManagerActor ! CheckForRunnableJob)

              complete {
                created {
                  fx
                }
              }
            }
          }
        }
      } ~
      sharedJobRoutes(dbActor, userActor)
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
      with UserServiceActorRefProvider
      with EngineManagerActorProvider
      with LoggerFactoryProvider
      with SmrtLinkConfigProvider
      with JobManagerServiceProvider =>
  val pbsmrtpipeServiceJobType: Singleton[PbsmrtpipeServiceJobType] =
    Singleton(() => new PbsmrtpipeServiceJobType(
      jobsDaoActor(),
      userServiceActorRef(),
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
