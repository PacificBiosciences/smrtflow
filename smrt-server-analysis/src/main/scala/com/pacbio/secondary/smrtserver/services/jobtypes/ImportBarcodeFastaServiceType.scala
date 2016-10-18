package com.pacbio.secondary.smrtserver.services.jobtypes

import java.net.{URI, URL}
import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.smrtlink.actors.{EngineManagerActorProvider, JobsDaoActorProvider}
import com.pacbio.secondary.smrtlink.services.jobtypes.JobTypeService
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.pacbio.secondary.smrtserver.models.SecondaryAnalysisJsonProtocols
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobtypes.ConvertImportFastaBarcodesOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.models._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import spray.json._
import spray.http.MediaTypes
import spray.httpx.SprayJsonSupport
import SprayJsonSupport._
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider


class ImportFastaBarcodesServiceType(
    dbActor: ActorRef,
    authenticator: Authenticator,
    serviceStatusHost: String,
    port: Int,
    smrtLinkVersion: Option[String],
    smrtLinkToolsVersion: Option[String])
  extends JobTypeService with LazyLogging {

  import SecondaryAnalysisJsonProtocols._

  override val endpoint = "convert-fasta-barcodes"
  override val description = "Import fasta reference and create a generated a Reference DataSet XML file."

  def toURI(baseURL: URL, uuid: UUID): URI = {
    // there has to be a cleaner way to do this
    new URI(s"${baseURL.getProtocol}://${baseURL.getHost}:${baseURL.getPort}${baseURL.getPath}/${uuid.toString}")
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
          optionalAuthenticate(authenticator.wso2Auth) { user =>
            entity(as[ConvertImportFastaBarcodesOptions]) { sopts =>
              val uuid = UUID.randomUUID()
              val coreJob = CoreJob(uuid, sopts)
              val comment = s"Import/Convert Fasta File to Barcode DataSet"

              val fx = Future {sopts.validate}.flatMap {
                case Some(e) => Future { throw new UnprocessableEntityError(s"Failed to validate: $e") }
                case _ => (dbActor ? CreateJobType(
                  uuid,
                  s"Job $endpoint",
                  comment,
                  endpoint,
                  CoreJob(uuid, sopts),
                  None,
                  sopts.toJson.toString(),
                  user.map(_.login),
                  smrtLinkVersion,
                  smrtLinkToolsVersion)).mapTo[EngineJob]
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

trait ImportFastaBarcodesServiceTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with SmrtLinkConfigProvider
    with JobManagerServiceProvider =>

  val importFastaBarcodesServiceType: Singleton[ImportFastaBarcodesServiceType] =
    Singleton(() => new ImportFastaBarcodesServiceType(jobsDaoActor(), authenticator(), if (host() != "0.0.0.0") host() else java.net.InetAddress.getLocalHost.getCanonicalHostName,
      port(),
      smrtLinkVersion(),
      smrtLinkToolsVersion()))
      .bindToSet(JobTypes)
}
