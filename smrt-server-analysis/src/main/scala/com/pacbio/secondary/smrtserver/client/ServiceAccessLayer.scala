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
import com.pacbio.secondary.analysis.engine.CommonMessages.MessageResponse
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
    // Not sure where this should go
    val TERMINATE_JOB = "terminate"
  }

  object AnalysisJobTypes extends JobTypesTrait {
    val IMPORT_DSTORE = "import-datastore"
    val CONVERT_FASTA = "convert-fasta-reference"
    val CONVERT_BARCODES = "convert-fasta-barcodes"
    val CONVERT_MOVIE = "convert-rs-movie"
    val EXPORT_DS = "export-datasets"
    val PB_PIPE = "pbsmrtpipe"
  }

  override def serviceStatusEndpoints: Vector[String] = Vector(
      ServiceEndpoints.ROOT_JOBS + "/" + AnalysisJobTypes.IMPORT_DS,
      ServiceEndpoints.ROOT_JOBS + "/" + AnalysisJobTypes.CONVERT_FASTA,
      ServiceEndpoints.ROOT_JOBS + "/" + AnalysisJobTypes.CONVERT_BARCODES,
      ServiceEndpoints.ROOT_JOBS + "/" + AnalysisJobTypes.PB_PIPE,
      ServiceEndpoints.ROOT_DS + "/" + DataSetTypes.SUBREADS,
      ServiceEndpoints.ROOT_DS + "/" + DataSetTypes.HDFSUBREADS,
      ServiceEndpoints.ROOT_DS + "/" + DataSetTypes.REFERENCES,
      ServiceEndpoints.ROOT_DS + "/" + DataSetTypes.BARCODES)

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

  def getServiceManifestsPipeline: HttpRequest => Future[Seq[PacBioComponentManifest]] = sendReceiveAuthenticated ~> unmarshal[Seq[PacBioComponentManifest]]
  def getServiceManifestPipeline: HttpRequest => Future[PacBioComponentManifest] = sendReceiveAuthenticated ~> unmarshal[PacBioComponentManifest]

  protected def getJobsByType(jobType: String): Future[Seq[EngineJob]] = getJobsPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_JOBS + "/" + jobType))
  }

  def getPacBioComponentManifests: Future[Seq[PacBioComponentManifest]] = getServiceManifestsPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_SERVICE_MANIFESTS))
  }
  // Added in smrtflow 0.1.11 and SA > 3.2.0
  def getPacBioComponentManifestById(manifestId: String): Future[PacBioComponentManifest] = getServiceManifestPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_SERVICE_MANIFESTS + "/" + manifestId))
  }


  def getAnalysisJobs: Future[Seq[EngineJob]] = getJobsByType(AnalysisJobTypes.PB_PIPE)
  def getImportJobs: Future[Seq[EngineJob]] = getJobsByType(AnalysisJobTypes.IMPORT_DS)
  def getMergeJobs: Future[Seq[EngineJob]] = getJobsByType(AnalysisJobTypes.MERGE_DS)
  def getFastaConvertJobs: Future[Seq[EngineJob]] = getJobsByType(AnalysisJobTypes.CONVERT_FASTA)
  def getBarcodeConvertJobs: Future[Seq[EngineJob]] = getJobsByType(AnalysisJobTypes.CONVERT_BARCODES)

  def getJob(jobId: IdAble): Future[EngineJob] = getJobPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_JOBS + "/" + jobId.toIdString))
  }

  def deleteJob(jobId: UUID, removeFiles: Boolean = true, dryRun: Boolean = false): Future[EngineJob] = getJobPipeline {
    Post(toUrl(ServiceEndpoints.ROOT_JOBS + "/delete-job"),
         DeleteJobServiceOptions(jobId, removeFiles, dryRun = Some(dryRun)))
  }

  def getJobChildren(jobId: IdAble): Future[Seq[EngineJob]] = getJobsPipeline {
    Get(toUrl(ServiceEndpoints.ROOT_JOBS + "/" + jobId.toIdString + "/children"))
  }

  def getJobByTypeAndId(jobType: String, jobId: IdAble): Future[EngineJob] = getJobPipeline {
    Get(toJobUrl(jobType, jobId))
  }

  def getAnalysisJob(jobId: IdAble): Future[EngineJob] = {
    getJobByTypeAndId(AnalysisJobTypes.PB_PIPE, jobId)
  }


  def getAnalysisJobDataStore(jobId: IdAble) = getJobDataStore(AnalysisJobTypes.PB_PIPE, jobId)
  def getImportFastaJobDataStore(jobId: IdAble) = getJobDataStore(AnalysisJobTypes.CONVERT_FASTA, jobId)
  def getImportBarcodesJobDataStore(jobId: IdAble) = getJobDataStore(AnalysisJobTypes.CONVERT_BARCODES, jobId)
  def getConvertRsMovieJobDataStore(jobId: IdAble) = getJobDataStore(AnalysisJobTypes.CONVERT_MOVIE, jobId)
  def getExportDataSetsJobDataStore(jobId: IdAble) = getJobDataStore(AnalysisJobTypes.EXPORT_DS, jobId)

  def getAnalysisJobReports(jobId: IdAble) = getJobReports(jobId, AnalysisJobTypes.PB_PIPE)

  // FIXME I think this still only works with Int
  def getAnalysisJobEntryPoints(jobId: Int): Future[Seq[EngineJobEntryPoint]] = getEntryPointsPipeline {
    Get(toJobResourceUrl(AnalysisJobTypes.PB_PIPE, jobId, ServiceResourceTypes.ENTRY_POINTS))
  }

  protected def getJobReport(jobType: String, jobId: IdAble, reportId: UUID): Future[Report] = getReportPipeline {
    Get(toJobResourceIdUrl(jobType, jobId, ServiceResourceTypes.REPORTS, reportId))
  }

  // FIXME there is some degeneracy in the URLs - this actually works just fine
  // for import-dataset and merge-dataset jobs too
  def getAnalysisJobReport(jobId: IdAble, reportId: UUID): Future[Report] = getJobReport(AnalysisJobTypes.PB_PIPE, jobId, reportId)

  def terminatePbsmrtpipeJob(jobId: Int): Future[MessageResponse] =
    getMessageResponsePipeline { Post(toJobResourceUrl(AnalysisJobTypes.PB_PIPE, jobId, AnalysisServiceEndpoints.TERMINATE_JOB))}

  def getReportViewRules: Future[Seq[ReportViewRule]] = getReportViewRulesPipeline {
    Get(toUrl(AnalysisServiceEndpoints.ROOT_REPORT_RULES))
  }

  def getReportViewRule(reportId: String): Future[ReportViewRule] = getReportViewRulePipeline {
    Get(toUrl(AnalysisServiceEndpoints.ROOT_REPORT_RULES + s"/$reportId"))
  }

  def importDataSet(path: Path, dsMetaType: String): Future[EngineJob] = runJobPipeline {
    val dsMetaTypeObj = DataSetMetaTypes.toDataSetType(dsMetaType).get
    Post(
      toUrl(AnalysisServiceEndpoints.ROOT_JOBS + "/" + AnalysisJobTypes.IMPORT_DS),
      ImportDataSetOptions(toP(path), dsMetaTypeObj))
  }

  def importFasta(path: Path, name: String, organism: String, ploidy: String): Future[EngineJob] = runJobPipeline {
    Post(
      toUrl(AnalysisServiceEndpoints.ROOT_JOBS + "/" + AnalysisJobTypes.CONVERT_FASTA),
      ConvertImportFastaOptions(toP(path), name, ploidy, organism))
  }

  def importFastaBarcodes(path: Path, name: String): Future[EngineJob] = runJobPipeline {
    Post(
      toUrl(AnalysisServiceEndpoints.ROOT_JOBS + "/" + AnalysisJobTypes.CONVERT_BARCODES),
      ConvertImportFastaBarcodesOptions(toP(path), name))
  }

  def mergeDataSets(datasetType: String, ids: Seq[Int], name: String) = runJobPipeline {
    Post(toUrl(AnalysisServiceEndpoints.ROOT_JOBS + "/" + AnalysisJobTypes.MERGE_DS),
         DataSetMergeServiceOptions(datasetType, ids, name))
  }

  def convertRsMovie(path: Path, name: String) = runJobPipeline {
    Post(toUrl(AnalysisServiceEndpoints.ROOT_JOBS + "/" + AnalysisJobTypes.CONVERT_MOVIE),
      MovieMetadataToHdfSubreadOptions(toP(path), name))
  }

  def exportDataSets(datasetType: String, ids: Seq[Int], outputPath: Path) = runJobPipeline {
    Post(toUrl(AnalysisServiceEndpoints.ROOT_JOBS + "/" + AnalysisJobTypes.EXPORT_DS),
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
      toUrl(AnalysisServiceEndpoints.ROOT_JOBS + "/" + AnalysisJobTypes.PB_PIPE),
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
    val requestTimeOut = 30.seconds
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
