package com.pacbio.secondary.smrtlink.services.jobtypes

import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.pacbio.common.actors.{UserServiceActorRefProvider, UserServiceActor}
import com.pacbio.common.auth.{AuthenticatorProvider, Authenticator}
import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.analysis.engine.CommonMessages.CheckForRunnableJob
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels.{JobEvent, PipelineBaseOption, BoundEntryPoint, EngineJob}
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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


class MockPbsmrtpipeJobType(dbActor: ActorRef, userActor: ActorRef, engineManagerActor: ActorRef, authenticator: Authenticator) extends JobTypeService with LazyLogging {

  import SmrtLinkJsonProtocols._

  val endpoint = "mock-pbsmrtpipe"
  val description = "Mock Pbmsrtpipe Job used for Development purposes"

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
    }
}

trait MockPbsmrtpipeJobTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with UserServiceActorRefProvider
    with EngineManagerActorProvider
    with JobManagerServiceProvider =>

  val mockPbsmrtpipeJobType: Singleton[MockPbsmrtpipeJobType] =
    Singleton(() => new MockPbsmrtpipeJobType(jobsDaoActor(), userServiceActorRef(), engineManagerActor(), authenticator())).bindToSet(JobTypes)
}
