// derived from PrimaryClient.scala in PAWS
package com.pacbio.secondary.smrttools.client

import com.pacbio.secondary.analysis.constants.{GlobalConstants, FileTypes}
import com.pacbio.secondary.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.smrtserver.models._
import com.pacbio.secondary.smrtlink.models._

import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.reports.ReportModels
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

//FIXME(mkocher)(2016-2-2): This needs to be centralized.
object SmrtLinkServicesModels {

  case class CreateDataSet(path: String, datasetType: String)
  case class CreateReferenceSet(path: String, name: String, organism: String,
                                ploidy: String)
}

object ServicesClientJsonProtocol extends SmrtLinkJsonProtocols with SecondaryAnalysisJsonProtocols

/**
 * Client to Primary Services
 */

object ServiceEndpoints {
  val ROOT_JM = "/secondary-analysis/job-manager"
  val ROOT_JOBS = ROOT_JM + "/jobs"
  val ROOT_DS = "/secondary-analysis/datasets"
  val ROOT_PT = "/secondary-analysis/resolved-pipeline-templates"
}

object ServiceResourceTypes {
  val REPORTS = "reports"
  val DATASTORE = "datastore"
  val ENTRY_POINTS = "entry-points"
}

// FIXME this should probably use com.pacbio.secondary.analysis.jobtypes
object JobTypes {
  val IMPORT_DS = "import-dataset"
  val IMPORT_DSTORE = "import-datastore"
  val MERGE_DS = "merge-datasets"
  val PB_PIPE = "pbsmrtpipe"
  val MOCK_PB_PIPE = "mock-pbsmrtpipe"
  val CONVERT_FASTA = "convert-fasta-reference"
}

// FIXME this for sure needs to be somewhere else
object DataSetTypes {
  val SUBREADS = "subreads"
  val HDFSUBREADS = "hdfsubreads"
  val REFERENCES = "references"
  val BARCODES = "barcodes"
  val CCSREADS = "ccsreads"
  val ALIGNMENTS = "alignments"
}

class ServiceAccessLayer(val baseUrl: URL)(implicit actorSystem: ActorSystem) {

  import ServicesClientJsonProtocol._
  import SmrtLinkServicesModels._
  import SprayJsonSupport._
  import ReportModels._

  // Context to run futures in
  implicit val executionContext = actorSystem.dispatcher

  private def toUrl(segment: String): String =
    new URL(baseUrl.getProtocol, baseUrl.getHost, baseUrl.getPort, segment).toString
  private def toUiRootUrl(port: Int): String =
    new URL(baseUrl.getProtocol, baseUrl.getHost, port, "/").toString
  private def toJobUrl(jobType: String, jobId: Int): String = {
    toUrl(s"${ServiceEndpoints.ROOT_JOBS}/${jobType}/${jobId}")
  }
  private def toJobResourceUrl(jobType: String, jobId: Int, resourceType: String): String = {
    toUrl(s"${ServiceEndpoints.ROOT_JOBS}/${jobType}/${jobId}/${resourceType}")
  }
  private def toJobResourceIdUrl(jobType: String, jobId: Int, resourceType: String, resourceId: UUID): String = {
    toUrl(s"${ServiceEndpoints.ROOT_JOBS}/${jobType}/${jobId}/${resourceType}/${resourceId}")
  }

  private def toDataSetsUrl(dsType: String): String = {
    toUrl(s"${ServiceEndpoints.ROOT_DS}/${dsType}")
  }
  private def toDataSetUrl(dsType: String, dsId: Int): String = {
    toUrl(s"${ServiceEndpoints.ROOT_DS}/${dsType}/${dsId}")
  }

