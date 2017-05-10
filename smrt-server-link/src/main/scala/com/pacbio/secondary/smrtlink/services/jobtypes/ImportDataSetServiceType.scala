package com.pacbio.secondary.smrtlink.services.jobtypes

import java.util.UUID
import java.nio.file.{Path,Paths}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Try,Success,Failure}

import akka.actor.ActorRef
import akka.pattern.ask
import spray.httpx.SprayJsonSupport._
import spray.json._

import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.{CommonModelImplicits,UserRecord}
import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.common.services.PacBioServiceErrors.{ResourceNotFoundError,UnprocessableEntityError}
import com.pacbio.secondary.analysis.datasets.DataSetFileUtils
import com.pacbio.secondary.analysis.engine.CommonMessages._
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobtypes.ImportDataSetOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.DataSetMetaDataSet
import com.pacbio.secondary.smrtlink.models.SmrtLinkJsonProtocols._
import com.pacbio.secondary.smrtlink.models.SecondaryAnalysisJsonProtocols._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider

class ImportDataSetServiceType(dbActor: ActorRef,
                               authenticator: Authenticator,
                               smrtLinkVersion: Option[String])
  extends {
    override val endpoint = JobTypeIds.IMPORT_DATASET.id
    override val description = "Import a Pacbio DataSet XML file"
  } with JobTypeService[ImportDataSetOptions](dbActor, authenticator)
    with DataSetFileUtils {

  import CommonModelImplicits._

  private def validate(sopts: ImportDataSetOptions): Future[ImportDataSetOptions] = {
    Future { ValidateImportDataSetUtils.validateDataSetImportOpts(sopts) }.flatMap {
      case Some(err) => Future.failed(new UnprocessableEntityError(s"Failed to validate dataset $err. Options $sopts"))
      case _ => Future { sopts }
    }
  }

  // Hack to allow invalid inputs to be passed through to a new job (which will
  // subsequently fail)
  private def getUuid(path: String): UUID = Try {
    dsUuidFromPath(Paths.get(path))
  } match {
    case Success(uuid) => uuid
    case Failure(err: IllegalArgumentException) =>
      throw new ResourceNotFoundError("Not an XML file, will default to standard failure mode")
    case Failure(err: Throwable) => throw err
  }

  private def updateDbIfNecessary(sopts: ImportDataSetOptions): Future[EngineJob] = {
    for {
      uuid <- Future { getUuid(sopts.path) }
      ds <- (dbActor ? GetDataSetMetaByUUID(uuid)).mapTo[DataSetMetaDataSet]
      engineJob <- (dbActor ? GetJobByIdAble(ds.jobId)).mapTo[EngineJob]
      _ <- (dbActor ? UpdateDataStoreFile(uuid, sopts.path, true)).mapTo[MessageResponse]
      msg <- (dbActor ? UpdateDataSetByUUID(uuid, sopts.path, true)).mapTo[MessageResponse]
    } yield engineJob
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

    override def createEngineJob(dbActor: ActorRef,
                                 opts: ImportDataSetOptions,
                                 user: Option[UserRecord]): Future[EngineJob] = {
      updateDbIfNecessary(opts).recoverWith { case err: ResourceNotFoundError =>
        createJob(opts, user).flatMap { c => (dbActor ? c).mapTo[EngineJob] }
      }
  }
}

trait ImportDataSetServiceTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with JobManagerServiceProvider with SmrtLinkConfigProvider =>

  val importDataSetServiceType: Singleton[ImportDataSetServiceType] =
    Singleton(() => new ImportDataSetServiceType(jobsDaoActor(), authenticator(), smrtLinkVersion())).bindToSet(JobTypes)
}
