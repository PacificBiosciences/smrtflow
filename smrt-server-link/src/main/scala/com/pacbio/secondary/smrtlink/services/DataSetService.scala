package com.pacbio.secondary.smrtlink.services

import java.nio.file.{Path, Paths}
import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import scala.reflect.ClassTag
//import shapeless.HNil

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.server.Route
import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.pattern.ask
import spray.json._
import SprayJsonSupport._

import com.pacbio.common.models.CommonModels._
import com.pacbio.common.models.CommonModelSpraySupport._
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
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.models.QueryOperators._
import com.pacbio.secondary.smrtlink.services.utils.SmrtDirectives
import com.pacbio.secondary.smrtlink.actors.CommonMessages._
import com.pacbio.secondary.smrtlink.SmrtLinkConstants
import com.pacbio.secondary.smrtlink.actors.{JobsDao, JobsDaoProvider}
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{
  EngineJob,
  JobConstants
}
import com.pacbio.secondary.smrtlink.analysis.bio.FastaIterator
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetLoader

//
import collection.JavaConverters._

trait SearchQueryUtils {

  /**
    * Validate and URL decode the raw string.
    */
  protected def parseQueryOperator[T](
      sx: Option[String],
      f: (String) => Option[T]): Future[Option[T]] = {

    sx match {
      case Some(v) =>
        val urlDecodedString = java.net.URLDecoder.decode(v, "UTF-8")
        f(urlDecodedString) match {
          case Some(op) => Future.successful(Some(op))
          case _ =>
            Future.failed(
              UnprocessableEntityError(s"Invalid Filter `$urlDecodedString`"))
        }
      case _ =>
        // Nothing was provided
        Future.successful(None)
    }
  }

  // - If a projectId is provided, return only that Id.
  // - If a projectId is not provided, but a user is logged in, return all projectIds associated with
  // that user, plus the general project id.
  // - If a projectId is not provided, and no user is logged in, return an empty projectId list.
  def getProjectIds(dao: JobsDao,
                    projectId: Option[Int],
                    user: Option[UserRecord]): Future[Seq[Int]] =
    (projectId, user) match {
      case (Some(id), _) => Future.successful(Seq(id))
      case (None, Some(u)) =>
        dao
          .getUserProjects(u.userId)
          .map(_.map(_.project.id) :+ JobConstants.GENERAL_PROJECT_ID)
      case (None, None) => Future.successful(Nil)
    }
}

/**
  * Accessing DataSets by type. Currently several datasets types are
  * not completely supported (ContigSet, CCSreads, CCS Alignments)
  */
