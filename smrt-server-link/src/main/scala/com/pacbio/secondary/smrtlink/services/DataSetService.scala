package com.pacbio.secondary.smrtlink.services

import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.common.services.ServiceComposer
import com.pacbio.common.services.PacBioServiceErrors.ResourceNotFoundError
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.SmrtLinkConstants
import com.pacbio.secondary.smrtlink.actors.{JobsDaoActorProvider, JobsDaoActor}
import com.pacbio.secondary.smrtlink.loaders.SchemaLoader
import com.pacbio.secondary.smrtlink.models._
import shapeless.HNil
import spray.httpx.marshalling.Marshaller
import spray.routing.{PathMatcher1, Route}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

import spray.http.MediaTypes
import spray.json._
import spray.httpx.SprayJsonSupport
import SprayJsonSupport._

import scala.reflect.ClassTag


/**
 * Accessing DataSets by type. Currently several datasets types are
 * not completely supported (ContigSet, CCSreads, CCS Alignments)
 */
class DataSetService(dbActor: ActorRef) extends JobsBaseMicroService with SmrtLinkConstants {
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
  val SCHEMA_PREFIX = "_schema"
  val DETAILS_PREFIX = "details"

  // Default MAX number of records to return
  val DS_LIMIT = 2000

  val shortNameRx = {
    val xs = DataSetMetaTypes.ALL.map(_.shortName + "$").reduceLeft((a, c) => s"$a|$c")
    ("(" + xs + ")").r
  }

  case class MixedIdType(id: Either[Int, UUID]) {
    def map[T](fInt: Int => _ <: T, fUUID: UUID => _ <: T): T = id match {
      case Left(i) => fInt(i)
      case Right(i) => fUUID(i)
    }
  }

  // The order of JavaUUID and IntNumber are important here, as IntNumber will capture a UUID
  val MixedId: PathMatcher1[MixedIdType] = (JavaUUID | IntNumber).hflatMap { p =>
    val idOrUUID: Option[Either[Int, UUID]] = p.head match {
      case id: Int => Some(Left(id))
      case uuid: UUID => Some(Right(uuid))
      case _ => None
    }
    idOrUUID.map(MixedIdType).map(HNil.::)
  }

