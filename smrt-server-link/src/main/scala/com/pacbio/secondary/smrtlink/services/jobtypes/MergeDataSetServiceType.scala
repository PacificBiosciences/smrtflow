package com.pacbio.secondary.smrtlink.services.jobtypes

import java.util.UUID
import java.nio.file.{Path,Paths}

import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try, Properties}
import scala.concurrent.ExecutionContext.Implicits.global
import spray.json._
import spray.httpx.SprayJsonSupport
import spray.http.MediaTypes
import SprayJsonSupport._
import com.typesafe.scalalogging.LazyLogging
import akka.actor.ActorRef
import akka.pattern.ask

import com.pacbio.common.auth.{Authenticator, AuthenticatorProvider}
import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.analysis.datasets.io.ImplicitDataSetLoader._
import com.pacbio.secondary.analysis.datasets.validators.ImplicitDataSetValidators._
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels.{EngineJob, JobEvent, JobTypeIds}
import com.pacbio.secondary.analysis.jobtypes.MergeDataSetOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.{EngineManagerActorProvider, JobsDaoActorProvider}
import com.pacbio.secondary.smrtlink.models.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider


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
                            dbActor: ActorRef): Future[Seq[Path]] = {
    val fsx = datasets.map(id => ValidateImportDataSetUtils.resolveDataSet(dsType.dsId, id, dbActor))
    Future.sequence(fsx).map { f => 
      f.map { ds =>
        val path = Paths.get(ds.path)
        if (path.toFile.exists) path else {
          throw InValidJobOptionsError(s"The dataset path $path does not exist")
        }
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

object ValidatorDataSetMergeServiceOptions extends ValidatorDataSetServicesOptions {

  def apply(opts: DataSetMergeServiceOptions,
            dbActor: ActorRef): Future[MergeDataSetOptions] = {
    validate(opts, dbActor)
  }

  def validate(opts: DataSetMergeServiceOptions,
               dbActor: ActorRef): Future[MergeDataSetOptions] = {
    for {
      datasetType <- validateDataSetType(opts.datasetType)
      name <- validateName(opts.name)
      paths <- validateDataSetsExist(opts.ids, datasetType, dbActor)
    //  paths <- validateDataSets(paths, datasetType)
    } yield MergeDataSetOptions(datasetType.dsId, paths.map(_.toString), name)
  }

  def validateName(name: String): Future[String] = Future { name }

}


class MergeDataSetServiceJobType(dbActor: ActorRef,
                                 authenticator: Authenticator,
                                 smrtLinkVersion: Option[String],
                                 smrtLinkToolsVersion: Option[String])
  extends JobTypeService with LazyLogging {

  import SmrtLinkJsonProtocols._

  val endpoint = JobTypeIds.MERGE_DATASETS.id
  val description = "Merge PacBio XML DataSets (Subread, HdfSubread datasets types are supported)"

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
            entity(as[DataSetMergeServiceOptions]) { sopts =>

              val uuid = UUID.randomUUID()
              logger.info(s"attempting to create a merge-dataset job ${uuid.toString} with options $sopts")

              val fsx = sopts.ids.map(x => ValidateImportDataSetUtils.resolveDataSet(sopts.datasetType, x, dbActor))

              val fx = for {
                uuidPaths <- Future.sequence(fsx).map { f => f.map(sx => (sx.uuid, sx.path)) }
                resolvedPaths <- Future { uuidPaths.map(x => x._2) }
                engineEntryPoints <- Future { uuidPaths.map(x => EngineJobEntryPointRecord(x._1, sopts.datasetType)) }
                mergeDataSetOptions <- Future { MergeDataSetOptions(sopts.datasetType, resolvedPaths, sopts.name) }
                coreJob <- Future { CoreJob(uuid, mergeDataSetOptions) }
                engineJob <- (dbActor ? CreateJobType(uuid, s"Job $endpoint", s"Merging Datasets", endpoint, coreJob, Some(engineEntryPoints), mergeDataSetOptions.toJson.toString, user.map(_.userId), smrtLinkVersion, smrtLinkToolsVersion)).mapTo[EngineJob]
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

trait MergeDataSetServiceJobTypeProvider {
  this: JobsDaoActorProvider
    with AuthenticatorProvider
    with JobManagerServiceProvider with SmrtLinkConfigProvider =>

  val mergeDataSetServiceJobType: Singleton[MergeDataSetServiceJobType] =
    Singleton(() => new MergeDataSetServiceJobType(jobsDaoActor(), authenticator(), smrtLinkVersion(), smrtLinkToolsVersion())).bindToSet(JobTypes)
}
