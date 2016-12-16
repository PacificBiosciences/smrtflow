package com.pacbio.secondary.smrtlink.client

import com.pacbio.secondary.analysis.engine.CommonMessages.MessageResponse
import com.pacbio.secondary.analysis.datasets.io.DataSetJsonProtocols
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.reports._
import com.pacbio.common.models._
import com.pacbio.common.client._
import com.pacificbiosciences.pacbiodatasets._

import akka.actor.ActorSystem

import spray.http._
import spray.client.pipelining._
import spray.httpx.SprayJsonSupport
import spray.httpx.unmarshalling.FromResponseUnmarshaller
import spray.json.DefaultJsonProtocol

import scala.concurrent.Future
import scala.concurrent.duration._

import java.net.URL
import java.util.UUID

object ServicesClientJsonProtocol extends SmrtLinkJsonProtocols with ReportJsonProtocol with DataSetJsonProtocols

trait ServiceEndpointsTrait {
  val ROOT_JM = "/secondary-analysis/job-manager"
  val ROOT_JOBS = ROOT_JM + "/jobs"
  val ROOT_DS = "/secondary-analysis/datasets"
  val ROOT_RUNS = "/smrt-link/runs"
  val ROOT_DATASTORE = "/secondary-analysis/datastore-files"
  val ROOT_PROJECTS = "/secondary-analysis/projects"
  val ROOT_SERVICE_MANIFESTS = "/services/manifests" // keeping with the naming convention
}

trait ServiceResourceTypesTrait {
  val REPORTS = "reports"
  val DATASTORE = "datastore"
  val ENTRY_POINTS = "entry-points"
}

trait JobTypesTrait {
  val IMPORT_DS = "import-dataset"
  val MERGE_DS = "merge-datasets"
  val MOCK_PB_PIPE = "mock-pbsmrtpipe"
}

// FIXME this for sure needs to be somewhere else
trait DataSetTypesTrait {
  val SUBREADS = "subreads"
  val HDFSUBREADS = "hdfsubreads"
  val REFERENCES = "references"
  val BARCODES = "barcodes"
  val GMAPREFERENCES = "gmapreferences"
  val CCSREADS = "ccsreads"
  val ALIGNMENTS = "alignments"
  val CONTIGS = "contigs"
  val CCSALIGNMENTS = "ccsalignments"
}

