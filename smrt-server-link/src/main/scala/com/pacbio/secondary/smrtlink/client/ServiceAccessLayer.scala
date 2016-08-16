package com.pacbio.secondary.smrtlink.client

import com.pacbio.secondary.analysis.engine.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.reports._
import com.pacbio.common.models._
import com.pacbio.common.client._

import akka.actor.ActorSystem
import spray.client.pipelining._
import scala.concurrent.duration._

import spray.http._
import spray.httpx.SprayJsonSupport
import spray.httpx.unmarshalling.FromResponseUnmarshaller
import spray.json.DefaultJsonProtocol

import scala.concurrent.Future

import java.net.URL
import java.util.UUID

object ServicesClientJsonProtocol extends SmrtLinkJsonProtocols with ReportJsonProtocol

trait ServiceEndpointsTrait {
  val ROOT_JM = "/secondary-analysis/job-manager"
  val ROOT_JOBS = ROOT_JM + "/jobs"
  val ROOT_DS = "/secondary-analysis/datasets"
  val ROOT_RUNS = "/smrt-link/runs"
  val ROOT_DATASTORE = "/secondary-analysis/datastore-files"
  val ROOT_PROJECTS = "/secondary-analysis/projects"
}

trait ServiceResourceTypesTrait {
  val REPORTS = "reports"
  val DATASTORE = "datastore"
  val ENTRY_POINTS = "entry-points"
}

// FIXME this should probably use com.pacbio.secondary.analysis.jobtypes
trait JobTypesTrait {
  val IMPORT_DS = "import-dataset"
  val IMPORT_DSTORE = "import-datastore"
  val MERGE_DS = "merge-datasets"
  val PB_PIPE = "pbsmrtpipe"
  val MOCK_PB_PIPE = "mock-pbsmrtpipe"
  val CONVERT_FASTA = "convert-fasta-reference"
  val CONVERT_BARCODES = "convert-fasta-barcodes"
  val CONVERT_MOVIE = "convert-rs-movie"
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

class SmrtLinkServiceAccessLayer(baseUrl: URL)(implicit actorSystem: ActorSystem) extends ServiceAccessLayer(baseUrl)(actorSystem) {

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
      ServiceEndpoints.ROOT_JOBS + "/" + JobTypes.CONVERT_FASTA,
      ServiceEndpoints.ROOT_JOBS + "/" + JobTypes.CONVERT_BARCODES,
      ServiceEndpoints.ROOT_JOBS + "/" + JobTypes.PB_PIPE,
      ServiceEndpoints.ROOT_DS + "/" + DataSetTypes.SUBREADS,
      ServiceEndpoints.ROOT_DS + "/" + DataSetTypes.HDFSUBREADS,
      ServiceEndpoints.ROOT_DS + "/" + DataSetTypes.REFERENCES,
      ServiceEndpoints.ROOT_DS + "/" + DataSetTypes.BARCODES)


  // Pipelines and serialization
  protected def getDataSetMetaDataPipeline: HttpRequest => Future[DataSetMetaDataSet] = sendReceive ~> unmarshal[DataSetMetaDataSet]

  private def getDataSetPipeline[T <: ServiceDataSetMetadata](implicit fmt: FromResponseUnmarshaller[T]): HttpRequest => Future[T] = sendReceive ~> unmarshal[T]
  protected def getSubreadSetPipeline = getDataSetPipeline[SubreadServiceDataSet]
  protected def getHdfSubreadSetPipeline = getDataSetPipeline[HdfSubreadServiceDataSet]
  protected def getReferenceSetPipeline = getDataSetPipeline[ReferenceServiceDataSet]
  protected def getGmapReferenceSetPipeline = getDataSetPipeline[GmapReferenceServiceDataSet]
  protected def getBarcodeSetPipeline = getDataSetPipeline[BarcodeServiceDataSet]
  protected def getAlignmentSetPipeline = getDataSetPipeline[AlignmentServiceDataSet]
  protected def getConsensusReadSetPipeline = getDataSetPipeline[ConsensusReadServiceDataSet]
  protected def getConsensusAlignmentSetPipeline = getDataSetPipeline[ConsensusAlignmentServiceDataSet]
  protected def getContigSetPipeline = getDataSetPipeline[ContigServiceDataSet]

