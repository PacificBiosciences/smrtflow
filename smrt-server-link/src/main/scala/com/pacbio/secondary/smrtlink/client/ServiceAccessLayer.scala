package com.pacbio.secondary.smrtlink.client

import java.net.URL
import java.util.UUID

import akka.actor.ActorSystem
import com.pacbio.common.auth.Authenticator._
import com.pacbio.common.auth.JwtUtils._
import com.pacbio.common.client._
import com.pacbio.common.models._
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.datasets.io.DataSetJsonProtocols
import com.pacbio.secondary.analysis.engine.CommonMessages.MessageResponse
import com.pacbio.secondary.analysis.jobs.JobModels
import com.pacbio.secondary.analysis.reports._
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.models._
import com.pacificbiosciences.pacbiodatasets._
import spray.client.pipelining._
import spray.http._
import spray.httpx.SprayJsonSupport
import spray.httpx.unmarshalling.FromResponseUnmarshaller

import scala.concurrent.Future
import scalaj.http.Base64

object ServicesClientJsonProtocol extends SmrtLinkJsonProtocols with ReportJsonProtocol with DataSetJsonProtocols

trait ServiceEndpointConstants extends JobServiceConstants {
  val ROOT_JM = s"/$ROOT_SERVICE_PREFIX/$SERVICE_PREFIX"
  val ROOT_JOBS = s"$ROOT_JM/$JOB_ROOT_PREFIX"
  val ROOT_DS = s"/$ROOT_SERVICE_PREFIX/datasets"
  val ROOT_RUNS = "/smrt-link/runs"
  val ROOT_DATASTORE = s"/$ROOT_SERVICE_PREFIX/$DATASTORE_FILES_PREFIX"
  val ROOT_PROJECTS = s"/$ROOT_SERVICE_PREFIX/projects"
  val ROOT_SERVICE_MANIFESTS = "/services/manifests" // keeping with the naming convention
  val ROOT_EULA = "/smrt-base/eula"
}

trait JobTypesConstants {
  val IMPORT_DS = "import-dataset"
  val MERGE_DS = "merge-datasets"
  val MOCK_PB_PIPE = "mock-pbsmrtpipe"
}

class SmrtLinkServiceAccessLayer(baseUrl: URL, authUser: Option[String] = None)
    (implicit actorSystem: ActorSystem)
    extends ServiceAccessLayer(baseUrl)(actorSystem)
    with ServiceEndpointConstants
    with JobTypesConstants {

  import CommonModelImplicits._
  import CommonModels._
  import JobModels._
  import ReportModels._
  import ServicesClientJsonProtocol._
  import SprayJsonSupport._

  // TODO(smcclellan): Apply header to all endpoints, or at least all requiring auth
  val headers = authUser
    .map(u => "{\"" + USERNAME_CLAIM + "\":\"" + u + "\",\"" + ROLES_CLAIM + "\":[]}")
    .map(c => Base64.encodeString("{}") + "." + Base64.encodeString(c) + ".abc")
    .map(j => HttpHeaders.RawHeader(JWT_HEADER, j))
    .toSeq

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

  override def serviceStatusEndpoints: Vector[String] = Vector(
      ROOT_JOBS + "/" + IMPORT_DS,
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
}
