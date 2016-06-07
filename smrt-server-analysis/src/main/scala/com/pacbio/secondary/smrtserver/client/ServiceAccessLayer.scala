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

//FIXME(mkocher)(2016-2-2): This needs to be centralized.
object AnalysisServicesModels {

  case class CreateDataSet(path: String, datasetType: String)

  case class CreateReferenceSet(
      path: String,
      name: String,
      organism: String,
      ploidy: String)
}

object AnalysisClientJsonProtocol extends SmrtLinkJsonProtocols with SecondaryAnalysisJsonProtocols

class AnalysisServiceAccessLayer(baseUrl: URL)(implicit actorSystem: ActorSystem) extends SmrtLinkServiceAccessLayer(baseUrl)(actorSystem) {

  import AnalysisClientJsonProtocol._
  import AnalysisServicesModels._
  import SprayJsonSupport._
  import ReportModels._

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

  protected def getJobReport(jobType: String, jobId: Int, reportId: UUID): Future[Report] = getReportPipeline {
    Get(toJobResourceIdUrl(jobType, jobId, ServiceResourceTypes.REPORTS, reportId))
  }

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
