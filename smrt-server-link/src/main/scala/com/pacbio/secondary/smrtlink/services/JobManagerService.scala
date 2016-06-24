package com.pacbio.secondary.smrtlink.services

import java.nio.file.Paths

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import com.pacbio.common.actors.ActorSystemProvider
import com.pacbio.common.dependency.{SetBinding, SetBindings, Singleton}
import com.pacbio.common.models.{PacBioComponent, PacBioComponentManifest}
import com.pacbio.common.services.ServiceComposer
import com.pacbio.common.services.utils.{StatusGenerator, StatusGeneratorProvider}
import com.pacbio.secondary.analysis.engine.EngineConfig
import com.pacbio.secondary.analysis.pbsmrtpipe.{CommandTemplate, PbsmrtpipeEngineOptions}
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.jobtypes._
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.routing._
import spray.routing.directives.FileAndResourceDirectives

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


class JobManagerService(
    dbActor: ActorRef,
    statusGenerator: StatusGenerator,
    engineConfig: EngineConfig,
    jobTypes: Set[JobTypeService],
    pbsmrtpipeEngineOptions: PbsmrtpipeEngineOptions,
    pbsmrtpipeCmdTemplate: Option[CommandTemplate],
    port: Int,
    analysisHost: String)(implicit val actorSystem: ActorSystem)
  extends JobService with JobsBaseMicroService with FileAndResourceDirectives {

  import JobsDaoActor._
  import SmrtLinkJsonProtocols._

  override implicit val timeout = Timeout(10.seconds)

  implicit val routing = RoutingSettings.default

  val serviceId = toServiceId("job_manager")
  val deps = Seq(PacBioComponent(toServiceId("status"), "0.1.0"))
  override val manifest = PacBioComponentManifest(
    serviceId,
    "Service Job Manager",
    "0.3.2",
    "Secondary Analysis Job Manager Service to run Job types. See /job-manager/job-types for available job types",
    deps)

  def wrap(t: JobTypeService): Route = pathPrefix(SERVICE_PREFIX / JOB_ROOT_PREFIX) { t.routes }
  val jobServiceTypeRoutes = jobTypes.map(wrap).reduce(_ ~ _)

  val jobTypeEndPoints = jobTypes.map(x => JobTypeEndPoint(x.endpoint, x.description))
  val jobTypeRoutes =
    pathPrefix(SERVICE_PREFIX / JOB_TYPES_PREFIX) {
      get {
        complete {
          jobTypeEndPoints
        }
      }
    }

  val statusRoutes =
    pathPrefix(SERVICE_PREFIX) {
      // TODO(smcclellan): Do we need this endpoint? Why not use the /status endpoint in base-smrt-server?
      path("status") {
        get {
          complete {
            ok {
              statusGenerator.getStatus
            }
          }
        }
      } ~
      pathPrefix(JOB_ROOT_PREFIX) {
        sharedJobRoutes(dbActor)
      }
    }

  // This is for debugging
  val dataStoreFilesRoutes =
    pathPrefix(DATASTORE_FILES_PREFIX) {
      pathEnd {
        get {
          complete {
            (dbActor ? GetDataStoreFiles(1000)).mapTo[Seq[DataStoreServiceFile]]
          }
        }
      } ~
      pathPrefix(JavaUUID) { datastoreFileUUID =>
        pathEnd {
          get {
            complete {
              ok {
                (dbActor ? GetDataStoreFileByUUID(datastoreFileUUID)).mapTo[DataStoreServiceFile]
              }
            }
          }
        } ~
        // TODO(smcclellan): Combine download and resources endpoints?
        path("download") {
          get {
            onSuccess((dbActor ? GetDataStoreFileByUUID(datastoreFileUUID)).mapTo[DataStoreServiceFile]) { file =>
              getFromFile(file.path)
            }
          }
        } ~
        path("resources") {
          get {
            parameter("relpath") { relpath =>
              onSuccess((dbActor ? GetDataStoreFileByUUID(datastoreFileUUID)).mapTo[DataStoreServiceFile]) { file =>
                val resourcePath = Paths.get(file.path).resolveSibling(relpath)
                getFromFile(resourcePath.toFile)
              }
            }
          }
        }
      }
    }

  val engineConfigRoutes =
    pathPrefix(SERVICE_PREFIX / ENGINE_CONFIG_PREFIX) {
      pathEnd {
        get {
          complete {
            engineConfig
          }
        }
      }
    }

  override val routes = statusRoutes ~ engineConfigRoutes ~ jobTypeRoutes ~ dataStoreFilesRoutes ~ jobServiceTypeRoutes
}

trait JobManagerServiceProvider {
  this: SetBindings
    with SmrtLinkConfigProvider
    with JobsDaoActorProvider
    with StatusGeneratorProvider
    with ActorSystemProvider
    with ServiceComposer =>

  val jobManagerService: Singleton[JobManagerService] =
    Singleton{ () =>
      implicit val system = actorSystem()
      new JobManagerService(
        jobsDaoActor(),
        statusGenerator(),
        jobEngineConfig(),
        set(JobTypes),
        pbsmrtpipeEngineOptions(),
        cmdTemplate(),
        port(),
        host())
    }

  object JobTypes extends SetBinding[JobTypeService]

  addService(jobManagerService)
}
