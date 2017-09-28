package com.pacbio.secondary.smrtlink.services

import java.nio.file.{Path, Paths}
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
import com.pacbio.secondary.smrtlink.auth.{
  Authenticator,
  AuthenticatorProvider
}
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.{
  MethodNotImplementedError,
  ResourceNotFoundError,
  UnprocessableEntityError
}
import com.pacbio.secondary.smrtlink.analysis.datasets.{
  DataSetMetaTypes,
  DataSetUpdateUtils
}
import com.pacbio.secondary.smrtlink.actors.CommonMessages._
import com.pacbio.secondary.smrtlink.SmrtLinkConstants
import com.pacbio.secondary.smrtlink.actors.{JobsDao, JobsDaoProvider}
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.EngineJob
import com.pacbio.common.models.CommonModels._
import com.pacbio.common.models.CommonModelSpraySupport._
import com.pacbio.secondary.smrtlink.analysis.bio.{Fasta, FastaIterator}
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetLoader

import collection.JavaConversions._

/**
  * Accessing DataSets by type. Currently several datasets types are
  * not completely supported (ContigSet, CCSreads, CCS Alignments)
  */
class DataSetService(dao: JobsDao, authenticator: Authenticator)
    extends SmrtLinkBaseRouteMicroService
    with SmrtLinkConstants {
  // For all the Message types

  import CommonModelImplicits._

  // For all the serialization protocols

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  val manifest = PacBioComponentManifest(toServiceId("smrtlink.dataset"),
                                         "SMRT Link DataSetService Service",
                                         "0.1.0",
                                         "SMRT Link Analysis DataSet Service")

  val DATASET_TYPES_PREFIX = "dataset-types"
  val DATASET_PREFIX = "datasets"
  val DETAILS_PREFIX = "details"
  val DETAILED_RECORDS_PREFIX = "record-names"

  /**
    * Load Barcode Names/Ids from the Fasta file
    * TODO(mpkocher)(8-26-2017) Move to central location and add unittest
    *
    * @param barcodeSet Path to the Barcode Set.
    * @return
    */
  def loadBarcodeNames(barcodeSet: Path): Seq[String] = {

    val bs = DataSetLoader.loadAndResolveBarcodeSet(barcodeSet)

    bs.getExternalResources.getExternalResource
      .find(_.getMetaType == FileTypes.FASTA_BC.fileTypeId)
      .map(_.getResourceId)
      .map(p => new FastaIterator(Paths.get(p).toFile))
      .map(items => items.map(_.getName).toList)
      .getOrElse(Seq.empty[String])

  }

  /**
    * Only the BarcodeSet supports loading the Contig names/ids from the fasta file
    */
  def validateBarcodeShortName(shortName: String): Future[String] = {
    if (shortName == "barcodes")
      Future.successful("Successful barcodeSet type")
    else
      Future.failed(new UnprocessableEntityError(
        s"DataSet $shortName not supported. Only BarcodeSets are supported."))
  }

  // Default MAX number of records to return
  val DS_LIMIT = 2000

  val shortNameRx = {
    val xs = DataSetMetaTypes.ALL
      .map(_.shortName + "$")
      .reduceLeft((a, c) => s"$a|$c")
    ("(" + xs + ")").r
  }

  // - If a projectId is provided, return only that Id.
  // - If a projectId is not provided, but a user is logged in, return all projectIds associated with
  // that user, plus the general project id.
  // - If a projectId is not provided, and no user is logged in, return an empty projectId list.
  def getProjectIds(projectId: Option[Int],
                    user: Option[UserRecord]): Future[Seq[Int]] =
    (projectId, user) match {
      case (Some(id), _) => Future(Seq(id))
      case (None, Some(u)) =>
        dao
          .getUserProjects(u.userId)
          .map(_.map(_.project.id) :+ GENERAL_PROJECT_ID)
      case (None, None) => Future(Nil)
    }

  // Need to add boilerplate getters here to plug into the current model and adhere to the interface
  // This should be cleaned up at somepoint.

  // SubreadSet
  def getSubreadSet(
      limit: Int,
      includeInactive: Boolean = false,
      projectIds: Seq[Int] = Nil): Future[Seq[SubreadServiceDataSet]] =
    dao.getSubreadDataSets(limit, includeInactive, projectIds)
  def getSubreadSetById(i: IdAble): Future[SubreadServiceDataSet] =
    dao.getSubreadDataSetById(i)
  def getSubreadSetDetailsById(i: IdAble): Future[String] =
    dao.getSubreadDataSetDetailsById(i)

  // HdfSubreadSet
  def getHdfSubreadSet(
      limit: Int,
      includeInactive: Boolean = false,
      projectIds: Seq[Int] = Nil): Future[Seq[HdfSubreadServiceDataSet]] =
    dao.getHdfDataSets(limit, includeInactive, projectIds)
  def getHdfSubreadById(i: IdAble): Future[HdfSubreadServiceDataSet] =
    dao.getHdfDataSetById(i)
  def getHdfSubreadDetailsById(i: IdAble): Future[String] =
    dao.getHdfDataSetDetailsById(i)

  // AlignmentSets
  def getAlignmentSet(
      limit: Int,
      includeInactive: Boolean = false,
      projectIds: Seq[Int] = Nil): Future[Seq[AlignmentServiceDataSet]] =
    dao.getAlignmentDataSets(limit, includeInactive, projectIds)
  def getAlignmentSetById(i: IdAble): Future[AlignmentServiceDataSet] =
    dao.getAlignmentDataSetById(i)
  def getAlignmentSetDetails(i: IdAble): Future[String] =
    dao.getAlignmentDataSetDetailsById(i)

  // ReferenceSets
  def getReferenceSet(
      limit: Int,
      includeInactive: Boolean = false,
      projectIds: Seq[Int] = Nil): Future[Seq[ReferenceServiceDataSet]] =
    dao.getReferenceDataSets(limit, includeInactive, projectIds)
  def getReferenceSetById(i: IdAble): Future[ReferenceServiceDataSet] =
    dao.getReferenceDataSetById(i)
  def getReferenceSetDetails(i: IdAble): Future[String] =
    dao.getReferenceDataSetDetailsById(i)

  // GmapReferenceSet
  def getGmapReferenceSet(
      limit: Int,
      includeInactive: Boolean = false,
      projectIds: Seq[Int] = Nil): Future[Seq[GmapReferenceServiceDataSet]] =
    dao.getGmapReferenceDataSets(limit, includeInactive, projectIds)
  def getGmapReferenceSetById(i: IdAble): Future[GmapReferenceServiceDataSet] =
    dao.getGmapReferenceDataSetById(i)
  def getGmapReferenceSetDetails(i: IdAble): Future[String] =
    dao.getGmapReferenceDataSetDetailsById(i)

  /// BarcodeSet
  def getBarcodeSet(
      limit: Int,
      includeInactive: Boolean = false,
      projectIds: Seq[Int] = Nil): Future[Seq[BarcodeServiceDataSet]] =
    dao.getBarcodeDataSets(limit, includeInactive, projectIds)
  def getBarcodeSetById(i: IdAble): Future[BarcodeServiceDataSet] =
    dao.getBarcodeDataSetById(i)
  def getBarcodeSetDetails(i: IdAble): Future[String] =
    dao.getBarcodeDataSetDetailsById(i)

  // Consensus Reads
  def getConsensusReadSet(
      limit: Int,
      includeInactive: Boolean = false,
      projectIds: Seq[Int] = Nil): Future[Seq[ConsensusReadServiceDataSet]] =
    dao.getConsensusReadDataSets(limit, includeInactive, projectIds)
  def getConsensusReadSetById(i: IdAble): Future[ConsensusReadServiceDataSet] =
    dao.getConsensusReadDataSetById(i)
  def getConsensusReadSetDetails(i: IdAble): Future[String] =
    dao.getConsensusReadDataSetDetailsById(i)

  // Consensus AlignmentSets
  def getConsensusAlignmentSet(limit: Int,
                               includeInactive: Boolean = false,
                               projectIds: Seq[Int] = Nil)
    : Future[Seq[ConsensusAlignmentServiceDataSet]] =
    dao.getConsensusAlignmentDataSets(limit, includeInactive, projectIds)
  def getConsensusAlignmentSetById(
      i: IdAble): Future[ConsensusAlignmentServiceDataSet] =
    dao.getConsensusAlignmentDataSetById(i)
  def getConsensusAlignmentSetDetails(i: IdAble): Future[String] =
    dao.getConsensusAlignmentDataSetDetailsById(i)

  // ContigSets
  def getContigDataSet(
      limit: Int,
      includeInactive: Boolean = false,
      projectIds: Seq[Int] = Nil): Future[Seq[ContigServiceDataSet]] =
    dao.getContigDataSets(limit, includeInactive, projectIds)
  def getContigDataSetById(i: IdAble): Future[ContigServiceDataSet] =
    dao.getContigDataSetById(i)
  def getContigDataSetDetails(i: IdAble): Future[String] =
    dao.getContigDataSetDetailsById(i)

  def updateDataSet(id: IdAble, sopts: DataSetUpdateRequest) = {
    val f1 = sopts.isActive
      .map { setIsActive =>
        dao.deleteDataSetById(id, setIsActive = setIsActive)
      }
      .getOrElse(Future.successful(MessageResponse("")))

    val f2 =
      if (sopts.bioSampleName.isDefined || sopts.wellSampleName.isDefined) {
        for {
          ds <- dao.getDataSetMetaData(id)
          _ <- DataSetUpdateUtils
            .testApplyEdits(Paths.get(ds.path),
                            sopts.bioSampleName,
                            sopts.wellSampleName)
            .map { err =>
              Future.failed(new UnprocessableEntityError(err))
            }
            .getOrElse(Future.successful("no errors"))
          msg <- dao.updateSubreadSetDetails(id,
                                             sopts.bioSampleName,
                                             sopts.wellSampleName)
        } yield msg
      } else Future.successful(MessageResponse(""))

    for {
      resp1 <- f1
      resp2 <- f2
    } yield MessageResponse(s"${resp1.message} ${resp2.message}")
  }

  def datasetRoutes[R <: ServiceDataSetMetadata](
      shortName: String,
      GetDataSets: (Int, Boolean, Seq[Int]) => Future[Seq[R]],
      GetDataSetById: IdAble => Future[R],
      GetDetailsById: IdAble => Future[String])(
      implicit ct: ClassTag[R],
      ma: Marshaller[R],
      sm: Marshaller[Seq[R]]): Route =
    optionalAuthenticate(authenticator.wso2Auth) { user =>
      pathPrefix(shortName) {
        pathEnd {
          get {
            parameters('showAll.?, 'projectId.as[Int].?) {
              (showAll, projectId) =>
                complete {
                  ok {
                    getProjectIds(projectId, user)
                      .map { ids =>
                        GetDataSets(DS_LIMIT, showAll.isDefined, ids)
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
                    GetDataSetById(id)
                  }
                }
              } ~
                put {
                  entity(as[DataSetUpdateRequest]) { sopts =>
                    complete {
                      ok {
                        updateDataSet(id, sopts)
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
                        GetDetailsById(id)
                      }
                    }
                  }
                }
              } ~
              path(DETAILED_RECORDS_PREFIX) {
                pathEndOrSingleSlash {
                  get {
                    complete {
                      ok {
                        for {
                          _ <- validateBarcodeShortName(shortName)
                          dataset <- GetDataSetById(id)
                          recordNames <- Future.successful(
                            loadBarcodeNames(Paths.get(dataset.path)))
                        } yield recordNames
                      }
                    }
                  }
                }
              } ~
              path(JOB_REPORT_PREFIX) {
                get {
                  complete {
                    ok {
                      for {
                        dataset <- GetDataSetById(id)
                        reports <- dao.getDataStoreReportFilesByJobId(
                          dataset.jobId)
                      } yield reports
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
              dao.getDataSetTypes
            }
          }
        }
      } ~
        path(shortNameRx) { shortName =>
          get {
            complete {
              ok {
                DataSetMetaTypes
                  .fromShortName(shortName)
                  .map(t => dao.getDataSetTypeById(t.dsId))
                  .getOrElse(throw new ResourceNotFoundError(
                    s"Unable to find dataset type Id '$shortName"))
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
                dao.getDataSetById(id)
              }
            }
          } ~
            put {
              entity(as[DataSetUpdateRequest]) { sopts =>
                complete {
                  ok {
                    updateDataSet(id, sopts)
                  }
                }
              }
            }
        } ~
          path(JavaUUID / "jobs") { uuid =>
            get {
              complete {
                ok {
                  dao.getDataSetJobsByUUID(uuid)
                }
              }
            }
          } ~
          datasetRoutes[SubreadServiceDataSet](
            DataSetMetaTypes.Subread.shortName,
            getSubreadSet,
            getSubreadSetById,
            getSubreadSetDetailsById) ~
          datasetRoutes[HdfSubreadServiceDataSet](
            DataSetMetaTypes.HdfSubread.shortName,
            getHdfSubreadSet,
            getHdfSubreadById,
            getHdfSubreadDetailsById) ~
          datasetRoutes[AlignmentServiceDataSet](
            DataSetMetaTypes.Alignment.shortName,
            getAlignmentSet,
            getAlignmentSetById,
            getAlignmentSetDetails) ~
          datasetRoutes[ReferenceServiceDataSet](
            DataSetMetaTypes.Reference.shortName,
            getReferenceSet,
            getReferenceSetById,
            getReferenceSetDetails) ~
          datasetRoutes[GmapReferenceServiceDataSet](
            DataSetMetaTypes.GmapReference.shortName,
            getGmapReferenceSet,
            getGmapReferenceSetById,
            getGmapReferenceSetDetails) ~
          datasetRoutes[BarcodeServiceDataSet](
            DataSetMetaTypes.Barcode.shortName,
            getBarcodeSet,
            getBarcodeSetById,
            getBarcodeSetDetails) ~
          datasetRoutes[ConsensusReadServiceDataSet](
            DataSetMetaTypes.CCS.shortName,
            getConsensusReadSet,
            getConsensusReadSetById,
            getConsensusReadSetDetails) ~
          datasetRoutes[ConsensusAlignmentServiceDataSet](
            DataSetMetaTypes.AlignmentCCS.shortName,
            getConsensusAlignmentSet,
            getConsensusAlignmentSetById,
            getConsensusAlignmentSetDetails) ~
          datasetRoutes[ContigServiceDataSet](
            DataSetMetaTypes.Contig.shortName,
            getContigDataSet,
            getContigDataSetById,
            getContigDataSetDetails)
      }
}

trait DataSetServiceProvider {
  this: JobsDaoProvider with AuthenticatorProvider with ServiceComposer =>

  val dataSetService: Singleton[DataSetService] =
    Singleton(() => new DataSetService(jobsDao(), authenticator()))

  addService(dataSetService)
}
