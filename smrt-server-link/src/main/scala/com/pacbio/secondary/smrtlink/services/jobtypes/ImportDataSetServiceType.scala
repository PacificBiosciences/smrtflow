package com.pacbio.secondary.smrtlink.services.jobtypes

import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import spray.http.MediaTypes
import spray.json._
import spray.httpx.SprayJsonSupport
import SprayJsonSupport._
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobtypes.ImportDataSetOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.{EngineManagerActorProvider, JobsDaoActorProvider}
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider


class ImportDataSetServiceType(dbActor: ActorRef,
                               authenticator: Authenticator,
                               smrtLinkVersion: Option[String],
                               smrtLinkToolsVersion: Option[String]) extends JobTypeService {

  import SmrtLinkJsonProtocols._

  override val endpoint = "import-dataset"
  override val description = "Import a Pacbio DataSet XML file"


  def validate(sopts: ImportDataSetOptions): Future[ImportDataSetOptions] = {
    Future { ValidateImportDataSetUtils.validateDataSetImportOpts(sopts) }.flatMap {
      case Some(err) => Future.failed(new UnprocessableEntityError(s"Failed to validate dataset $err. Options $sopts"))
      case _ => Future { sopts }
    }
  }

  def createJob(sopts:ImportDataSetOptions, createdBy: Option[String]): Future[EngineJob] = {
    logger.info(s"Attempting to create import-dataset Job with options $sopts")

    val uuid = UUID.randomUUID()
    val desc = s"Importing DataSet"
    val name = s"Job $endpoint"

    val fx = for {
      vopts <- validate(sopts)
      engineJob <- (dbActor ? CreateJobType(uuid, name, desc, endpoint,  CoreJob(uuid, sopts), None, sopts.toJson.toString(), createdBy, smrtLinkVersion, smrtLinkToolsVersion)).mapTo[EngineJob]
    } yield engineJob

    fx
  }


  override val routes =
    pathPrefix(endpoint) {
      pathEndOrSingleSlash {
        get {
          complete {
            jobList(dbActor, endpoint)
          }
        } ~
        post {
          optionalAuthenticate(authenticator.wso2Auth) { authInfo =>
            entity(as[ImportDataSetOptions]) { sopts =>
              complete {
                created {
                  createJob(sopts, authInfo.map(_.login))
                }
              }
            }
          }
        }
      } ~
      sharedJobRoutes(dbActor)
    }
}

trait ImportDataSetServiceTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with JobManagerServiceProvider with SmrtLinkConfigProvider =>

  val importDataSetServiceType: Singleton[ImportDataSetServiceType] =
    Singleton(() => new ImportDataSetServiceType(jobsDaoActor(), authenticator(), smrtLinkVersion(), smrtLinkToolsVersion())).bindToSet(JobTypes)
}
