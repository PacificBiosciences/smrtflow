package com.pacbio.secondary.smrtlink.services.jobtypes

import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.analysis.engine.CommonMessages.CheckForRunnableJob
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels.{BoundEntryPoint, EngineJob, JobEvent, PipelineBaseOption}
import com.pacbio.secondary.analysis.jobtypes.MockPbSmrtPipeJobOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.{EngineManagerActorProvider, JobsDaoActorProvider}
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.typesafe.scalalogging.LazyLogging
import spray.json._
import spray.http.MediaTypes
import spray.httpx.SprayJsonSupport
import SprayJsonSupport._
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


class MockPbsmrtpipeJobType(dbActor: ActorRef,
                            authenticator: Authenticator,
                            smrtLinkVersion: Option[String],
                            smrtLinkToolsVersion: Option[String]) extends JobTypeService with LazyLogging {

  import SmrtLinkJsonProtocols._

  val endpoint = "mock-pbsmrtpipe"
  val description = "Mock Pbmsrtpipe Job used for Development purposes"

  val routes =
    pathPrefix(endpoint) {
      pathEndOrSingleSlash {
        get {
          parameter('showAll.?) { showAll =>
            complete {
              jobList(dbActor, endpoint, showAll.isDefined)
            }
          }
        } ~
        post {
          optionalAuthenticate(authenticator.wso2Auth) { user =>
            entity(as[PbSmrtPipeServiceOptions]) { ropts =>
              // 0. Mock Validation of inputs (skip this for now)
              // 1.  Create a new job in db
              // 2. Create a new CoreJob instance
              // 3. Submit CoreJob to manager
              val uuid = UUID.randomUUID()
              val entryPoints = ropts.entryPoints.map(x => BoundEntryPoint(x.entryId, "/tmp/file.fasta"))
              val taskOptions = Seq[PipelineBaseOption]()
              val workflowOptions = Seq[PipelineBaseOption]()
              val envPath = ""
              val opts = MockPbSmrtPipeJobOptions(ropts.pipelineId, entryPoints, taskOptions, workflowOptions, envPath)
              val coreJob = CoreJob(uuid, opts)
              logger.info(s"Got options $opts")
              val jsonSettings = ropts.toJson.toString()
              val fx = (dbActor ? CreateJobType(
                uuid,
                ropts.name,
                s"Mock pbsmrtpipe Pipeline ${opts.toString}",
                endpoint,
                coreJob,
                None,
                jsonSettings,
                user.map(_.userId),
                smrtLinkVersion,
                smrtLinkToolsVersion
              )).mapTo[EngineJob]

              fx.onSuccess {
                case _ => dbActor ! CheckForRunnableJob
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
      sharedJobRoutes(dbActor)
    }
}

trait MockPbsmrtpipeJobTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with JobManagerServiceProvider with SmrtLinkConfigProvider =>

  val mockPbsmrtpipeJobType: Singleton[MockPbsmrtpipeJobType] =
    Singleton(() => new MockPbsmrtpipeJobType(jobsDaoActor(), authenticator(), smrtLinkVersion(), smrtLinkToolsVersion())).bindToSet(JobTypes)
}
