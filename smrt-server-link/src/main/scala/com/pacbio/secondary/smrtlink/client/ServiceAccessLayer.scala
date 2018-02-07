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
import com.pacbio.secondary.smrtlink.analysis.jobtypes._
import com.pacbio.secondary.smrtlink.jsonprotocols._
import com.pacbio.secondary.smrtlink.analysis.reports._
import com.pacbio.secondary.smrtlink.auth.JwtUtils._
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.auth.JwtUtilsImpl
import com.pacbio.secondary.smrtlink.jobtypes._
import com.pacbio.secondary.smrtlink.models._

class SmrtLinkServiceClient(baseUrl: URL, authUser: Option[String])(
    implicit actorSystem: ActorSystem)
    extends ServiceAccessLayer(baseUrl)(actorSystem)
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

  def this(host: String, port: Int, authUser: Option[String] = None)(
      implicit actorSystem: ActorSystem) {
    this(UrlUtils.convertToUrl(host, port), authUser)(actorSystem)
  }

  private def toP(path: Path) = path.toAbsolutePath.toString

  private def jobRoot(jobType: String) = s"${ROOT_JOBS}/${jobType}"

  protected def toJobUrl(jobType: String, jobId: IdAble): String =
    toUrl(jobRoot(jobType) + s"/${jobId.toIdString}")

  protected def toJobResourceUrl(jobId: IdAble, resourceType: String): String =
    toUrl(s"${ROOT_JOBS}/${jobId.toIdString}/$resourceType")

  protected def toJobResourceIdUrl(jobId: IdAble,
                                   resourceType: String,
                                   resourceId: UUID) =
    toUrl(s"${ROOT_JOBS}/${jobId.toIdString}/$resourceType/$resourceId")

  private def dsRoot(dsType: String) = s"${ROOT_DS}/${dsType}"

  protected def toDataSetsUrl(dsType: String): String = toUrl(dsRoot(dsType))

  protected def toDataSetUrl(dsType: String, dsId: IdAble): String =
    toUrl(dsRoot(dsType) + s"/${dsId.toIdString}")

  protected def toDataSetResourcesUrl(dsType: String,
                                      dsId: IdAble,
                                      resourceType: String): String =
    toUrl(dsRoot(dsType) + s"/${dsId.toIdString}/$resourceType")

  protected def toDataSetResourceUrl(dsType: String,
                                     dsId: IdAble,
                                     resourceType: String,
                                     resourceId: UUID) =
    toUrl(dsRoot(dsType) + s"/${dsId.toIdString}/$resourceType/$resourceId")

  protected def toPacBioDataBundleUrl(
      bundleType: Option[String] = None): String = {
    val segment = bundleType.map(b => s"/$b").getOrElse("")
    toUrl(ROOT_PB_DATA_BUNDLE + segment)
  }

  protected def toUiRootUrl(port: Int): String =
    new URL(baseUrl.getProtocol, baseUrl.getHost, port, "/").toString

  def serviceStatusEndpoints: Vector[String] =
    Vector(
      jobRoot(JobTypeIds.IMPORT_DATASET.id),
      jobRoot(JobTypeIds.CONVERT_FASTA_REFERENCE.id),
      jobRoot(JobTypeIds.CONVERT_FASTA_BARCODES.id),
      jobRoot(JobTypeIds.PBSMRTPIPE.id),
      ROOT_DS + "/" + DataSetMetaTypes.Subread.shortName,
      ROOT_DS + "/" + DataSetMetaTypes.HdfSubread.shortName,
      ROOT_DS + "/" + DataSetMetaTypes.Reference.shortName,
      ROOT_DS + "/" + DataSetMetaTypes.Barcode.shortName
    )

  /**
    * Check an endpoint for status 200
    *
    * @param endpointPath Provided as Relative to the base url in Client.
    * @return
    */
  def checkServiceEndpoint(endpointPath: String): Int =
    checkEndpoint(toUrl(endpointPath))

  /**
    * Check the UI webserver for "Status"
    *
    * @param uiPort UI webserver port
    * @return
    */
  def checkUiEndpoint(uiPort: Int): Int = checkEndpoint(toUiRootUrl(uiPort))

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
      Get(toUrl(ROOT_DS + "/" + datasetId.toIdString)))

  def deleteDataSet(datasetId: IdAble): Future[MessageResponse] =
    getMessageResponse(
      Put(toUrl(ROOT_DS + "/" + datasetId.toIdString),
          DataSetUpdateRequest(Some(false))))

  def getSubreadSets: Future[Seq[SubreadServiceDataSet]] =
    getObject[Seq[SubreadServiceDataSet]](
      Get(toDataSetsUrl(DataSetMetaTypes.Subread.shortName)))

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

  def getHdfSubreadSets: Future[Seq[HdfSubreadServiceDataSet]] =
    getObject[Seq[HdfSubreadServiceDataSet]](
      Get(toDataSetsUrl(DataSetMetaTypes.HdfSubread.shortName)))

  def getHdfSubreadSet(dsId: IdAble): Future[HdfSubreadServiceDataSet] =
    getObject[HdfSubreadServiceDataSet](
      Get(toDataSetUrl(DataSetMetaTypes.HdfSubread.shortName, dsId)))

  def getHdfSubreadSetDetails(dsId: IdAble): Future[HdfSubreadSet] =
    getObject[HdfSubreadSet](
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.HdfSubread.shortName,
                              dsId,
                              "details")))

  def getBarcodeSets: Future[Seq[BarcodeServiceDataSet]] =
    getObject[Seq[BarcodeServiceDataSet]](
      Get(toDataSetsUrl(DataSetMetaTypes.Barcode.shortName)))

  def getBarcodeSet(dsId: IdAble): Future[BarcodeServiceDataSet] =
    getObject[BarcodeServiceDataSet](
      Get(toDataSetUrl(DataSetMetaTypes.Barcode.shortName, dsId)))

  def getBarcodeSetDetails(dsId: IdAble): Future[BarcodeSet] =
    getObject[BarcodeSet](
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.Barcode.shortName,
                              dsId,
                              "details")))

  def getReferenceSets: Future[Seq[ReferenceServiceDataSet]] =
    getObject[Seq[ReferenceServiceDataSet]](
      Get(toDataSetsUrl(DataSetMetaTypes.Reference.shortName)))

  def getReferenceSet(dsId: IdAble): Future[ReferenceServiceDataSet] =
    getObject[ReferenceServiceDataSet](
      Get(toDataSetUrl(DataSetMetaTypes.Reference.shortName, dsId)))

  def getReferenceSetDetails(dsId: IdAble): Future[ReferenceSet] =
    getObject[ReferenceSet](
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.Reference.shortName,
                              dsId,
                              "details")))

  def getGmapReferenceSets: Future[Seq[GmapReferenceServiceDataSet]] =
    getObject[Seq[GmapReferenceServiceDataSet]](
      Get(toDataSetsUrl(DataSetMetaTypes.GmapReference.shortName)))

  def getGmapReferenceSet(dsId: IdAble): Future[GmapReferenceServiceDataSet] =
    getObject[GmapReferenceServiceDataSet](
      Get(toDataSetUrl(DataSetMetaTypes.GmapReference.shortName, dsId)))

  def getGmapReferenceSetDetails(dsId: IdAble): Future[GmapReferenceSet] =
    getObject[GmapReferenceSet](
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.GmapReference.shortName,
                              dsId,
                              "details")))

  def getAlignmentSets: Future[Seq[AlignmentServiceDataSet]] =
    getObject[Seq[AlignmentServiceDataSet]](
      Get(toDataSetsUrl(DataSetMetaTypes.Alignment.shortName)))

  def getAlignmentSet(dsId: IdAble): Future[AlignmentServiceDataSet] =
    getObject[AlignmentServiceDataSet](
      Get(toDataSetUrl(DataSetMetaTypes.Alignment.shortName, dsId)))

  def getAlignmentSetDetails(dsId: IdAble): Future[AlignmentSet] =
    getObject[AlignmentSet](
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.Alignment.shortName,
                              dsId,
                              "details")))

  def getConsensusReadSets: Future[Seq[ConsensusReadServiceDataSet]] =
    getObject[Seq[ConsensusReadServiceDataSet]](
      Get(toDataSetsUrl(DataSetMetaTypes.CCS.shortName)))

  def getConsensusReadSet(dsId: IdAble): Future[ConsensusReadServiceDataSet] =
    getObject[ConsensusReadServiceDataSet](
      Get(toDataSetUrl(DataSetMetaTypes.CCS.shortName, dsId)))

  def getConsensusReadSetDetails(dsId: IdAble): Future[ConsensusReadSet] =
    getObject[ConsensusReadSet](Get(
      toDataSetResourcesUrl(DataSetMetaTypes.CCS.shortName, dsId, "details")))

  def getConsensusAlignmentSets
    : Future[Seq[ConsensusAlignmentServiceDataSet]] =
    getObject[Seq[ConsensusAlignmentServiceDataSet]](
      Get(toDataSetsUrl(DataSetMetaTypes.AlignmentCCS.shortName)))

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

  def getContigSets: Future[Seq[ContigServiceDataSet]] =
    getObject[Seq[ContigServiceDataSet]](
      Get(toDataSetsUrl(DataSetMetaTypes.Contig.shortName)))

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
    http.singleRequest(Get(toUrl(ROOT_DATASTORE + s"/${fileId}/download")))

  def updateDataStoreFile(
      fileId: UUID,
      isActive: Boolean = true,
      path: Option[Path] = None,
      fileSize: Option[Long] = None): Future[MessageResponse] =
    getMessageResponse(
      Put(
        toUrl(ROOT_DATASTORE + s"/$fileId"),
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
                               relpath: String): Future[HttpResponse] =
    http.singleRequest(
      Get(toUrl(ROOT_DATASTORE + s"/${fileId}/resources?relpath=${relpath}")))

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

  // Runs

  protected def getRunUrl(runId: UUID): String = toUrl(s"${ROOT_RUNS}/$runId")

  protected def getCollectionsUrl(runId: UUID): String =
    toUrl(s"${ROOT_RUNS}/$runId/collections")

  protected def getCollectionUrl(runId: UUID, collectionId: UUID): String =
    toUrl(s"${ROOT_RUNS}/$runId/collections/$collectionId")

  def getRuns: Future[Seq[RunSummary]] =
    getObject[Seq[RunSummary]](Get(toUrl(ROOT_RUNS)))

  def getRun(runId: UUID): Future[Run] =
    getObject[Run](Get(getRunUrl(runId)))

  def getCollections(runId: UUID): Future[Seq[CollectionMetadata]] =
    getObject[Seq[CollectionMetadata]](Get(getCollectionsUrl(runId)))

  def getCollection(runId: UUID,
                    collectionId: UUID): Future[CollectionMetadata] =
    getObject[CollectionMetadata](Get(getCollectionUrl(runId, collectionId)))

  def createRun(dataModel: String): Future[RunSummary] =
    getObject[RunSummary](Post(toUrl(ROOT_RUNS), RunCreate(dataModel)))

  def updateRun(runId: UUID,
                dataModel: Option[String] = None,
                reserved: Option[Boolean] = None): Future[RunSummary] =
    getObject[RunSummary](
      Post(getRunUrl(runId), RunUpdate(dataModel, reserved)))

  def deleteRun(runId: UUID): Future[MessageResponse] =
    getMessageResponse(Delete(getRunUrl(runId)))

  def getProjects: Future[Seq[Project]] =
    getObject[Seq[Project]](Get(toUrl(ROOT_PROJECTS)).withHeaders(headers: _*))

  def getProject(projectId: Int): Future[FullProject] =
    getObject[FullProject](
      Get(toUrl(ROOT_PROJECTS + s"/$projectId")).withHeaders(headers: _*))

  def createProject(name: String, description: String): Future[FullProject] =
    getObject[FullProject](
      Post(toUrl(ROOT_PROJECTS),
           ProjectRequest(name, description, None, None, None, None))
        .withHeaders(headers: _*))

  def updateProject(projectId: Int,
                    request: ProjectRequest): Future[FullProject] =
    getObject[FullProject](
      Put(toUrl(ROOT_PROJECTS + s"/$projectId"), request)
        .withHeaders(headers: _*))

  // User agreements (not really a EULA)
  def getEula(version: String): Future[EulaRecord] =
    getObject[EulaRecord](Get(toUrl(ROOT_EULA + s"/$version")))

  def getEulas: Future[Seq[EulaRecord]] =
    getObject[Seq[EulaRecord]](Get(ROOT_EULA))

  def acceptEula(user: String,
                 enableInstallMetrics: Boolean = true): Future[EulaRecord] =
    getObject[EulaRecord](
      Post(toUrl(ROOT_EULA), EulaAcceptance(user, enableInstallMetrics)))

  def deleteEula(version: String): Future[MessageResponse] =
    getMessageResponse(Delete(toUrl(ROOT_EULA + s"/$version")))

  private def toJobQuery(showAll: Boolean, projectId: Option[Int]) = {
    val query1 = if (showAll) Seq("showAll") else Seq.empty[String]
    val query2 =
      if (projectId.isDefined) Seq(s"projectId=${projectId.get}")
      else Seq.empty[String]
    val queries = query1 ++ query2
    if (queries.isEmpty) ""
    else "?" + (query1 ++ query2).reduce(_ + "&" + _)
  }

  def getJobsByType(jobType: String,
                    showAll: Boolean = false,
                    projectId: Option[Int] = None): Future[Seq[EngineJob]] =
    getObject[Seq[EngineJob]](
      Get(toUrl(jobRoot(jobType) + toJobQuery(showAll, projectId))))

  def getJobsByProject(projectId: Int): Future[Seq[EngineJob]] =
    getObject[Seq[EngineJob]](Get(toUrl(ROOT_JOBS + s"?projectId=$projectId")))

  def getPacBioComponentManifests: Future[Seq[PacBioComponentManifest]] =
    getObject[Seq[PacBioComponentManifest]](Get(toUrl(ROOT_SERVICE_MANIFESTS)))

  // Added in smrtflow 0.1.11 and SA > 3.2.0
  def getPacBioComponentManifestById(
      manifestId: String): Future[PacBioComponentManifest] =
    getObject[PacBioComponentManifest](
      Get(toUrl(ROOT_SERVICE_MANIFESTS + "/" + manifestId)))

  def getAnalysisJobs: Future[Seq[EngineJob]] =
    getJobsByType(JobTypeIds.PBSMRTPIPE.id)

  def getImportJobs: Future[Seq[EngineJob]] =
    getJobsByType(JobTypeIds.IMPORT_DATASET.id)

  def getMergeJobs: Future[Seq[EngineJob]] =
    getJobsByType(JobTypeIds.MERGE_DATASETS.id)

  def getFastaConvertJobs: Future[Seq[EngineJob]] =
    getJobsByType(JobTypeIds.CONVERT_FASTA_REFERENCE.id)

  def getBarcodeConvertJobs: Future[Seq[EngineJob]] =
    getJobsByType(JobTypeIds.CONVERT_FASTA_BARCODES.id)

  def getDatasetDeleteJobs: Future[Seq[EngineJob]] =
    getJobsByType(JobTypeIds.DELETE_DATASETS.id)

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
    getObject[EngineJob](Get(toUrl(ROOT_JOBS + "/" + jobId.toIdString)))

  def deleteJob(jobId: UUID,
                removeFiles: Boolean = true,
                dryRun: Boolean = false,
                force: Boolean = false): Future[EngineJob] =
    getObject[EngineJob](
      Post(
        toUrl(jobRoot(JobTypeIds.DELETE_JOB.id)),
        DeleteSmrtLinkJobOptions(jobId,
                                 Some(s"Delete job $jobId name"),
                                 None,
                                 removeFiles,
                                 dryRun = Some(dryRun),
                                 force = Some(force))
      ))

  def getJobChildren(jobId: IdAble): Future[Seq[EngineJob]] =
    getObject[Seq[EngineJob]](
      Get(toUrl(ROOT_JOBS + "/" + jobId.toIdString + "/children")))

  def getJobByTypeAndId(jobType: String, jobId: IdAble): Future[EngineJob] =
    getObject[EngineJob](Get(toJobUrl(jobType, jobId)))

  def getAnalysisJob(jobId: IdAble): Future[EngineJob] = {
    getJobByTypeAndId(JobTypeIds.PBSMRTPIPE.id, jobId)
  }

  def getJobEntryPoints(jobId: IdAble): Future[Seq[EngineJobEntryPoint]] =
    getObject[Seq[EngineJobEntryPoint]](
      Get(toJobResourceUrl(jobId, ENTRY_POINTS_PREFIX)))

  def terminatePbsmrtpipeJob(jobId: Int): Future[MessageResponse] =
    getMessageResponse(Post(toJobResourceUrl(jobId, TERMINATE_JOB)))

  def getReportViewRules: Future[Seq[ReportViewRule]] =
    getObject[Seq[ReportViewRule]](Get(toUrl(ROOT_REPORT_RULES)))

  def getReportViewRule(reportId: String): Future[ReportViewRule] =
    getObject[ReportViewRule](Get(toUrl(ROOT_REPORT_RULES + s"/$reportId")))

  def importDataSet(
      path: Path,
      dsMetaType: DataSetMetaTypes.DataSetMetaType): Future[EngineJob] =
    getObject[EngineJob](
      Post(
        toUrl(jobRoot(JobTypeIds.IMPORT_DATASET.id)),
        ImportDataSetJobOptions(path.toAbsolutePath, dsMetaType, None, None)))

  def importFasta(path: Path,
                  name: String,
                  organism: String,
                  ploidy: String): Future[EngineJob] =
    getObject[EngineJob](
      Post(
        toUrl(jobRoot(JobTypeIds.CONVERT_FASTA_REFERENCE.id)),
        ImportFastaJobOptions(toP(path), ploidy, organism, Some(name), None)))

  def importFastaBarcodes(path: Path, name: String): Future[EngineJob] =
    getObject[EngineJob](
      Post(
        toUrl(jobRoot(JobTypeIds.CONVERT_FASTA_BARCODES.id)),
        ImportBarcodeFastaJobOptions(path.toAbsolutePath, Some(name), None)))

  def importFastaGmap(path: Path,
                      name: String,
                      organism: String,
                      ploidy: String): Future[EngineJob] =
    getObject[EngineJob](
      Post(
        toUrl(jobRoot(JobTypeIds.CONVERT_FASTA_GMAPREFERENCE.id)),
        ImportFastaJobOptions(toP(path), ploidy, organism, Some(name), None)))

  def mergeDataSets(datasetType: DataSetMetaTypes.DataSetMetaType,
                    ids: Seq[IdAble],
                    name: String) =
    getObject[EngineJob](
      Post(toUrl(jobRoot(JobTypeIds.MERGE_DATASETS.id)),
           MergeDataSetJobOptions(datasetType, ids, Some(name), None)))

  def convertRsMovie(path: Path, name: String) =
    getObject[EngineJob](
      Post(toUrl(jobRoot(JobTypeIds.CONVERT_RS_MOVIE.id)),
           RsConvertMovieToDataSetJobOptions(toP(path), Some(name), None)))

  def exportDataSets(datasetType: DataSetMetaTypes.DataSetMetaType,
                     ids: Seq[IdAble],
                     outputPath: Path,
                     deleteAfterExport: Boolean = false): Future[EngineJob] =
    getObject[EngineJob](
      Post(toUrl(jobRoot(JobTypeIds.EXPORT_DATASETS.id)),
           ExportDataSetsJobOptions(datasetType,
                                    ids,
                                    outputPath.toAbsolutePath,
                                    Some(deleteAfterExport))))

  def deleteDataSets(datasetType: DataSetMetaTypes.DataSetMetaType,
                     ids: Seq[Int],
                     removeFiles: Boolean = true): Future[EngineJob] =
    getObject[EngineJob](
      Post(
        toUrl(jobRoot(JobTypeIds.DELETE_DATASETS.id)),
        DataSetDeleteServiceOptions(datasetType.toString, ids, removeFiles)))

  def getPipelineTemplate(pipelineId: String): Future[PipelineTemplate] =
    getObject[PipelineTemplate](Get(toUrl(ROOT_PT + "/" + pipelineId)))

  def getPipelineTemplates: Future[Seq[PipelineTemplate]] =
    getObject[Seq[PipelineTemplate]](Get(toUrl(ROOT_PT)))

  def getPipelineTemplateViewRules: Future[Seq[PipelineTemplateViewRule]] =
    getObject[Seq[PipelineTemplateViewRule]](Get(toUrl(ROOT_PTRULES)))

  def getPipelineTemplateViewRule(
      pipelineId: String): Future[PipelineTemplateViewRule] =
    getObject[PipelineTemplateViewRule](
      Get(toUrl(ROOT_PTRULES + s"/$pipelineId")))

  def getPipelineDataStoreViewRules(
      pipelineId: String): Future[PipelineDataStoreViewRules] =
    getObject[PipelineDataStoreViewRules](
      Get(toUrl(ROOT_DS_RULES + s"/$pipelineId")))

  def runAnalysisPipeline(
      pipelineOptions: PbsmrtpipeJobOptions): Future[EngineJob] =
    getObject[EngineJob](
      Post(toUrl(jobRoot(JobTypeIds.PBSMRTPIPE.id)), pipelineOptions))

  def createMultiAnalysisJob(
      multiAnalysisJobOptions: MultiAnalysisJobOptions): Future[EngineJob] =
    getObject[EngineJob](
      Post(toUrl(s"$ROOT_MULTI_JOBS/${JobTypeIds.MJOB_MULTI_ANALYSIS.id}"),
           multiAnalysisJobOptions))

  def updateMultiAnalysisJobToSubmit(ix: IdAble): Future[MessageResponse] =
    getMessageResponse(Post(toUrl(
      s"$ROOT_MULTI_JOBS/${JobTypeIds.MJOB_MULTI_ANALYSIS.id}/${ix.toIdString}/submit")))

  def getMultiAnalysisChildrenJobs(ix: IdAble): Future[Seq[EngineJob]] =
    getObject[Seq[EngineJob]](Get(toUrl(
      s"$ROOT_MULTI_JOBS/${JobTypeIds.MJOB_MULTI_ANALYSIS.id}/${ix.toIdString}/jobs")))

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
      Post(toUrl(jobRoot(JobTypeIds.TS_SYSTEM_STATUS.id)),
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
      Post(toUrl(jobRoot(JobTypeIds.TS_JOB.id)),
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
      Post(toUrl(jobRoot(JobTypeIds.DB_BACKUP.id)),
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
      Post(toUrl(jobRoot(JobTypeIds.EXPORT_JOBS.id)),
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
      Post(toUrl(jobRoot(JobTypeIds.IMPORT_JOB.id)),
           ImportSmrtLinkJobOptions(zipPath, mockJobId = Some(mockJobId))))

  def copyDataSet(dsId: IdAble,
                  filters: Seq[Seq[DataSetFilterProperty]],
                  dsName: Option[String] = None): Future[EngineJob] =
    getObject[EngineJob](
      Post(toUrl(jobRoot(JobTypeIds.DS_COPY.id)),
           CopyDataSetJobOptions(dsId, filters, dsName, None, None, None)))

  def getAlarms(): Future[Seq[AlarmStatus]] =
    getObject[Seq[AlarmStatus]](Get(toUrl(ROOT_ALARMS)))

  def addRegistryService(host: String,
                         port: Int,
                         resourceId: String): Future[RegistryResource] = {
    getObject[RegistryResource](
      Post(toUrl(s"/$ROOT_SL_PREFIX/registry-service/resources"),
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
      Get(toUrl(
        s"/$ROOT_SL_PREFIX/registry-service/resources/${resource.toString}/proxy${px.toString}"))
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
