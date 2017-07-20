package com.pacbio.secondary.smrtlink.services.jobtypes

import java.nio.file.Paths
import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.{CommonModelImplicits, UserRecord}
import com.pacbio.common.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels.{EngineJob, JobTypeIds}
import com.pacbio.secondary.analysis.jobtypes.DeleteDatasetsOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.SecondaryAnalysisJsonProtocols._
import com.pacbio.secondary.smrtlink.models.SecondaryModels.DataSetDeleteServiceOptions
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.typesafe.scalalogging.LazyLogging
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeleteDataSetsServiceJobType(dbActor: ActorRef,
                                   authenticator: Authenticator,
                                   smrtLinkVersion: Option[String])
    extends {
      override val endpoint = JobTypeIds.DELETE_DATASETS.id
      override val description = "Delete PacBio XML DataSets and associated resources"
    } with JobTypeService[DataSetDeleteServiceOptions](dbActor, authenticator) with ProjectIdJoiner with LazyLogging {

  import CommonModelImplicits._

  private def deleteDataSet(ds: ServiceDataSetMetadata): Future[Any] = {
    logger.info(s"Setting isActive=false for dataset ${ds.uuid.toString}")
    logger.info(s"Will remove file(s) at ${ds.path}")
    dbActor ? DeleteDataSetById(ds.uuid)
  }

  private def getUpstreamDataSets(jobIds: Seq[Int], dsMetaType: String): Future[Seq[ServiceDataSetMetadata]] = {
    val fx = for {
      jobs <- Future.sequence { jobIds.map(j => (dbActor ? GetJobByIdAble(j)).mapTo[EngineJob]) }
      entryPoints <- Future.sequence { jobs.filter(_.jobTypeId == "merge-datasets").map { j => (dbActor ? GetEngineJobEntryPoints(j.id)).mapTo[Seq[EngineJobEntryPoint]] } }.map(_.flatten)
      datasets <- Future.sequence { entryPoints.map(ep => ValidateImportDataSetUtils.resolveDataSetByAny(dsMetaType, Right(ep.datasetUUID), dbActor)) }
    } yield datasets
    // TODO logging
    fx
  }

  override def createJob(opts: DataSetDeleteServiceOptions, user: Option[UserRecord]): Future[CreateJobType] =
    // FIXME too much code duplication here
    for {
      uuid <- Future {
        if (opts.datasetType != DataSetMetaTypes.Subread.dsId) {
          throw new UnprocessableEntityError("Only SubreadSets may be deleted at present.")
        }
        val uuid = UUID.randomUUID()
        logger.info(s"attempting to create a delete-datasets job ${uuid.toString} with options $opts")
        uuid
      }
      datasets <- Future.sequence(opts.ids.map(x => ValidateImportDataSetUtils.resolveDataSet(DataSetMetaTypes.Subread.dsId, x, dbActor)))
      upstreamDataSets <- getUpstreamDataSets(datasets.map(_.jobId), DataSetMetaTypes.Subread.dsId)
      allDataSets <- Future { datasets ++ upstreamDataSets }
      _ <- Future.sequence { allDataSets.map(deleteDataSet) }
    } yield CreateJobType(
      uuid,
      s"Job $endpoint",
      s"Deleting Datasets",
      endpoint,
      CoreJob(uuid, DeleteDatasetsOptions(
        allDataSets.map(ds => Paths.get(ds.path)),
        removeFiles = true,
        joinProjectIds(allDataSets.map(_.projectId)))),
      Some(datasets.map(ds => EngineJobEntryPointRecord(ds.uuid, opts.datasetType))),
      opts.toJson.toString(),
      user.map(_.userId),
      user.flatMap(_.userEmail),
      smrtLinkVersion)
}

trait DeleteDataSetsServiceJobTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with JobManagerServiceProvider with SmrtLinkConfigProvider =>

  val deleteDataSetServiceJobType: Singleton[DeleteDataSetsServiceJobType] =
    Singleton(() => new DeleteDataSetsServiceJobType(jobsDaoActor(), authenticator(), smrtLinkVersion())).bindToSet(JobTypes)
}
