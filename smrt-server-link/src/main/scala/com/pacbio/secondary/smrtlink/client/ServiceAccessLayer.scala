package com.pacbio.secondary.smrtlink.client

import java.net.URL
import java.nio.file.Path
import java.util.UUID

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

import akka.actor.ActorSystem
import akka.http.scaladsl.server._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.client.RequestBuilding._
import com.typesafe.scalalogging.LazyLogging
import spray.json._

import com.pacificbiosciences.pacbiodatasets._
import com.pacbio.common.models._
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.analysis.datasets.{
  DataSetMetaTypes,
  DataSetFilterProperty
}
import com.pacbio.secondary.smrtlink.analysis.reports.ReportModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.jsonprotocols._
import com.pacbio.secondary.smrtlink.analysis.reports._
import com.pacbio.secondary.smrtlink.auth.JwtUtils._
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.auth.JwtUtilsImpl
import com.pacbio.secondary.smrtlink.jobtypes._
import com.pacbio.secondary.smrtlink.models._

class SmrtLinkServiceClient(
    host: String,
    port: Int,
    authUser: Option[String] = None)(implicit actorSystem: ActorSystem)
    extends ServiceAccessLayer(host, port)(actorSystem)
    with ServiceEndpointConstants
    with LazyLogging {

  import CommonModelImplicits._
  import CommonModels._
  import SprayJsonSupport._
  import SmrtLinkJsonProtocols._

  protected def getMessageResponse(
      request: HttpRequest): Future[MessageResponse] =
    getObject[MessageResponse](request)

  // To reuse the current models, we need an email address of the user.
  private val userRecord: Option[UserRecord] =
    authUser.map(u => UserRecord(u, Some(s"$u/domain.com")))

  private val jwtUtils = new JwtUtilsImpl

  val headers = userRecord
    .map(u => RawHeader(JWT_HEADER, jwtUtils.userRecordToJwt(u)))
    .toSeq

  private def toP(path: Path) = path.toAbsolutePath.toString

  // Perhaps these should be pushed into ServiceEndpoint Constants for consistency
  // and centralization. Everything in this class should consume and construct
  /// Uri.Path instances, not raw strings.

  // FIXME. The Convention is for the RootUri to prepend the leading slash
  val ROOT_SL_PREFIX_URI_PATH: Uri.Path = Uri.Path(ROOT_SL_PREFIX)

  val ROOT_JM_URI_PATH: Uri.Path = ROOT_SL_PREFIX_URI_PATH / JOB_MANAGER_PREFIX
  val ROOT_JOBS_URI_PATH: Uri.Path = ROOT_JM_URI_PATH / JOB_ROOT_PREFIX
  val ROOT_MULTI_JOBS_URI_PATH
    : Uri.Path = ROOT_JM_URI_PATH / JOB_MULTI_ROOT_PREFIX
  val ROOT_DS_URI_PATH: Uri.Path = ROOT_SL_PREFIX_URI_PATH / "datasets"
  val ROOT_PB_DATA_BUNDLE_URI_PATH
    : Uri.Path = ROOT_SL_PREFIX_URI_PATH / "bundles"
  val ROOT_DATASTORE_URI_PATH
    : Uri.Path = ROOT_SL_PREFIX_URI_PATH / DATASTORE_FILES_PREFIX
  val ROOT_RUNS_URI_PATH: Uri.Path = ROOT_SL_PREFIX_URI_PATH / "runs"
  val ROOT_PROJECTS_URI_PATH: Uri.Path = ROOT_SL_PREFIX_URI_PATH / "projects"
  val ROOT_EULA_URI_PATH: Uri.Path = ROOT_SL_PREFIX_URI_PATH / "eula"
  val ROOT_SERVICE_MANIFESTS_URI_PATH
    : Uri.Path = ROOT_SL_PREFIX_URI_PATH / "manifests"
  val ROOT_ALARMS_URI_PATH: Uri.Path = ROOT_SL_PREFIX_URI_PATH / "alarms"
  val ROOT_PT_URI_PATH
    : Uri.Path = ROOT_SL_PREFIX_URI_PATH / "resolved-pipeline-templates"
  val ROOT_REGISTRY_URI_PATH
    : Uri.Path = ROOT_SL_PREFIX_URI_PATH / "registry-service"
  // Rules
  val ROOT_REPORT_RULES_URI_PATH
    : Uri.Path = ROOT_SL_PREFIX_URI_PATH / "report-view-rules"
  val ROOT_PT_RULES_URI_PATH
    : Uri.Path = ROOT_SL_PREFIX_URI_PATH / "pipeline-template-view-rules"
  val ROOT_DS_RULES_URI_PATH
    : Uri.Path = ROOT_SL_PREFIX_URI_PATH / "pipeline-datastore-view-rules"

  protected def toJobUriPath(jobType: String,
                             jobId: Option[IdAble] = None): Uri.Path = {
    val base = ROOT_JOBS_URI_PATH / jobType
    jobId.map(i => base / i.toIdString).getOrElse(base)
  }

  // delete this, use toJobUriPath
  private def jobRoot(jobType: String): Uri.Path = toJobUriPath(jobType)

  protected def toJobUri(jobType: String, jobId: Option[IdAble]): Uri =
    toUri(toJobUriPath(jobType, jobId))

  /**
    * Naked job-type-less call to get a job by IdAble
    */
  protected def toJobResourceUriPath(jobId: IdAble,
                                     resourceType: String): Uri.Path =
    ROOT_JOBS_URI_PATH / jobId.toIdString / resourceType

  protected def toJobResourceUrl(jobId: IdAble, resourceType: String): Uri =
    toUri(toJobResourceUriPath(jobId, resourceType))

  protected def toJobResourceIdUrl(jobId: IdAble,
                                   resourceType: String,
                                   resourceId: UUID): Uri =
    toUri(
      ROOT_JOBS_URI_PATH / jobId.toIdString / resourceType / resourceId.toString)

  private def dsRoot(dsType: String): Uri.Path = ROOT_DS_URI_PATH / dsType

  protected def toDataSetsUrl(dsType: String): Uri = toUri(dsRoot(dsType))

  protected def toDataSetUrl(dsType: String, dsId: IdAble): Uri =
    toUri(dsRoot(dsType) / dsId.toIdString)

  protected def toDataSetResourcesUrl(dsType: String,
                                      dsId: IdAble,
                                      resourceType: String): Uri =
    toUri(dsRoot(dsType) / dsId.toIdString / resourceType)

  protected def toDataSetResourceUrl(dsType: String,
                                     dsId: IdAble,
                                     resourceType: String,
                                     resourceId: UUID) =
    toUri(
      dsRoot(dsType) / dsId.toIdString / resourceType / resourceId.toString)

  protected def toPacBioDataBundleUrl(bundleType: Option[String] = None): Uri = {
    val path = bundleType
      .map(b => ROOT_PB_DATA_BUNDLE_URI_PATH / b)
      .getOrElse(ROOT_PB_DATA_BUNDLE_URI_PATH)

    toUri(path)
  }

  def serviceStatusEndpoints: Vector[Uri.Path] =
    Vector(
      jobRoot(JobTypeIds.IMPORT_DATASET.id),
      jobRoot(JobTypeIds.CONVERT_FASTA_REFERENCE.id),
      jobRoot(JobTypeIds.CONVERT_FASTA_BARCODES.id),
      jobRoot(JobTypeIds.PBSMRTPIPE.id),
      ROOT_DS_URI_PATH / DataSetMetaTypes.Subread.shortName,
      ROOT_DS_URI_PATH / DataSetMetaTypes.HdfSubread.shortName,
      ROOT_DS_URI_PATH / DataSetMetaTypes.Reference.shortName,
      ROOT_DS_URI_PATH / DataSetMetaTypes.Barcode.shortName
    )

  /**
    * Check an endpoint for status 200
    *
    * @param endpointPath Provided as Relative to the base url in Client.
    * @return
    */
  def checkServiceEndpoint(endpointPath: Uri.Path): Int =
    checkEndpoint(toUri(endpointPath))

  /**
    * Check the UI webserver for "Status" on a non-https
    *
    * @param uiPort UI (e.g., tomcat) webserver port
    * @return
    */
  def checkUiEndpoint(uiPort: Int): Int = {
    checkEndpoint(
      RootUri.copy(authority = RootUri.authority.copy(port = uiPort)))
  }

  /**
    * Run over each defined Endpoint (provided as relative segments to the base)
    *
    * Will NOT fail early. It will run over all endpoints and return non-zero
    * if the any of the results have failed.
    *
    * Note, this is blocking.
    *
    * @return
    */
  def checkServiceEndpoints: Int = {
    serviceStatusEndpoints
      .map(checkServiceEndpoint)
      .foldLeft(0) { (a, v) =>
        Seq(a, v).max
      }
  }

  def getDataSet(datasetId: IdAble): Future[DataSetMetaDataSet] =
    getObject[DataSetMetaDataSet](
      Get(toUri(ROOT_DS_URI_PATH / datasetId.toIdString)))

  def deleteDataSet(datasetId: IdAble): Future[MessageResponse] =
    getMessageResponse(
      Put(toUri(ROOT_DS_URI_PATH / datasetId.toIdString),
          DataSetUpdateRequest(Some(false))))

  protected def toDataSetUrlWithQuery(
      ds: DataSetMetaTypes.DataSetMetaType,
      c: Option[DataSetSearchCriteria]): Uri = {
    val q = c.map(_.toQuery).getOrElse(Uri.Query())
    toUri(ROOT_DS_URI_PATH / ds.shortName).withQuery(q)
  }

  def getSubreadSets(c: Option[DataSetSearchCriteria] = None)
    : Future[Seq[SubreadServiceDataSet]] =
    getObject[Seq[SubreadServiceDataSet]](
      Get(toDataSetUrlWithQuery(DataSetMetaTypes.Subread, c)))

  def getSubreadSet(dsId: IdAble): Future[SubreadServiceDataSet] =
    getObject[SubreadServiceDataSet](
      Get(toDataSetUrl(DataSetMetaTypes.Subread.shortName, dsId)))

  def getSubreadSetDetails(dsId: IdAble): Future[SubreadSet] =
    getObject[SubreadSet](
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.Subread.shortName,
                              dsId,
                              "details")))

  def getSubreadSetReports(dsId: IdAble): Future[Seq[DataStoreReportFile]] =
    getObject[Seq[DataStoreReportFile]](
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.Subread.shortName,
                              dsId,
                              JOB_REPORT_PREFIX)))

  def updateSubreadSetDetails(
      dsId: IdAble,
      isActive: Option[Boolean] = None,
      bioSampleName: Option[String] = None,
      wellSampleName: Option[String] = None): Future[MessageResponse] =
    getMessageResponse(
      Put(toDataSetUrl(DataSetMetaTypes.Subread.shortName, dsId),
          DataSetUpdateRequest(isActive, bioSampleName, wellSampleName)))

  def getHdfSubreadSets(c: Option[DataSetSearchCriteria] = None)
    : Future[Seq[HdfSubreadServiceDataSet]] =
    getObject[Seq[HdfSubreadServiceDataSet]](
      Get(toDataSetUrlWithQuery(DataSetMetaTypes.HdfSubread, c)))

  def getHdfSubreadSet(dsId: IdAble): Future[HdfSubreadServiceDataSet] =
    getObject[HdfSubreadServiceDataSet](
      Get(toDataSetUrl(DataSetMetaTypes.HdfSubread.shortName, dsId)))

  def getHdfSubreadSetDetails(dsId: IdAble): Future[HdfSubreadSet] =
    getObject[HdfSubreadSet](
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.HdfSubread.shortName,
                              dsId,
                              "details")))

  def getBarcodeSets(c: Option[DataSetSearchCriteria] = None)
    : Future[Seq[BarcodeServiceDataSet]] =
    getObject[Seq[BarcodeServiceDataSet]](
      Get(toDataSetUrlWithQuery(DataSetMetaTypes.Barcode, c)))

  def getBarcodeSet(dsId: IdAble): Future[BarcodeServiceDataSet] =
    getObject[BarcodeServiceDataSet](
      Get(toDataSetUrl(DataSetMetaTypes.Barcode.shortName, dsId)))

  def getBarcodeSetDetails(dsId: IdAble): Future[BarcodeSet] =
    getObject[BarcodeSet](
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.Barcode.shortName,
                              dsId,
                              "details")))

  def getReferenceSets(c: Option[DataSetSearchCriteria] = None)
    : Future[Seq[ReferenceServiceDataSet]] =
    getObject[Seq[ReferenceServiceDataSet]](
      Get(toDataSetUrlWithQuery(DataSetMetaTypes.Reference, c)))

  def getReferenceSet(dsId: IdAble): Future[ReferenceServiceDataSet] =
    getObject[ReferenceServiceDataSet](
      Get(toDataSetUrl(DataSetMetaTypes.Reference.shortName, dsId)))

  def getReferenceSetDetails(dsId: IdAble): Future[ReferenceSet] =
    getObject[ReferenceSet](
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.Reference.shortName,
                              dsId,
                              "details")))

  def getGmapReferenceSets(c: Option[DataSetSearchCriteria] = None)
    : Future[Seq[GmapReferenceServiceDataSet]] =
    getObject[Seq[GmapReferenceServiceDataSet]](
      Get(toDataSetUrlWithQuery(DataSetMetaTypes.GmapReference, c)))

  def getGmapReferenceSet(dsId: IdAble): Future[GmapReferenceServiceDataSet] =
    getObject[GmapReferenceServiceDataSet](
      Get(toDataSetUrl(DataSetMetaTypes.GmapReference.shortName, dsId)))

  def getGmapReferenceSetDetails(dsId: IdAble): Future[GmapReferenceSet] =
    getObject[GmapReferenceSet](
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.GmapReference.shortName,
                              dsId,
                              "details")))

  def getAlignmentSets(c: Option[DataSetSearchCriteria] = None)
    : Future[Seq[AlignmentServiceDataSet]] =
    getObject[Seq[AlignmentServiceDataSet]](
      Get(toDataSetUrlWithQuery(DataSetMetaTypes.Alignment, c)))

  def getAlignmentSet(dsId: IdAble): Future[AlignmentServiceDataSet] =
    getObject[AlignmentServiceDataSet](
      Get(toDataSetUrl(DataSetMetaTypes.Alignment.shortName, dsId)))

  def getAlignmentSetDetails(dsId: IdAble): Future[AlignmentSet] =
    getObject[AlignmentSet](
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.Alignment.shortName,
                              dsId,
                              "details")))

  def getConsensusReadSets(c: Option[DataSetSearchCriteria] = None)
    : Future[Seq[ConsensusReadServiceDataSet]] =
    getObject[Seq[ConsensusReadServiceDataSet]](
      Get(toDataSetUrlWithQuery(DataSetMetaTypes.CCS, c)))

  def getConsensusReadSet(dsId: IdAble): Future[ConsensusReadServiceDataSet] =
    getObject[ConsensusReadServiceDataSet](
      Get(toDataSetUrl(DataSetMetaTypes.CCS.shortName, dsId)))

  def getConsensusReadSetDetails(dsId: IdAble): Future[ConsensusReadSet] =
    getObject[ConsensusReadSet](Get(
      toDataSetResourcesUrl(DataSetMetaTypes.CCS.shortName, dsId, "details")))

  def getConsensusAlignmentSets(c: Option[DataSetSearchCriteria] = None)
    : Future[Seq[ConsensusAlignmentServiceDataSet]] =
    getObject[Seq[ConsensusAlignmentServiceDataSet]](
      Get(toDataSetUrlWithQuery(DataSetMetaTypes.AlignmentCCS, c)))

  def getConsensusAlignmentSet(
      dsId: IdAble): Future[ConsensusAlignmentServiceDataSet] =
    getObject[ConsensusAlignmentServiceDataSet](
      Get(toDataSetUrl(DataSetMetaTypes.AlignmentCCS.shortName, dsId)))

  def getConsensusAlignmentSetDetails(
      dsId: IdAble): Future[ConsensusAlignmentSet] =
    getObject[ConsensusAlignmentSet](
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.AlignmentCCS.shortName,
                              dsId,
                              "details")))

  def getTranscriptSets(c: Option[DataSetSearchCriteria] = None)
    : Future[Seq[TranscriptServiceDataSet]] =
    getObject[Seq[TranscriptServiceDataSet]](
      Get(toDataSetUrlWithQuery(DataSetMetaTypes.Transcript, c)))

  def getTranscriptSet(dsId: IdAble): Future[TranscriptServiceDataSet] =
    getObject[TranscriptServiceDataSet](
      Get(toDataSetUrl(DataSetMetaTypes.Transcript.shortName, dsId)))

  def getTranscriptSetDetails(dsId: IdAble): Future[TranscriptSet] =
    getObject[TranscriptSet](
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.Transcript.shortName,
                              dsId,
                              "details")))

  def getContigSets(c: Option[DataSetSearchCriteria] = None)
    : Future[Seq[ContigServiceDataSet]] =
    getObject[Seq[ContigServiceDataSet]](
      Get(toDataSetUrlWithQuery(DataSetMetaTypes.Contig, c)))

  def getContigSet(dsId: IdAble): Future[ContigServiceDataSet] =
    getObject[ContigServiceDataSet](
      Get(toDataSetUrl(DataSetMetaTypes.Contig.shortName, dsId)))

  def getContigSetDetails(dsId: IdAble): Future[ContigSet] =
    getObject[ContigSet](
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.Contig.shortName,
                              dsId,
                              "details")))

  def getJobDataStore(jobId: IdAble): Future[Seq[DataStoreServiceFile]] =
    getObject[Seq[DataStoreServiceFile]](
      Get(toJobResourceUrl(jobId, JOB_DATASTORE_PREFIX)))

  // FIXME how to convert to String?
  def getDataStoreFile(fileId: UUID): Future[HttpResponse] =
    http.singleRequest(
      Get(toUri(ROOT_DATASTORE_URI_PATH / fileId.toString / "download")))

  def updateDataStoreFile(
      fileId: UUID,
      isActive: Boolean = true,
      path: Option[Path] = None,
      fileSize: Option[Long] = None): Future[MessageResponse] =
    getMessageResponse(
      Put(
        toUri(ROOT_DATASTORE_URI_PATH / fileId.toString),
        DataStoreFileUpdateRequest(isActive, path.map(_.toString), fileSize)))

  /*def getDataStoreFileBinary(fileId: UUID): Future[Array[Byte]] = rawDataPipeline {
    Get(toUrl(ROOT_DATASTORE + s"/${fileId}/download"))
  }*/

  def getJobReports(jobId: IdAble): Future[Seq[DataStoreReportFile]] =
    getObject[Seq[DataStoreReportFile]](
      Get(toJobResourceUrl(jobId, JOB_REPORT_PREFIX)))

  def getJobReport(jobId: IdAble, reportId: UUID) =
    getObject[Report](
      Get(toJobResourceIdUrl(jobId, JOB_REPORT_PREFIX, reportId)))

  def getDataStoreFileResource(fileId: UUID,
                               relpath: String): Future[HttpResponse] = {
    val query = Uri.Query("relpath" -> relpath)
    val uri = toUri(ROOT_DATASTORE_URI_PATH / fileId.toString / "resources")
      .withQuery(query)
    http.singleRequest(Get(uri))
  }

  def getJobTasks(jobId: IdAble): Future[Seq[JobTask]] =
    getObject[Seq[JobTask]](Get(toJobResourceUrl(jobId, JOB_TASK_PREFIX)))

  def getJobTask(jobId: IdAble, taskId: UUID): Future[JobTask] =
    getObject[JobTask](
      Get(toJobResourceUrl(jobId, JOB_TASK_PREFIX + "/" + taskId.toString)))

  def getJobEvents(jobId: Int): Future[Seq[JobEvent]] =
    getObject[Seq[JobEvent]](Get(toJobResourceUrl(jobId, JOB_EVENT_PREFIX)))

  def getJobOptions(jobId: Int): Future[PipelineTemplatePreset] =
    getObject[PipelineTemplatePreset](
      Get(toJobResourceUrl(jobId, JOB_OPTIONS)))

  def createJobTask(jobId: IdAble,
                    task: CreateJobTaskRecord): Future[JobTask] =
    getObject[JobTask](Post(toJobResourceUrl(jobId, JOB_TASK_PREFIX), task))

  def updateJobTask(jobId: IdAble,
                    update: UpdateJobTaskRecord): Future[JobTask] =
    getObject[JobTask](
      Put(
        toJobResourceUrl(jobId, JOB_TASK_PREFIX + "/" + update.uuid.toString),
        update))

  def updateJob(jobId: IdAble,
                name: Option[String],
                comment: Option[String],
                tags: Option[String]): Future[EngineJob] =
    getObject[EngineJob](
      Put(toUri(ROOT_JOBS_URI_PATH / jobId.toIdString),
          UpdateJobRecord(name, comment, tags)))

  // Runs
  protected def getRunUriPath(runId: UUID): Uri.Path =
    ROOT_RUNS_URI_PATH / runId.toString
  protected def getRunCollectionUriPath(runId: UUID,
                                        collectionId: UUID): Uri.Path =
    getRunUriPath(runId) / "collections" / collectionId.toString

  protected def getRunUrl(runId: UUID): Uri = toUri(getRunUriPath(runId))

  protected def getCollectionsUrl(runId: UUID): Uri =
    toUri(getRunUriPath(runId) / "collections")

  protected def getCollectionUrl(runId: UUID, collectionId: UUID): Uri =
    toUri(getRunCollectionUriPath(runId, collectionId))

  def getRuns: Future[Seq[RunSummary]] =
    getObject[Seq[RunSummary]](Get(toUri(ROOT_RUNS_URI_PATH)))

  def getRun(runId: UUID): Future[Run] =
    getObject[Run](Get(getRunUrl(runId)))

  def getCollections(runId: UUID): Future[Seq[CollectionMetadata]] =
    getObject[Seq[CollectionMetadata]](Get(getCollectionsUrl(runId)))

  def getCollection(runId: UUID,
                    collectionId: UUID): Future[CollectionMetadata] =
    getObject[CollectionMetadata](Get(getCollectionUrl(runId, collectionId)))

  def createRun(dataModel: String): Future[RunSummary] =
    getObject[RunSummary](
      Post(toUri(ROOT_RUNS_URI_PATH), RunCreate(dataModel)))

  def updateRun(runId: UUID,
                dataModel: Option[String] = None,
                reserved: Option[Boolean] = None): Future[RunSummary] =
    getObject[RunSummary](
      Post(getRunUrl(runId), RunUpdate(dataModel, reserved)))

  def deleteRun(runId: UUID): Future[MessageResponse] =
    getMessageResponse(Delete(getRunUrl(runId)))

  def getProjects: Future[Seq[Project]] =
    getObject[Seq[Project]](
      Get(toUri(ROOT_PROJECTS_URI_PATH)).withHeaders(headers: _*))

  def getProject(projectId: Int): Future[FullProject] =
    getObject[FullProject](
      Get(toUri(ROOT_PROJECTS_URI_PATH / projectId.toString))
        .withHeaders(headers: _*))

  // XXX note that the project-related API calls require authentication and
  // aren't actually usable in this class; use AuthenticatedServiceAccessLayer
  // instead
  def createProject(
      name: String,
      description: String,
      userName: Option[String] = None,
      grantRoleToAll: Option[ProjectRequestRole.ProjectRequestRole] = None)
    : Future[FullProject] = {
    val members = userName.map { user =>
      Seq(ProjectRequestUser(user, ProjectUserRole.OWNER))
    }
    val d =
      ProjectRequest(name, description, None, grantRoleToAll, None, members)
    getObject[FullProject](
      Post(toUri(ROOT_PROJECTS_URI_PATH), d).withHeaders(headers: _*))
  }

  def updateProject(projectId: Int,
                    request: ProjectRequest): Future[FullProject] =
    getObject[FullProject](
      Put(toUri(ROOT_PROJECTS_URI_PATH / projectId.toString), request)
        .withHeaders(headers: _*))

  // User agreements (not really a EULA)
  def getEula(version: String): Future[EulaRecord] =
    getObject[EulaRecord](Get(toUri(ROOT_EULA_URI_PATH / version)))

  def getEulas: Future[Seq[EulaRecord]] =
    getObject[Seq[EulaRecord]](Get(ROOT_EULA))

  def acceptEula(user: String,
                 enableInstallMetrics: Boolean = true,
                 enableJobMetrics: Boolean = true): Future[EulaRecord] =
    getObject[EulaRecord](
      Post(toUri(ROOT_EULA_URI_PATH),
           EulaAcceptance(user, enableInstallMetrics, Some(enableJobMetrics))))

  def deleteEula(version: String): Future[MessageResponse] =
    getMessageResponse(Delete(toUri(ROOT_EULA_URI_PATH / version)))

  protected def toJobUrlWithQuery(jobType: String,
                                  c: Option[JobSearchCriteria],
                                  projectId: Option[Int] = None): Uri = {
    val q = c
      .map { sc =>
        projectId
          .map(p => sc.withProject(p))
          .getOrElse(sc)
          .toQuery
      }
      .getOrElse {
        projectId
          .map(p => Uri.Query("projectId" -> p.toString))
          .getOrElse(Uri.Query())
      }
    toUri(jobRoot(jobType)).withQuery(q)
  }

  def getJobsByType(jobType: String,
                    searchCriteria: Option[JobSearchCriteria] = None,
                    projectId: Option[Int] = None): Future[Seq[EngineJob]] =
    getObject[Seq[EngineJob]](
      Get(toJobUrlWithQuery(jobType, searchCriteria, projectId)))

  def getJobsByProject(projectId: Int): Future[Seq[EngineJob]] = {
    val q = Uri.Query("projectId" -> projectId.toString)
    getObject[Seq[EngineJob]](Get(toUri(ROOT_JOBS_URI_PATH).withQuery(q)))
  }

  def getPacBioComponentManifests: Future[Seq[PacBioComponentManifest]] =
    getObject[Seq[PacBioComponentManifest]](
      Get(toUri(ROOT_SERVICE_MANIFESTS_URI_PATH)))

  // Added in smrtflow 0.1.11 and SA > 3.2.0
  def getPacBioComponentManifestById(
      manifestId: String): Future[PacBioComponentManifest] =
    getObject[PacBioComponentManifest](
      Get(toUri(ROOT_SERVICE_MANIFESTS_URI_PATH / manifestId)))

  def getAnalysisJobs(searchCriteria: Option[JobSearchCriteria] = None) =
    getJobsByType(JobTypeIds.PBSMRTPIPE.id, searchCriteria)

  def getImportJobs(searchCriteria: Option[JobSearchCriteria] = None) =
    getJobsByType(JobTypeIds.IMPORT_DATASET.id, searchCriteria)

  def getMergeJobs(searchCriteria: Option[JobSearchCriteria] = None) =
    getJobsByType(JobTypeIds.MERGE_DATASETS.id, searchCriteria)

  def getFastaConvertJobs(searchCriteria: Option[JobSearchCriteria] = None) =
    getJobsByType(JobTypeIds.CONVERT_FASTA_REFERENCE.id, searchCriteria)

  def getBarcodeConvertJobs(searchCriteria: Option[JobSearchCriteria] = None) =
    getJobsByType(JobTypeIds.CONVERT_FASTA_BARCODES.id, searchCriteria)

  def getDatasetDeleteJobs(searchCriteria: Option[JobSearchCriteria] = None) =
    getJobsByType(JobTypeIds.DELETE_DATASETS.id, searchCriteria)

  def getTsSystemBundleJobs(searchCriteria: Option[JobSearchCriteria] = None) =
    getJobsByType(JobTypeIds.TS_SYSTEM_STATUS.id, searchCriteria)

  def getTsFailedJobBundleJobs(
      searchCriteria: Option[JobSearchCriteria] = None) =
    getJobsByType(JobTypeIds.TS_JOB.id, searchCriteria)

  def getDbBackUpJobs(searchCriteria: Option[JobSearchCriteria] = None) =
    getJobsByType(JobTypeIds.DB_BACKUP.id, searchCriteria)

  def getAnalysisJobsForProject(projectId: Int): Future[Seq[EngineJob]] =
    getJobsByType(JobTypeIds.PBSMRTPIPE.id, projectId = Some(projectId))

  def getImportJobsForProject(projectId: Int): Future[Seq[EngineJob]] =
    getJobsByType(JobTypeIds.IMPORT_DATASET.id, projectId = Some(projectId))

  def getMergeJobsForProject(projectId: Int): Future[Seq[EngineJob]] =
    getJobsByType(JobTypeIds.MERGE_DATASETS.id, projectId = Some(projectId))

  def getFastaConvertJobsForProject(projectId: Int): Future[Seq[EngineJob]] =
    getJobsByType(JobTypeIds.CONVERT_FASTA_REFERENCE.id,
                  projectId = Some(projectId))

  def getBarcodeConvertJobsForProject(projectId: Int): Future[Seq[EngineJob]] =
    getJobsByType(JobTypeIds.CONVERT_FASTA_BARCODES.id,
                  projectId = Some(projectId))

  def getJob(jobId: IdAble): Future[EngineJob] =
    getObject[EngineJob](Get(toUri(ROOT_JOBS_URI_PATH / jobId.toIdString)))

  def deleteJob(jobId: UUID,
                removeFiles: Boolean = true,
                dryRun: Boolean = false,
                force: Boolean = false,
                projectId: Option[Int] = None): Future[EngineJob] =
    getObject[EngineJob](
      Post(
        toUri(jobRoot(JobTypeIds.DELETE_JOB.id)),
        DeleteSmrtLinkJobOptions(jobId,
                                 Some(s"Delete job $jobId name"),
                                 None,
                                 removeFiles,
                                 dryRun = Some(dryRun),
                                 force = Some(force),
                                 projectId = projectId)
      ))

  def getJobChildren(jobId: IdAble): Future[Seq[EngineJob]] =
    getObject[Seq[EngineJob]](
      Get(toUri(ROOT_JOBS_URI_PATH / jobId.toIdString / "children")))

  def getJobByTypeAndId(jobType: String, jobId: IdAble): Future[EngineJob] =
    getObject[EngineJob](Get(toJobUri(jobType, Some(jobId))))

  def getAnalysisJob(jobId: IdAble): Future[EngineJob] = {
    getJobByTypeAndId(JobTypeIds.PBSMRTPIPE.id, jobId)
  }

  def getJobEntryPoints(jobId: IdAble): Future[Seq[EngineJobEntryPoint]] =
    getObject[Seq[EngineJobEntryPoint]](
      Get(toJobResourceUrl(jobId, ENTRY_POINTS_PREFIX)))

  def terminatePbsmrtpipeJob(jobId: Int): Future[MessageResponse] =
    getMessageResponse(Post(toJobResourceUrl(jobId, TERMINATE_JOB)))

  def getReportViewRules: Future[Seq[ReportViewRule]] =
    getObject[Seq[ReportViewRule]](Get(toUri(ROOT_REPORT_RULES_URI_PATH)))

  def getReportViewRule(reportId: String): Future[ReportViewRule] =
    getObject[ReportViewRule](
      Get(toUri(ROOT_REPORT_RULES_URI_PATH / reportId)))

  def importDataSet(path: Path,
                    dsMetaType: DataSetMetaTypes.DataSetMetaType,
                    projectId: Option[Int] = None): Future[EngineJob] =
    getObject[EngineJob](
      Post(toUri(jobRoot(JobTypeIds.IMPORT_DATASET.id)),
           ImportDataSetJobOptions(path.toAbsolutePath,
                                   dsMetaType,
                                   None,
                                   None,
                                   projectId = projectId)))

  def importFasta(path: Path,
                  name: String,
                  organism: String,
                  ploidy: String,
                  projectId: Option[Int] = None): Future[EngineJob] =
    getObject[EngineJob](
      Post(toUri(jobRoot(JobTypeIds.CONVERT_FASTA_REFERENCE.id)),
           ImportFastaJobOptions(toP(path),
                                 ploidy,
                                 organism,
                                 Some(name),
                                 None,
                                 projectId = projectId)))

  def importFastaBarcodes(path: Path,
                          name: String,
                          projectId: Option[Int] = None): Future[EngineJob] =
    getObject[EngineJob](
      Post(toUri(jobRoot(JobTypeIds.CONVERT_FASTA_BARCODES.id)),
           ImportBarcodeFastaJobOptions(path.toAbsolutePath,
                                        Some(name),
                                        None,
                                        projectId = projectId)))

  def importFastaGmap(path: Path,
                      name: String,
                      organism: String,
                      ploidy: String,
                      projectId: Option[Int] = None): Future[EngineJob] =
    getObject[EngineJob](
      Post(toUri(jobRoot(JobTypeIds.CONVERT_FASTA_GMAPREFERENCE.id)),
           ImportFastaJobOptions(toP(path),
                                 ploidy,
                                 organism,
                                 Some(name),
                                 None,
                                 projectId = projectId)))

  def mergeDataSets(datasetType: DataSetMetaTypes.DataSetMetaType,
                    ids: Seq[IdAble],
                    name: String,
                    projectId: Option[Int] = None) =
    getObject[EngineJob](
      Post(toUri(jobRoot(JobTypeIds.MERGE_DATASETS.id)),
           MergeDataSetJobOptions(datasetType,
                                  ids,
                                  Some(name),
                                  None,
                                  projectId = projectId)))

  def convertRsMovie(path: Path, name: String, projectId: Option[Int] = None) =
    getObject[EngineJob](
      Post(toUri(jobRoot(JobTypeIds.CONVERT_RS_MOVIE.id)),
           RsConvertMovieToDataSetJobOptions(toP(path),
                                             Some(name),
                                             None,
                                             projectId = projectId)))

  def exportDataSets(datasetType: DataSetMetaTypes.DataSetMetaType,
                     ids: Seq[IdAble],
                     outputPath: Path,
                     deleteAfterExport: Boolean = false,
                     projectId: Option[Int] = None): Future[EngineJob] =
    getObject[EngineJob](
      Post(
        toUri(jobRoot(JobTypeIds.EXPORT_DATASETS.id)),
        ExportDataSetsJobOptions(datasetType,
                                 ids,
                                 outputPath.toAbsolutePath,
                                 Some(deleteAfterExport),
                                 projectId = projectId)
      ))

  def deleteDataSets(datasetType: DataSetMetaTypes.DataSetMetaType,
                     ids: Seq[IdAble],
                     removeFiles: Boolean = true,
                     projectId: Option[Int] = None): Future[EngineJob] =
    getObject[EngineJob](
      Post(toUri(jobRoot(JobTypeIds.DELETE_DATASETS.id)),
           DeleteDataSetJobOptions(ids,
                                   datasetType,
                                   removeFiles,
                                   None,
                                   None,
                                   projectId = projectId)))

  def getPipelineTemplate(pipelineId: String): Future[PipelineTemplate] =
    getObject[PipelineTemplate](Get(toUri(ROOT_PT_URI_PATH / pipelineId)))

  def getPipelineTemplates: Future[Seq[PipelineTemplate]] =
    getObject[Seq[PipelineTemplate]](Get(toUri(ROOT_PT_URI_PATH)))

  def getPipelineTemplateViewRules: Future[Seq[PipelineTemplateViewRule]] =
    getObject[Seq[PipelineTemplateViewRule]](
      Get(toUri(ROOT_PT_RULES_URI_PATH)))

  def getPipelineTemplateViewRule(
      pipelineId: String): Future[PipelineTemplateViewRule] =
    getObject[PipelineTemplateViewRule](
      Get(toUri(ROOT_PT_RULES_URI_PATH / pipelineId)))

  def getPipelineDataStoreViewRules(
      pipelineId: String): Future[PipelineDataStoreViewRules] =
    getObject[PipelineDataStoreViewRules](
      Get(toUri(ROOT_DS_RULES_URI_PATH / pipelineId)))

  def runAnalysisPipeline(
      pipelineOptions: PbsmrtpipeJobOptions): Future[EngineJob] =
    getObject[EngineJob](
      Post(toUri(jobRoot(JobTypeIds.PBSMRTPIPE.id)), pipelineOptions))

  def toMultiJobUriPath(ix: Option[IdAble] = None): Uri.Path = {
    val base = ROOT_MULTI_JOBS_URI_PATH / JobTypeIds.MJOB_MULTI_ANALYSIS.id
    ix.map(i => base / i.toIdString).getOrElse(base)
  }

  def createMultiAnalysisJob(
      multiAnalysisJobOptions: MultiAnalysisJobOptions): Future[EngineJob] =
    getObject[EngineJob](
      Post(toUri(toMultiJobUriPath()), multiAnalysisJobOptions))

  def updateMultiAnalysisJobToSubmit(ix: IdAble): Future[MessageResponse] =
    getMessageResponse(Post(toUri(toMultiJobUriPath(Some(ix)) / "submit")))

  def getMultiAnalysisChildrenJobs(ix: IdAble): Future[Seq[EngineJob]] =
    getObject[Seq[EngineJob]](Get(toUri(toMultiJobUriPath(Some(ix)) / "jobs")))

  // PacBio Data Bundle
  def getPacBioDataBundles(): Future[Seq[PacBioDataBundle]] =
    getObject[Seq[PacBioDataBundle]](Get(toPacBioDataBundleUrl()))

  def getPacBioDataBundleByTypeId(
      typeId: String): Future[Seq[PacBioDataBundle]] =
    getObject[Seq[PacBioDataBundle]](Get(toPacBioDataBundleUrl(Some(typeId))))

  def getPacBioDataBundleByTypeAndVersionId(typeId: String,
                                            versionId: String) =
    getObject[PacBioDataBundle](
      Get(toPacBioDataBundleUrl(Some(s"$typeId/$versionId"))))

  def runTsSystemStatus(user: String, comment: String): Future[EngineJob] = {
    getObject[EngineJob](
      Post(toUri(jobRoot(JobTypeIds.TS_SYSTEM_STATUS.id)),
           TsSystemStatusServiceOptions(user, comment)))
  }

  /**
    * Create a Failed TechSupport Job
    *
    * @param jobId   Failed Job id that is in the FAILED state is supported
    * @param user    User that has created the job TGZ bundle
    * @param comment Comment
    * @return
    */
  def runTsJobBundle(jobId: Int,
                     user: String,
                     comment: String): Future[EngineJob] =
    getObject[EngineJob](
      Post(toUri(jobRoot(JobTypeIds.TS_JOB.id)),
           TsJobBundleJobServiceOptions(jobId, user, comment)))

  /**
    * Submit a request to create a DB BackUp Job
    *
    * The server must be configured with a backup path, otherwise, there should be an error raised.
    *
    * @param user    User that is creating the request to backup the db
    * @param comment A comment from the user
    * @return
    */
  def runDbBackUpJob(user: String, comment: String): Future[EngineJob] =
    getObject[EngineJob](
      Post(toUri(jobRoot(JobTypeIds.DB_BACKUP.id)),
           DbBackUpServiceJobOptions(user, comment)))

  /**
    * Start export of SMRT Link job(s)
    */
  def exportJobs(jobIds: Seq[IdAble],
                 outputPath: Path,
                 includeEntryPoints: Boolean = false,
                 name: Option[String] = None,
                 description: Option[String] = None): Future[EngineJob] =
    getObject[EngineJob](
      Post(toUri(jobRoot(JobTypeIds.EXPORT_JOBS.id)),
           ExportSmrtLinkJobOptions(jobIds,
                                    outputPath,
                                    includeEntryPoints,
                                    name,
                                    description)))

  /**
    * Start import of SMRT Link job from zip file
    *
    * @param zipPath   pathname of ZIP file exported by SMRT Link
    * @param mockJobId if true, job UUID will be re-generated
    * @return new EngineJob object
    */
  def importJob(zipPath: Path, mockJobId: Boolean = false): Future[EngineJob] =
    getObject[EngineJob](
      Post(toUri(jobRoot(JobTypeIds.IMPORT_JOB.id)),
           ImportSmrtLinkJobOptions(zipPath, mockJobId = Some(mockJobId))))

  def copyDataSet(dsId: IdAble,
                  filters: Seq[Seq[DataSetFilterProperty]],
                  dsName: Option[String] = None): Future[EngineJob] =
    getObject[EngineJob](
      Post(toUri(jobRoot(JobTypeIds.DS_COPY.id)),
           CopyDataSetJobOptions(dsId, filters, dsName, None, None)))

  def getAlarms(): Future[Seq[AlarmStatus]] =
    getObject[Seq[AlarmStatus]](Get(toUri(ROOT_ALARMS_URI_PATH)))

  def addRegistryService(host: String,
                         port: Int,
                         resourceId: String): Future[RegistryResource] = {
    getObject[RegistryResource](
      Post(toUri(ROOT_REGISTRY_URI_PATH / "resources"),
           RegistryResourceCreate(host, port, resourceId))
    )
  }

  /**
    * Simple access of only GET
    *
    * @param resource UUID of the registry Proxy Resource
    * @param path Segment to get from Proxy Service
    * @return
    */
  def getRegistryProxy(resource: UUID, path: Uri.Path): Future[HttpResponse] = {
    val px = if (path.startsWithSlash) path else Uri.Path./ ++ path
    getObject[HttpResponse](
      Get(
        toUri(
          Uri.Path(s"$ROOT_REGISTRY_URI_PATH/resources/$resource/proxy$px")))
    )
  }

  /**
    * FIXME(mpkocher)(2016-8-22)
    * - replace tStart with JodaDateTime
    * - make sleepTime configurable
    * - Add Retry to Poll
    * - Raise Custom Exception type for Failed job to distinguish Failed jobs and jobs that exceeded maxTime
    * - replace while loop with recursion
    *
    * @param jobId   Job Id or UUID
    * @param maxTime Max time to poll for the job
    * @return EngineJob
    */
  def pollForSuccessfulJob(jobId: IdAble,
                           maxTime: Option[FiniteDuration] = None,
                           sleepTime: Int = 5000): Try[EngineJob] = {
    var nIterations = 0
    var continueRunning = true
    // This is the fundamental control structure for communicating failure
    var errorMessage: Option[String] = None

    val requestTimeOut = 30.seconds
    val tStart = java.lang.System.currentTimeMillis() / 1000.0
    logger.debug(
      s"Polling for core job ${jobId.toIdString} to finish successfully")

    def hasExceededMaxRunTime(): Boolean = {
      val tCurrent = java.lang.System.currentTimeMillis() / 1000.0
      val maxTimeSec: Int = maxTime.map(_.toSeconds.toInt).getOrElse(-1)

      ((maxTimeSec > 0) && (tCurrent - tStart > maxTimeSec))
    }

    while (continueRunning) {
      nIterations += 1
      Thread.sleep(sleepTime)

      val tx2 = Try(Await.result(getJob(jobId), requestTimeOut))

      tx2 match {
        case Success(xjob) =>
          xjob.state match {
            case AnalysisJobStates.SUCCESSFUL =>
              continueRunning = false
              errorMessage = None
            case AnalysisJobStates.FAILED =>
              continueRunning = false
              errorMessage = xjob.errorMessage
            case AnalysisJobStates.TERMINATED =>
              continueRunning = false
              errorMessage = xjob.errorMessage
            case _ =>
              val exceededTime = hasExceededMaxRunTime()
              continueRunning = true
              if (exceededTime) {
                continueRunning = false
                errorMessage = Some(
                  s"Job ${xjob.id} Run time exceeded specified limit ($maxTime s)")
              }
          }
        case Failure(errorMsg) =>
          continueRunning = false
          errorMessage = Some(s"Job ${jobId.toIdString} Error $errorMsg")
      }
    }

    errorMessage match {
      case Some(errorMsg) =>
        Failure(
          new Exception(s"Failed to run job ${jobId.toIdString}. $errorMsg"))
      case None =>
        Try(Await.result(getJob(jobId), requestTimeOut))

    }
  }

  /**
    * Block (if maxTime is provided) and Poll for a job to reach a completed (failed or successful) state.
    *
    * Consolidate the duplication with pollForSuccessfulJob
    *
    * @param jobId     Engine Job Id
    * @param maxTime   max time to block and poll for the job, otherwise only create the job
    * @param sleepTime time between polling when run in blocking mode.
    * @return
    */
  def pollForCompletedJob(jobId: IdAble,
                          maxTime: Option[FiniteDuration] = None,
                          sleepTime: Int = 5000): Try[EngineJob] = {
    var exitFlag = true
    var nIterations = 0
    val requestTimeOut = 30.seconds
    var runningJob: Option[EngineJob] = None
    val tStart = java.lang.System.currentTimeMillis() / 1000.0
    logger.debug(s"Polling for core job id:${jobId.toIdString} to complete")

    def failIfExceededMaxTime(job: EngineJob): Try[EngineJob] = {
      val tCurrent = java.lang.System.currentTimeMillis() / 1000.0

      // This could be cleaned up, but these changes are turning into
      // a large yakk shaving exercise
      val maxTimeSec: Int = maxTime.map(_.toSeconds.toInt).getOrElse(-1)

      if ((maxTimeSec > 0) && (tCurrent - tStart > maxTimeSec)) {
        Failure(new Exception(
          s"Job ${job.id} ${runningJob.map(_.state).getOrElse("")} Run time exceeded specified limit ($maxTime s)"))
      } else {
        Success(job)
      }
    }

    while (exitFlag) {
      nIterations += 1
      Thread.sleep(sleepTime)

      val tx = for {
        createdJob <- Try {
          Await.result(getJob(jobId), requestTimeOut)
        }
        _ <- failIfExceededMaxTime(createdJob)
      } yield createdJob

      tx match {
        case Success(job) =>
          if (job.isComplete) {
            exitFlag = false
            runningJob = Some(job)
          }
        case Failure(_) =>
          exitFlag = false
      }
    }

    // If the job couldn't get created, this is catching the case when the job can't
    // be created.
    runningJob match {
      case Some(job) => Success(job)
      case _ =>
        Failure(new Exception(
          s"Failed to run job ${jobId.toIdString}. ${runningJob.map(_.errorMessage).getOrElse("")}"))
    }
  }

}
