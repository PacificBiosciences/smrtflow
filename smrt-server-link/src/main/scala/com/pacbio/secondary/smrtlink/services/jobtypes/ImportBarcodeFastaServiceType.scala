package com.pacbio.secondary.smrtlink.services.jobtypes

import java.util.UUID

import akka.actor.ActorRef
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.UserRecord
import com.pacbio.common.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobtypes.ConvertImportFastaBarcodesOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.SecondaryAnalysisJsonProtocols._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.typesafe.scalalogging.LazyLogging
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ImportFastaBarcodesServiceType(
    dbActor: ActorRef,
    authenticator: Authenticator,
    serviceStatusHost: String,
    port: Int,
    smrtLinkVersion: Option[String],
    smrtLinkToolsVersion: Option[String])
  extends {
    override val endpoint = JobTypeIds.CONVERT_FASTA_BARCODES.id
    override val description = "Import fasta reference and create a generated a Reference DataSet XML file."
  } with JobTypeService[ConvertImportFastaBarcodesOptions](dbActor, authenticator) with LazyLogging {

  override def createJob(sopts: ConvertImportFastaBarcodesOptions, user: Option[UserRecord]): Future[CreateJobType] = Future {
    sopts.validate match {
      case Some(e) => throw new UnprocessableEntityError(s"Failed to validate: $e")
      case _ =>
        val uuid = UUID.randomUUID()
        CreateJobType(
          uuid,
          s"Job $endpoint",
          s"Import/Convert Fasta File to Barcode DataSet",
          endpoint,
          CoreJob(uuid, sopts),
          None,
          sopts.toJson.toString(),
          user.map(_.userId),
          smrtLinkVersion,
          smrtLinkToolsVersion)
    }
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
