package com.pacbio.secondary.smrtserver.services.jobtypes

import java.util.UUID

import akka.actor.ActorRef
import akka.util.Timeout
import akka.pattern.ask
import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.analysis.engine.CommonMessages.CheckForRunnableJob
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobtypes.MovieMetadataToHdfSubreadOptions
import com.pacbio.secondary.smrtlink.actors.{EngineManagerActorProvider, JobsDaoActorProvider}
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.jobtypes.JobTypeService
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.pacbio.secondary.smrtserver.models.SecondaryAnalysisJsonProtocols
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import spray.http._
import spray.json._
import spray.httpx.SprayJsonSupport
import SprayJsonSupport._


class RsConvertMovieToDataSetServiceType(dbActor: ActorRef, engineManagerActor: ActorRef) extends JobTypeService with LazyLogging {

  import SecondaryAnalysisJsonProtocols._

  override val endpoint = "convert-rs-movie"
  override val description = "Import RS metadata.xml and create an HdfSubread DataSet XML file"

  override val routes =
    pathPrefix(endpoint) {
      pathEndOrSingleSlash {
        get {
          complete {
            (dbActor ? GetJobsByJobType(endpoint)).mapTo[Seq[EngineJob]]
          }
        } ~
        post {
          entity(as[MovieMetadataToHdfSubreadOptions]) { sopts =>
            val uuid = UUID.randomUUID()
            val coreJob = CoreJob(uuid, sopts)
            val jsonSettings = sopts.toJson.toString()
            val fx = (dbActor ? CreateJobType(
              uuid,
              s"Job $endpoint", s"RS movie convert to HdfSubread XML ",
              endpoint,
              coreJob,
              None,
              jsonSettings)).mapTo[EngineJob]

            complete {
              created {
                fx
              }
            }
          }
        }
      } ~
      sharedJobRoutes(dbActor)
    }
}

trait RsConvertMovieToDataSetServiceTypeProvider {
  this: JobsDaoActorProvider
      with EngineManagerActorProvider
      with JobManagerServiceProvider =>

  val rsConvertMovieToDataSetServiceType: Singleton[RsConvertMovieToDataSetServiceType] =
    Singleton(() => new RsConvertMovieToDataSetServiceType(jobsDaoActor(), engineManagerActor())).bindToSet(JobTypes)
}
