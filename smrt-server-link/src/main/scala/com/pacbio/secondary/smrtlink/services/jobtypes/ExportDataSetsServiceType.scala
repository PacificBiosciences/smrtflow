
package com.pacbio.secondary.smrtlink.services.jobtypes

import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask
import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels.{EngineJob, JobTypeIds}
import com.pacbio.secondary.analysis.jobtypes.ExportDataSetsOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.JobsDaoActorProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.models.SecondaryModels.DataSetExportServiceOptions
import com.pacbio.secondary.smrtlink.models.{SecondaryAnalysisJsonProtocols, _}
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
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
                                   smrtLinkVersion: Option[String],
                                   smrtLinkToolsVersion: Option[String])
    extends JobTypeService with LazyLogging {

  import SecondaryAnalysisJsonProtocols._

  val endpoint = JobTypeIds.EXPORT_DATASETS.id
  val description = "Export PacBio XML DataSets to ZIP file"

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
            entity(as[DataSetExportServiceOptions]) { sopts =>

              val uuid = UUID.randomUUID()
              logger.info(s"attempting to create an export-datasets job ${uuid.toString} with options $sopts")
              // FIXME too much code duplication here
              val fsx = sopts.ids.map(x => ValidateImportDataSetUtils.resolveDataSet(sopts.datasetType, x, dbActor))

              val fx = for {
                uuidPaths <- Future.sequence(fsx).map { f => f.map(sx => (sx.uuid, sx.path)) }
                resolvedPaths <- Future { uuidPaths.map(x => x._2) }
                engineEntryPoints <- Future { uuidPaths.map(x => EngineJobEntryPointRecord(x._1, sopts.datasetType)) }
                //engineEntryPoints <- Future { vopts.paths.map(p => EngineJobEntryPointRecord(p, vopts.datasetType.dsId)) }
                vopts <- ValidatorDataSetExportServiceOptions(sopts, dbActor)
                coreJob <- Future { CoreJob(uuid, vopts) }
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

trait ExportDataSetsServiceJobTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with JobManagerServiceProvider with SmrtLinkConfigProvider =>

  val exportDataSetServiceJobType: Singleton[ExportDataSetsServiceJobType] =
    Singleton(() => new ExportDataSetsServiceJobType(jobsDaoActor(), authenticator(), smrtLinkVersion(), smrtLinkToolsVersion())).bindToSet(JobTypes)
}
