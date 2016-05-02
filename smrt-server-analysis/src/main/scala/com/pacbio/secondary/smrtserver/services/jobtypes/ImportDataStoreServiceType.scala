package com.pacbio.secondary.smrtserver.services.jobtypes

import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.analysis.engine.CommonMessages.CheckForRunnableJob
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobtypes.ImportDataStoreOptions
import com.pacbio.secondary.smrtlink.actors.{EngineManagerActorProvider, JobsDaoActorProvider}
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.jobtypes.JobTypeService
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.pacbio.secondary.smrtserver.models.SecondaryAnalysisJsonProtocols
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import spray.json._
import spray.http._
import spray.httpx.SprayJsonSupport
import SprayJsonSupport._


class ImportDataStoreServiceType(dbActor: ActorRef, engineManagerActor: ActorRef)
  extends JobTypeService with LazyLogging {

  import SecondaryAnalysisJsonProtocols._

  override val endpoint = "import-datastore"
  override val description = "Import a PacBio DataStore JSON file"

  override val routes =
    pathPrefix(endpoint) {
      pathEndOrSingleSlash {
        get {
          complete {
            (dbActor ? GetJobsByJobType(endpoint)).mapTo[Seq[EngineJob]]
          }
        } ~
        post {
          entity(as[ImportDataStoreOptions]) { sopts =>
            val uuid = UUID.randomUUID()
            val coreJob = CoreJob(uuid, sopts)
            val jsonSettings = sopts.toJson.toString()
            val fx = (dbActor ? CreateJobType(
              uuid,
              s"Job $endpoint",
              s"Importing DataStore",
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

trait ImportDataStoreServiceTypeProvider {
  this: JobsDaoActorProvider
      with EngineManagerActorProvider
      with JobManagerServiceProvider =>

  val importDataStoreServiceType: Singleton[ImportDataStoreServiceType] =
    Singleton(() => new ImportDataStoreServiceType(jobsDaoActor(), engineManagerActor())).bindToSet(JobTypes)
}
