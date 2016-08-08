package com.pacbio.secondary.smrtlink.client

import com.pacbio.secondary.analysis.engine.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.reports._
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
  import ReportModels._

  object ServiceEndpoints extends ServiceEndpointsTrait
  object ServiceResourceTypes extends ServiceResourceTypesTrait
  object JobTypes extends JobTypesTrait
  object DataSetTypes extends DataSetTypesTrait

  private def jobRoot(jobType: String) = s"${ServiceEndpoints.ROOT_JOBS}/${jobType}"
  protected def toJobUrl(jobType: String, jobId: Either[Int,UUID]): String = {
    jobId match {
      case Left(id) => toUrl(jobRoot(jobType) + s"/$id")
      case Right(uuid) => toUrl(jobRoot(jobType) + s"/$uuid")
    }
  }
  protected def toJobResourceUrl(jobType: String, jobId: Either[Int,UUID],
                                 resourceType: String): String = {
    jobId match {
      case Left(id) => toUrl(jobRoot(jobType) + s"/$id/$resourceType")
      case Right(uuid) => toUrl(jobRoot(jobType) + s"/$uuid/$resourceType")
    }
  }
  protected def toJobResourceIdUrl(jobType: String, jobId: Either[Int,UUID],
                                   resourceType: String, resourceId: UUID): String = {
    jobId match {
      case Left(id) => toUrl(jobRoot(jobType) + s"/$id/$resourceType/$resourceId")
      case Right(uuid) => toUrl(jobRoot(jobType) + s"/$uuid/$resourceType/$resourceId")
    }
  }

  private def dsRoot(dsType: String) = s"${ServiceEndpoints.ROOT_DS}/${dsType}"
  protected def toDataSetsUrl(dsType: String): String = {
    toUrl(dsRoot(dsType))
  }
  protected def toDataSetUrl(dsType: String, dsId: Either[Int,UUID]): String = {
    dsId match {
      case Left(id) => toUrl(dsRoot(dsType) + s"/$dsType/$id")
      case Right(uuid) => toUrl(dsRoot(dsType) + s"/$dsType/$uuid")
    }
  }
  protected def toDataSetResourcesUrl(dsType: String, dsId: Either[Int,UUID],
                                     resourceType: String): String = {
    dsId match {
      case Left(id) => toUrl(dsRoot(dsType) + s"/$id/$resourceType")
      case Right(uuid) => toUrl(dsRoot(dsType) + s"/$uuid/$resourceType")
    }
  }
  protected def toDataSetResourceUrl(dsType: String, dsId: Either[Int,UUID],
                                     resourceType: String, resourceId: UUID): String = {
    dsId match {
      case Left(id) => toUrl(dsRoot(dsType) + s"/$id/$resourceType/$resourceId")
      case Right(uuid) => toUrl(dsRoot(dsType) + s"/$uuid/$resourceType/$resourceId")
    }
  }

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

  def getDataStorePipeline: HttpRequest => Future[Seq[DataStoreServiceFile]] = sendReceive ~> unmarshal[Seq[DataStoreServiceFile]]
  def getEntryPointsPipeline: HttpRequest => Future[Seq[EngineJobEntryPoint]] = sendReceive ~> unmarshal[Seq[EngineJobEntryPoint]]
  def getReportsPipeline: HttpRequest => Future[Seq[DataStoreReportFile]] = sendReceive ~> unmarshal[Seq[DataStoreReportFile]]
  def getReportPipeline: HttpRequest => Future[Report] = sendReceive ~> unmarshal[Report]

  protected def getRunsPipeline: HttpRequest => Future[Seq[RunSummary]] = sendReceive ~> unmarshal[Seq[RunSummary]]
  protected def getRunSummaryPipeline: HttpRequest => Future[RunSummary] = sendReceive ~> unmarshal[RunSummary]
  protected def getRunPipeline: HttpRequest => Future[Run] = sendReceive ~> unmarshal[Run]
  protected def getCollectionsPipeline: HttpRequest => Future[Seq[CollectionMetadata]] = sendReceive ~> unmarshal[Seq[CollectionMetadata]]
  protected def getCollectionPipeline: HttpRequest => Future[CollectionMetadata] = sendReceive ~> unmarshal[CollectionMetadata]

  protected def getMessageResponsePipeline: HttpRequest => Future[MessageResponse] = sendReceive ~> unmarshal[MessageResponse]
  def getDataSetByAny(datasetId: Either[Int, UUID]): Future[DataSetMetaDataSet] = {
    datasetId match {
      case Left(x) => getDataSetById(x)
      case Right(x) => getDataSetByUuid(x)
    }
  }

  def getDataSetById(datasetId: Int): Future[DataSetMetaDataSet] = getDataSetMetaDataPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_DS + "/" + datasetId))
  }

  def getDataSetByUuid(datasetId: UUID): Future[DataSetMetaDataSet] = getDataSetMetaDataPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_DS + "/" + datasetId))
  }

  def getSubreadSets: Future[Seq[SubreadServiceDataSet]] = getSubreadSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.SUBREADS))
  }

  def getSubreadSet(dsId: Either[Int,UUID]): Future[SubreadServiceDataSet] = getSubreadSetPipeline {
    Get(toDataSetUrl(DataSetTypes.SUBREADS, dsId))
  }
  def getSubreadSetById(dsId: Int) = getSubreadSet(Left(dsId))
  def getSubreadSetByUuid(dsId: UUID) = getSubreadSet(Right(dsId))

  def getSubreadSetReports(dsId: Either[Int,UUID]): Future[Seq[DataStoreReportFile]] = getReportsPipeline {
    Get(toDataSetResourcesUrl(DataSetTypes.SUBREADS, dsId, ServiceResourceTypes.REPORTS))
  }
  def getSubreadSetReport(dsId: Either[Int,UUID], reportId: UUID): Future[Report] = getReportPipeline {
    Get(toDataSetResourceUrl(DataSetTypes.SUBREADS, dsId, ServiceResourceTypes.REPORTS, reportId))
  }

  def getHdfSubreadSets: Future[Seq[HdfSubreadServiceDataSet]] = getHdfSubreadSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.HDFSUBREADS))
  }

  def getHdfSubreadSet(dsId: Either[Int,UUID]): Future[HdfSubreadServiceDataSet] = getHdfSubreadSetPipeline {
    Get(toDataSetUrl(DataSetTypes.HDFSUBREADS, dsId))
  }
  def getHdfSubreadSetById(dsId: Int) = getHdfSubreadSet(Left(dsId))
  def getHdfSubreadSetByUuid(dsId: UUID) = getHdfSubreadSet(Right(dsId))

  def getBarcodeSets: Future[Seq[BarcodeServiceDataSet]] = getBarcodeSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.BARCODES))
  }

  def getBarcodeSet(dsId: Either[Int,UUID]): Future[BarcodeServiceDataSet] = getBarcodeSetPipeline {
    Get(toDataSetUrl(DataSetTypes.BARCODES, dsId))
  }
  def getBarcodeSetById(dsId: Int) = getBarcodeSet(Left(dsId))
  def getBarcodeSetByUuid(dsId: UUID) = getBarcodeSet(Right(dsId))

  def getReferenceSets: Future[Seq[ReferenceServiceDataSet]] = getReferenceSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.REFERENCES))
  }

  def getReferenceSet(dsId: Either[Int,UUID]): Future[ReferenceServiceDataSet] = getReferenceSetPipeline {
    Get(toDataSetUrl(DataSetTypes.REFERENCES, dsId))
  }
  def getReferenceSetById(dsId: Int) = getReferenceSet(Left(dsId))
  def getReferenceSetByUuid(dsId: UUID) = getReferenceSet(Right(dsId))

  def getGmapReferenceSets: Future[Seq[GmapReferenceServiceDataSet]] = getGmapReferenceSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.GMAPREFERENCES))
  }

  def getGmapReferenceSet(dsId: Either[Int,UUID]): Future[GmapReferenceServiceDataSet] = getGmapReferenceSetPipeline {
    Get(toDataSetUrl(DataSetTypes.GMAPREFERENCES, dsId))
  }
  def getGmapReferenceSetById(dsId: Int) = getGmapReferenceSet(Left(dsId))
  def getGmapReferenceSetByUuid(dsId: UUID) = getGmapReferenceSet(Right(dsId))

  def getAlignmentSets: Future[Seq[AlignmentServiceDataSet]] = getAlignmentSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.ALIGNMENTS))
  }

  def getAlignmentSet(dsId: Either[Int,UUID]): Future[AlignmentServiceDataSet] = getAlignmentSetPipeline {
    Get(toDataSetUrl(DataSetTypes.ALIGNMENTS, dsId))
  }
  def getAlignmentSetById(dsId: Int) = getAlignmentSet(Left(dsId))
  def getAlignmentSetByUuid(dsId: UUID) = getAlignmentSet(Right(dsId))

  def getConsensusReadSets: Future[Seq[ConsensusReadServiceDataSet]] = getConsensusReadSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.CCSREADS))
  }

  def getConsensusReadSet(dsId: Either[Int,UUID]): Future[ConsensusReadServiceDataSet] = getConsensusReadSetPipeline {
    Get(toDataSetUrl(DataSetTypes.CCSREADS, dsId))
  }
  def getConsensusReadSetById(dsId: Int) = getConsensusReadSet(Left(dsId))
  def getConsensusReadSetByUuid(dsId: UUID) = getConsensusReadSet(Right(dsId))

  def getConsensusAlignmentSets: Future[Seq[ConsensusAlignmentServiceDataSet]] = getConsensusAlignmentSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.CCSALIGNMENTS))
  }

  def getConsensusAlignmentSet(dsId: Either[Int,UUID]): Future[ConsensusAlignmentServiceDataSet] = getConsensusAlignmentSetPipeline {
    Get(toDataSetUrl(DataSetTypes.CCSALIGNMENTS, dsId))
  }
  def getConsensusAlignmentSetById(dsId: Int) = getConsensusAlignmentSet(Left(dsId))
  def getConsensusAlignmentSetByUuid(dsId: UUID) = getConsensusAlignmentSet(Right(dsId))

  def getContigSets: Future[Seq[ContigServiceDataSet]] = getContigSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.CONTIGS))
  }

  def getContigSet(dsId: Either[Int,UUID]): Future[ContigServiceDataSet] = getContigSetPipeline {
    Get(toDataSetUrl(DataSetTypes.CONTIGS, dsId))
  }
  def getContigSetById(dsId: Int) = getContigSet(Left(dsId))
  def getContigSetByUuid(dsId: UUID) = getContigSet(Right(dsId))

  def getAnalysisJobEntryPoints(jobId: Int): Future[Seq[EngineJobEntryPoint]] = getEntryPointsPipeline {
    Get(toJobResourceUrl(JobTypes.PB_PIPE, Left(jobId), ServiceResourceTypes.ENTRY_POINTS))
  }

  protected def getJobDataStore(jobType: String, jobId: Either[Int,UUID]) : Future[Seq[DataStoreServiceFile]] = getDataStorePipeline {
    Get(toJobResourceUrl(jobType, jobId, ServiceResourceTypes.DATASTORE))
  }

  def getAnalysisJobDataStore(jobId: Either[Int,UUID]) = getJobDataStore(JobTypes.PB_PIPE, jobId)
  def getImportDatasetJobDataStore(jobId: Either[Int,UUID]) = getJobDataStore(JobTypes.IMPORT_DS, jobId)
  def getImportFastaJobDataStore(jobId: Either[Int,UUID]) = getJobDataStore(JobTypes.CONVERT_FASTA, jobId)
  def getMergeDatasetJobDataStore(jobId: Either[Int,UUID]) = getJobDataStore(JobTypes.MERGE_DS, jobId)
  def getImportBarcodesJobDataStore(jobId: Either[Int,UUID]) = getJobDataStore(JobTypes.CONVERT_BARCODES, jobId)

  protected def getJobReports(jobId: Either[Int,UUID], jobType: String): Future[Seq[DataStoreReportFile]] = getReportsPipeline {
    Get(toJobResourceUrl(jobType, jobId, ServiceResourceTypes.REPORTS))
  }

  def getAnalysisJobReports(jobId: Either[Int,UUID]) = getJobReports(jobId, JobTypes.PB_PIPE)
  def getImportJobReports(jobId: Either[Int,UUID]) = getJobReports(jobId, JobTypes.IMPORT_DS)
  // XXX CONVERT_FASTA does not generate reports yet; what about MERGE_DS?

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
}
