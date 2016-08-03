package com.pacbio.secondary.smrtserver.client

import com.pacbio.secondary.smrtserver.models._
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.reports.ReportModels
import com.pacbio.secondary.smrtlink.client._
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
import java.lang.System

//FIXME(mkocher)(2016-2-2): This needs to be centralized.
object AnalysisServicesModels {

  case class CreateDataSet(path: String, datasetType: String)

  case class CreateReferenceSet(
      path: String,
      name: String,
      organism: String,
      ploidy: String)

  case class CreateBarcodeSet(path: String, name: String)
}

object AnalysisClientJsonProtocol extends SmrtLinkJsonProtocols with SecondaryAnalysisJsonProtocols

class AnalysisServiceAccessLayer(baseUrl: URL)(implicit actorSystem: ActorSystem) extends SmrtLinkServiceAccessLayer(baseUrl)(actorSystem) {

  import AnalysisClientJsonProtocol._
  import AnalysisServicesModels._
  import SprayJsonSupport._
  import ReportModels._

  def this(host: String, port: Int)(implicit actorSystem: ActorSystem) {
    this(UrlUtils.convertToUrl(host, port))(actorSystem)
  }

  object AnalysisServiceEndpoints extends ServiceEndpointsTrait {
    val ROOT_PT = "/secondary-analysis/resolved-pipeline-templates"
  }

  def getReportPipeline: HttpRequest => Future[Report] = sendReceive ~> unmarshal[Report]
  def getJobPipeline: HttpRequest => Future[EngineJob] = sendReceive ~> unmarshal[EngineJob]
  // XXX this fails when createdBy is an object instead of a string
  def getJobsPipeline: HttpRequest => Future[Seq[EngineJob]] = sendReceive ~> unmarshal[Seq[EngineJob]]
  def runJobPipeline: HttpRequest => Future[EngineJob] = sendReceive ~> unmarshal[EngineJob]
  def getPipelineTemplatePipeline: HttpRequest => Future[PipelineTemplate] = sendReceive ~> unmarshal[PipelineTemplate]

  protected def getJobsByType(jobType: String): Future[Seq[EngineJob]] = getJobsPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_JOBS + "/" + jobType))
  }

  def getAnalysisJobs: Future[Seq[EngineJob]] = getJobsByType(JobTypes.PB_PIPE)
  def getImportJobs: Future[Seq[EngineJob]] = getJobsByType(JobTypes.IMPORT_DS)
  def getMergeJobs: Future[Seq[EngineJob]] = getJobsByType(JobTypes.MERGE_DS)
  def getFastaConvertJobs: Future[Seq[EngineJob]] = getJobsByType(JobTypes.CONVERT_FASTA)
  def getBarcodeConvertJobs: Future[Seq[EngineJob]] = getJobsByType(JobTypes.CONVERT_BARCODES)

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

  protected def getJobReport(jobType: String, jobId: Int, reportId: UUID): Future[Report] = getReportPipeline {
    Get(toJobResourceIdUrl(jobType, jobId, ServiceResourceTypes.REPORTS, reportId))
  }

  // FIXME there is some degeneracy in the URLs - this actually works just fine
  // for import-dataset and merge-dataset jobs too
  def getAnalysisJobReport(jobId: Int, reportId: UUID): Future[Report] = getJobReport(JobTypes.PB_PIPE, jobId, reportId)

  def importDataSet(path: String, dsMetaType: String): Future[EngineJob] = runJobPipeline {
    Post(
      toUrl(AnalysisServiceEndpoints.ROOT_JOBS + "/" + JobTypes.IMPORT_DS),
      CreateDataSet(path, dsMetaType))
  }

  def importFasta(path: String, name: String, organism: String, ploidy: String): Future[EngineJob] = runJobPipeline {
    Post(
      toUrl(AnalysisServiceEndpoints.ROOT_JOBS + "/" + JobTypes.CONVERT_FASTA),
      CreateReferenceSet(path, name, organism, ploidy))
  }

  def importFastaBarcodes(path: String, name: String): Future[EngineJob] = runJobPipeline {
    Post(
      toUrl(AnalysisServiceEndpoints.ROOT_JOBS + "/" + JobTypes.CONVERT_BARCODES),
      CreateBarcodeSet(path, name))
  }

  def mergeDataSets(datasetType: String, ids: Seq[Int], name: String) = runJobPipeline {
    Post(toUrl(AnalysisServiceEndpoints.ROOT_JOBS + "/" + JobTypes.MERGE_DS),
         DataSetMergeServiceOptions(datasetType, ids, name))
  }

  def getPipelineTemplateJson(pipelineId: String): Future[String] = rawJsonPipeline {
    Get(toUrl(AnalysisServiceEndpoints.ROOT_PT + "/" + pipelineId))
  }

  // FIXME this doesn't quite work...
  def getPipelineTemplate(pipelineId: String): Future[PipelineTemplate] = getPipelineTemplatePipeline {
    Get(toUrl(AnalysisServiceEndpoints.ROOT_PT + "/" + pipelineId))
  }

  def runAnalysisPipeline(pipelineOptions: PbSmrtPipeServiceOptions): Future[EngineJob] = runJobPipeline {
    Post(
      toUrl(AnalysisServiceEndpoints.ROOT_JOBS + "/" + JobTypes.PB_PIPE),
      pipelineOptions)
  }

  // FIXME this could be cleaner, and logging would be helpful
  def pollForJob(jobId: UUID, maxTime: Int = -1): Int = {
    var exitFlag = true
    var nIterations = 0
    val sleepTime = 5000
    val requestTimeOut = 10.seconds
    var jobState: Option[String] = None
    val tStart = java.lang.System.currentTimeMillis() / 1000.0

    while(exitFlag) {
      nIterations += 1
      Thread.sleep(sleepTime)
      Try { Await.result(getJobByUuid(jobId), requestTimeOut) } match {
        case Success(x) => x.state match {
          case AnalysisJobStates.SUCCESSFUL => {
            exitFlag = false
            jobState = Some("SUCCESSFUL")
          }
          case AnalysisJobStates.FAILED => throw new Exception(s"Analysis job $jobId failed")
          case sx => jobState = Some(s"${x.state.stateId}")
        }
        case Failure(err) => throw new Exception(s"Failed getting job $jobId state ${err.getMessage}")
      }
      val tCurrent = java.lang.System.currentTimeMillis() / 1000.0
      if ((maxTime > 0) && (tCurrent - tStart > maxTime)) {
        throw new Exception(s"Run time exceeded specified limit (${maxTime} s)")
      }
    }

    jobState match {
      case sx @ Some("SUCCESSFUL") => 0
      case _ => throw new Exception(s"Unable to Successfully run job $jobId")
    }
  }

}
