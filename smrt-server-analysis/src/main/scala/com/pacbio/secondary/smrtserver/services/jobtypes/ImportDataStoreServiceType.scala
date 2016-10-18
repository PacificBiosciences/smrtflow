package com.pacbio.secondary.smrtserver.services.jobtypes

import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobtypes.ImportDataStoreOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.jobtypes.JobTypeService
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.pacbio.secondary.smrtserver.models.SecondaryAnalysisJsonProtocols
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import spray.json._
import spray.http._
import spray.httpx.SprayJsonSupport
import SprayJsonSupport._
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider


class ImportDataStoreServiceType(dbActor: ActorRef, authenticator: Authenticator,
                                 smrtLinkVersion: Option[String],
                                 smrtLinkToolsVersion: Option[String])
  extends JobTypeService with LazyLogging {

  import SecondaryAnalysisJsonProtocols._

  override val endpoint = "import-datastore"
  override val description = "Import a PacBio DataStore JSON file"

  override val routes =
    pathPrefix(endpoint) {
      pathEndOrSingleSlash {
        get {
          complete {
            jobList(dbActor, endpoint)
          }
        } ~
        post {
          optionalAuthenticate(authenticator.wso2Auth) { user =>
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
                jsonSettings,
                user.map(_.userName),
                smrtLinkVersion,
                smrtLinkToolsVersion)).mapTo[EngineJob]

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

trait ImportDataStoreServiceTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with JobManagerServiceProvider
    with SmrtLinkConfigProvider =>

  val importDataStoreServiceType: Singleton[ImportDataStoreServiceType] =
    Singleton(() => new ImportDataStoreServiceType(jobsDaoActor(), authenticator(), smrtLinkVersion(), smrtLinkToolsVersion())).bindToSet(JobTypes)
}
