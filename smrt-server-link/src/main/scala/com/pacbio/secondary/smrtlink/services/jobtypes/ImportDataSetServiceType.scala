package com.pacbio.secondary.smrtlink.services.jobtypes

import java.util.UUID

import akka.actor.ActorRef
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.UserRecord
import com.pacbio.common.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobtypes.ImportDataSetOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.SmrtLinkJsonProtocols._
import com.pacbio.secondary.smrtlink.models.SecondaryAnalysisJsonProtocols._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ImportDataSetServiceType(dbActor: ActorRef,
                               authenticator: Authenticator,
                               smrtLinkVersion: Option[String])
  extends {
    override val endpoint = JobTypeIds.IMPORT_DATASET.id
    override val description = "Import a Pacbio DataSet XML file"
  } with JobTypeService[ImportDataSetOptions](dbActor, authenticator) {

  private def validate(sopts: ImportDataSetOptions): Future[ImportDataSetOptions] = {
    Future { ValidateImportDataSetUtils.validateDataSetImportOpts(sopts) }.flatMap {
      case Some(err) => Future.failed(new UnprocessableEntityError(s"Failed to validate dataset $err. Options $sopts"))
      case _ => Future { sopts }
    }
  }

  override def createJob(sopts: ImportDataSetOptions, user: Option[UserRecord]): Future[CreateJobType] = validate(sopts).map { vopts =>
    logger.info(s"Attempting to create import-dataset Job with options $sopts")

    val uuid = UUID.randomUUID()
    CreateJobType(
      uuid,
      s"Job $endpoint",
      "Importing DataSet",
      endpoint,
      CoreJob(uuid, sopts),
      None,
      sopts.toJson.toString(),
      user.map(_.userId),
      smrtLinkVersion)
  }
}

trait ImportDataSetServiceTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with JobManagerServiceProvider with SmrtLinkConfigProvider =>

  val importDataSetServiceType: Singleton[ImportDataSetServiceType] =
    Singleton(() => new ImportDataSetServiceType(jobsDaoActor(), authenticator(), smrtLinkVersion())).bindToSet(JobTypes)
}
