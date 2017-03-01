
package com.pacbio.secondary.smrtlink.services.jobtypes

import java.nio.file.Paths
import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.common.services.PacBioServiceErrors.UnprocessableEntityError
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels.{EngineJob, JobTypeIds}
import com.pacbio.secondary.analysis.jobtypes.DeleteDatasetsOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.SecondaryModels.DataSetDeleteServiceOptions
import com.pacbio.secondary.smrtlink.models.{SecondaryAnalysisJsonProtocols, _}
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.typesafe.scalalogging.LazyLogging
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeleteDataSetsServiceJobType(dbActor: ActorRef,
                                   authenticator: Authenticator,
                                   smrtLinkVersion: Option[String],
                                   smrtLinkToolsVersion: Option[String])
    extends JobTypeService with LazyLogging {

  import CommonModelImplicits._
  import SecondaryAnalysisJsonProtocols._

  val endpoint = JobTypeIds.DELETE_DATASETS.id
  val description = "Delete PacBio XML DataSets and associated resources"

  private def deleteDataSet(ds: ServiceDataSetMetadata): Future[Any] = {
    logger.info(s"Setting isActive=false for dataset ${ds.uuid.toString}")
    logger.info(s"Will remove file(s) at ${ds.path}")
    dbActor ? DeleteDataSetByUUID(ds.uuid)
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

  val routes =
    pathPrefix(endpoint) {
      pathEndOrSingleSlash {
        get {
          parameter('showAll.?) { showAll =>
            complete {
              jobList(dbActor, endpoint, showAll.isDefined)
            }
          }
        } ~
        post {
          optionalAuthenticate(authenticator.wso2Auth) { user =>
            entity(as[DataSetDeleteServiceOptions]) { sopts =>
              if (sopts.datasetType != DataSetMetaTypes.Subread.dsId) {
                throw new UnprocessableEntityError("Only SubreadSets may be deleted at present.")
              }
              val uuid = UUID.randomUUID()
              logger.info(s"attempting to create a delete-datasets job ${uuid.toString} with options $sopts")
              // FIXME too much code duplication here
              val fsx = sopts.ids.map(x => ValidateImportDataSetUtils.resolveDataSet(DataSetMetaTypes.Subread.dsId, x, dbActor))

              val fx = for {
                uuidPaths <- Future.sequence(fsx).map { f => f.map(sx => (sx.uuid, sx.path)) }
                dsJobIds <- Future.sequence(fsx).map { f => f.map(sx => sx.jobId) }
                upstreamDataSets <- getUpstreamDataSets(dsJobIds, DataSetMetaTypes.Subread.dsId)
                resolvedPaths <- Future { uuidPaths.map(x => Paths.get(x._2)) ++ upstreamDataSets.map(ds => Paths.get(ds.path)) }
                engineEntryPoints <- Future { uuidPaths.map(x => EngineJobEntryPointRecord(x._1, sopts.datasetType)) }
                coreJob <- Future { CoreJob(uuid, DeleteDatasetsOptions(resolvedPaths, true)) }
                _ <- Future.sequence(fsx).map { f => f.map(sx => deleteDataSet(sx)) }
                _ <- Future.sequence { upstreamDataSets.map(deleteDataSet(_)) }
                engineJob <- (dbActor ? CreateJobType(uuid, s"Job $endpoint", s"Deleting Datasets", endpoint, coreJob, Some(engineEntryPoints), sopts.toJson.toString, user.map(_.userId), smrtLinkVersion, smrtLinkToolsVersion)).mapTo[EngineJob]
              } yield engineJob

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

trait DeleteDataSetsServiceJobTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with JobManagerServiceProvider with SmrtLinkConfigProvider =>

  val deleteDataSetServiceJobType: Singleton[DeleteDataSetsServiceJobType] =
    Singleton(() => new DeleteDataSetsServiceJobType(jobsDaoActor(), authenticator(), smrtLinkVersion(), smrtLinkToolsVersion())).bindToSet(JobTypes)
}
