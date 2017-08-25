package com.pacbio.secondary.smrtlink.services.jobtypes

import java.util.UUID
import java.nio.file.Paths

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try
import akka.actor.ActorRef
import akka.pattern.ask
import spray.httpx.SprayJsonSupport._
import spray.json._
import com.pacbio.secondary.smrtlink.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.{ResourceNotFoundError, UnprocessableEntityError}
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetFileUtils
import com.pacbio.secondary.smrtlink.actors.CommonMessages._
import com.pacbio.secondary.smrtlink.analysis.jobs.CoreJob
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobtypes.ImportDataSetOptions
import com.pacbio.secondary.smrtlink.jobtypes.ImportDataSetJobOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.{DataSetMetaDataSet, UserRecord}
import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.pacbio.secondary.smrtlink.validators.ValidateImportDataSetUtils

class ImportDataSetServiceType(dbActor: ActorRef,
                               authenticator: Authenticator,
                               smrtLinkVersion: Option[String])
  extends {
    override val endpoint = JobTypeIds.IMPORT_DATASET.id
    override val description = "Import a Pacbio DataSet XML file"
  } with JobTypeService[ImportDataSetOptions](dbActor, authenticator)
    with DataSetFileUtils {

  import CommonModelImplicits._

  // This interface should be pushed back to the base class
  private def validate(sopts: ImportDataSetOptions): Future[ImportDataSetOptions] = {
    Future { ValidateImportDataSetUtils.validateDataSetImportOpts(sopts) }.flatMap {
      case Some(err) => Future.failed(new UnprocessableEntityError(s"Failed to validate dataset $err. Options Provided: path=${sopts.path} datasetType=${sopts.datasetType}"))
      case _ => Future { sopts }
    }
  }

  def andLog(sx: String): String = {
    logger.info(sx)
    sx
  }

  private def updateDbIfNecessary(sopts: ImportDataSetOptions): Future[EngineJob] = {
    for {
      dsMini <- Future.fromTry(Try(getDataSetMiniMeta(Paths.get(sopts.path))))
      ds <- (dbActor ? GetDataSetMetaById(dsMini.uuid)).mapTo[DataSetMetaDataSet]
      engineJob <- (dbActor ? GetJobByIdAble(ds.jobId)).mapTo[EngineJob]
      m1 <- (dbActor ? UpdateDataStoreFile(dsMini.uuid, true, Some(sopts.path))).mapTo[MessageResponse]
      m2 <- (dbActor ? UpdateDataSetByUUID(dsMini.uuid, sopts.path, true)).mapTo[MessageResponse]
      _ <- Future.successful(andLog(s"$m1 $m2"))
    } yield engineJob
  }

  override def createJob(sopts: ImportDataSetOptions, user: Option[UserRecord]): Future[CreateJobType] = validate(ImportDataSetOptions(sopts.path, sopts.datasetType)).map { vopts =>
    logger.info(s"Attempting to create import-dataset Job with options $sopts")

    // workaround
    val opts = ImportDataSetOptions(vopts.path, vopts.datasetType)

    val uuid = UUID.randomUUID()
    CreateJobType(
      uuid,
      s"Job $endpoint",
      "Importing DataSet",
      endpoint,
      CoreJob(uuid, opts),
      None,
      sopts.toJson.toString(),
      user.map(_.userId),
      user.flatMap(_.userEmail),
      smrtLinkVersion)
  }

    override def createEngineJob(dbActor: ActorRef,
                                 opts: ImportDataSetOptions,
                                 user: Option[UserRecord]): Future[EngineJob] = {

      val creator = createJob(opts, user).flatMap { c => (dbActor ? c).mapTo[EngineJob]}

      for {
        //_ <- validate(sopts)
        job <- updateDbIfNecessary(opts).recoverWith { case err: ResourceNotFoundError => creator }
      } yield job
  }
}

trait ImportDataSetServiceTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with JobManagerServiceProvider with SmrtLinkConfigProvider =>

  val importDataSetServiceType: Singleton[ImportDataSetServiceType] =
    Singleton(() => new ImportDataSetServiceType(jobsDaoActor(), authenticator(), smrtLinkVersion())).bindToSet(JobTypes)
}
