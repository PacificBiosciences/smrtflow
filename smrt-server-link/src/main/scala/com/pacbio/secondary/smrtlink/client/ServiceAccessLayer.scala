package com.pacbio.secondary.smrtlink.client

import java.net.URL
import java.nio.file.Path
import java.util.UUID

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scalaj.http.Base64
import akka.actor.ActorSystem
import spray.client.pipelining._
import spray.http._
import spray.httpx.SprayJsonSupport
import spray.httpx.unmarshalling.FromResponseUnmarshaller
import com.pacificbiosciences.pacbiodatasets._
import com.pacbio.common.auth.Authenticator._
import com.pacbio.common.auth.JwtUtils._
import com.pacbio.common.client._
import com.pacbio.common.models._
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.datasets.io.DataSetJsonProtocols
import com.pacbio.secondary.analysis.engine.CommonMessages.MessageResponse
import com.pacbio.secondary.analysis.jobs.{AnalysisJobStates, JobModels}
import com.pacbio.secondary.analysis.jobtypes._
import com.pacbio.secondary.analysis.reports._
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.models._


object ServicesClientJsonProtocol
    extends SmrtLinkJsonProtocols
    with ReportJsonProtocol
    with DataSetJsonProtocols
    with SecondaryAnalysisJsonProtocols {}

