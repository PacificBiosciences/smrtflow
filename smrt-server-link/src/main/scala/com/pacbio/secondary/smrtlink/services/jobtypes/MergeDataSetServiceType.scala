package com.pacbio.secondary.smrtlink.services.jobtypes

import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import akka.actor.ActorRef
import com.pacbio.secondary.smrtlink.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.common.models.CommonModels.{IdAble, IntIdAble}
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.CoreJob
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.JobTypeIds
import com.pacbio.secondary.smrtlink.analysis.jobtypes.MergeDataSetOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.SecondaryAnalysisJsonProtocols._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.typesafe.scalalogging.LazyLogging
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// This should be pushed down to pbscala, Replace with scalaz to use a consistent validation model in all packages
case class InValidJobOptionsError(msg: String) extends Exception(msg)


trait ValidatorDataSetServicesOptions {

  import CommonModelImplicits._

  def validateDataSetType(datasetType: String): Future[DataSetMetaTypes.DataSetMetaType] = {
    DataSetMetaTypes.toDataSetType(datasetType)
        .map(d => Future.successful(d))
        .getOrElse(Future.failed(throw InValidJobOptionsError(s"Unsupported dataset type '$datasetType'")))
  }

  def validatePath(serviceDataSet: ServiceDataSetMetadata): Future[ServiceDataSetMetadata] = {
    val p = Paths.get(serviceDataSet.path)
    if (Files.exists(p) && p.toFile.isFile) Future.successful(serviceDataSet)
    else Future.failed(throw new UnprocessableEntityError(s"Unable to find DataSet id:${serviceDataSet.id} UUID:${serviceDataSet.uuid} Path:$p"))
  }

  /**
    * Resolve the DataSet and Validate the Path to the XML file.
    *
    * In a future iteration, this should validate all the external resources in the DataSet XML file
    *
    * @param dsType DataSet MetaType
    * @param dsId DataSet Int
    * @return
    */
  def resolveAndValidatePath(dsType: String, dsId: IdAble, dbActor: ActorRef): Future[ServiceDataSetMetadata] = {

    for {
      dsm <- ValidateImportDataSetUtils.resolveDataSet(dsType, dsId, dbActor)
      validatedDataSet <- validatePath(dsm)
    } yield validatedDataSet

  }

  def validateDataSetsExist(datasets: Seq[Int],
                            dsType: DataSetMetaTypes.DataSetMetaType,
                            dbActor: ActorRef): Future[Seq[ServiceDataSetMetadata]] = {
    Future.sequence(datasets.map(id => resolveAndValidatePath(dsType.dsId, id, dbActor)))
  }

  // FIXME does not compile yet
  /*
  def validateDataSets(datasets: Seq[Path],
                       dsType: DataSetMetaTypes.DataSetMetaType): Future[Seq[Path]] = {
    Try {
      datasets.map { path =>
        val ds = loaderAndResolveType(dsType, path) //DataSetLoader.loadType(dsType, path)
        validator(ds) match {
          case Success(ds) => path
          case Failure(errorsNel) =>
            val msg = errorsNel.list.mkString("; ")
            throw InValidJobOptionsError(s"Failed validation of ${path.toString}: $msg")
        }
      }
    } match {
      case Success(paths) => Future { paths }
      case Failure(err) => Future.failed(err)
    }
  }*/

}


class MergeDataSetServiceJobType(dbActor: ActorRef,
                                 authenticator: Authenticator,
                                 smrtLinkVersion: Option[String])
  extends {
    override val endpoint = JobTypeIds.MERGE_DATASETS.id
    override val description = "Merge PacBio XML DataSets (Subread, HdfSubread datasets types are supported)"
  } with JobTypeService[DataSetMergeServiceOptions](dbActor, authenticator) with ProjectIdJoiner with LazyLogging with ValidatorDataSetServicesOptions{

  override def createJob(sopts: DataSetMergeServiceOptions, user: Option[UserRecord]): Future[CreateJobType] = {
    val uuid = UUID.randomUUID()
    logger.info(s"attempting to create a merge-dataset job ${uuid.toString} with options $sopts")

    Future.sequence(sopts.ids.map(x => resolveAndValidatePath(sopts.datasetType, IntIdAble(x), dbActor))).map { datasets =>
      val mergeDataSetOptions = MergeDataSetOptions(
        sopts.datasetType,
        datasets.map(_.path),
        sopts.name,
        joinProjectIds(datasets.map(_.projectId)))
      CreateJobType(
        uuid,
        s"Job $endpoint", s"Merging ${datasets.length} Datasets",
        endpoint,
        CoreJob(uuid, mergeDataSetOptions),
        Some(datasets.map(ds => EngineJobEntryPointRecord(ds.uuid, sopts.datasetType))),
        mergeDataSetOptions.toJson.toString(),
        user.map(_.userId),
        user.flatMap(_.userEmail),
        smrtLinkVersion)
    }
  }
}

trait MergeDataSetServiceJobTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with JobManagerServiceProvider with SmrtLinkConfigProvider =>

  val mergeDataSetServiceJobType: Singleton[MergeDataSetServiceJobType] =
    Singleton(() => new MergeDataSetServiceJobType(jobsDaoActor(), authenticator(), smrtLinkVersion())).bindToSet(JobTypes)
}
