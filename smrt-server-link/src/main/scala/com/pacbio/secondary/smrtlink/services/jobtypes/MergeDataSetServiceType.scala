package com.pacbio.secondary.smrtlink.services.jobtypes

import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.pacbio.common.actors.{UserServiceActorRefProvider, UserServiceActor}
import com.pacbio.common.auth.{AuthenticatorProvider, Authenticator}
import com.pacbio.common.dependency.Singleton
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.engine.CommonMessages.CheckForRunnableJob
import com.pacbio.secondary.analysis.jobs.CoreJob
import com.pacbio.secondary.analysis.jobs.JobModels.{JobEvent, EngineJob}
import com.pacbio.secondary.analysis.jobtypes.MergeDataSetOptions
import com.pacbio.secondary.smrtlink.actors.JobsDaoActor._
import com.pacbio.secondary.smrtlink.actors.{EngineManagerActorProvider, JobsDaoActorProvider}
import com.pacbio.secondary.smrtlink.models.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.services.JobManagerServiceProvider
import com.typesafe.scalalogging.LazyLogging
import spray.http.MediaTypes

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import spray.json._
import spray.httpx.SprayJsonSupport
import SprayJsonSupport._


// This should be pushed down to pbscala, Replace with scalaz to use a consistent validation model in all packages
case class InValidJobOptionsError(msg: String) extends Exception(msg)


object ValidatorDataSetMergeServiceOptions {

  def apply(opts: DataSetMergeServiceOptions): Option[InValidJobOptionsError] = {
    validate(opts)
  }

  def validate(opts: DataSetMergeServiceOptions): Option[InValidJobOptionsError] = {
    for {
      v1 <- validateDataSetType(opts.datasetType)
      v3 <- validateName(opts.name)
      v4 <- validateDataSetExists(opts.ids)
    } yield v4
  }

  def validateDataSetType(datasetType: String): Option[InValidJobOptionsError] = {
    DataSetMetaTypes.toDataSetType(datasetType) match {
      case Some(x) => None
      case _ => Some(InValidJobOptionsError("Unsupported dataset type '$datasetType'"))
    }
  }

  def validateName(name: String): Option[InValidJobOptionsError] = {
    None
  }

  def validateDataSetExists(ids: Seq[Int]): Option[InValidJobOptionsError] = {
    None
  }

}


class MergeDataSetServiceJobType(dbActor: ActorRef, userActor: ActorRef, engineManagerActor: ActorRef, authenticator: Authenticator)
  extends JobTypeService with LazyLogging {

  import SmrtLinkJsonProtocols._

  val endpoint = "merge-datasets"
  val description = "Merge PacBio XML DataSets (Subread, HdfSubread datasets types are supported)"

  val routes =
    pathPrefix(endpoint) {
      pathEndOrSingleSlash {
        get {
          complete {
            jobList(dbActor, userActor, endpoint)
          }
        } ~
        post {
          optionalAuthenticate(authenticator.jwtAuth) { authInfo =>
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
                engineJob <- (dbActor ? CreateJobType(uuid, s"Job $endpoint", s"Merging Datasets", endpoint, coreJob, Some(engineEntryPoints), mergeDataSetOptions.toJson.toString, authInfo.map(_.login))).mapTo[EngineJob]
              } yield engineJob

              fx.foreach(_ => engineManagerActor ! CheckForRunnableJob)

              complete {
                created {
                  fx.map(job => addUser(userActor, job))
                }
              }
            }
          }
        }
      } ~
      sharedJobRoutes(dbActor, userActor)
    }
}

trait MergeDataSetServiceJobTypeProvider {
  this: JobsDaoActorProvider
      with AuthenticatorProvider
      with UserServiceActorRefProvider
      with EngineManagerActorProvider
      with JobManagerServiceProvider =>

  val mergeDataSetServiceJobType: Singleton[MergeDataSetServiceJobType] =
    Singleton(() => new MergeDataSetServiceJobType(jobsDaoActor(), userServiceActorRef(), engineManagerActor(), authenticator())).bindToSet(JobTypes)
}