  // Pipelines and serialization
  def respPipeline: HttpRequest => Future[HttpResponse] = sendReceive
  def rawJsonPipeline: HttpRequest => Future[String] = sendReceive ~> unmarshal[String]
  def serviceStatusPipeline: HttpRequest => Future[ServiceStatus] = sendReceive ~> unmarshal[ServiceStatus]
  def getDataSetMetaDataPipeline: HttpRequest => Future[DataSetMetaDataSet] = sendReceive ~> unmarshal[DataSetMetaDataSet]
  // TODO add type-parameterized getDataSetsPipeline
  def getSubreadSetsPipeline: HttpRequest => Future[Seq[SubreadServiceDataSet]] = sendReceive ~> unmarshal[Seq[SubreadServiceDataSet]]
  def getSubreadSetPipeline: HttpRequest => Future[SubreadServiceDataSet] = sendReceive ~> unmarshal[SubreadServiceDataSet]
  def getHdfSubreadSetsPipeline: HttpRequest => Future[Seq[HdfSubreadServiceDataSet]] = sendReceive ~> unmarshal[Seq[HdfSubreadServiceDataSet]]
  def getHdfSubreadSetPipeline: HttpRequest => Future[HdfSubreadServiceDataSet] = sendReceive ~> unmarshal[HdfSubreadServiceDataSet]
  def getReferenceSetsPipeline: HttpRequest => Future[Seq[ReferenceServiceDataSet]] = sendReceive ~> unmarshal[Seq[ReferenceServiceDataSet]]
  def getReferenceSetPipeline: HttpRequest => Future[ReferenceServiceDataSet] = sendReceive ~> unmarshal[ReferenceServiceDataSet]
  def getBarcodeSetsPipeline: HttpRequest => Future[Seq[BarcodeServiceDataSet]] = sendReceive ~> unmarshal[Seq[BarcodeServiceDataSet]]
  def getBarcodeSetPipeline: HttpRequest => Future[BarcodeServiceDataSet] = sendReceive ~> unmarshal[BarcodeServiceDataSet]
  def getAlignmentSetsPipeline: HttpRequest => Future[Seq[AlignmentServiceDataSet]] = sendReceive ~> unmarshal[Seq[AlignmentServiceDataSet]]
  def getAlignmentSetPipeline: HttpRequest => Future[AlignmentServiceDataSet] = sendReceive ~> unmarshal[AlignmentServiceDataSet]
  def getConsensusReadSetsPipeline: HttpRequest => Future[Seq[CCSreadServiceDataSet]] = sendReceive ~> unmarshal[Seq[CCSreadServiceDataSet]]
  def getConsensusReadSetPipeline: HttpRequest => Future[CCSreadServiceDataSet] = sendReceive ~> unmarshal[CCSreadServiceDataSet]
  //def getDataSetPipeline[T: ClassManifest]: HttpRequest => Future[T] = sendReceive ~> unmarshal[T]
  def getJobPipeline: HttpRequest => Future[EngineJob] = sendReceive ~> unmarshal[EngineJob]
  // XXX this fails when createdBy is an object instead of a string
  def getJobsPipeline: HttpRequest => Future[Seq[EngineJob]] = sendReceive ~> unmarshal[Seq[EngineJob]]
  def getDataStorePipeline: HttpRequest => Future[Seq[DataStoreServiceFile]] = sendReceive ~> unmarshal[Seq[DataStoreServiceFile]]
  def runJobPipeline: HttpRequest => Future[EngineJob] = sendReceive ~> unmarshal[EngineJob]
  def getEntryPointsPipeline: HttpRequest => Future[Seq[EngineJobEntryPoint]] = sendReceive ~> unmarshal[Seq[EngineJobEntryPoint]]
  //def getReportPipeline: HttpRequest => Future[Report] = sendReceive ~> unmarshal[Report]
  def getJobReportsPipeline: HttpRequest => Future[Seq[DataStoreReportFile]] = sendReceive ~> unmarshal[Seq[DataStoreReportFile]]
  def getPipelineTemplatePipeline: HttpRequest => Future[PipelineTemplate] = sendReceive ~> unmarshal[PipelineTemplate]

  val statusUrl = toUrl("/status")

  def getStatus: Future[ServiceStatus] = serviceStatusPipeline {
    Get(statusUrl)
  }

  def getEndpoint(endpointUrl: String): Future[HttpResponse] = respPipeline {
    Get(endpointUrl)
  }

  def getServiceEndpoint(endpointPath: String): Future[HttpResponse] = respPipeline {
    Get(toUrl(endpointPath))
  }

  def checkEndpoint(endpointUrl: String): Int = {
    Try {
      Await.result(getEndpoint(endpointUrl), 20 seconds)
    } match {
      // FIXME need to make this more generic
      case Success(x) => {
        x.status match {
          case StatusCodes.Success(_) =>
            println(s"found endpoint ${endpointUrl}")
            0
          case _ =>
            println(s"error retrieving ${endpointUrl}: ${x.status}")
            1
        }
      }
      case Failure(err) => {
        println(s"failed to retrieve endpoint ${endpointUrl}")
        println(s"${err}")
        1
      }
    }
  }

  def checkServiceEndpoint(endpointPath: String): Int = checkEndpoint(toUrl(endpointPath))

  def checkUiEndpoint(uiPort: Int): Int = checkEndpoint(toUiRootUrl(uiPort))

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