class SmrtLinkServiceAccessLayer(baseUrl: URL, authToken: Option[String] = None)
    (implicit actorSystem: ActorSystem)
    extends ServiceAccessLayer(baseUrl)(actorSystem)  {

  import ServicesClientJsonProtocol._
  import SprayJsonSupport._
  import CommonModels._
  import CommonModelImplicits._
  import ReportModels._

  object ServiceEndpoints extends ServiceEndpointsTrait
  object ServiceResourceTypes extends ServiceResourceTypesTrait
  object JobTypes extends JobTypesTrait
  object DataSetTypes extends DataSetTypesTrait

  private def jobRoot(jobType: String) = s"${ServiceEndpoints.ROOT_JOBS}/${jobType}"
  protected def toJobUrl(jobType: String, jobId: IdAble): String =
    toUrl(jobRoot(jobType) + s"/${jobId.toIdString}")
  protected def toJobResourceUrl(jobType: String, jobId: IdAble,
                                 resourceType: String): String =
    toUrl(jobRoot(jobType) + s"/${jobId.toIdString}/$resourceType")
  protected def toJobResourceIdUrl(jobType: String, jobId: IdAble,
                                   resourceType: String, resourceId: UUID) =
    toUrl(jobRoot(jobType) + s"/${jobId.toIdString}/$resourceType/$resourceId")
  private def dsRoot(dsType: String) = s"${ServiceEndpoints.ROOT_DS}/${dsType}"
  protected def toDataSetsUrl(dsType: String): String = toUrl(dsRoot(dsType))
  protected def toDataSetUrl(dsType: String, dsId: IdAble): String =
    toUrl(dsRoot(dsType) + s"/${dsId.toIdString}")
  protected def toDataSetResourcesUrl(dsType: String, dsId: IdAble,
                                     resourceType: String): String =
    toUrl(dsRoot(dsType) + s"/${dsId.toIdString}/$resourceType")
  protected def toDataSetResourceUrl(dsType: String, dsId: IdAble,
                                     resourceType: String, resourceId: UUID) =
    toUrl(dsRoot(dsType) + s"/${dsId.toIdString}/$resourceType/$resourceId")

  override def serviceStatusEndpoints: Vector[String] = Vector(
      ServiceEndpoints.ROOT_JOBS + "/" + JobTypes.IMPORT_DS,
      ServiceEndpoints.ROOT_DS + "/" + DataSetTypes.SUBREADS,
      ServiceEndpoints.ROOT_DS + "/" + DataSetTypes.HDFSUBREADS,
      ServiceEndpoints.ROOT_DS + "/" + DataSetTypes.REFERENCES,
      ServiceEndpoints.ROOT_DS + "/" + DataSetTypes.BARCODES)


  // FIXME(nechols)(2016-09-21) disabled due to WSO2, will revisit later
  /*protected def sendReceiveAuthenticated = authToken match {
    case Some(token) => addHeader("Authorization", s"Bearer $token") ~> sendReceive
    case None => sendReceive
  }*/
  protected def sendReceiveAuthenticated = sendReceive

  // Pipelines and serialization
  protected def getDataSetMetaDataPipeline: HttpRequest => Future[DataSetMetaDataSet] = sendReceiveAuthenticated ~> unmarshal[DataSetMetaDataSet]

  private def getDataSetPipeline[T <: ServiceDataSetMetadata](implicit fmt: FromResponseUnmarshaller[T]): HttpRequest => Future[T] = sendReceiveAuthenticated ~> unmarshal[T]
  protected def getSubreadSetPipeline = getDataSetPipeline[SubreadServiceDataSet]
  protected def getHdfSubreadSetPipeline = getDataSetPipeline[HdfSubreadServiceDataSet]
  protected def getReferenceSetPipeline = getDataSetPipeline[ReferenceServiceDataSet]
  protected def getGmapReferenceSetPipeline = getDataSetPipeline[GmapReferenceServiceDataSet]
  protected def getBarcodeSetPipeline = getDataSetPipeline[BarcodeServiceDataSet]
  protected def getAlignmentSetPipeline = getDataSetPipeline[AlignmentServiceDataSet]
  protected def getConsensusReadSetPipeline = getDataSetPipeline[ConsensusReadServiceDataSet]
  protected def getConsensusAlignmentSetPipeline = getDataSetPipeline[ConsensusAlignmentServiceDataSet]
  protected def getContigSetPipeline = getDataSetPipeline[ContigServiceDataSet]

  // DATASET DETAILS (full object parsed from XML)
  private def getDataSetDetailsPipeline[T <: DataSetType](implicit fmt: FromResponseUnmarshaller[T]): HttpRequest => Future[T] = sendReceiveAuthenticated ~> unmarshal[T]
  protected def getSubreadSetDetailsPipeline = getDataSetDetailsPipeline[SubreadSet]
  protected def getHdfSubreadSetDetailsPipeline = getDataSetDetailsPipeline[HdfSubreadSet]
  protected def getReferenceSetDetailsPipeline = getDataSetDetailsPipeline[ReferenceSet]
  protected def getGmapReferenceSetDetailsPipeline = getDataSetDetailsPipeline[GmapReferenceSet]
  protected def getBarcodeSetDetailsPipeline = getDataSetDetailsPipeline[BarcodeSet]
  protected def getAlignmentSetDetailsPipeline = getDataSetDetailsPipeline[AlignmentSet]
  protected def getConsensusReadSetDetailsPipeline = getDataSetDetailsPipeline[ConsensusReadSet]
  protected def getConsensusAlignmentSetDetailsPipeline = getDataSetDetailsPipeline[ConsensusAlignmentSet]
  protected def getContigSetDetailsPipeline = getDataSetDetailsPipeline[ContigSet]

  private def getDataSetsPipeline[T <: ServiceDataSetMetadata](implicit fmt: FromResponseUnmarshaller[Seq[T]]): HttpRequest => Future[Seq[T]] = sendReceiveAuthenticated ~> unmarshal[Seq[T]]
  protected def getSubreadSetsPipeline = getDataSetsPipeline[SubreadServiceDataSet]
  protected def getHdfSubreadSetsPipeline = getDataSetsPipeline[HdfSubreadServiceDataSet]
  protected def getReferenceSetsPipeline = getDataSetsPipeline[ReferenceServiceDataSet]
  protected def getGmapReferenceSetsPipeline = getDataSetsPipeline[GmapReferenceServiceDataSet]
  protected def getBarcodeSetsPipeline = getDataSetsPipeline[BarcodeServiceDataSet]
  protected def getAlignmentSetsPipeline = getDataSetsPipeline[AlignmentServiceDataSet]
  protected def getConsensusReadSetsPipeline = getDataSetsPipeline[ConsensusReadServiceDataSet]
  protected def getConsensusAlignmentSetsPipeline = getDataSetsPipeline[ConsensusAlignmentServiceDataSet]
  protected def getContigSetsPipeline = getDataSetsPipeline[ContigServiceDataSet]

  protected def getDataStorePipeline: HttpRequest => Future[Seq[DataStoreServiceFile]] = sendReceiveAuthenticated ~> unmarshal[Seq[DataStoreServiceFile]]
  protected def getEntryPointsPipeline: HttpRequest => Future[Seq[EngineJobEntryPoint]] = sendReceiveAuthenticated ~> unmarshal[Seq[EngineJobEntryPoint]]
  protected def getReportsPipeline: HttpRequest => Future[Seq[DataStoreReportFile]] = sendReceiveAuthenticated ~> unmarshal[Seq[DataStoreReportFile]]
  protected def getReportPipeline: HttpRequest => Future[Report] = sendReceiveAuthenticated ~> unmarshal[Report]

  protected def getRunsPipeline: HttpRequest => Future[Seq[RunSummary]] = sendReceiveAuthenticated ~> unmarshal[Seq[RunSummary]]
  protected def getRunSummaryPipeline: HttpRequest => Future[RunSummary] = sendReceiveAuthenticated ~> unmarshal[RunSummary]
  protected def getRunPipeline: HttpRequest => Future[Run] = sendReceiveAuthenticated ~> unmarshal[Run]
  protected def getCollectionsPipeline: HttpRequest => Future[Seq[CollectionMetadata]] = sendReceiveAuthenticated ~> unmarshal[Seq[CollectionMetadata]]
  protected def getCollectionPipeline: HttpRequest => Future[CollectionMetadata] = sendReceiveAuthenticated ~> unmarshal[CollectionMetadata]

  protected def getProjectsPipeline: HttpRequest => Future[Seq[Project]] = sendReceiveAuthenticated ~> unmarshal[Seq[Project]]
  protected def getProjectPipeline: HttpRequest => Future[FullProject] = sendReceiveAuthenticated ~> unmarshal[FullProject]

  protected def getMessageResponsePipeline: HttpRequest => Future[MessageResponse] = sendReceiveAuthenticated ~> unmarshal[MessageResponse]

  def getDataSet(datasetId: IdAble): Future[DataSetMetaDataSet] = getDataSetMetaDataPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_DS + "/" + datasetId.toIdString))
  }

  def deleteDataSet(datasetId: IdAble): Future[MessageResponse] = getMessageResponsePipeline {
    Put(toUrl(ServiceEndpoints.ROOT_DS + "/" + datasetId.toIdString),
        DataSetUpdateRequest(false))
  }

  def getSubreadSets: Future[Seq[SubreadServiceDataSet]] = getSubreadSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.SUBREADS))
  }

  def getSubreadSet(dsId: IdAble): Future[SubreadServiceDataSet] = getSubreadSetPipeline {
    Get(toDataSetUrl(DataSetTypes.SUBREADS, dsId))
  }

  def getSubreadSetDetails(dsId: IdAble): Future[SubreadSet] = getSubreadSetDetailsPipeline {
    Get(toDataSetResourcesUrl(DataSetTypes.SUBREADS, dsId, "details"))
  }

  def getSubreadSetReports(dsId: IdAble): Future[Seq[DataStoreReportFile]] = getReportsPipeline {
    Get(toDataSetResourcesUrl(DataSetTypes.SUBREADS, dsId, ServiceResourceTypes.REPORTS))
  }

  def getHdfSubreadSets: Future[Seq[HdfSubreadServiceDataSet]] = getHdfSubreadSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.HDFSUBREADS))
  }

  def getHdfSubreadSet(dsId: IdAble): Future[HdfSubreadServiceDataSet] = getHdfSubreadSetPipeline {
    Get(toDataSetUrl(DataSetTypes.HDFSUBREADS, dsId))
  }

  def getHdfSubreadSetDetails(dsId: IdAble): Future[HdfSubreadSet] = getHdfSubreadSetDetailsPipeline {
    Get(toDataSetResourcesUrl(DataSetTypes.HDFSUBREADS, dsId, "details"))
  }

  def getBarcodeSets: Future[Seq[BarcodeServiceDataSet]] = getBarcodeSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.BARCODES))
  }

  def getBarcodeSet(dsId: IdAble): Future[BarcodeServiceDataSet] = getBarcodeSetPipeline {
    Get(toDataSetUrl(DataSetTypes.BARCODES, dsId))
  }

  def getBarcodeSetDetails(dsId: IdAble): Future[BarcodeSet] = getBarcodeSetDetailsPipeline {
    Get(toDataSetResourcesUrl(DataSetTypes.BARCODES, dsId, "details"))
  }

  def getReferenceSets: Future[Seq[ReferenceServiceDataSet]] = getReferenceSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.REFERENCES))
  }

  def getReferenceSet(dsId: IdAble): Future[ReferenceServiceDataSet] = getReferenceSetPipeline {
    Get(toDataSetUrl(DataSetTypes.REFERENCES, dsId))
  }

  def getReferenceSetDetails(dsId: IdAble): Future[ReferenceSet] = getReferenceSetDetailsPipeline {
    Get(toDataSetResourcesUrl(DataSetTypes.REFERENCES, dsId, "details"))
  }

  def getGmapReferenceSets: Future[Seq[GmapReferenceServiceDataSet]] = getGmapReferenceSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.GMAPREFERENCES))
  }

  def getGmapReferenceSet(dsId: IdAble): Future[GmapReferenceServiceDataSet] = getGmapReferenceSetPipeline {
    Get(toDataSetUrl(DataSetTypes.GMAPREFERENCES, dsId))
  }

  def getGmapReferenceSetDetails(dsId: IdAble): Future[GmapReferenceSet] = getGmapReferenceSetDetailsPipeline {
    Get(toDataSetResourcesUrl(DataSetTypes.GMAPREFERENCES, dsId, "details"))
  }

  def getAlignmentSets: Future[Seq[AlignmentServiceDataSet]] = getAlignmentSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.ALIGNMENTS))
  }

  def getAlignmentSet(dsId: IdAble): Future[AlignmentServiceDataSet] = getAlignmentSetPipeline {
    Get(toDataSetUrl(DataSetTypes.ALIGNMENTS, dsId))
  }

  def getAlignmentSetDetails(dsId: IdAble): Future[AlignmentSet] = getAlignmentSetDetailsPipeline {
    Get(toDataSetResourcesUrl(DataSetTypes.ALIGNMENTS, dsId, "details"))
  }

  def getConsensusReadSets: Future[Seq[ConsensusReadServiceDataSet]] = getConsensusReadSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.CCSREADS))
  }

  def getConsensusReadSet(dsId: IdAble): Future[ConsensusReadServiceDataSet] = getConsensusReadSetPipeline {
    Get(toDataSetUrl(DataSetTypes.CCSREADS, dsId))
  }

  def getConsensusReadSetDetails(dsId: IdAble): Future[ConsensusReadSet] = getConsensusReadSetDetailsPipeline {
    Get(toDataSetResourcesUrl(DataSetTypes.CCSREADS, dsId, "details"))
  }

  def getConsensusAlignmentSets: Future[Seq[ConsensusAlignmentServiceDataSet]] = getConsensusAlignmentSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.CCSALIGNMENTS))
  }

  def getConsensusAlignmentSet(dsId: IdAble): Future[ConsensusAlignmentServiceDataSet] = getConsensusAlignmentSetPipeline {
    Get(toDataSetUrl(DataSetTypes.CCSALIGNMENTS, dsId))
  }

  def getConsensusAlignmentSetDetails(dsId: IdAble): Future[ConsensusAlignmentSet] = getConsensusAlignmentSetDetailsPipeline {
    Get(toDataSetResourcesUrl(DataSetTypes.CCSALIGNMENTS, dsId, "details"))
  }

  def getContigSets: Future[Seq[ContigServiceDataSet]] = getContigSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.CONTIGS))
  }

  def getContigSet(dsId: IdAble): Future[ContigServiceDataSet] = getContigSetPipeline {
    Get(toDataSetUrl(DataSetTypes.CONTIGS, dsId))
  }

  def getContigSetDetails(dsId: IdAble): Future[ContigSet] = getContigSetDetailsPipeline {
    Get(toDataSetResourcesUrl(DataSetTypes.CONTIGS, dsId, "details"))
  }

  protected def getJobDataStore(jobType: String, jobId: IdAble) : Future[Seq[DataStoreServiceFile]] = getDataStorePipeline {
    Get(toJobResourceUrl(jobType, jobId, ServiceResourceTypes.DATASTORE))
  }

  def getImportDatasetJobDataStore(jobId: IdAble) = getJobDataStore(JobTypes.IMPORT_DS, jobId)
  def getMergeDatasetJobDataStore(jobId: IdAble) = getJobDataStore(JobTypes.MERGE_DS, jobId)

  // FIXME how to convert to String?
  def getDataStoreFile(fileId: UUID): Future[HttpResponse] = respPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_DATASTORE + s"/${fileId}/download"))
  }

  /*def getDataStoreFileBinary(fileId: UUID): Future[Array[Byte]] = rawDataPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_DATASTORE + s"/${fileId}/download"))
  }*/

  def getReport(reportId: UUID): Future[Report] = getReportPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_DATASTORE + s"/${reportId}/download"))
  }

  protected def getJobReports(jobId: IdAble, jobType: String): Future[Seq[DataStoreReportFile]] = getReportsPipeline {
    Get(toJobResourceUrl(jobType, jobId, ServiceResourceTypes.REPORTS))
  }

  def getImportJobReports(jobId: IdAble) = getJobReports(jobId, JobTypes.IMPORT_DS)

  def getDataStoreFileResource(fileId: UUID, relpath: String): Future[HttpResponse] = respPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_DATASTORE + s"/${fileId}/resources?relpath=${relpath}"))
  }

  // Runs

  protected def getRunUrl(runId: UUID): String = toUrl(s"${ServiceEndpoints.ROOT_RUNS}/$runId")
  protected def getCollectionsUrl(runId: UUID): String = toUrl(s"${ServiceEndpoints.ROOT_RUNS}/$runId/collections")
  protected def getCollectionUrl(runId: UUID, collectionId: UUID): String = toUrl(s"${ServiceEndpoints.ROOT_RUNS}/$runId/collections/$collectionId")

  def getRuns: Future[Seq[RunSummary]] = getRunsPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_RUNS))
  }

  def getRun(runId: UUID): Future[Run] = getRunPipeline {
    Get(getRunUrl(runId))
  }

  def getCollections(runId: UUID): Future[Seq[CollectionMetadata]] = getCollectionsPipeline {
    Get(getCollectionsUrl(runId))
  }

  def getCollection(runId: UUID, collectionId: UUID): Future[CollectionMetadata] = getCollectionPipeline {
    Get(getCollectionUrl(runId, collectionId))
  }

  def createRun(dataModel: String): Future[RunSummary] = getRunSummaryPipeline {
    Post(toUrl(ServiceEndpoints.ROOT_RUNS), RunCreate(dataModel))
  }

  def updateRun(runId: UUID, dataModel: Option[String] = None, reserved: Option[Boolean] = None): Future[RunSummary] = getRunSummaryPipeline {
    Post(getRunUrl(runId), RunUpdate(dataModel, reserved))
  }

  def deleteRun(runId: UUID): Future[MessageResponse] = getMessageResponsePipeline {
    Delete(getRunUrl(runId))
  }

  // FIXME(nechols)(2016-09-21) these are currently broken pending fixes to
  // authentication
  def getProjects: Future[Seq[Project]] = getProjectsPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_PROJECTS))
  }

  def getProject(projectId: Int): Future[FullProject] = getProjectPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_PROJECTS + s"/$projectId"))
  }

  def createProject(name: String, description: String): Future[FullProject] = getProjectPipeline {
    Post(toUrl(ServiceEndpoints.ROOT_PROJECTS),
         ProjectRequest(name, description, None, None, None))
  }

  def updateProject(projectId: Int, request: ProjectRequest): Future[FullProject] = getProjectPipeline {
    Put(toUrl(ServiceEndpoints.ROOT_PROJECTS + s"/$projectId"), request)
  }
}
