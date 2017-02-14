package com.pacbio.secondary.smrtserver.services.jobtypes

import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask
import com.pacbio.common.auth.{AuthenticatorProvider, Authenticator}
import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.analysis.engine.CommonMessages.CheckForRunnableJob
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels.{JobEvent, EngineJob, JobTypeIds}
import com.pacbio.secondary.analysis.jobtypes.SimpleDevJobOptions
import com.pacbio.secondary.smrtlink.actors.{EngineManagerActorProvider, JobsDaoActorProvider}
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.jobtypes.JobTypeService
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.pacbio.secondary.smrtserver.models.SecondaryAnalysisJsonProtocols
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global

import spray.json._
import spray.httpx.SprayJsonSupport._
import spray.http._


class SimpleServiceJobType(dbActor: ActorRef, authenticator: Authenticator) extends JobTypeService with LazyLogging {
  import SecondaryAnalysisJsonProtocols._

  override val endpoint = JobTypeIds.SIMPLE
  override val description = "Simple Job for debugging and development"

  override val routes =
    pathPrefix(endpoint) {
      pathEndOrSingleSlash {
        get {
          // Get All Job types of "Simple"
          complete {
            jobList(dbActor, endpoint)
          }
        } ~
        post {
          optionalAuthenticate(authenticator.wso2Auth) { user =>
            entity(as[SimpleDevJobOptions]) { opts =>
              // 1.  Create a new job in db
              // 2. Create a new CoreJob instance
              // 3. Submit CoreJob to manager
              val uuid = UUID.randomUUID()
              val coreJob = CoreJob(uuid, opts)
              val jsonSettings = opts.toJson.toString()
              logger.info(s"Got options $opts")
              val fx = (dbActor ? CreateJobType(
                uuid,
                s"Job name $endpoint", s"Simple Pipeline ${opts.toString}",
                endpoint,
                coreJob,
                None,
                jsonSettings,
                user.map(_.userId), None, None)).mapTo[EngineJob]

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

trait SimpleServiceJobTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with JobManagerServiceProvider =>

  val simpleServiceJobType: Singleton[SimpleServiceJobType] =
    Singleton(() => new SimpleServiceJobType(jobsDaoActor(), authenticator())).bindToSet(JobTypes)
}
