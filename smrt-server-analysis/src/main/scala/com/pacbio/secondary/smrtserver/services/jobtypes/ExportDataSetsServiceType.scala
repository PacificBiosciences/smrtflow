
package com.pacbio.secondary.smrtserver.services.jobtypes

import java.util.UUID
import java.nio.file.{Files, Paths}

import spray.http.MediaTypes
import spray.json._
import spray.httpx.SprayJsonSupport
import SprayJsonSupport._
import com.typesafe.scalalogging.LazyLogging
import akka.actor.ActorRef
import akka.pattern.ask

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels.{EngineJob, JobEvent}
import com.pacbio.secondary.analysis.jobtypes.ExportDataSetsOptions
import com.pacbio.secondary.smrtlink.services.jobtypes.{JobTypeService, InValidJobOptionsError, ValidatorDataSetServicesOptions, ValidateImportDataSetUtils}
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.{EngineManagerActorProvider, JobsDaoActorProvider}
import com.pacbio.secondary.smrtlink.models.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtserver.models.SecondaryAnalysisJsonProtocols
import com.pacbio.secondary.smrtserver.models.SecondaryModels.DataSetExportServiceOptions


object ValidatorDataSetExportServiceOptions extends ValidatorDataSetServicesOptions {

  def apply(opts: DataSetExportServiceOptions): Option[InValidJobOptionsError] = {
    validate(opts)
  }

  def validateOutputPath(path: String): Option[InValidJobOptionsError] = {
    val p = Paths.get(path)
    val dir = p.getParent
    if (p.toFile.exists) Some(InValidJobOptionsError(s"The file ${path} already exists"))
    else if (! dir.toFile.exists) Some(InValidJobOptionsError(s"The directory ${dir.toString} does not exist"))
    else if (! Files.isWritable(dir)) Some(InValidJobOptionsError(s"SMRTLink does not have write permissions for the directory ${dir.toString}"))
    else None
  }

  def validate(opts: DataSetExportServiceOptions): Option[InValidJobOptionsError] = {
    for {
      v1 <- validateDataSetType(opts.datasetType)
      v3 <- validateOutputPath(opts.outputPath)
      v4 <- validateDataSetExists(opts.ids)
    } yield v4
  }

}

class ExportDataSetsServiceJobType(dbActor: ActorRef,
                                   authenticator: Authenticator,
                                   smrtLinkVersion: Option[String],
                                   smrtLinkToolsVersion: Option[String])
    extends JobTypeService with LazyLogging {

  import SecondaryAnalysisJsonProtocols._

  val endpoint = "export-datasets"
  val description = "Export PacBio XML DataSets to ZIP file"

  val routes = 
    pathPrefix(endpoint) {
      pathEndOrSingleSlash {
        get {
          complete {
            jobList(dbActor, endpoint)
          }
        } ~
        post {
          optionalAuthenticate(authenticator.jwtAuth) { authInfo =>
            entity(as[DataSetExportServiceOptions]) { sopts =>

              val uuid = UUID.randomUUID()
              logger.info(s"attempting to create an export-datasets job ${uuid.toString} with options $sopts")

              val fsx = sopts.ids.map(x => ValidateImportDataSetUtils.resolveDataSet(sopts.datasetType, x, dbActor))

              val fx = for {
                uuidPaths <- Future.sequence(fsx).map { f => f.map(sx => (sx.uuid, sx.path)) }
                resolvedPaths <- Future { uuidPaths.map(x => x._2) }
                engineEntryPoints <- Future { uuidPaths.map(x => EngineJobEntryPointRecord(x._1, sopts.datasetType)) }
                exportDataSetOptions <- Future { ExportDataSetsOptions(sopts.datasetType, resolvedPaths, sopts.outputPath) }
                coreJob <- Future { CoreJob(uuid, exportDataSetOptions) }
                engineJob <- (dbActor ? CreateJobType(uuid, s"Job $endpoint", s"Merging Datasets", endpoint, coreJob, Some(engineEntryPoints), exportDataSetOptions.toJson.toString, authInfo.map(_.login), smrtLinkVersion, smrtLinkToolsVersion)).mapTo[EngineJob]
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