  private def getDataSetsPipeline[T <: ServiceDataSetMetadata](implicit fmt: FromResponseUnmarshaller[Seq[T]]): HttpRequest => Future[Seq[T]] = sendReceive ~> unmarshal[Seq[T]]
  protected def getSubreadSetsPipeline = getDataSetsPipeline[SubreadServiceDataSet]
  protected def getHdfSubreadSetsPipeline = getDataSetsPipeline[HdfSubreadServiceDataSet]
  protected def getReferenceSetsPipeline = getDataSetsPipeline[ReferenceServiceDataSet]
  protected def getGmapReferenceSetsPipeline = getDataSetsPipeline[GmapReferenceServiceDataSet]
  protected def getBarcodeSetsPipeline = getDataSetsPipeline[BarcodeServiceDataSet]
  protected def getAlignmentSetsPipeline = getDataSetsPipeline[AlignmentServiceDataSet]
  protected def getConsensusReadSetsPipeline = getDataSetsPipeline[ConsensusReadServiceDataSet]
  protected def getConsensusAlignmentSetsPipeline = getDataSetsPipeline[ConsensusAlignmentServiceDataSet]
  protected def getContigSetsPipeline = getDataSetsPipeline[ContigServiceDataSet]

  protected def getDataStorePipeline: HttpRequest => Future[Seq[DataStoreServiceFile]] = sendReceive ~> unmarshal[Seq[DataStoreServiceFile]]
  protected def getEntryPointsPipeline: HttpRequest => Future[Seq[EngineJobEntryPoint]] = sendReceive ~> unmarshal[Seq[EngineJobEntryPoint]]
  protected def getReportsPipeline: HttpRequest => Future[Seq[DataStoreReportFile]] = sendReceive ~> unmarshal[Seq[DataStoreReportFile]]
  protected def getReportPipeline: HttpRequest => Future[Report] = sendReceive ~> unmarshal[Report]

  protected def getRunsPipeline: HttpRequest => Future[Seq[RunSummary]] = sendReceive ~> unmarshal[Seq[RunSummary]]
  protected def getRunSummaryPipeline: HttpRequest => Future[RunSummary] = sendReceive ~> unmarshal[RunSummary]
  protected def getRunPipeline: HttpRequest => Future[Run] = sendReceive ~> unmarshal[Run]
  protected def getCollectionsPipeline: HttpRequest => Future[Seq[CollectionMetadata]] = sendReceive ~> unmarshal[Seq[CollectionMetadata]]
  protected def getCollectionPipeline: HttpRequest => Future[CollectionMetadata] = sendReceive ~> unmarshal[CollectionMetadata]
  protected def getProjectsPipeline: HttpRequest => Future[Seq[Project]] = sendReceive ~> unmarshal[Seq[Project]]
  protected def getProjectPipeline: HttpRequest => Future[FullProject] = sendReceive ~> unmarshal[FullProject]

  protected def getMessageResponsePipeline: HttpRequest => Future[MessageResponse] = sendReceive ~> unmarshal[MessageResponse]

