package com.pacbio.secondary.smrtlink.services.jobtypes

import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import akka.actor.ActorRef
import com.pacbio.secondary.smrtlink.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.smrtlink.analysis.jobs.CoreJob
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.JobTypeIds
import com.pacbio.secondary.smrtlink.analysis.jobtypes.ExportDataSetsOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.pacbio.secondary.smrtlink.validators.ValidateImportDataSetUtils
import com.typesafe.scalalogging.LazyLogging
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ValidatorDataSetExportServiceOptions extends ValidatorDataSetServicesOptions with ProjectIdJoiner {

  def validateOutputPath(path: String): Future[Path] = {
    val p = Paths.get(path)
    val dir = p.getParent
    if (p.toFile.exists) Future.failed(new InValidJobOptionsError(s"The file $path already exists"))
    else if (! dir.toFile.exists) Future.failed(new InValidJobOptionsError(s"The directory ${dir.toString} does not exist"))
    else if (! Files.isWritable(dir)) Future.failed(new InValidJobOptionsError(s"SMRTLink does not have write permissions for the directory ${dir.toString}"))
    else Future { p }
  }

  def validate(opts: DataSetExportServiceOptions, dbActor: ActorRef): Future[ExportDataSetsOptions] = {
    for {
      datasetType <- validateDataSetType(opts.datasetType)
      outputPath <- validateOutputPath(opts.outputPath)
      datasets <- validateDataSetsExist(opts.ids, datasetType, dbActor)
      paths <- Future { datasets.map(ds => Paths.get(ds.path)) }
      projectId <- Future { joinProjectIds(datasets.map(_.projectId)) }
    } yield ExportDataSetsOptions(datasetType, paths, outputPath, projectId)
  }

  def apply(opts: DataSetExportServiceOptions, dbActor: ActorRef): Future[ExportDataSetsOptions] = {
    validate(opts, dbActor)
  }
}

class ExportDataSetsServiceJobType(dbActor: ActorRef,
                                   authenticator: Authenticator,
                                   smrtLinkVersion: Option[String])
    extends {
      override val endpoint = JobTypeIds.EXPORT_DATASETS.id
      override val description = "Export PacBio XML DataSets to ZIP file"
    } with JobTypeService[DataSetExportServiceOptions](dbActor, authenticator) with LazyLogging {

  import CommonModelImplicits._

  override def createJob(sopts: DataSetExportServiceOptions, user: Option[UserRecord]): Future[CreateJobType] =
    for {
      uuid <- Future {
        val uuid = UUID.randomUUID()
        logger.info(s"attempting to create an export-datasets job ${uuid.toString} with options $sopts")
        uuid
      }
      datasets <- Future.sequence(sopts.ids.map(x => ValidateImportDataSetUtils.resolveDataSet(sopts.datasetType, x, dbActor)))
      vopts <- ValidatorDataSetExportServiceOptions(sopts, dbActor)
    } yield CreateJobType(
      uuid,
      s"Job $endpoint",
      s"Deleting Datasets",
      endpoint,
      CoreJob(uuid, vopts),
      Some(datasets.map(ds => EngineJobEntryPointRecord(ds.uuid, sopts.datasetType))),
      sopts.toJson.toString(),
      user.map(_.userId),
      user.flatMap(_.userEmail),
      smrtLinkVersion)
}

trait ExportDataSetsServiceJobTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with JobManagerServiceProvider with SmrtLinkConfigProvider =>

  val exportDataSetServiceJobType: Singleton[ExportDataSetsServiceJobType] =
    Singleton(() => new ExportDataSetsServiceJobType(jobsDaoActor(), authenticator(), smrtLinkVersion())).bindToSet(JobTypes)
}
