package com.pacbio.secondary.smrtlink.services

import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import scala.reflect.ClassTag

import shapeless.HNil
import spray.httpx.marshalling.Marshaller
import spray.routing.{PathMatcher1, Route}
import spray.http.MediaTypes
import spray.json._
import spray.httpx.SprayJsonSupport
import SprayJsonSupport._


import com.pacbio.common.auth.{AuthenticatorProvider, Authenticator}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.{UserRecord, PacBioComponentManifest}
import com.pacbio.common.services.ServiceComposer
import com.pacbio.common.services.PacBioServiceErrors.{ResourceNotFoundError,MethodNotImplementedError}
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.engine.CommonMessages._
import com.pacbio.secondary.smrtlink.SmrtLinkConstants
import com.pacbio.secondary.smrtlink.actors.{JobsDaoActor, JobsDaoActorProvider}
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.jobs.JobModels.EngineJob
import com.pacbio.common.models.CommonModels._
import com.pacbio.common.models.CommonModelSpraySupport._


/**
 * Accessing DataSets by type. Currently several datasets types are
 * not completely supported (ContigSet, CCSreads, CCS Alignments)
 */
class DataSetService(dbActor: ActorRef, authenticator: Authenticator) extends JobsBaseMicroService with SmrtLinkConstants {
  // For all the Message types

  import JobsDaoActor._

  // For all the serialzation protocols

  import SmrtLinkJsonProtocols._

  val manifest = PacBioComponentManifest(
    toServiceId("smrtlink.dataset"),
    "SMRT Link DataSetService Service",
    "0.1.0",
    "SMRT Link Analysis DataSet Service")

  val DATASET_TYPES_PREFIX = "dataset-types"
  val DATASET_PREFIX = "datasets"
  val DETAILS_PREFIX = "details"

  // Default MAX number of records to return
  val DS_LIMIT = 2000

  val shortNameRx = {
    val xs = DataSetMetaTypes.ALL.map(_.shortName + "$").reduceLeft((a, c) => s"$a|$c")
    ("(" + xs + ")").r
  }

  // - If a projectId is provided, return only that Id.
  // - If a projectId is not provided, but a user is logged in, return all projectIds associated with
  // that user, plus the general project id.
  // - If a projectId is not provided, and no user is logged in, return an empty projectId list.
  def getProjectIds(projectId: Option[Int], user: Option[UserRecord]): Future[Seq[Int]] =
    (projectId, user) match {
      case (Some(id), _) => Future(Seq(id))
      case (None, Some(u)) => (dbActor ? GetUserProjects(u.userId))
        .mapTo[Seq[UserProjectResponse]]
        .map(_.map(_.project.id) :+ GENERAL_PROJECT_ID)
      case (None, None) => Future(Nil)
    }

  def datasetRoutes[R <: ServiceDataSetMetadata](
      shortName: String,
      GetDataSets: (Int, Boolean, Seq[Int]) => Any,
      GetDataSetById: IdAble => Any,
      GetDetailsById: IdAble => Any)(
      implicit ct: ClassTag[R],
      ma: Marshaller[R],
      sm: Marshaller[Seq[R]]): Route =
    optionalAuthenticate(authenticator.wso2Auth) { user =>
      pathPrefix(shortName) {
        pathEnd {
          get {
            parameters('showAll.?, 'projectId.as[Int].?) { (showAll, projectId) =>
              complete {
                ok {
                  getProjectIds(projectId, user).flatMap { ids =>
                    (dbActor ? GetDataSets(DS_LIMIT, showAll.isDefined, ids)).mapTo[Seq[R]]
                  }
                }
              }
            }
          }
        } ~
        pathPrefix(IdAbleMatcher) { id =>
          pathEnd {
            get {
              complete {
                ok {
                  (dbActor ? GetDataSetById(id)).mapTo[R]
                }
              }
            } ~
            put {
              entity(as[DataSetUpdateRequest]) { sopts =>
                complete {
                  ok {
                    if (sopts.isActive) {
                      throw new MethodNotImplementedError("Undelete of datasets not supported - please use 'dataset newuuid' to set a new UUID and re-import.")
                    } else {
                      (dbActor ? DeleteDataSetById(id)).mapTo[MessageResponse]
                    }
                  }
                }
              }
            }
          } ~
          path(DETAILS_PREFIX) {
            get {
              respondWithMediaType(MediaTypes.`application/json`) {
                complete {
                  ok {
                    (dbActor ? GetDetailsById(id)).mapTo[String]
                  }
                }
              }
            }
          } ~
          path(JOB_REPORT_PREFIX) {
            get {
              complete {
                ok {
                  val dataset: Future[R] = (dbActor ? GetDataSetById(id)).mapTo[R]
                  val reports: Future[Seq[DataStoreReportFile]] = dataset.flatMap { s =>
                    (dbActor ? GetDataStoreReportFilesByJobId(s.jobId)).mapTo[Seq[DataStoreReportFile]]
                  }
                  reports
                }
              }
            }
          }
        }
      }
    }