  private def getJobsByType(jobType: String): Future[Seq[EngineJob]] = getJobsPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_JOBS + "/" + jobType))
  }

  def getAnalysisJobs: Future[Seq[EngineJob]] = {
    getJobsByType(JobTypes.PB_PIPE)
  }

  def getImportJobs: Future[Seq[EngineJob]] = {
    getJobsByType(JobTypes.IMPORT_DS)
  }

  def getMergeJobs: Future[Seq[EngineJob]] = {
    getJobsByType(JobTypes.MERGE_DS)
  }

  def getFastaConvertJobs: Future[Seq[EngineJob]] = {
    getJobsByType(JobTypes.CONVERT_FASTA)
  }

  def getJobByAny(jobId: Either[Int, UUID]): Future[EngineJob] = {
    jobId match {
      case Left(x) => getJobById(x)
      case Right(x) => getJobByUuid(x)
    }
  }

  def getJobById(jobId: Int): Future[EngineJob] = getJobPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_JOBS + "/" + jobId))
  }

  def getJobByUuid(jobId: UUID): Future[EngineJob] = getJobPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_JOBS + "/" + jobId))
  }

  def getJobByTypeAndId(jobType: String, jobId: Int): Future[EngineJob] = getJobPipeline {
    Get(toJobUrl(jobType, jobId))
  }

  def getAnalysisJobById(jobId: Int): Future[EngineJob] = {
    getJobByTypeAndId(JobTypes.PB_PIPE, jobId)
  }

  def getAnalysisJobEntryPoints(jobId: Int): Future[Seq[EngineJobEntryPoint]] = getEntryPointsPipeline {
    Get(toJobResourceUrl(JobTypes.PB_PIPE, jobId, ServiceResourceTypes.ENTRY_POINTS))
  }

  private def getJobDataStore(jobType: String, jobId: Int) : Future[Seq[DataStoreServiceFile]] = getDataStorePipeline {
    Get(toJobResourceUrl(jobType, jobId, ServiceResourceTypes.DATASTORE))
  }

  def getAnalysisJobDataStore(jobId: Int): Future[Seq[DataStoreServiceFile]] = {
    getJobDataStore(JobTypes.PB_PIPE, jobId)
  }

  def getImportDatasetJobDataStore(jobId: Int): Future[Seq[DataStoreServiceFile]] = {
    getJobDataStore(JobTypes.IMPORT_DS, jobId)
  }

  def getImportFastaJobDataStore(jobId: Int): Future[Seq[DataStoreServiceFile]] = {
    getJobDataStore(JobTypes.CONVERT_FASTA, jobId)
  }

  def getMergeDatasetJobDataStore(jobId: Int): Future[Seq[DataStoreServiceFile]] = {
    getJobDataStore(JobTypes.MERGE_DS, jobId)
  }

  def getAnalysisJobReports(jobId: Int): Future[Seq[DataStoreReportFile]] = getJobReportsPipeline {
    Get(toJobResourceUrl(JobTypes.PB_PIPE, jobId, ServiceResourceTypes.REPORTS))
  }
/*
  private def getJobReportDetails(jobType: String, jobId: Int, reportId: UUID): Future[Report] = getReportPipeline {
    Get(toJobResourceIdUrl(JobTypes.PB_PIPE, jobId, ServiceResourceTypes.REPORTS, reportId)) 
  }

  def getAnalysisJobReportDetails(jobId: Int, reportId: UUID): Future[Report] = {
    getJobReportDetails(JobTypes.PB_PIPE, jobId, reportId)
  }
*/
  def importDataSet(path: String, dsMetaType: String): Future[EngineJob] = runJobPipeline {
    Post(toUrl(ServiceEndpoints.ROOT_JOBS + "/" + JobTypes.IMPORT_DS),
         CreateDataSet(path, dsMetaType))
  }

  def importFasta(path: String, name: String, organism: String, ploidy: String): Future[EngineJob] = runJobPipeline {
    Post(toUrl(ServiceEndpoints.ROOT_JOBS + "/" + JobTypes.CONVERT_FASTA),
         CreateReferenceSet(path, name, organism, ploidy))
  }

  def getPipelineTemplateJson(pipelineId: String): Future[String] = rawJsonPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_PT + "/" + pipelineId))
  }

  // FIXME this doesn't quite work...
  def getPipelineTemplate(pipelineId: String): Future[PipelineTemplate] = getPipelineTemplatePipeline {
    Get(toUrl(ServiceEndpoints.ROOT_PT + "/" + pipelineId))
  }

  def runAnalysisPipeline(pipelineOptions: PbSmrtPipeServiceOptions): Future[EngineJob] = runJobPipeline {
    Post(toUrl(ServiceEndpoints.ROOT_JOBS + "/" + JobTypes.PB_PIPE),
         pipelineOptions)
  }

  def pollForJob(jobId: UUID): Future[String] = {
    var exitFlag = true
    var nIterations = 0
    val sleepTime = 5000
    val requestTimeOut = 10.seconds
    var jobState: Option[String] = None

    while(exitFlag) {
      nIterations += 1
      Thread.sleep(sleepTime)
      Try { Await.result(getJobByUuid(jobId), requestTimeOut) } match {
        case Success(x) =>
          x.state match {
            case AnalysisJobStates.SUCCESSFUL =>
              //logger.info(s"Transfer Job $jobId was successful.")
              exitFlag = false
              jobState = Some("SUCCESSFUL")
            case AnalysisJobStates.FAILED =>
              //logger.info(s"Transfer Job $jobId was successful.")
              exitFlag = false
              jobState = Some("FAILED")
            case sx =>
              //logger.info(s"Iteration $nIterations. Got job state $sx Sleeping for $sleepTime ms")
              jobState = Some(s"${x.state.stateId}")
          }

        case Failure(err) =>
          val emsg = s"Failed getting job $jobId state ${err.getMessage}"
          //logger.error(emsg)
          exitFlag = false
          // this needs to return a JobResult
          jobState = Some("FAILED")
      }
    }

    jobState match {
      case sx @ Some("SUCCESSFUL") => Future { "SUCCESSFUL" }
      case Some(sx) =>  Future.failed(new Exception(s"Unable to Successfully run job $jobId $sx"))
      case _ => Future.failed(new Exception(s"Unable to Successfully run job $jobId"))
    }
  }

}