  def datasetRoutes[R <: ServiceDataSetMetadata](
      shortName: String,
      GetDataSets: Int => Any,
      schema: String,
      GetDataSetById: Int => Any,
      GetDataSetByUUID: UUID => Any,
      GetDetailsById: Int => Any,
      GetDetailsByUUID: UUID => Any)(
      implicit ct: ClassTag[R],
      ma: Marshaller[R],
      sm: Marshaller[Seq[R]]): Route =
    pathPrefix(shortName) {
      pathEnd {
        get {
          complete {
            ok {
              (dbActor ? GetDataSets(DS_LIMIT)).mapTo[Seq[R]]
            }
          }
        }
      } ~
      path(SCHEMA_PREFIX) {
        get {
          complete {
            ok {
              schema
            }
          }
        }
      } ~
      pathPrefix(MixedId) { id =>
        pathEnd {
          get {
            complete {
              ok {
                (dbActor ? id.map(GetDataSetById, GetDataSetByUUID)).mapTo[R]
              }
            }
          }
        } ~
        path(DETAILS_PREFIX) {
          get {
            respondWithMediaType(MediaTypes.`application/json`) {
              complete {
                ok {
                  (dbActor ? id.map(GetDetailsById, GetDetailsByUUID)).mapTo[String]
                }
              }
            }
          }
        } ~
        path(JOB_REPORT_PREFIX) {
          get {
            complete {
              ok {
                val dataset: Future[R] = (dbActor ? id.map(GetDataSetById, GetDataSetByUUID)).mapTo[R]
                val reports: Future[Seq[DataStoreReportFile]] = dataset.flatMap { s =>
                  (dbActor ? GetDataStoreReportFileByJobId(s.jobId)).mapTo[Seq[DataStoreReportFile]]
                }
                reports
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
      path(MixedId) { id =>
        get {
          complete {
            ok {
              (dbActor ? id.map(GetDataSetMetaById, GetDataSetMetaByUUID)).mapTo[DataSetMetaDataSet]
            }
          }
        }
      } ~
      datasetRoutes[SubreadServiceDataSet](
        DataSetMetaTypes.Subread.shortName,
        GetSubreadDataSets,
        SchemaLoader.subreadSchema.content,
        GetSubreadDataSetById,
        GetSubreadDataSetByUUID,
        GetSubreadDataSetDetailsById,
        GetSubreadDataSetDetailsByUUID) ~
      datasetRoutes[HdfSubreadServiceDataSet](
        DataSetMetaTypes.HdfSubread.shortName,
        GetHdfSubreadDataSets,
        SchemaLoader.subreadSchema.content,
        GetHdfSubreadDataSetById,
        GetHdfSubreadDataSetByUUID,
        GetHdfSubreadDataSetDetailsById,
        GetHdfSubreadDataSetDetailsByUUID) ~
      datasetRoutes[AlignmentServiceDataSet](
        DataSetMetaTypes.Alignment.shortName,
        GetAlignmentDataSets,
        SchemaLoader.alignmentSchema.content,
        GetAlignmentDataSetById,
        GetAlignmentDataSetByUUID,
        _ => throw new ResourceNotFoundError("Details not supported for AlignmentSet"),
        _ => throw new ResourceNotFoundError("Details not supported for AlignmentSet")) ~
      datasetRoutes[ReferenceServiceDataSet](
        DataSetMetaTypes.Reference.shortName,
        GetReferenceDataSets,
        SchemaLoader.referenceSchema.content,
        GetReferenceDataSetById,
        GetReferenceDataSetByUUID,
        GetReferenceDataSetDetailsById,
        GetReferenceDataSetDetailsByUUID) ~
      datasetRoutes[GmapReferenceServiceDataSet](
        DataSetMetaTypes.GmapReference.shortName,
        GetGmapReferenceDataSets,
        SchemaLoader.gmapReferenceSchema.content,
        GetGmapReferenceDataSetById,
        GetGmapReferenceDataSetByUUID,
        GetGmapReferenceDataSetDetailsById,
        GetGmapReferenceDataSetDetailsByUUID) ~
      datasetRoutes[BarcodeServiceDataSet](
        DataSetMetaTypes.Barcode.shortName,
        GetBarcodeDataSets,
        SchemaLoader.barcodeSchema.content,
        GetBarcodeDataSetsById,
        GetBarcodeDataSetsByUUID,
        GetBarcodeDataSetDetailsById,
        GetBarcodeDataSetDetailsByUUID) ~
      datasetRoutes[CCSreadServiceDataSet](
        DataSetMetaTypes.CCS.shortName,
        GetConsensusReadDataSets,
        SchemaLoader.ccsReadSchema.content,
        GetConsensusReadDataSetsById,
        GetConsensusReadDataSetsByUUID,
        _ => throw new ResourceNotFoundError("Details not supported for CCS DataSet"),
        _ => throw new ResourceNotFoundError("Details not supported for CCS DataSet")) ~
      datasetRoutes[ConsensusAlignmentServiceDataSet](
        DataSetMetaTypes.AlignmentCCS.shortName,
        GetConsensusAlignmentDataSets,
        SchemaLoader.ccsAlignmentSchema.content,
        GetConsensusAlignmentDataSetsById,
        GetConsensusAlignmentDataSetsByUUID,
        _ => throw new ResourceNotFoundError("Details not supported for ConsensusAlignmentSet"),
        _ => throw new ResourceNotFoundError("Details not supported for ConsensusAlignmentSet")) ~
      datasetRoutes[ContigServiceDataSet](
        DataSetMetaTypes.Contig.shortName,
        GetContigDataSets,
        SchemaLoader.contigSchema.content,
        GetContigDataSetsById,
        GetContigDataSetsByUUID,
        _ => throw new ResourceNotFoundError("Details not supported for ContigSet"),
        _ => throw new ResourceNotFoundError("Details not supported for ContigSet"))
    }
}

trait DataSetServiceProvider {
  this: JobsDaoActorProvider with ServiceComposer =>

  val dataSetService: Singleton[DataSetService] =
    Singleton(() => new DataSetService(jobsDaoActor()))

  addService(dataSetService)
}
