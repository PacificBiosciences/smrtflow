package com.pacbio.secondary.smrtlink.client

import com.pacbio.secondary.smrtlink.models._
import com.pacbio.common.client._
import com.pacbio.common.models._

import akka.actor.ActorSystem
import spray.client.pipelining._
import scala.concurrent.duration._
//import spray.http.StatusCode._
import spray.http._
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol

import scala.reflect.Manifest._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}
import scala.xml.XML

import java.net.URL
import java.util.UUID

object ServicesClientJsonProtocol extends SmrtLinkJsonProtocols

trait ServiceEndpointsTrait {
  val ROOT_JM = "/secondary-analysis/job-manager"
  val ROOT_JOBS = ROOT_JM + "/jobs"
  val ROOT_DS = "/secondary-analysis/datasets"
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

  object ServiceEndpoints extends ServiceEndpointsTrait
  object ServiceResourceTypes extends ServiceResourceTypesTrait
  object JobTypes extends JobTypesTrait
  object DataSetTypes extends DataSetTypesTrait

  protected def toJobUrl(jobType: String, jobId: Int): String = {
    toUrl(s"${ServiceEndpoints.ROOT_JOBS}/${jobType}/${jobId}")
  }
  protected def toJobResourceUrl(jobType: String, jobId: Int, resourceType: String): String = {
    toUrl(s"${ServiceEndpoints.ROOT_JOBS}/${jobType}/${jobId}/${resourceType}")
  }
  protected def toJobResourceIdUrl(jobType: String, jobId: Int, resourceType: String, resourceId: UUID): String = {
    toUrl(s"${ServiceEndpoints.ROOT_JOBS}/${jobType}/${jobId}/${resourceType}/${resourceId}")
  }