class SmrtLinkServiceAccessLayer(baseUrl: URL, authUser: Option[String] = None)
    (implicit actorSystem: ActorSystem)
    extends ServiceAccessLayer(baseUrl)(actorSystem)
    with AnalysisJobConstants
    with ServiceEndpointConstants {

  import CommonModelImplicits._
  import CommonModels._
  import JobModels._
  import ReportModels._
  import SecondaryModels._
  import ServicesClientJsonProtocol._
  import SprayJsonSupport._

  // TODO(smcclellan): Apply header to all endpoints, or at least all requiring auth
  val headers = authUser
    .map(u => "{\"" + USERNAME_CLAIM + "\":\"" + u + "\",\"" + ROLES_CLAIM + "\":[]}")
    .map(c => Base64.encodeString("{}") + "." + Base64.encodeString(c) + ".abc")
    .map(j => HttpHeaders.RawHeader(JWT_HEADER, j))
    .toSeq

  def this(host: String, port: Int)(implicit actorSystem: ActorSystem) {
    this(UrlUtils.convertToUrl(host, port))(actorSystem)
  }

  private def toP(path: Path) = path.toAbsolutePath.toString
  private def jobRoot(jobType: String) = s"${ROOT_JOBS}/${jobType}"
  protected def toJobUrl(jobType: String, jobId: IdAble): String =
    toUrl(jobRoot(jobType) + s"/${jobId.toIdString}")
  protected def toJobResourceUrl(jobType: String, jobId: IdAble,
                                 resourceType: String): String =
    toUrl(jobRoot(jobType) + s"/${jobId.toIdString}/$resourceType")
  protected def toJobResourceIdUrl(jobType: String, jobId: IdAble,
                                   resourceType: String, resourceId: UUID) =
    toUrl(jobRoot(jobType) + s"/${jobId.toIdString}/$resourceType/$resourceId")
  private def dsRoot(dsType: String) = s"${ROOT_DS}/${dsType}"
  protected def toDataSetsUrl(dsType: String): String = toUrl(dsRoot(dsType))
  protected def toDataSetUrl(dsType: String, dsId: IdAble): String =
    toUrl(dsRoot(dsType) + s"/${dsId.toIdString}")
  protected def toDataSetResourcesUrl(dsType: String, dsId: IdAble,
                                     resourceType: String): String =
    toUrl(dsRoot(dsType) + s"/${dsId.toIdString}/$resourceType")
  protected def toDataSetResourceUrl(dsType: String, dsId: IdAble,
                                     resourceType: String, resourceId: UUID) =
    toUrl(dsRoot(dsType) + s"/${dsId.toIdString}/$resourceType/$resourceId")

  protected def toPacBioDataBundleUrl(bundleType: Option[String] = None): String = {
    val segment = bundleType.map(b => s"/$b").getOrElse("")
    toUrl(ROOT_PB_DATA_BUNDLE + segment)
  }

  override def serviceStatusEndpoints: Vector[String] = Vector(
      ROOT_JOBS + "/" + IMPORT_DS,
      ROOT_JOBS + "/" + CONVERT_FASTA,
      ROOT_JOBS + "/" + CONVERT_BARCODES,
      ROOT_JOBS + "/" + PB_PIPE,
      ROOT_DS + "/" + DataSetMetaTypes.Subread.shortName,
      ROOT_DS + "/" + DataSetMetaTypes.HdfSubread.shortName,
      ROOT_DS + "/" + DataSetMetaTypes.Reference.shortName,
      ROOT_DS + "/" + DataSetMetaTypes.Barcode.shortName)


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
  protected def getJobTasksPipeline: HttpRequest => Future[Seq[JobTask]] = sendReceiveAuthenticated ~> unmarshal[Seq[JobTask]]
  protected def getJobTaskPipeline: HttpRequest => Future[JobTask] = sendReceiveAuthenticated ~> unmarshal[JobTask]
  protected def getJobEventsPipeline: HttpRequest => Future[Seq[JobEvent]] = sendReceiveAuthenticated ~> unmarshal[Seq[JobEvent]]
  protected def getJobOptionsPipeline: HttpRequest => Future[PipelineTemplatePreset] = sendReceiveAuthenticated ~> unmarshal[PipelineTemplatePreset]

  protected def getRunsPipeline: HttpRequest => Future[Seq[RunSummary]] = sendReceiveAuthenticated ~> unmarshal[Seq[RunSummary]]
  protected def getRunSummaryPipeline: HttpRequest => Future[RunSummary] = sendReceiveAuthenticated ~> unmarshal[RunSummary]
  protected def getRunPipeline: HttpRequest => Future[Run] = sendReceiveAuthenticated ~> unmarshal[Run]
  protected def getCollectionsPipeline: HttpRequest => Future[Seq[CollectionMetadata]] = sendReceiveAuthenticated ~> unmarshal[Seq[CollectionMetadata]]
  protected def getCollectionPipeline: HttpRequest => Future[CollectionMetadata] = sendReceiveAuthenticated ~> unmarshal[CollectionMetadata]

  protected def getProjectsPipeline: HttpRequest => Future[Seq[Project]] = sendReceiveAuthenticated ~> unmarshal[Seq[Project]]
  protected def getProjectPipeline: HttpRequest => Future[FullProject] = sendReceiveAuthenticated ~> unmarshal[FullProject]

  protected def getEulaPipeline: HttpRequest => Future[EulaRecord] = sendReceiveAuthenticated ~> unmarshal[EulaRecord]
  protected def getEulasPipeline: HttpRequest => Future[Seq[EulaRecord]] = sendReceiveAuthenticated ~> unmarshal[Seq[EulaRecord]]

  protected def getMessageResponsePipeline: HttpRequest => Future[MessageResponse] = sendReceiveAuthenticated ~> unmarshal[MessageResponse]

  def getJobPipeline: HttpRequest => Future[EngineJob] = sendReceiveAuthenticated ~> unmarshal[EngineJob]
  // XXX this fails when createdBy is an object instead of a string
  def getJobsPipeline: HttpRequest => Future[Seq[EngineJob]] = sendReceiveAuthenticated ~> unmarshal[Seq[EngineJob]]
  def runJobPipeline: HttpRequest => Future[EngineJob] = sendReceiveAuthenticated ~> unmarshal[EngineJob]
  def getReportViewRulesPipeline: HttpRequest => Future[Seq[ReportViewRule]] = sendReceiveAuthenticated ~> unmarshal[Seq[ReportViewRule]]
  def getReportViewRulePipeline: HttpRequest => Future[ReportViewRule] = sendReceiveAuthenticated ~> unmarshal[ReportViewRule]
  def getPipelineTemplatePipeline: HttpRequest => Future[PipelineTemplate] = sendReceiveAuthenticated ~> unmarshal[PipelineTemplate]
  def getPipelineTemplatesPipeline: HttpRequest => Future[Seq[PipelineTemplate]] = sendReceiveAuthenticated ~> unmarshal[Seq[PipelineTemplate]]
  def getPipelineTemplateViewRulesPipeline: HttpRequest => Future[Seq[PipelineTemplateViewRule]] = sendReceiveAuthenticated ~> unmarshal[Seq[PipelineTemplateViewRule]]
  def getPipelineTemplateViewRulePipeline: HttpRequest => Future[PipelineTemplateViewRule] = sendReceiveAuthenticated ~> unmarshal[PipelineTemplateViewRule]
  def getPipelineDataStoreViewRulesPipeline: HttpRequest => Future[PipelineDataStoreViewRules] = sendReceiveAuthenticated ~> unmarshal[PipelineDataStoreViewRules]

  def getPacBioDataBundlesPipeline: HttpRequest => Future[Seq[PacBioDataBundle]] = sendReceiveAuthenticated ~> unmarshal[Seq[PacBioDataBundle]]
  def getPacBioDataBundlePipeline: HttpRequest => Future[PacBioDataBundle] = sendReceiveAuthenticated ~> unmarshal[PacBioDataBundle]

  def getServiceManifestsPipeline: HttpRequest => Future[Seq[PacBioComponentManifest]] = sendReceiveAuthenticated ~> unmarshal[Seq[PacBioComponentManifest]]
  def getServiceManifestPipeline: HttpRequest => Future[PacBioComponentManifest] = sendReceiveAuthenticated ~> unmarshal[PacBioComponentManifest]

  def getDataSet(datasetId: IdAble): Future[DataSetMetaDataSet] = getDataSetMetaDataPipeline {
    Get(toUrl(ROOT_DS + "/" + datasetId.toIdString))
  }

  def deleteDataSet(datasetId: IdAble): Future[MessageResponse] = getMessageResponsePipeline {
    Put(toUrl(ROOT_DS + "/" + datasetId.toIdString),
        DataSetUpdateRequest(false))
  }

  def getSubreadSets: Future[Seq[SubreadServiceDataSet]] = getSubreadSetsPipeline {
    Get(toDataSetsUrl(DataSetMetaTypes.Subread.shortName))
  }

  def getSubreadSet(dsId: IdAble): Future[SubreadServiceDataSet] = getSubreadSetPipeline {
    Get(toDataSetUrl(DataSetMetaTypes.Subread.shortName, dsId))
  }

  def getSubreadSetDetails(dsId: IdAble): Future[SubreadSet] = getSubreadSetDetailsPipeline {
    Get(toDataSetResourcesUrl(DataSetMetaTypes.Subread.shortName, dsId, "details"))
  }

  def getSubreadSetReports(dsId: IdAble): Future[Seq[DataStoreReportFile]] = getReportsPipeline {
    Get(toDataSetResourcesUrl(DataSetMetaTypes.Subread.shortName, dsId, JOB_REPORT_PREFIX))
  }

  def getHdfSubreadSets: Future[Seq[HdfSubreadServiceDataSet]] = getHdfSubreadSetsPipeline {
    Get(toDataSetsUrl(DataSetMetaTypes.HdfSubread.shortName))
  }

  def getHdfSubreadSet(dsId: IdAble): Future[HdfSubreadServiceDataSet] = getHdfSubreadSetPipeline {
    Get(toDataSetUrl(DataSetMetaTypes.HdfSubread.shortName, dsId))
  }

  def getHdfSubreadSetDetails(dsId: IdAble): Future[HdfSubreadSet] = getHdfSubreadSetDetailsPipeline {
    Get(toDataSetResourcesUrl(DataSetMetaTypes.HdfSubread.shortName, dsId, "details"))
  }

  def getBarcodeSets: Future[Seq[BarcodeServiceDataSet]] = getBarcodeSetsPipeline {
    Get(toDataSetsUrl(DataSetMetaTypes.Barcode.shortName))
  }

  def getBarcodeSet(dsId: IdAble): Future[BarcodeServiceDataSet] = getBarcodeSetPipeline {
    Get(toDataSetUrl(DataSetMetaTypes.Barcode.shortName, dsId))
  }

  def getBarcodeSetDetails(dsId: IdAble): Future[BarcodeSet] = getBarcodeSetDetailsPipeline {
    Get(toDataSetResourcesUrl(DataSetMetaTypes.Barcode.shortName, dsId, "details"))
  }

  def getReferenceSets: Future[Seq[ReferenceServiceDataSet]] = getReferenceSetsPipeline {
    Get(toDataSetsUrl(DataSetMetaTypes.Reference.shortName))
  }

  def getReferenceSet(dsId: IdAble): Future[ReferenceServiceDataSet] = getReferenceSetPipeline {
    Get(toDataSetUrl(DataSetMetaTypes.Reference.shortName, dsId))
  }

  def getReferenceSetDetails(dsId: IdAble): Future[ReferenceSet] = getReferenceSetDetailsPipeline {
    Get(toDataSetResourcesUrl(DataSetMetaTypes.Reference.shortName, dsId, "details"))
  }

  def getGmapReferenceSets: Future[Seq[GmapReferenceServiceDataSet]] = getGmapReferenceSetsPipeline {
    Get(toDataSetsUrl(DataSetMetaTypes.GmapReference.shortName))
  }

  def getGmapReferenceSet(dsId: IdAble): Future[GmapReferenceServiceDataSet] = getGmapReferenceSetPipeline {
    Get(toDataSetUrl(DataSetMetaTypes.GmapReference.shortName, dsId))
  }

  def getGmapReferenceSetDetails(dsId: IdAble): Future[GmapReferenceSet] = getGmapReferenceSetDetailsPipeline {
    Get(toDataSetResourcesUrl(DataSetMetaTypes.GmapReference.shortName, dsId, "details"))
  }

  def getAlignmentSets: Future[Seq[AlignmentServiceDataSet]] = getAlignmentSetsPipeline {
    Get(toDataSetsUrl(DataSetMetaTypes.Alignment.shortName))
  }

  def getAlignmentSet(dsId: IdAble): Future[AlignmentServiceDataSet] = getAlignmentSetPipeline {
    Get(toDataSetUrl(DataSetMetaTypes.Alignment.shortName, dsId))
  }

  def getAlignmentSetDetails(dsId: IdAble): Future[AlignmentSet] = getAlignmentSetDetailsPipeline {
    Get(toDataSetResourcesUrl(DataSetMetaTypes.Alignment.shortName, dsId, "details"))
  }

  def getConsensusReadSets: Future[Seq[ConsensusReadServiceDataSet]] = getConsensusReadSetsPipeline {
    Get(toDataSetsUrl(DataSetMetaTypes.CCS.shortName))
  }

  def getConsensusReadSet(dsId: IdAble): Future[ConsensusReadServiceDataSet] = getConsensusReadSetPipeline {
    Get(toDataSetUrl(DataSetMetaTypes.CCS.shortName, dsId))
  }

  def getConsensusReadSetDetails(dsId: IdAble): Future[ConsensusReadSet] = getConsensusReadSetDetailsPipeline {
    Get(toDataSetResourcesUrl(DataSetMetaTypes.CCS.shortName, dsId, "details"))
  }

  def getConsensusAlignmentSets: Future[Seq[ConsensusAlignmentServiceDataSet]] = getConsensusAlignmentSetsPipeline {
    Get(toDataSetsUrl(DataSetMetaTypes.AlignmentCCS.shortName))
  }

  def getConsensusAlignmentSet(dsId: IdAble): Future[ConsensusAlignmentServiceDataSet] = getConsensusAlignmentSetPipeline {
    Get(toDataSetUrl(DataSetMetaTypes.AlignmentCCS.shortName, dsId))
  }

  def getConsensusAlignmentSetDetails(dsId: IdAble): Future[ConsensusAlignmentSet] = getConsensusAlignmentSetDetailsPipeline {
    Get(toDataSetResourcesUrl(DataSetMetaTypes.AlignmentCCS.shortName, dsId, "details"))
  }

  def getContigSets: Future[Seq[ContigServiceDataSet]] = getContigSetsPipeline {
    Get(toDataSetsUrl(DataSetMetaTypes.Contig.shortName))
  }

  def getContigSet(dsId: IdAble): Future[ContigServiceDataSet] = getContigSetPipeline {
    Get(toDataSetUrl(DataSetMetaTypes.Contig.shortName, dsId))
  }

  def getContigSetDetails(dsId: IdAble): Future[ContigSet] = getContigSetDetailsPipeline {
    Get(toDataSetResourcesUrl(DataSetMetaTypes.Contig.shortName, dsId, "details"))
  }

  protected def getJobDataStore(jobType: String, jobId: IdAble) : Future[Seq[DataStoreServiceFile]] = getDataStorePipeline {
    Get(toJobResourceUrl(jobType, jobId, JOB_DATASTORE_PREFIX))
  }

  def getImportDatasetJobDataStore(jobId: IdAble) = getJobDataStore(IMPORT_DS, jobId)
  def getMergeDatasetJobDataStore(jobId: IdAble) = getJobDataStore(MERGE_DS, jobId)

  // FIXME how to convert to String?
  def getDataStoreFile(fileId: UUID): Future[HttpResponse] = respPipeline {
    Get(toUrl(ROOT_DATASTORE + s"/${fileId}/download"))
  }

  /*def getDataStoreFileBinary(fileId: UUID): Future[Array[Byte]] = rawDataPipeline {
    Get(toUrl(ROOT_DATASTORE + s"/${fileId}/download"))
  }*/

  def getReport(reportId: UUID): Future[Report] = getReportPipeline {
    Get(toUrl(ROOT_DATASTORE + s"/${reportId}/download"))
  }

  protected def getJobReports(jobId: IdAble, jobType: String): Future[Seq[DataStoreReportFile]] = getReportsPipeline {
    Get(toJobResourceUrl(jobType, jobId, JOB_REPORT_PREFIX))
  }

  def getImportJobReports(jobId: IdAble) = getJobReports(jobId, IMPORT_DS)

  def getDataStoreFileResource(fileId: UUID, relpath: String): Future[HttpResponse] = respPipeline {
    Get(toUrl(ROOT_DATASTORE + s"/${fileId}/resources?relpath=${relpath}"))
  }

  protected def getJobTasks(jobType: String, jobId: IdAble): Future[Seq[JobTask]] = getJobTasksPipeline {
    Get(toJobResourceUrl(jobType, jobId, JOB_TASK_PREFIX))
  }

  protected def getJobTask(jobType: String, jobId: IdAble, taskId: UUID): Future[JobTask] = getJobTaskPipeline {
    Get(toJobResourceUrl(jobType, jobId, JOB_TASK_PREFIX + "/" + taskId.toString))
  }

  protected def getJobEvents(jobType: String, jobId: Int): Future[Seq[JobEvent]] = getJobEventsPipeline {
    Get(toJobResourceUrl(jobType, jobId, JOB_EVENT_PREFIX))
  }

  protected def getJobOptions(jobType: String, jobId: Int): Future[PipelineTemplatePreset] = getJobOptionsPipeline {
    Get(toJobResourceUrl(jobType, jobId, JOB_OPTIONS))
  }

  protected def createJobTask(jobType: String, jobId: IdAble, task: CreateJobTaskRecord): Future[JobTask] = getJobTaskPipeline {
    Post(toJobResourceUrl(jobType, jobId, JOB_TASK_PREFIX), task)
  }

  protected def updateJobTask(jobType: String, jobId: IdAble, update: UpdateJobTaskRecord): Future[JobTask] = getJobTaskPipeline {
    Put(toJobResourceUrl(jobType, jobId, JOB_TASK_PREFIX + "/" + update.uuid.toString), update)
  }

  // Runs

  protected def getRunUrl(runId: UUID): String = toUrl(s"${ROOT_RUNS}/$runId")
  protected def getCollectionsUrl(runId: UUID): String = toUrl(s"${ROOT_RUNS}/$runId/collections")
  protected def getCollectionUrl(runId: UUID, collectionId: UUID): String = toUrl(s"${ROOT_RUNS}/$runId/collections/$collectionId")

  def getRuns: Future[Seq[RunSummary]] = getRunsPipeline {
    Get(toUrl(ROOT_RUNS))
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
    Post(toUrl(ROOT_RUNS), RunCreate(dataModel))
  }

  def updateRun(runId: UUID, dataModel: Option[String] = None, reserved: Option[Boolean] = None): Future[RunSummary] = getRunSummaryPipeline {
    Post(getRunUrl(runId), RunUpdate(dataModel, reserved))
  }

  def deleteRun(runId: UUID): Future[MessageResponse] = getMessageResponsePipeline {
    Delete(getRunUrl(runId))
  }

  def getProjects: Future[Seq[Project]] = getProjectsPipeline {
    Get(toUrl(ROOT_PROJECTS)).withHeaders(headers:_*)
  }

  def getProject(projectId: Int): Future[FullProject] = getProjectPipeline {
    Get(toUrl(ROOT_PROJECTS + s"/$projectId")).withHeaders(headers:_*)
  }

  def createProject(name: String, description: String): Future[FullProject] = getProjectPipeline {
    Post(toUrl(ROOT_PROJECTS),
         ProjectRequest(name, description, None, None, None))
  }

  def updateProject(projectId: Int, request: ProjectRequest): Future[FullProject] = getProjectPipeline {
    Put(toUrl(ROOT_PROJECTS + s"/$projectId"), request)
  }

  // User agreements (not really a EULA)
  def getEula(version: String): Future[EulaRecord] = getEulaPipeline {
    Get(toUrl(ROOT_EULA + s"/$version"))
  }

  def getEulas: Future[Seq[EulaRecord]] = getEulasPipeline {
    Get(toUrl(ROOT_EULA))
  }

  def acceptEula(user: String, version: String, enableInstallMetrics: Boolean = true, enableJobMetrics: Boolean = true) = getEulaPipeline {
    Post(toUrl(ROOT_EULA), EulaAcceptance(user, version, enableInstallMetrics, enableJobMetrics))
  }

  def deleteEula(version: String) = getMessageResponsePipeline {
    Delete(toUrl(ROOT_EULA + s"/$version"))
  }

  protected def getJobsByType(jobType: String): Future[Seq[EngineJob]] = getJobsPipeline {
    Get(toUrl(ROOT_JOBS + "/" + jobType))
  }

  def getPacBioComponentManifests: Future[Seq[PacBioComponentManifest]] = getServiceManifestsPipeline {
    Get(toUrl(ROOT_SERVICE_MANIFESTS))
  }
  // Added in smrtflow 0.1.11 and SA > 3.2.0
  def getPacBioComponentManifestById(manifestId: String): Future[PacBioComponentManifest] = getServiceManifestPipeline {
    Get(toUrl(ROOT_SERVICE_MANIFESTS + "/" + manifestId))
  }


  def getAnalysisJobs: Future[Seq[EngineJob]] = getJobsByType(PB_PIPE)
  def getImportJobs: Future[Seq[EngineJob]] = getJobsByType(IMPORT_DS)
  def getMergeJobs: Future[Seq[EngineJob]] = getJobsByType(MERGE_DS)
  def getFastaConvertJobs: Future[Seq[EngineJob]] = getJobsByType(CONVERT_FASTA)
  def getBarcodeConvertJobs: Future[Seq[EngineJob]] = getJobsByType(CONVERT_BARCODES)

  def getJob(jobId: IdAble): Future[EngineJob] = getJobPipeline {
    Get(toUrl(ROOT_JOBS + "/" + jobId.toIdString))
  }

  def deleteJob(jobId: UUID, removeFiles: Boolean = true, dryRun: Boolean = false): Future[EngineJob] = getJobPipeline {
    Post(toUrl(ROOT_JOBS + "/delete-job"),
         DeleteJobServiceOptions(jobId, removeFiles, dryRun = Some(dryRun)))
  }

  def getJobChildren(jobId: IdAble): Future[Seq[EngineJob]] = getJobsPipeline {
    Get(toUrl(ROOT_JOBS + "/" + jobId.toIdString + "/children"))
  }

  def getJobByTypeAndId(jobType: String, jobId: IdAble): Future[EngineJob] = getJobPipeline {
    Get(toJobUrl(jobType, jobId))
  }

  def getAnalysisJob(jobId: IdAble): Future[EngineJob] = {
    getJobByTypeAndId(PB_PIPE, jobId)
  }

  def getAnalysisJobDataStore(jobId: IdAble) = getJobDataStore(PB_PIPE, jobId)
  def getImportFastaJobDataStore(jobId: IdAble) = getJobDataStore(CONVERT_FASTA, jobId)
  def getImportBarcodesJobDataStore(jobId: IdAble) = getJobDataStore(CONVERT_BARCODES, jobId)
  def getConvertRsMovieJobDataStore(jobId: IdAble) = getJobDataStore(CONVERT_MOVIE, jobId)
  def getExportDataSetsJobDataStore(jobId: IdAble) = getJobDataStore(EXPORT_DS, jobId)

  def getAnalysisJobReports(jobId: IdAble) = getJobReports(jobId, PB_PIPE)

  // FIXME I think this still only works with Int
  def getAnalysisJobEntryPoints(jobId: Int): Future[Seq[EngineJobEntryPoint]] = getEntryPointsPipeline {
    Get(toJobResourceUrl(PB_PIPE, jobId, ENTRY_POINTS_PREFIX))
  }

  protected def getJobReport(jobType: String, jobId: IdAble, reportId: UUID): Future[Report] = getReportPipeline {
    Get(toJobResourceIdUrl(jobType, jobId, JOB_REPORT_PREFIX, reportId))
  }

  // FIXME there is some degeneracy in the URLs - this actually works just fine
  // for import-dataset and merge-dataset jobs too
  def getAnalysisJobReport(jobId: IdAble, reportId: UUID): Future[Report] = getJobReport(PB_PIPE, jobId, reportId)
  def getAnalysisJobTasks(jobId: Int): Future[Seq[JobTask]] = getJobTasks(PB_PIPE, jobId)
  def getAnalysisJobTask(jobId: Int, taskId: UUID): Future[JobTask] = getJobTask(PB_PIPE, jobId, taskId)
  def getAnalysisJobEvents(jobId: Int): Future[Seq[JobEvent]] = getJobEvents(PB_PIPE, jobId)
  def getAnalysisJobOptions(jobId: Int): Future[PipelineTemplatePreset] = getJobOptions(PB_PIPE, jobId)

  def terminatePbsmrtpipeJob(jobId: Int): Future[MessageResponse] =
    getMessageResponsePipeline { Post(toJobResourceUrl(PB_PIPE, jobId, TERMINATE_JOB))}

  def getReportViewRules: Future[Seq[ReportViewRule]] = getReportViewRulesPipeline {
    Get(toUrl(ROOT_REPORT_RULES))
  }

  def getReportViewRule(reportId: String): Future[ReportViewRule] = getReportViewRulePipeline {
    Get(toUrl(ROOT_REPORT_RULES + s"/$reportId"))
  }

  def importDataSet(path: Path, dsMetaType: String): Future[EngineJob] = runJobPipeline {
    val dsMetaTypeObj = DataSetMetaTypes.toDataSetType(dsMetaType).get
    Post(
      toUrl(ROOT_JOBS + "/" + IMPORT_DS),
      ImportDataSetOptions(toP(path), dsMetaTypeObj))
  }

  def importFasta(path: Path, name: String, organism: String, ploidy: String): Future[EngineJob] = runJobPipeline {
    Post(
      toUrl(ROOT_JOBS + "/" + CONVERT_FASTA),
      ConvertImportFastaOptions(toP(path), name, ploidy, organism))
  }

  def importFastaBarcodes(path: Path, name: String): Future[EngineJob] = runJobPipeline {
    Post(
      toUrl(ROOT_JOBS + "/" + CONVERT_BARCODES),
      ConvertImportFastaBarcodesOptions(toP(path), name))
  }

  def mergeDataSets(datasetType: String, ids: Seq[Int], name: String) = runJobPipeline {
    Post(toUrl(ROOT_JOBS + "/" + MERGE_DS),
         DataSetMergeServiceOptions(datasetType, ids, name))
  }

  def convertRsMovie(path: Path, name: String) = runJobPipeline {
    Post(toUrl(ROOT_JOBS + "/" + CONVERT_MOVIE),
      MovieMetadataToHdfSubreadOptions(toP(path), name))
  }

  def exportDataSets(datasetType: String, ids: Seq[Int], outputPath: Path) = runJobPipeline {
    Post(toUrl(ROOT_JOBS + "/" + EXPORT_DS),
         DataSetExportServiceOptions(datasetType, ids, toP(outputPath)))
  }

  def deleteDataSets(datasetType: String, ids: Seq[Int], removeFiles: Boolean = true) = runJobPipeline {
    Post(toUrl(ROOT_JOBS + "/" + DELETE_DS),
         DataSetDeleteServiceOptions(datasetType, ids, removeFiles))
  }

  def getPipelineTemplate(pipelineId: String): Future[PipelineTemplate] = getPipelineTemplatePipeline {
    Get(toUrl(ROOT_PT + "/" + pipelineId))
  }

  def getPipelineTemplates: Future[Seq[PipelineTemplate]] = getPipelineTemplatesPipeline {
    Get(toUrl(ROOT_PT))
  }

  def getPipelineTemplateViewRules: Future[Seq[PipelineTemplateViewRule]] = getPipelineTemplateViewRulesPipeline {
    Get(toUrl(ROOT_PTRULES))
  }

  def getPipelineTemplateViewRule(pipelineId: String): Future[PipelineTemplateViewRule] = getPipelineTemplateViewRulePipeline {
    Get(toUrl(ROOT_PTRULES + s"/$pipelineId"))
  }

  def getPipelineDataStoreViewRules(pipelineId: String): Future[PipelineDataStoreViewRules] = getPipelineDataStoreViewRulesPipeline {
    Get(toUrl(ROOT_DS_RULES + s"/$pipelineId"))
  }

  def runAnalysisPipeline(pipelineOptions: PbSmrtPipeServiceOptions): Future[EngineJob] = runJobPipeline {
    Post(toUrl(ROOT_JOBS + "/" + PB_PIPE), pipelineOptions)
  }

  // PacBio Data Bundle
  def getPacBioDataBundles() = getPacBioDataBundlesPipeline { Get(toPacBioDataBundleUrl()) }

  def getPacBioDataBundleByTypeId(typeId: String) =
    getPacBioDataBundlePipeline { Get(toPacBioDataBundleUrl(Some(typeId))) }

  def getPacBioDataBundleByTypeAndVersionId(typeId: String, versionId: String) =
    getPacBioDataBundlePipeline { Get(toPacBioDataBundleUrl(Some(s"$typeId/$versionId")))}


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
      case _ => Failure(new Exception(s"Failed to run job ${jobId.toIdString}."))
    }
  }
}