  def getDataSet(datasetId: IdAble): Future[DataSetMetaDataSet] = getDataSetMetaDataPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_DS + "/" + datasetId.toIdString))
  }

  def getSubreadSets: Future[Seq[SubreadServiceDataSet]] = getSubreadSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.SUBREADS))
  }

  def getSubreadSet(dsId: IdAble): Future[SubreadServiceDataSet] = getSubreadSetPipeline {
    Get(toDataSetUrl(DataSetTypes.SUBREADS, dsId))
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

  def getBarcodeSets: Future[Seq[BarcodeServiceDataSet]] = getBarcodeSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.BARCODES))
  }

  def getBarcodeSet(dsId: IdAble): Future[BarcodeServiceDataSet] = getBarcodeSetPipeline {
    Get(toDataSetUrl(DataSetTypes.BARCODES, dsId))
  }

  def getReferenceSets: Future[Seq[ReferenceServiceDataSet]] = getReferenceSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.REFERENCES))
  }

  def getReferenceSet(dsId: IdAble): Future[ReferenceServiceDataSet] = getReferenceSetPipeline {
    Get(toDataSetUrl(DataSetTypes.REFERENCES, dsId))
  }

  def getGmapReferenceSets: Future[Seq[GmapReferenceServiceDataSet]] = getGmapReferenceSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.GMAPREFERENCES))
  }

  def getGmapReferenceSet(dsId: IdAble): Future[GmapReferenceServiceDataSet] = getGmapReferenceSetPipeline {
    Get(toDataSetUrl(DataSetTypes.GMAPREFERENCES, dsId))
  }

  def getAlignmentSets: Future[Seq[AlignmentServiceDataSet]] = getAlignmentSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.ALIGNMENTS))
  }

  def getAlignmentSet(dsId: IdAble): Future[AlignmentServiceDataSet] = getAlignmentSetPipeline {
    Get(toDataSetUrl(DataSetTypes.ALIGNMENTS, dsId))
  }

  def getConsensusReadSets: Future[Seq[ConsensusReadServiceDataSet]] = getConsensusReadSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.CCSREADS))
  }

  def getConsensusReadSet(dsId: IdAble): Future[ConsensusReadServiceDataSet] = getConsensusReadSetPipeline {
    Get(toDataSetUrl(DataSetTypes.CCSREADS, dsId))
  }

  def getConsensusAlignmentSets: Future[Seq[ConsensusAlignmentServiceDataSet]] = getConsensusAlignmentSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.CCSALIGNMENTS))
  }

  def getConsensusAlignmentSet(dsId: IdAble): Future[ConsensusAlignmentServiceDataSet] = getConsensusAlignmentSetPipeline {
    Get(toDataSetUrl(DataSetTypes.CCSALIGNMENTS, dsId))
  }

  def getContigSets: Future[Seq[ContigServiceDataSet]] = getContigSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.CONTIGS))
  }

  def getContigSet(dsId: IdAble): Future[ContigServiceDataSet] = getContigSetPipeline {
    Get(toDataSetUrl(DataSetTypes.CONTIGS, dsId))
  }

  // FIXME I think this still only works with Int
  def getAnalysisJobEntryPoints(jobId: Int): Future[Seq[EngineJobEntryPoint]] = getEntryPointsPipeline {
    Get(toJobResourceUrl(JobTypes.PB_PIPE, jobId, ServiceResourceTypes.ENTRY_POINTS))
  }

  protected def getJobDataStore(jobType: String, jobId: IdAble) : Future[Seq[DataStoreServiceFile]] = getDataStorePipeline {
    Get(toJobResourceUrl(jobType, jobId, ServiceResourceTypes.DATASTORE))
  }

  def getAnalysisJobDataStore(jobId: IdAble) = getJobDataStore(JobTypes.PB_PIPE, jobId)
  def getImportDatasetJobDataStore(jobId: IdAble) = getJobDataStore(JobTypes.IMPORT_DS, jobId)
  def getImportFastaJobDataStore(jobId: IdAble) = getJobDataStore(JobTypes.CONVERT_FASTA, jobId)
  def getMergeDatasetJobDataStore(jobId: IdAble) = getJobDataStore(JobTypes.MERGE_DS, jobId)
  def getImportBarcodesJobDataStore(jobId: IdAble) = getJobDataStore(JobTypes.CONVERT_BARCODES, jobId)

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

  def getAnalysisJobReports(jobId: IdAble) = getJobReports(jobId, JobTypes.PB_PIPE)
  def getImportJobReports(jobId: IdAble) = getJobReports(jobId, JobTypes.IMPORT_DS)
  // XXX CONVERT_FASTA does not generate reports yet; what about MERGE_DS?

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
}