  protected def toDataSetsUrl(dsType: String): String = {
    toUrl(s"${ServiceEndpoints.ROOT_DS}/${dsType}")
  }
  protected def toDataSetUrl(dsType: String, dsId: Int): String = {
    toUrl(s"${ServiceEndpoints.ROOT_DS}/${dsType}/${dsId}")
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
  def getDataSetMetaDataPipeline: HttpRequest => Future[DataSetMetaDataSet] = sendReceive ~> unmarshal[DataSetMetaDataSet]
  // TODO add type-parameterized getDataSetsPipeline
  def getSubreadSetsPipeline: HttpRequest => Future[Seq[SubreadServiceDataSet]] = sendReceive ~> unmarshal[Seq[SubreadServiceDataSet]]
  def getSubreadSetPipeline: HttpRequest => Future[SubreadServiceDataSet] = sendReceive ~> unmarshal[SubreadServiceDataSet]
  def getHdfSubreadSetsPipeline: HttpRequest => Future[Seq[HdfSubreadServiceDataSet]] = sendReceive ~> unmarshal[Seq[HdfSubreadServiceDataSet]]
  def getHdfSubreadSetPipeline: HttpRequest => Future[HdfSubreadServiceDataSet] = sendReceive ~> unmarshal[HdfSubreadServiceDataSet]
  def getReferenceSetsPipeline: HttpRequest => Future[Seq[ReferenceServiceDataSet]] = sendReceive ~> unmarshal[Seq[ReferenceServiceDataSet]]
  def getReferenceSetPipeline: HttpRequest => Future[ReferenceServiceDataSet] = sendReceive ~> unmarshal[ReferenceServiceDataSet]
  def getGmapReferenceSetsPipeline: HttpRequest => Future[Seq[GmapReferenceServiceDataSet]] = sendReceive ~> unmarshal[Seq[GmapReferenceServiceDataSet]]
  def getGmapReferenceSetPipeline: HttpRequest => Future[GmapReferenceServiceDataSet] = sendReceive ~> unmarshal[GmapReferenceServiceDataSet]
  def getBarcodeSetsPipeline: HttpRequest => Future[Seq[BarcodeServiceDataSet]] = sendReceive ~> unmarshal[Seq[BarcodeServiceDataSet]]
  def getBarcodeSetPipeline: HttpRequest => Future[BarcodeServiceDataSet] = sendReceive ~> unmarshal[BarcodeServiceDataSet]
  def getAlignmentSetsPipeline: HttpRequest => Future[Seq[AlignmentServiceDataSet]] = sendReceive ~> unmarshal[Seq[AlignmentServiceDataSet]]
  def getAlignmentSetPipeline: HttpRequest => Future[AlignmentServiceDataSet] = sendReceive ~> unmarshal[AlignmentServiceDataSet]
  def getConsensusReadSetsPipeline: HttpRequest => Future[Seq[CCSreadServiceDataSet]] = sendReceive ~> unmarshal[Seq[CCSreadServiceDataSet]]
  def getConsensusReadSetPipeline: HttpRequest => Future[CCSreadServiceDataSet] = sendReceive ~> unmarshal[CCSreadServiceDataSet]
  def getConsensusAlignmentSetsPipeline: HttpRequest => Future[Seq[ConsensusAlignmentServiceDataSet]] = sendReceive ~> unmarshal[Seq[ConsensusAlignmentServiceDataSet]]
  def getConsensusAlignmentSetPipeline: HttpRequest => Future[ConsensusAlignmentServiceDataSet] = sendReceive ~> unmarshal[ConsensusAlignmentServiceDataSet]
  def getContigSetsPipeline: HttpRequest => Future[Seq[ContigServiceDataSet]] = sendReceive ~> unmarshal[Seq[ContigServiceDataSet]]
  def getContigSetPipeline: HttpRequest => Future[ContigServiceDataSet] = sendReceive ~> unmarshal[ContigServiceDataSet]
  //def getDataSetPipeline[T: ClassManifest]: HttpRequest => Future[T] = sendReceive ~> unmarshal[T]
  def getDataStorePipeline: HttpRequest => Future[Seq[DataStoreServiceFile]] = sendReceive ~> unmarshal[Seq[DataStoreServiceFile]]
  def getEntryPointsPipeline: HttpRequest => Future[Seq[EngineJobEntryPoint]] = sendReceive ~> unmarshal[Seq[EngineJobEntryPoint]]
  def getJobReportsPipeline: HttpRequest => Future[Seq[DataStoreReportFile]] = sendReceive ~> unmarshal[Seq[DataStoreReportFile]]


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

  def getSubreadSetById(dsId: Int): Future[SubreadServiceDataSet] = getSubreadSetPipeline {
    Get(toDataSetUrl(DataSetTypes.SUBREADS, dsId))
  }

  def getHdfSubreadSets: Future[Seq[HdfSubreadServiceDataSet]] = getHdfSubreadSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.HDFSUBREADS))
  }

  def getHdfSubreadSetById(dsId: Int): Future[HdfSubreadServiceDataSet] = getHdfSubreadSetPipeline {
    Get(toDataSetUrl(DataSetTypes.HDFSUBREADS, dsId))
  }

  def getBarcodeSets: Future[Seq[BarcodeServiceDataSet]] = getBarcodeSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.BARCODES))
  }

  def getBarcodeSetById(dsId: Int): Future[BarcodeServiceDataSet] = getBarcodeSetPipeline {
    Get(toDataSetUrl(DataSetTypes.BARCODES, dsId))
  }

  def getReferenceSets: Future[Seq[ReferenceServiceDataSet]] = getReferenceSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.REFERENCES))
  }

  def getReferenceSetById(dsId: Int): Future[ReferenceServiceDataSet] = getReferenceSetPipeline {
    Get(toDataSetUrl(DataSetTypes.REFERENCES, dsId))
  }

  def getGmapReferenceSets: Future[Seq[GmapReferenceServiceDataSet]] = getGmapReferenceSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.GMAPREFERENCES))
  }

  def getGmapReferenceSetById(dsId: Int): Future[GmapReferenceServiceDataSet] = getGmapReferenceSetPipeline {
    Get(toDataSetUrl(DataSetTypes.GMAPREFERENCES, dsId))
  }

  def getAlignmentSets: Future[Seq[AlignmentServiceDataSet]] = getAlignmentSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.ALIGNMENTS))
  }

  def getAlignmentSetById(dsId: Int): Future[AlignmentServiceDataSet] = getAlignmentSetPipeline {
    Get(toDataSetUrl(DataSetTypes.ALIGNMENTS, dsId))
  }

  def getConsensusReadSets: Future[Seq[CCSreadServiceDataSet]] = getConsensusReadSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.CCSREADS))
  }

  def getConsensusReadSetById(dsId: Int): Future[CCSreadServiceDataSet] = getConsensusReadSetPipeline {
    Get(toDataSetUrl(DataSetTypes.CCSREADS, dsId))
  }

  def getConsensusAlignmentSets: Future[Seq[ConsensusAlignmentServiceDataSet]] = getConsensusAlignmentSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.CCSALIGNMENTS))
  }

  def getConsensusAlignmentSetById(dsId: Int): Future[ConsensusAlignmentServiceDataSet] = getConsensusAlignmentSetPipeline {
    Get(toDataSetUrl(DataSetTypes.CCSALIGNMENTS, dsId))
  }

  def getContigSets: Future[Seq[ContigServiceDataSet]] = getContigSetsPipeline {
    Get(toDataSetsUrl(DataSetTypes.CONTIGS))
  }

  def getContigSetById(dsId: Int): Future[ContigServiceDataSet] = getContigSetPipeline {
    Get(toDataSetUrl(DataSetTypes.CONTIGS, dsId))
  }

  def getAnalysisJobEntryPoints(jobId: Int): Future[Seq[EngineJobEntryPoint]] = getEntryPointsPipeline {
    Get(toJobResourceUrl(JobTypes.PB_PIPE, jobId, ServiceResourceTypes.ENTRY_POINTS))
  }

  protected def getJobDataStore(jobType: String, jobId: Int) : Future[Seq[DataStoreServiceFile]] = getDataStorePipeline {
    Get(toJobResourceUrl(jobType, jobId, ServiceResourceTypes.DATASTORE))
  }

  def getAnalysisJobDataStore(jobId: Int) = getJobDataStore(JobTypes.PB_PIPE, jobId)
  def getImportDatasetJobDataStore(jobId: Int) = getJobDataStore(JobTypes.IMPORT_DS, jobId)
  def getImportFastaJobDataStore(jobId: Int) = getJobDataStore(JobTypes.CONVERT_FASTA, jobId)
  def getMergeDatasetJobDataStore(jobId: Int) = getJobDataStore(JobTypes.MERGE_DS, jobId)
  def getImportBarcodesJobDataStore(jobId: Int) = getJobDataStore(JobTypes.CONVERT_BARCODES, jobId)

  protected def getJobReports(jobId: Int, jobType: String): Future[Seq[DataStoreReportFile]] = getJobReportsPipeline {
    Get(toJobResourceUrl(jobType, jobId, ServiceResourceTypes.REPORTS))
  }

  def getAnalysisJobReports(jobId: Int) = getJobReports(jobId, JobTypes.PB_PIPE)
  def getImportJobReports(jobId: Int) = getJobReports(jobId, JobTypes.IMPORT_DS)
  // XXX CONVERT_FASTA does not generate reports yet; what about MERGE_DS?
}
