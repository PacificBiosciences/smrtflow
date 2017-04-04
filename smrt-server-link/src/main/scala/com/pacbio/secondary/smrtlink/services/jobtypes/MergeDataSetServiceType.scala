package com.pacbio.secondary.smrtlink.services.jobtypes

import java.nio.file.Paths
import java.util.UUID

import akka.actor.ActorRef
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.UserRecord
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels.JobTypeIds
import com.pacbio.secondary.analysis.jobtypes.MergeDataSetOptions
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
  def validateDataSetType(datasetType: String): Future[DataSetMetaTypes.DataSetMetaType] = {
    DataSetMetaTypes.toDataSetType(datasetType) match {
      case Some(dst) => Future { dst }
      case _ => Future.failed(new InValidJobOptionsError("Unsupported dataset type '$datasetType'"))
    }
  }

  def validateDataSetsExist(datasets: Seq[Int],
                            dsType: DataSetMetaTypes.DataSetMetaType,
                            dbActor: ActorRef): Future[Seq[ServiceDataSetMetadata]] = {
    val fsx = datasets.map(id => ValidateImportDataSetUtils.resolveDataSet(dsType.dsId, id, dbActor))
    Future.sequence(fsx).map { f =>
      f.map { ds =>
        val path = Paths.get(ds.path)
        if (!path.toFile.exists) {
          throw InValidJobOptionsError(s"The dataset path $path does not exist")
        }
        ds
      }
    }
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

object ValidatorDataSetMergeServiceOptions extends ValidatorDataSetServicesOptions with ProjectIdJoiner {

  def apply(opts: DataSetMergeServiceOptions,
            dbActor: ActorRef): Future[MergeDataSetOptions] = {
    validate(opts, dbActor)
  }

  def validate(opts: DataSetMergeServiceOptions,
               dbActor: ActorRef): Future[MergeDataSetOptions] = {
    for {
      datasetType <- validateDataSetType(opts.datasetType)
      name <- validateName(opts.name)
      datasets <- validateDataSetsExist(opts.ids, datasetType, dbActor)
      projectId <- Future { joinProjectIds(datasets.map(_.projectId)) }
    } yield MergeDataSetOptions(datasetType.dsId, datasets.map(_.path), name, projectId)
  }

  def validateName(name: String): Future[String] = Future { name }

}


class MergeDataSetServiceJobType(dbActor: ActorRef,
                                 authenticator: Authenticator,
                                 smrtLinkVersion: Option[String])
  extends {
    override val endpoint = JobTypeIds.MERGE_DATASETS.id
    override val description = "Merge PacBio XML DataSets (Subread, HdfSubread datasets types are supported)"
  } with JobTypeService[DataSetMergeServiceOptions](dbActor, authenticator) with ProjectIdJoiner with LazyLogging {

  override def createJob(sopts: DataSetMergeServiceOptions, user: Option[UserRecord]): Future[CreateJobType] = {
    val uuid = UUID.randomUUID()
    logger.info(s"attempting to create a merge-dataset job ${uuid.toString} with options $sopts")
    Future.sequence(sopts.ids.map(x => ValidateImportDataSetUtils.resolveDataSet(sopts.datasetType, x, dbActor))).map { datasets =>
      val mergeDataSetOptions = MergeDataSetOptions(
        sopts.datasetType,
        datasets.map(_.path),
        sopts.name,
        joinProjectIds(datasets.map(_.projectId)))
      CreateJobType(
        uuid,
        s"Job $endpoint", "Merging Datasets",
        endpoint,
        CoreJob(uuid, mergeDataSetOptions),
        Some(datasets.map(ds => EngineJobEntryPointRecord(ds.uuid, sopts.datasetType))),
        mergeDataSetOptions.toJson.toString(),
        user.map(_.userId),
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