  val routes =
    pathPrefix(DATASET_TYPES_PREFIX) {
      pathEnd {
        get {
          complete {
            ok {
              (dbActor ? GetDataSetTypes).mapTo[Seq[ServiceDataSetMetaType]]
            }
          }
        }
      } ~
      path(shortNameRx) { shortName =>
        get {
          complete {
            ok {
              DataSetMetaTypes.fromShortName(shortName)
                .map(t => (dbActor ? GetDataSetTypeById(t.dsId)).mapTo[ServiceDataSetMetaType])
                .getOrElse(throw new ResourceNotFoundError(s"Unable to find dataset type Id '$shortName"))
            }
          }
        }
      }
    } ~
    pathPrefix(DATASET_PREFIX) {
      path(IdAbleMatcher) { id =>
        get {
          complete {
            ok {
              (dbActor ? GetDataSetMetaById(id)).mapTo[DataSetMetaDataSet]
            }
          }
        } ~
        put {
          entity(as[DataSetUpdateRequest]) { sopts =>
            complete {
              ok {
                if (sopts.isActive) {
                  throw new MethodNotImplementedError("Undelete of datasets not supported - please use 'dataset newuuid' to set a new UUID and re-import.")
                } else {
                  (dbActor ? DeleteDataSetById(id)).mapTo[MessageResponse]
                }
              }
            }
          }
        }
      } ~
      path(JavaUUID / "jobs") { uuid =>
        get {
          complete {
            ok {
              (dbActor ? GetDataSetJobsByUUID(uuid)).mapTo[Seq[EngineJob]]
            }
          }
        }
      } ~
      datasetRoutes[SubreadServiceDataSet](
        DataSetMetaTypes.Subread.shortName,
        GetSubreadDataSets,
        GetSubreadDataSetById,
        GetSubreadDataSetDetailsById) ~
      datasetRoutes[HdfSubreadServiceDataSet](
        DataSetMetaTypes.HdfSubread.shortName,
        GetHdfSubreadDataSets,
        GetHdfSubreadDataSetById,
        GetHdfSubreadDataSetDetailsById) ~
      datasetRoutes[AlignmentServiceDataSet](
        DataSetMetaTypes.Alignment.shortName,
        GetAlignmentDataSets,
        GetAlignmentDataSetById,
        GetAlignmentDataSetDetailsById) ~
      datasetRoutes[ReferenceServiceDataSet](
        DataSetMetaTypes.Reference.shortName,
        GetReferenceDataSets,
        GetReferenceDataSetById,
        GetReferenceDataSetDetailsById) ~
      datasetRoutes[GmapReferenceServiceDataSet](
        DataSetMetaTypes.GmapReference.shortName,
        GetGmapReferenceDataSets,
        GetGmapReferenceDataSetById,
        GetGmapReferenceDataSetDetailsById) ~
      datasetRoutes[BarcodeServiceDataSet](
        DataSetMetaTypes.Barcode.shortName,
        GetBarcodeDataSets,
        GetBarcodeDataSetById,
        GetBarcodeDataSetDetailsById) ~
      datasetRoutes[ConsensusReadServiceDataSet](
        DataSetMetaTypes.CCS.shortName,
        GetConsensusReadDataSets,
        GetConsensusReadDataSetById,
        GetConsensusReadDataSetDetailsById) ~
      datasetRoutes[ConsensusAlignmentServiceDataSet](
        DataSetMetaTypes.AlignmentCCS.shortName,
        GetConsensusAlignmentDataSets,
        GetConsensusAlignmentDataSetById,
        GetConsensusAlignmentDataSetDetailsById) ~
      datasetRoutes[ContigServiceDataSet](
        DataSetMetaTypes.Contig.shortName,
        GetContigDataSets,
        GetContigDataSetById,
        GetContigDataSetDetailsById)
    }
}

trait DataSetServiceProvider {
  this: JobsDaoActorProvider with AuthenticatorProvider with ServiceComposer =>

  val dataSetService: Singleton[DataSetService] =
    Singleton(() => new DataSetService(jobsDaoActor(), authenticator()))

  addService(dataSetService)
}