class DataSetService(dao: JobsDao)
    extends SmrtLinkBaseRouteMicroService
    with SmrtLinkConstants
    with SearchQueryUtils {
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

    bs.getExternalResources.getExternalResource.asScala
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

  val shortNameRx = {
    val xs = DataSetMetaTypes.ALL
      .map(_.shortName + "$")
      .reduceLeft((a, c) => s"$a|$c")
    ("(" + xs + ")").r
  }

  // Need to add boilerplate getters here to plug into the current model and adhere to the interface
  // This should be cleaned up at somepoint.

  // SubreadSet
  def getSubreadSet(
      c: DataSetSearchCriteria): Future[Seq[SubreadServiceDataSet]] =
    dao.getSubreadDataSets(c)
  def getSubreadSetById(i: IdAble): Future[SubreadServiceDataSet] =
    dao.getSubreadDataSetById(i)
  def getSubreadSetDetailsById(i: IdAble): Future[String] =
    dao.getSubreadDataSetDetailsById(i)

  // HdfSubreadSet
  def getHdfSubreadSet(
      c: DataSetSearchCriteria): Future[Seq[HdfSubreadServiceDataSet]] =
    dao.getHdfDataSets(c)
  def getHdfSubreadById(i: IdAble): Future[HdfSubreadServiceDataSet] =
    dao.getHdfDataSetById(i)
  def getHdfSubreadDetailsById(i: IdAble): Future[String] =
    dao.getHdfDataSetDetailsById(i)

  // AlignmentSets
  def getAlignmentSet(
      c: DataSetSearchCriteria): Future[Seq[AlignmentServiceDataSet]] =
    dao.getAlignmentDataSets(c)
  def getAlignmentSetById(i: IdAble): Future[AlignmentServiceDataSet] =
    dao.getAlignmentDataSetById(i)
  def getAlignmentSetDetails(i: IdAble): Future[String] =
    dao.getAlignmentDataSetDetailsById(i)

  // ReferenceSets
  def getReferenceSet(
      c: DataSetSearchCriteria): Future[Seq[ReferenceServiceDataSet]] =
    dao.getReferenceDataSets(c)
  def getReferenceSetById(i: IdAble): Future[ReferenceServiceDataSet] =
    dao.getReferenceDataSetById(i)
  def getReferenceSetDetails(i: IdAble): Future[String] =
    dao.getReferenceDataSetDetailsById(i)

  // GmapReferenceSet
  def getGmapReferenceSet(
      c: DataSetSearchCriteria): Future[Seq[GmapReferenceServiceDataSet]] =
    dao.getGmapReferenceDataSets(c)
  def getGmapReferenceSetById(i: IdAble): Future[GmapReferenceServiceDataSet] =
    dao.getGmapReferenceDataSetById(i)
  def getGmapReferenceSetDetails(i: IdAble): Future[String] =
    dao.getGmapReferenceDataSetDetailsById(i)

  /// BarcodeSet
  def getBarcodeSet(
      c: DataSetSearchCriteria): Future[Seq[BarcodeServiceDataSet]] =
    dao.getBarcodeDataSets(c)
  def getBarcodeSetById(i: IdAble): Future[BarcodeServiceDataSet] =
    dao.getBarcodeDataSetById(i)
  def getBarcodeSetDetails(i: IdAble): Future[String] =
    dao.getBarcodeDataSetDetailsById(i)

  // Consensus Reads
  def getConsensusReadSet(
      c: DataSetSearchCriteria): Future[Seq[ConsensusReadServiceDataSet]] =
    dao.getConsensusReadDataSets(c)
  def getConsensusReadSetById(i: IdAble): Future[ConsensusReadServiceDataSet] =
    dao.getConsensusReadDataSetById(i)
  def getConsensusReadSetDetails(i: IdAble): Future[String] =
    dao.getConsensusReadDataSetDetailsById(i)

  // Consensus AlignmentSets
  def getConsensusAlignmentSet(c: DataSetSearchCriteria)
    : Future[Seq[ConsensusAlignmentServiceDataSet]] =
    dao.getConsensusAlignmentDataSets(c)
  def getConsensusAlignmentSetById(
      i: IdAble): Future[ConsensusAlignmentServiceDataSet] =
    dao.getConsensusAlignmentDataSetById(i)
  def getConsensusAlignmentSetDetails(i: IdAble): Future[String] =
    dao.getConsensusAlignmentDataSetDetailsById(i)

  // ContigSets
  def getContigDataSet(
      c: DataSetSearchCriteria): Future[Seq[ContigServiceDataSet]] =
    dao.getContigDataSets(c)
  def getContigDataSetById(i: IdAble): Future[ContigServiceDataSet] =
    dao.getContigDataSetById(i)
  def getContigDataSetDetails(i: IdAble): Future[String] =
    dao.getContigDataSetDetailsById(i)

  // TranscriptSets
  def getTranscriptSet(
      c: DataSetSearchCriteria): Future[Seq[TranscriptServiceDataSet]] =
    dao.getTranscriptDataSets(c)

  def getTranscriptSetById(i: IdAble): Future[TranscriptServiceDataSet] =
    dao.getTranscriptDataSetById(i)

  def getTranscriptSetDetails(i: IdAble): Future[String] =
    dao.getTranscriptDataSetDetailsById(i)

  def updateDataSet(id: IdAble,
                    sopts: DataSetUpdateRequest): Future[MessageResponse] = {
    def f1 =
      sopts.isActive
        .map { setIsActive =>
          dao.deleteDataSetById(id, setIsActive = setIsActive)
        }
        .getOrElse(Future.successful(MessageResponse("")))

    def mapError(opt: Option[String]): Future[MessageResponse] = {
      opt
        .map(msg => Future.failed(UnprocessableEntityError(msg)))
        .getOrElse(Future.successful(MessageResponse("Successfully Updated")))
    }

    def f2 =
      if (sopts.bioSampleName.isDefined || sopts.wellSampleName.isDefined) {
        for {
          ds <- dao.getDataSetMetaData(id)
          _ <- mapError(
            DataSetUpdateUtils.testApplyEdits(Paths.get(ds.path),
                                              sopts.bioSampleName,
                                              sopts.wellSampleName))
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

  /**
    * URL decode, convert to QueryOperators and
    * return a DataSet DataSetSearchCriteria.
    */
  def parseDataSetSearchCriteria(
      projectIds: Set[Int],
      isActive: Option[Boolean],
      limit: Int,
      marker: Option[Int],
      id: Option[String],
      path: Option[String],
      uuid: Option[String],
      name: Option[String],
      createdAt: Option[String],
      updatedAt: Option[String],
      importedAt: Option[String],
      numRecords: Option[String],
      totalLength: Option[String],
      version: Option[String],
      jobId: Option[String],
      parentUuid: Option[String],
      projectId: Option[String]): Future[DataSetSearchCriteria] = {

    val search =
      DataSetSearchCriteria(projectIds,
                            isActive = isActive,
                            limit = limit,
                            marker = marker)

    for {
      qId <- parseQueryOperator[IntQueryOperator](id,
                                                  IntQueryOperator.fromString)
      qUUID <- parseQueryOperator[UUIDQueryOperator](
        uuid,
        UUIDQueryOperator.fromString)
      qName <- parseQueryOperator[StringQueryOperator](
        name,
        StringQueryOperator.fromString)
      qNumRecords <- parseQueryOperator[LongQueryOperator](
        numRecords,
        LongQueryOperator.fromString)
      qTotalLength <- parseQueryOperator[LongQueryOperator](
        totalLength,
        LongQueryOperator.fromString)
      qVersion <- parseQueryOperator[StringQueryOperator](
        version,
        StringQueryOperator.fromString)
      qJobId <- parseQueryOperator[IntQueryOperator](
        jobId,
        IntQueryOperator.fromString)
      qProjectId <- parseQueryOperator[IntQueryOperator](
        projectId,
        IntQueryOperator.fromString)
      qCreatedAt <- parseQueryOperator[DateTimeQueryOperator](
        createdAt,
        DateTimeQueryOperator.fromString)
      qUpdatedAt <- parseQueryOperator[DateTimeQueryOperator](
        updatedAt,
        DateTimeQueryOperator.fromString)
      qImportedAt <- parseQueryOperator[DateTimeQueryOperator](
        importedAt,
        DateTimeQueryOperator.fromString)
      qPath <- parseQueryOperator[StringQueryOperator](
        path,
        StringQueryOperator.fromString)
      qParentUuid <- parseQueryOperator[UUIDQueryOperator](
        parentUuid,
        UUIDQueryOperator.fromString)
    } yield
      search.copy(
        name = qName,
        id = qId,
        uuid = qUUID,
        path = qPath,
        createdAt = qCreatedAt,
        updatedAt = qUpdatedAt,
        importedAt = qImportedAt,
        numRecords = qNumRecords,
        totalLength = qTotalLength,
        version = qVersion,
        jobId = qJobId,
        parentUuid = qParentUuid,
        projectId = qProjectId
      )

  }

  def datasetRoutes[R <: ServiceDataSetMetadata](
      shortName: String,
      GetDataSets: (DataSetSearchCriteria) => Future[Seq[R]],
      GetDataSetById: IdAble => Future[R],
      GetDetailsById: IdAble => Future[String])(
      implicit ct: ClassTag[R],
      ma: ToEntityMarshaller[R],
      sm: ToEntityMarshaller[Seq[R]]): Route =
    SmrtDirectives.extractOptionalUserRecord { user =>
      pathPrefix(shortName) {
        pathEnd {
          get {
            parameters(
              'isActive.as[Boolean].?,
              'limit.as[Int].?,
              'marker.as[Int].?,
              'id.?,
              'uuid.?,
              'path.?,
              'name.?,
              'createdAt.?,
              'updatedAt.?,
              'importedAt.?,
              'numRecords.?,
              'totalLength.?,
              'version.?,
              'jobId.?,
              'parentUuid.?,
              'projectId.?
            ) {
              (isActive,
               limit,
               marker,
               id,
               uuid,
               path,
               name,
               createdAt,
               updatedAt,
               importedAt,
               numRecords,
               totalLength,
               version,
               jobId,
               parentUuid,
               projectId) =>
                encodeResponse {
                  complete {
                    for {
                      // workaround for this project id oddness in the API
                      ids <- getProjectIds(dao, projectId.map(_.toInt), user)
                      searchCriteria <- parseDataSetSearchCriteria(
                        ids.toSet,
                        Some(isActive.getOrElse(true)),
                        limit.getOrElse(
                          DataSetSearchCriteria.DEFAULT_MAX_DATASETS),
                        marker,
                        id,
                        path,
                        uuid,
                        name,
                        createdAt,
                        updatedAt,
                        importedAt,
                        numRecords,
                        totalLength,
                        version,
                        jobId,
                        parentUuid,
                        projectId
                      )
                      datasets <- GetDataSets(searchCriteria)
                    } yield datasets
                  }
                }
            }
          }
        } ~
          pathPrefix(IdAbleMatcher) { id =>
            pathEnd {
              get {
                complete {
                  GetDataSetById(id)
                }
              } ~
                put {
                  entity(as[DataSetUpdateRequest]) { sopts =>
                    complete {
                      updateDataSet(id, sopts)
                    }
                  }
                }
            } ~
              path(DETAILS_PREFIX) {
                get {
                  complete {
                    GetDetailsById(id).map(_.parseJson) // To get the correct mime-type
                  }
                }
              } ~
              path(DETAILED_RECORDS_PREFIX) {
                pathEndOrSingleSlash {
                  get {
                    complete {
                      for {
                        _ <- validateBarcodeShortName(shortName)
                        dataset <- GetDataSetById(id)
                        recordNames <- Future.successful(
                          loadBarcodeNames(Paths.get(dataset.path)))
                      } yield recordNames
                    }
                  }
                }
              } ~
              path(JOB_REPORT_PREFIX) {
                get {
                  complete {
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

  val routes =
    pathPrefix(DATASET_TYPES_PREFIX) {
      pathEnd {
        get {
          complete {
            dao.getDataSetTypes
          }
        }
      } ~
        path(shortNameRx) { shortName =>
          get {
            complete {
              dao.getDataSetTypeById(shortName)
            }
          }
        }
    } ~
      pathPrefix(DATASET_PREFIX) {
        path(IdAbleMatcher) { id =>
          get {
            complete {
              dao.getDataSetById(id)
            }
          } ~
            put {
              entity(as[DataSetUpdateRequest]) { sopts =>
                complete {
                  updateDataSet(id, sopts)
                }
              }
            }
        } ~
          path(JavaUUID / "jobs") { uuid =>
            get {
              complete {
                dao.getDataSetJobsByUUID(uuid)
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
            getContigDataSetDetails) ~
          datasetRoutes[TranscriptServiceDataSet](
            DataSetMetaTypes.Transcript.shortName,
            getTranscriptSet,
            getTranscriptSetById,
            getTranscriptSetDetails)
      }
}

trait DataSetServiceProvider { this: JobsDaoProvider with ServiceComposer =>

  val dataSetService: Singleton[DataSetService] =
    Singleton(() => new DataSetService(jobsDao()))

  addService(dataSetService)
}
