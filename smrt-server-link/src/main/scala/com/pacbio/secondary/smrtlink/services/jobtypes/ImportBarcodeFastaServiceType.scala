package com.pacbio.secondary.smrtlink.services.jobtypes

import java.util.UUID

import akka.actor.ActorRef
import com.pacbio.secondary.smrtlink.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.models.UserRecord
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.smrtlink.analysis.jobs.CoreJob
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobtypes.ConvertImportFastaBarcodesOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._
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
    smrtLinkVersion: Option[String])
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
          user.flatMap(_.userEmail),
          smrtLinkVersion)
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
      smrtLinkVersion()))
      .bindToSet(JobTypes)
}
