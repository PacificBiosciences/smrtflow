package com.pacbio.secondary.smrtserver.services.jobtypes

import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask
import com.pacbio.common.actors.{MetricsProvider, Metrics}
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobtypes.MovieMetadataToHdfSubreadOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.{EngineManagerActorProvider, JobsDaoActorProvider}
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.pacbio.secondary.smrtlink.services.jobtypes.JobTypeService
import com.pacbio.secondary.smrtserver.models.SecondaryAnalysisJsonProtocols
import com.typesafe.scalalogging.LazyLogging
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global


class RsConvertMovieToDataSetServiceType(dbActor: ActorRef,
                                         engineManagerActor: ActorRef,
                                         authenticator: Authenticator,
                                         metrics: Metrics) extends JobTypeService with LazyLogging {

  import SecondaryAnalysisJsonProtocols._

  override val endpoint = "convert-rs-movie"
  override val description = "Import RS metadata.xml and create an HdfSubread DataSet XML file"

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
            entity(as[MovieMetadataToHdfSubreadOptions]) { sopts =>
              val uuid = UUID.randomUUID()
              val coreJob = CoreJob(uuid, sopts)
              val jsonSettings = sopts.toJson.toString()
              val fx = metrics(dbActor ? CreateJobType(
                uuid,
                s"Job $endpoint", s"RS movie convert to HdfSubread XML ",
                endpoint,
                coreJob,
                None,
                jsonSettings,
                authInfo.map(_.login))).mapTo[EngineJob]

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

trait RsConvertMovieToDataSetServiceTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with MetricsProvider
    with EngineManagerActorProvider
    with JobManagerServiceProvider =>

  val rsConvertMovieToDataSetServiceType: Singleton[RsConvertMovieToDataSetServiceType] =
    Singleton(() => new RsConvertMovieToDataSetServiceType(
      jobsDaoActor(),
      engineManagerActor(),
      authenticator(),
      metrics())).bindToSet(JobTypes)
}
