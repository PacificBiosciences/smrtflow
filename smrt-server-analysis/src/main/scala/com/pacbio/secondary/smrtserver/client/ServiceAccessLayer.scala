package com.pacbio.secondary.smrtserver.client

import com.pacbio.secondary.smrtserver.models._
import com.pacbio.secondary.smrtlink.client._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.reports.ReportModels
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.jobs.{AnalysisJobStates, JobModels}
import com.pacbio.secondary.analysis.jobtypes._
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
import java.nio.file.Path


object AnalysisClientJsonProtocol extends SmrtLinkJsonProtocols with SecondaryAnalysisJsonProtocols

class AnalysisServiceAccessLayer(baseUrl: URL, authToken: Option[String] = None)
    (implicit actorSystem: ActorSystem)
    extends SmrtLinkServiceAccessLayer(baseUrl, authToken)(actorSystem) {

  import AnalysisClientJsonProtocol._
  import SecondaryModels._
  import SprayJsonSupport._
  import ReportModels._
  import JobModels._
  import CommonModels._
  import CommonModelImplicits._

  def this(host: String, port: Int)(implicit actorSystem: ActorSystem) {
    this(UrlUtils.convertToUrl(host, port))(actorSystem)
  }

  object AnalysisServiceEndpoints extends ServiceEndpointsTrait {
    val ROOT_PT = "/secondary-analysis/resolved-pipeline-templates"
    val ROOT_PTRULES = "/secondary-analysis/pipeline-template-view-rules"
    val ROOT_REPORT_RULES = "/secondary-analysis/report-view-rules"
    val ROOT_DS_RULES = "/secondary-analysis/pipeline-datastore-view-rules"
  }

  private def toP(path: Path) = path.toAbsolutePath.toString

  def getJobPipeline: HttpRequest => Future[EngineJob] = sendReceiveAuthenticated ~> unmarshal[EngineJob]
  // XXX this fails when createdBy is an object instead of a string
  def getJobsPipeline: HttpRequest => Future[Seq[EngineJob]] = sendReceiveAuthenticated ~> unmarshal[Seq[EngineJob]]
  def runJobPipeline: HttpRequest => Future[EngineJob] = sendReceiveAuthenticated ~> unmarshal[EngineJob]
  def getReportViewRulesPipeline: HttpRequest => Future[Seq[ReportViewRule]] = sendReceiveAuthenticated ~> unmarshal[Seq[ReportViewRule]]
  def getReportViewRulePipeline: HttpRequest => Future[ReportViewRule] = sendReceiveAuthenticated ~> unmarshal[ReportViewRule]
  def getPipelineTemplatePipeline: HttpRequest => Future[PipelineTemplate] = sendReceiveAuthenticated ~> unmarshal[PipelineTemplate]
  def getPipelineTemplateViewRulesPipeline: HttpRequest => Future[Seq[PipelineTemplateViewRule]] = sendReceiveAuthenticated ~> unmarshal[Seq[PipelineTemplateViewRule]]
  def getPipelineTemplateViewRulePipeline: HttpRequest => Future[PipelineTemplateViewRule] = sendReceiveAuthenticated ~> unmarshal[PipelineTemplateViewRule]
  def getPipelineDataStoreViewRulesPipeline: HttpRequest => Future[PipelineDataStoreViewRules] = sendReceiveAuthenticated ~> unmarshal[PipelineDataStoreViewRules]

  protected def getJobsByType(jobType: String): Future[Seq[EngineJob]] = getJobsPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_JOBS + "/" + jobType))
  }

  def getAnalysisJobs: Future[Seq[EngineJob]] = getJobsByType(JobTypes.PB_PIPE)
  def getImportJobs: Future[Seq[EngineJob]] = getJobsByType(JobTypes.IMPORT_DS)
  def getMergeJobs: Future[Seq[EngineJob]] = getJobsByType(JobTypes.MERGE_DS)
  def getFastaConvertJobs: Future[Seq[EngineJob]] = getJobsByType(JobTypes.CONVERT_FASTA)
  def getBarcodeConvertJobs: Future[Seq[EngineJob]] = getJobsByType(JobTypes.CONVERT_BARCODES)

  def getJob(jobId: IdAble): Future[EngineJob] = getJobPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_JOBS + "/" + jobId.toIdString))
  }

  def getJobByTypeAndId(jobType: String, jobId: IdAble): Future[EngineJob] = getJobPipeline {
    Get(toJobUrl(jobType, jobId))
  }

  def getAnalysisJob(jobId: IdAble): Future[EngineJob] = {
    getJobByTypeAndId(JobTypes.PB_PIPE, jobId)
  }

  protected def getJobReport(jobType: String, jobId: IdAble, reportId: UUID): Future[Report] = getReportPipeline {
    Get(toJobResourceIdUrl(jobType, jobId, ServiceResourceTypes.REPORTS, reportId))
  }

  // FIXME there is some degeneracy in the URLs - this actually works just fine
  // for import-dataset and merge-dataset jobs too
  def getAnalysisJobReport(jobId: IdAble, reportId: UUID): Future[Report] = getJobReport(JobTypes.PB_PIPE, jobId, reportId)

  def getReportViewRules: Future[Seq[ReportViewRule]] = getReportViewRulesPipeline {
    Get(toUrl(AnalysisServiceEndpoints.ROOT_REPORT_RULES))
  }

  def getReportViewRule(reportId: String): Future[ReportViewRule] = getReportViewRulePipeline {
    Get(toUrl(AnalysisServiceEndpoints.ROOT_REPORT_RULES + s"/$reportId"))
  }

  def importDataSet(path: Path, dsMetaType: String): Future[EngineJob] = runJobPipeline {
    val dsMetaTypeObj = DataSetMetaTypes.toDataSetType(dsMetaType).get
    Post(
      toUrl(AnalysisServiceEndpoints.ROOT_JOBS + "/" + JobTypes.IMPORT_DS),
      ImportDataSetOptions(toP(path), dsMetaTypeObj))
  }

  def importFasta(path: Path, name: String, organism: String, ploidy: String): Future[EngineJob] = runJobPipeline {
    Post(
      toUrl(AnalysisServiceEndpoints.ROOT_JOBS + "/" + JobTypes.CONVERT_FASTA),
      ConvertImportFastaOptions(toP(path), name, ploidy, organism))
  }

  def importFastaBarcodes(path: Path, name: String): Future[EngineJob] = runJobPipeline {
    Post(
      toUrl(AnalysisServiceEndpoints.ROOT_JOBS + "/" + JobTypes.CONVERT_BARCODES),
      ConvertImportFastaBarcodesOptions(toP(path), name))
  }

  def mergeDataSets(datasetType: String, ids: Seq[Int], name: String) = runJobPipeline {
    Post(toUrl(AnalysisServiceEndpoints.ROOT_JOBS + "/" + JobTypes.MERGE_DS),
         DataSetMergeServiceOptions(datasetType, ids, name))
  }

  def convertRsMovie(path: Path, name: String) = runJobPipeline {
    Post(toUrl(AnalysisServiceEndpoints.ROOT_JOBS + "/" + JobTypes.CONVERT_MOVIE),
      MovieMetadataToHdfSubreadOptions(toP(path), name))
  }

  def exportDataSets(datasetType: String, ids: Seq[Int], outputPath: Path) = runJobPipeline {
    Post(toUrl(AnalysisServiceEndpoints.ROOT_JOBS + "/" + JobTypes.EXPORT_DS),
         DataSetExportServiceOptions(datasetType, ids, toP(outputPath)))
  }

  def getPipelineTemplateJson(pipelineId: String): Future[String] = rawJsonPipeline {
    Get(toUrl(AnalysisServiceEndpoints.ROOT_PT + "/" + pipelineId))
  }

  // FIXME this doesn't quite work...
  def getPipelineTemplate(pipelineId: String): Future[PipelineTemplate] = getPipelineTemplatePipeline {
    Get(toUrl(AnalysisServiceEndpoints.ROOT_PT + "/" + pipelineId))
  }

  def getPipelineTemplateViewRules: Future[Seq[PipelineTemplateViewRule]] = getPipelineTemplateViewRulesPipeline {
    Get(toUrl(AnalysisServiceEndpoints.ROOT_PTRULES))
  }

  def getPipelineTemplateViewRule(pipelineId: String): Future[PipelineTemplateViewRule] = getPipelineTemplateViewRulePipeline {
    Get(toUrl(AnalysisServiceEndpoints.ROOT_PTRULES + s"/$pipelineId"))
  }

  def getPipelineDataStoreViewRules(pipelineId: String): Future[PipelineDataStoreViewRules] = getPipelineDataStoreViewRulesPipeline {
    Get(toUrl(AnalysisServiceEndpoints.ROOT_DS_RULES + s"/$pipelineId"))
  }

  def runAnalysisPipeline(pipelineOptions: PbSmrtPipeServiceOptions): Future[EngineJob] = runJobPipeline {
    Post(
      toUrl(AnalysisServiceEndpoints.ROOT_JOBS + "/" + JobTypes.PB_PIPE),
      pipelineOptions)
  }

  /**
    * FIXME(mpkocher)(2016-8-22)
    * - maxTime should be Option[Duration]
    * - replace tStart with JodaDateTime
    * - make sleepTime configurable
    * - Add Retry to Poll
    * - Raise Custom Exception type for Failed job to distinquish Failed jobs and jobs that exceeded maxTime
    * - replace while loop with recursion
    *
    * @param jobId Job Id or UUID
    * @param maxTime Max time to poll for the job
    *
    * @return EngineJob
    */
  def pollForJob(jobId: IdAble, maxTime: Int = -1): Try[EngineJob] = {
    var exitFlag = true
    var nIterations = 0
    val sleepTime = 5000
    val requestTimeOut = 10.seconds
    var runningJob: Option[EngineJob] = None
    val tStart = java.lang.System.currentTimeMillis() / 1000.0

    def failIfNotState(state: AnalysisJobStates.JobStates, job: EngineJob): Try[EngineJob] = {
      if (job.state == state) Success(job)
      else Failure(new Exception(s"Job id:${job.id} name:${job.name} failed. State:${job.state} at ${job.updatedAt}"))
    }

    def failIfFailedJob(job: EngineJob): Try[EngineJob] = {
      if (job.state != AnalysisJobStates.FAILED) Success(job)
      else Failure(new Exception(s"Job id:${job.id} name:${job.name} failed. State:${job.state} at ${job.updatedAt}"))
    }

    def failIfNotSuccessfulJob(job: EngineJob) = failIfNotState(AnalysisJobStates.SUCCESSFUL, job)

    def failIfExceededMaxTime(job: EngineJob): Try[EngineJob] = {
      val tCurrent = java.lang.System.currentTimeMillis() / 1000.0
      if ((maxTime > 0) && (tCurrent - tStart > maxTime)) {
        Failure(new Exception(s"Job ${job.id} Run time exceeded specified limit ($maxTime s)"))
      } else {
        Success(job)
      }
    }

    while(exitFlag) {
      nIterations += 1
      Thread.sleep(sleepTime)

      val tx = for {
        job <- Try { Await.result(getJob(jobId), requestTimeOut)}
        notFailedJob <- failIfFailedJob(job)
        _ <- failIfExceededMaxTime(notFailedJob)
      } yield notFailedJob

      tx match {
        case Success(job) =>
          if (job.state == AnalysisJobStates.SUCCESSFUL) {
            exitFlag = false
            runningJob = Some(job)
          }
        case Failure(ex) =>
            exitFlag = false
            runningJob = None
      }
    }

    runningJob match {
      case Some(job) => failIfNotSuccessfulJob(job)
      case _ => Failure(new Exception(s"Failed to run job $jobId."))
    }
  }

}
