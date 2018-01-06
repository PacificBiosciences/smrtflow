package com.pacbio.secondary.smrtlink.client

import java.net.URL
import java.nio.file.Path
import java.util.UUID

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scalaj.http.Base64
import akka.actor.ActorSystem
import akka.http.scaladsl.server._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{
  HttpRequest,
  HttpResponse,
  Uri,
  ContentTypes,
  StatusCodes
}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.unmarshalling.{FromResponseUnmarshaller, Unmarshal}
import com.typesafe.scalalogging.LazyLogging
import com.pacificbiosciences.pacbiodatasets._
import com.pacbio.secondary.smrtlink.auth.JwtUtils._
import com.pacbio.common.models._
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetJsonProtocols
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.analysis.reports.ReportModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobtypes._
import com.pacbio.secondary.smrtlink.analysis.reports._
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.jobtypes._
import com.pacbio.secondary.smrtlink.models._

class SmrtLinkServiceAccessLayer(baseUrl: URL, authUser: Option[String])(
    implicit actorSystem: ActorSystem)
    extends ServiceAccessLayer(baseUrl)(actorSystem)
    with ServiceEndpointConstants
    with LazyLogging {

  import CommonModelImplicits._
  import CommonModels._
  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._
  import SprayJsonSupport._

  /**
    * Migration to internally use Spray/akka data models.
    *
    * @param segment relative segment (this must include the leading slash!)
    * @return
    */
  def toUri(segment: String): Uri =
    Uri(
      s"${baseUrl.getProtocol}://${baseUrl.getHost}:${baseUrl.getPort}$segment")

  protected def getResponse(request: HttpRequest): Future[HttpResponse] =
    http
      .singleRequest(request)
      .flatMap { response =>
        response.status match {
          case StatusCodes.OK => Future.successful(response)
          case _ => Future.failed(new Exception(response.toString))
        }
      }

  protected def getMessageResponse(
      request: HttpRequest): Future[MessageResponse] =
    getResponse(request)
      .flatMap(Unmarshal(_).to[MessageResponse])

  def getUrlResponse(uri: Uri) = http.singleRequest(Get(uri))

  val headers = authUser
    .map(u =>
      "{\"" + USERNAME_CLAIM + "\":\"" + u + "\",\"" + ROLES_CLAIM + "\":[]}")
    .map(c =>
      Base64.encodeString("{}") + "." + Base64.encodeString(c) + ".abc")
    .map(j => RawHeader(JWT_HEADER, j))
    .toSeq

  def this(host: String, port: Int, authUser: Option[String] = None)(
      implicit actorSystem: ActorSystem) {
    this(UrlUtils.convertToUrl(host, port), authUser)(actorSystem)
  }

  private def toP(path: Path) = path.toAbsolutePath.toString

  private def jobRoot(jobType: String) = s"${ROOT_JOBS}/${jobType}"

  protected def toJobUrl(jobType: String, jobId: IdAble): String =
    toUrl(jobRoot(jobType) + s"/${jobId.toIdString}")

  protected def toJobResourceUrl(jobType: String,
                                 jobId: IdAble,
                                 resourceType: String): String =
    toUrl(jobRoot(jobType) + s"/${jobId.toIdString}/$resourceType")

  protected def toJobResourceIdUrl(jobType: String,
                                   jobId: IdAble,
                                   resourceType: String,
                                   resourceId: UUID) =
    toUrl(jobRoot(jobType) + s"/${jobId.toIdString}/$resourceType/$resourceId")

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

  override def serviceStatusEndpoints: Vector[String] =
    Vector(
      ROOT_JOBS + "/" + JobTypeIds.IMPORT_DATASET.id,
      ROOT_JOBS + "/" + JobTypeIds.CONVERT_FASTA_REFERENCE.id,
      ROOT_JOBS + "/" + JobTypeIds.CONVERT_FASTA_BARCODES.id,
      ROOT_JOBS + "/" + JobTypeIds.PBSMRTPIPE,
      ROOT_DS + "/" + DataSetMetaTypes.Subread.shortName,
      ROOT_DS + "/" + DataSetMetaTypes.HdfSubread.shortName,
      ROOT_DS + "/" + DataSetMetaTypes.Reference.shortName,
      ROOT_DS + "/" + DataSetMetaTypes.Barcode.shortName
    )

  def getDataSet(datasetId: IdAble): Future[DataSetMetaDataSet] =
    getUrlResponse(toUri(ROOT_DS + "/" + datasetId.toIdString))
      .flatMap(Unmarshal(_).to[DataSetMetaDataSet])

  def deleteDataSet(datasetId: IdAble): Future[MessageResponse] =
    getMessageResponse(
      Put(toUrl(ROOT_DS + "/" + datasetId.toIdString),
          DataSetUpdateRequest(Some(false))))

  def getSubreadSets: Future[Seq[SubreadServiceDataSet]] =
    http
      .singleRequest(Get(toDataSetsUrl(DataSetMetaTypes.Subread.shortName)))
      .flatMap(Unmarshal(_).to[Seq[SubreadServiceDataSet]])

  def getSubreadSet(dsId: IdAble): Future[SubreadServiceDataSet] =
    http
      .singleRequest(
        Get(toDataSetUrl(DataSetMetaTypes.Subread.shortName, dsId)))
      .flatMap(Unmarshal(_).to[SubreadServiceDataSet])

  def getSubreadSetDetails(dsId: IdAble): Future[SubreadSet] =
    http
      .singleRequest(
        Get(
          toDataSetResourcesUrl(DataSetMetaTypes.Subread.shortName,
                                dsId,
                                "details")))
      .flatMap(Unmarshal(_).to[SubreadSet])

  def getSubreadSetReports(dsId: IdAble): Future[Seq[DataStoreReportFile]] =
    http
      .singleRequest(
        Get(
          toDataSetResourcesUrl(DataSetMetaTypes.Subread.shortName,
                                dsId,
                                JOB_REPORT_PREFIX)))
      .flatMap(Unmarshal(_).to[Seq[DataStoreReportFile]])

  def updateSubreadSetDetails(
      dsId: IdAble,
      isActive: Option[Boolean] = None,
      bioSampleName: Option[String] = None,
      wellSampleName: Option[String] = None): Future[MessageResponse] =
    getMessageResponse(
      Put(toDataSetUrl(DataSetMetaTypes.Subread.shortName, dsId),
          DataSetUpdateRequest(isActive, bioSampleName, wellSampleName)))

  def getHdfSubreadSets: Future[Seq[HdfSubreadServiceDataSet]] =
    http
      .singleRequest(Get(toDataSetsUrl(DataSetMetaTypes.HdfSubread.shortName)))
      .flatMap(Unmarshal(_).to[Seq[HdfSubreadServiceDataSet]])

  def getHdfSubreadSet(dsId: IdAble): Future[HdfSubreadServiceDataSet] =
    http
      .singleRequest(
        Get(toDataSetUrl(DataSetMetaTypes.HdfSubread.shortName, dsId)))
      .flatMap(Unmarshal(_).to[HdfSubreadServiceDataSet])

  def getHdfSubreadSetDetails(dsId: IdAble): Future[HdfSubreadSet] =
    http
      .singleRequest(
        Get(
          toDataSetResourcesUrl(DataSetMetaTypes.HdfSubread.shortName,
                                dsId,
                                "details")))
      .flatMap(Unmarshal(_).to[HdfSubreadSet])

  def getBarcodeSets: Future[Seq[BarcodeServiceDataSet]] =
    http
      .singleRequest(Get(toDataSetsUrl(DataSetMetaTypes.Barcode.shortName)))
      .flatMap(Unmarshal(_).to[Seq[BarcodeServiceDataSet]])

  def getBarcodeSet(dsId: IdAble): Future[BarcodeServiceDataSet] =
    http
      .singleRequest(
        Get(toDataSetUrl(DataSetMetaTypes.Barcode.shortName, dsId)))
      .flatMap(Unmarshal(_).to[BarcodeServiceDataSet])

  def getBarcodeSetDetails(dsId: IdAble): Future[BarcodeSet] =
    http
      .singleRequest(
        Get(
          toDataSetResourcesUrl(DataSetMetaTypes.Barcode.shortName,
                                dsId,
                                "details")))
      .flatMap(Unmarshal(_).to[BarcodeSet])

  def getReferenceSets: Future[Seq[ReferenceServiceDataSet]] =
    http
      .singleRequest(Get(toDataSetsUrl(DataSetMetaTypes.Reference.shortName)))
      .flatMap(Unmarshal(_).to[Seq[ReferenceServiceDataSet]])

  def getReferenceSet(dsId: IdAble): Future[ReferenceServiceDataSet] =
    http
      .singleRequest(
        Get(toDataSetUrl(DataSetMetaTypes.Reference.shortName, dsId)))
      .flatMap(Unmarshal(_).to[ReferenceServiceDataSet])

  def getReferenceSetDetails(dsId: IdAble): Future[ReferenceSet] =
    http
      .singleRequest(
        Get(
          toDataSetResourcesUrl(DataSetMetaTypes.Reference.shortName,
                                dsId,
                                "details")))
      .flatMap(Unmarshal(_).to[ReferenceSet])

  def getGmapReferenceSets: Future[Seq[GmapReferenceServiceDataSet]] =
    http
      .singleRequest(
        Get(toDataSetsUrl(DataSetMetaTypes.GmapReference.shortName)))
      .flatMap(Unmarshal(_).to[Seq[GmapReferenceServiceDataSet]])

  def getGmapReferenceSet(dsId: IdAble): Future[GmapReferenceServiceDataSet] =
    http
      .singleRequest(
        Get(toDataSetUrl(DataSetMetaTypes.GmapReference.shortName, dsId)))
      .flatMap(Unmarshal(_).to[GmapReferenceServiceDataSet])

  def getGmapReferenceSetDetails(dsId: IdAble): Future[GmapReferenceSet] =
    http
      .singleRequest(
        Get(
          toDataSetResourcesUrl(DataSetMetaTypes.GmapReference.shortName,
                                dsId,
                                "details")))
      .flatMap(Unmarshal(_).to[GmapReferenceSet])

  def getAlignmentSets: Future[Seq[AlignmentServiceDataSet]] =
    http
      .singleRequest(Get(toDataSetsUrl(DataSetMetaTypes.Alignment.shortName)))
      .flatMap(Unmarshal(_).to[Seq[AlignmentServiceDataSet]])

  def getAlignmentSet(dsId: IdAble): Future[AlignmentServiceDataSet] =
    http
      .singleRequest(
        Get(toDataSetUrl(DataSetMetaTypes.Alignment.shortName, dsId)))
      .flatMap(Unmarshal(_).to[AlignmentServiceDataSet])

  def getAlignmentSetDetails(dsId: IdAble): Future[AlignmentSet] =
    http
      .singleRequest(
        Get(
          toDataSetResourcesUrl(DataSetMetaTypes.Alignment.shortName,
                                dsId,
                                "details")))
      .flatMap(Unmarshal(_).to[AlignmentSet])

  def getConsensusReadSets: Future[Seq[ConsensusReadServiceDataSet]] =
    http
      .singleRequest(Get(toDataSetsUrl(DataSetMetaTypes.CCS.shortName)))
      .flatMap(Unmarshal(_).to[Seq[ConsensusReadServiceDataSet]])

  def getConsensusReadSet(dsId: IdAble): Future[ConsensusReadServiceDataSet] =
    http
      .singleRequest(Get(toDataSetUrl(DataSetMetaTypes.CCS.shortName, dsId)))
      .flatMap(Unmarshal(_).to[ConsensusReadServiceDataSet])

  def getConsensusReadSetDetails(dsId: IdAble): Future[ConsensusReadSet] =
    http
      .singleRequest(
        Get(
          toDataSetResourcesUrl(DataSetMetaTypes.CCS.shortName,
                                dsId,
                                "details")))
      .flatMap(Unmarshal(_).to[ConsensusReadSet])

  def getConsensusAlignmentSets
    : Future[Seq[ConsensusAlignmentServiceDataSet]] =
    http
      .singleRequest(
        Get(toDataSetsUrl(DataSetMetaTypes.AlignmentCCS.shortName)))
      .flatMap(Unmarshal(_).to[Seq[ConsensusAlignmentServiceDataSet]])

  def getConsensusAlignmentSet(
      dsId: IdAble): Future[ConsensusAlignmentServiceDataSet] =
    http
      .singleRequest(
        Get(toDataSetUrl(DataSetMetaTypes.AlignmentCCS.shortName, dsId)))
      .flatMap(Unmarshal(_).to[ConsensusAlignmentServiceDataSet])

  def getConsensusAlignmentSetDetails(
      dsId: IdAble): Future[ConsensusAlignmentSet] =
    http
      .singleRequest(
        Get(
          toDataSetResourcesUrl(DataSetMetaTypes.AlignmentCCS.shortName,
                                dsId,
                                "details")))
      .flatMap(Unmarshal(_).to[ConsensusAlignmentSet])

  def getContigSets: Future[Seq[ContigServiceDataSet]] =
    http
      .singleRequest(Get(toDataSetsUrl(DataSetMetaTypes.Contig.shortName)))
      .flatMap(Unmarshal(_).to[Seq[ContigServiceDataSet]])

  def getContigSet(dsId: IdAble): Future[ContigServiceDataSet] =
    http
      .singleRequest(
        Get(toDataSetUrl(DataSetMetaTypes.Contig.shortName, dsId)))
      .flatMap(Unmarshal(_).to[ContigServiceDataSet])

  def getContigSetDetails(dsId: IdAble): Future[ContigSet] =
    http
      .singleRequest(
        Get(
          toDataSetResourcesUrl(DataSetMetaTypes.Contig.shortName,
                                dsId,
                                "details")))
      .flatMap(Unmarshal(_).to[ContigSet])

  protected def getJobDataStore(
      jobType: String,
      jobId: IdAble): Future[Seq[DataStoreServiceFile]] =
    http
      .singleRequest(
        Get(toJobResourceUrl(jobType, jobId, JOB_DATASTORE_PREFIX)))
      .flatMap(Unmarshal(_).to[Seq[DataStoreServiceFile]])

  def getImportDatasetJobDataStore(jobId: IdAble) =
    getJobDataStore(JobTypeIds.IMPORT_DATASET.id, jobId)

  def getMergeDatasetJobDataStore(jobId: IdAble) =
    getJobDataStore(JobTypeIds.MERGE_DATASETS.id, jobId)

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

  def getReport(reportId: UUID): Future[Report] =
    http
      .singleRequest(Get(toUrl(ROOT_DATASTORE + s"/$reportId/download")))
      .map(_.entity.withContentType(ContentTypes.`application/json`))
      .flatMap(Unmarshal(_).to[Report])

  protected def getJobReports(
      jobId: IdAble,
      jobType: String): Future[Seq[DataStoreReportFile]] =
    http
      .singleRequest(Get(toJobResourceUrl(jobType, jobId, JOB_REPORT_PREFIX)))
      .flatMap(Unmarshal(_).to[Seq[DataStoreReportFile]])

  def getImportJobReports(jobId: IdAble) =
    getJobReports(jobId, JobTypeIds.IMPORT_DATASET.id)

  def getDataStoreFileResource(fileId: UUID,
                               relpath: String): Future[HttpResponse] =
    http.singleRequest(
      Get(toUrl(ROOT_DATASTORE + s"/${fileId}/resources?relpath=${relpath}")))

  protected def getJobTasks(jobType: String,
                            jobId: IdAble): Future[Seq[JobTask]] =
    http
      .singleRequest(Get(toJobResourceUrl(jobType, jobId, JOB_TASK_PREFIX)))
      .flatMap(Unmarshal(_).to[Seq[JobTask]])

  protected def getJobTask(jobType: String,
                           jobId: IdAble,
                           taskId: UUID): Future[JobTask] =
    http
      .singleRequest(
        Get(
          toJobResourceUrl(jobType,
                           jobId,
                           JOB_TASK_PREFIX + "/" + taskId.toString)))
      .flatMap(Unmarshal(_).to[JobTask])

  protected def getJobEvents(jobType: String,
                             jobId: Int): Future[Seq[JobEvent]] =
    http
      .singleRequest(Get(toJobResourceUrl(jobType, jobId, JOB_EVENT_PREFIX)))
      .flatMap(Unmarshal(_).to[Seq[JobEvent]])

  protected def getJobOptions(jobType: String,
                              jobId: Int): Future[PipelineTemplatePreset] =
    http
      .singleRequest(Get(toJobResourceUrl(jobType, jobId, JOB_OPTIONS)))
      .flatMap(Unmarshal(_).to[PipelineTemplatePreset])

  protected def createJobTask(jobType: String,
                              jobId: IdAble,
                              task: CreateJobTaskRecord): Future[JobTask] =
    http
      .singleRequest(
        Post(toJobResourceUrl(jobType, jobId, JOB_TASK_PREFIX), task))
      .flatMap(Unmarshal(_).to[JobTask])

  protected def updateJobTask(jobType: String,
                              jobId: IdAble,
                              update: UpdateJobTaskRecord): Future[JobTask] =
    http
      .singleRequest(
        Put(toJobResourceUrl(jobType,
                             jobId,
                             JOB_TASK_PREFIX + "/" + update.uuid.toString),
            update))
      .flatMap(Unmarshal(_).to[JobTask])

  // Runs

  protected def getRunUrl(runId: UUID): String = toUrl(s"${ROOT_RUNS}/$runId")

  protected def getCollectionsUrl(runId: UUID): String =
    toUrl(s"${ROOT_RUNS}/$runId/collections")

  protected def getCollectionUrl(runId: UUID, collectionId: UUID): String =
    toUrl(s"${ROOT_RUNS}/$runId/collections/$collectionId")

  def getRuns: Future[Seq[RunSummary]] =
    http
      .singleRequest(Get(toUrl(ROOT_RUNS)))
      .flatMap(Unmarshal(_).to[Seq[RunSummary]])

  def getRun(runId: UUID): Future[Run] =
    http
      .singleRequest(Get(getRunUrl(runId)))
      .flatMap(Unmarshal(_).to[Run])

  def getCollections(runId: UUID): Future[Seq[CollectionMetadata]] =
    http
      .singleRequest(Get(getCollectionsUrl(runId)))
      .flatMap(Unmarshal(_).to[Seq[CollectionMetadata]])

  def getCollection(runId: UUID,
                    collectionId: UUID): Future[CollectionMetadata] =
    http
      .singleRequest(Get(getCollectionUrl(runId, collectionId)))
      .flatMap(Unmarshal(_).to[CollectionMetadata])

  def createRun(dataModel: String): Future[RunSummary] =
    http
      .singleRequest(Post(toUrl(ROOT_RUNS), RunCreate(dataModel)))
      .flatMap(Unmarshal(_).to[RunSummary])

  def updateRun(runId: UUID,
                dataModel: Option[String] = None,
                reserved: Option[Boolean] = None): Future[RunSummary] =
    http
      .singleRequest(Post(getRunUrl(runId), RunUpdate(dataModel, reserved)))
      .flatMap(Unmarshal(_).to[RunSummary])

  def deleteRun(runId: UUID): Future[MessageResponse] =
    getMessageResponse(Delete(getRunUrl(runId)))

  def getProjects: Future[Seq[Project]] =
    http
      .singleRequest(Get(toUrl(ROOT_PROJECTS)).withHeaders(headers: _*))
      .flatMap(Unmarshal(_).to[Seq[Project]])

  def getProject(projectId: Int): Future[FullProject] =
    http
      .singleRequest(
        Get(toUrl(ROOT_PROJECTS + s"/$projectId")).withHeaders(headers: _*))
      .flatMap(Unmarshal(_).to[FullProject])

  def createProject(name: String, description: String): Future[FullProject] =
    http
      .singleRequest(
        Post(toUrl(ROOT_PROJECTS),
             ProjectRequest(name, description, None, None, None, None))
          .withHeaders(headers: _*))
      .flatMap(Unmarshal(_).to[FullProject])

  def updateProject(projectId: Int,
                    request: ProjectRequest): Future[FullProject] =
    http
      .singleRequest(
        Put(toUrl(ROOT_PROJECTS + s"/$projectId"), request)
          .withHeaders(headers: _*))
      .flatMap(Unmarshal(_).to[FullProject])

  // User agreements (not really a EULA)
  def getEula(version: String): Future[EulaRecord] =
    http
      .singleRequest(Get(toUrl(ROOT_EULA + s"/$version")))
      .flatMap(Unmarshal(_).to[EulaRecord])

  def getEulas: Future[Seq[EulaRecord]] =
    http
      .singleRequest(Get(ROOT_EULA))
      .flatMap(Unmarshal(_).to[Seq[EulaRecord]])

  def acceptEula(user: String,
                 enableInstallMetrics: Boolean = true): Future[EulaRecord] =
    http
      .singleRequest(
        Post(toUrl(ROOT_EULA), EulaAcceptance(user, enableInstallMetrics)))
      .flatMap(Unmarshal(_).to[EulaRecord])

  def deleteEula(version: String): Future[MessageResponse] =
    getMessageResponse(Delete(toUrl(ROOT_EULA + s"/$version")))

  def getJobsByType(jobType: String,
                    showAll: Boolean = false,
                    projectId: Option[Int] = None): Future[Seq[EngineJob]] =
    http
      .singleRequest {
        val query1 = if (showAll) Seq("showAll") else Seq.empty[String]
        val query2 =
          if (projectId.isDefined) Seq(s"projectId=${projectId.get}")
          else Seq.empty[String]
        val queries = query1 ++ query2
        val queryString =
          if (queries.isEmpty) ""
          else "?" + (query1 ++ query2).reduce(_ + "&" + _)
        Get(toUrl(ROOT_JOBS + "/" + jobType + queryString))
      }
      .flatMap(Unmarshal(_).to[Seq[EngineJob]])

  def getJobsByProject(projectId: Int): Future[Seq[EngineJob]] =
    http
      .singleRequest(Get(toUrl(ROOT_JOBS + s"?projectId=$projectId")))
      .flatMap(Unmarshal(_).to[Seq[EngineJob]])

  def getPacBioComponentManifests: Future[Seq[PacBioComponentManifest]] =
    http
      .singleRequest(Get(toUrl(ROOT_SERVICE_MANIFESTS)))
      .flatMap(Unmarshal(_).to[Seq[PacBioComponentManifest]])

  // Added in smrtflow 0.1.11 and SA > 3.2.0
  def getPacBioComponentManifestById(
      manifestId: String): Future[PacBioComponentManifest] =
    http
      .singleRequest(Get(toUrl(ROOT_SERVICE_MANIFESTS + "/" + manifestId)))
      .flatMap(Unmarshal(_).to[PacBioComponentManifest])

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
    http
      .singleRequest(Get(toUrl(ROOT_JOBS + "/" + jobId.toIdString)))
      .flatMap(Unmarshal(_).to[EngineJob])

  def deleteJob(jobId: UUID,
                removeFiles: Boolean = true,
                dryRun: Boolean = false,
                force: Boolean = false): Future[EngineJob] =
    http
      .singleRequest(
        Post(toUrl(ROOT_JOBS + "/delete-job"),
             DeleteSmrtLinkJobOptions(jobId,
                                      Some(s"Delete job $jobId name"),
                                      None,
                                      removeFiles,
                                      dryRun = Some(dryRun),
                                      force = Some(force))))
      .flatMap(Unmarshal(_).to[EngineJob])

  def getJobChildren(jobId: IdAble): Future[Seq[EngineJob]] =
    http
      .singleRequest(
        Get(toUrl(ROOT_JOBS + "/" + jobId.toIdString + "/children")))
      .flatMap(Unmarshal(_).to[Seq[EngineJob]])

  def getJobByTypeAndId(jobType: String, jobId: IdAble): Future[EngineJob] =
    http
      .singleRequest(Get(toJobUrl(jobType, jobId)))
      .flatMap(Unmarshal(_).to[EngineJob])

  def getAnalysisJob(jobId: IdAble): Future[EngineJob] = {
    getJobByTypeAndId(JobTypeIds.PBSMRTPIPE.id, jobId)
  }

  def getAnalysisJobDataStore(jobId: IdAble) =
    getJobDataStore(JobTypeIds.PBSMRTPIPE.id, jobId)

  def getImportFastaJobDataStore(jobId: IdAble) =
    getJobDataStore(JobTypeIds.CONVERT_FASTA_REFERENCE.id, jobId)

  def getImportBarcodesJobDataStore(jobId: IdAble) =
    getJobDataStore(JobTypeIds.CONVERT_FASTA_BARCODES.id, jobId)

  def getConvertRsMovieJobDataStore(jobId: IdAble) =
    getJobDataStore(JobTypeIds.CONVERT_RS_MOVIE.id, jobId)

  def getExportDataSetsJobDataStore(jobId: IdAble) =
    getJobDataStore(JobTypeIds.EXPORT_DATASETS.id, jobId)

  def getExportJobsDataStore(jobId: IdAble) =
    getJobDataStore(JobTypeIds.EXPORT_JOBS.id, jobId)

  def getAnalysisJobReports(jobId: IdAble) =
    getJobReports(jobId, JobTypeIds.PBSMRTPIPE.id)

  // FIXME I think this still only works with Int
  def getAnalysisJobEntryPoints(jobId: Int): Future[Seq[EngineJobEntryPoint]] =
    http
      .singleRequest(
        Get(
          toJobResourceUrl(JobTypeIds.PBSMRTPIPE.id,
                           jobId,
                           ENTRY_POINTS_PREFIX)))
      .flatMap(Unmarshal(_).to[Seq[EngineJobEntryPoint]])

  protected def getJobReport(jobType: String,
                             jobId: IdAble,
                             reportId: UUID): Future[Report] =
    http
      .singleRequest(
        Get(toJobResourceIdUrl(jobType, jobId, JOB_REPORT_PREFIX, reportId)))
      .flatMap(Unmarshal(_).to[Report])

  // FIXME there is some degeneracy in the URLs - this actually works just fine
  // for import-dataset and merge-dataset jobs too
  def getAnalysisJobReport(jobId: IdAble, reportId: UUID): Future[Report] =
    getJobReport(JobTypeIds.PBSMRTPIPE.id, jobId, reportId)

  def getAnalysisJobTasks(jobId: Int): Future[Seq[JobTask]] =
    getJobTasks(JobTypeIds.PBSMRTPIPE.id, jobId)

  def getAnalysisJobTask(jobId: Int, taskId: UUID): Future[JobTask] =
    getJobTask(JobTypeIds.PBSMRTPIPE.id, jobId, taskId)

  def getAnalysisJobEvents(jobId: Int): Future[Seq[JobEvent]] =
    getJobEvents(JobTypeIds.PBSMRTPIPE.id, jobId)

  def getAnalysisJobOptions(jobId: Int): Future[PipelineTemplatePreset] =
    getJobOptions(JobTypeIds.PBSMRTPIPE.id, jobId)

  def terminatePbsmrtpipeJob(jobId: Int): Future[MessageResponse] =
    getMessageResponse(
      Post(toJobResourceUrl(JobTypeIds.PBSMRTPIPE.id, jobId, TERMINATE_JOB)))

  def getReportViewRules: Future[Seq[ReportViewRule]] =
    http
      .singleRequest(Get(toUrl(ROOT_REPORT_RULES)))
      .flatMap(Unmarshal(_).to[Seq[ReportViewRule]])

  def getReportViewRule(reportId: String): Future[ReportViewRule] =
    http
      .singleRequest(Get(toUrl(ROOT_REPORT_RULES + s"/$reportId")))
      .flatMap(Unmarshal(_).to[ReportViewRule])

  def importDataSet(
      path: Path,
      dsMetaType: DataSetMetaTypes.DataSetMetaType): Future[EngineJob] =
    http
      .singleRequest(
        Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.IMPORT_DATASET.id),
             ImportDataSetOptions(path.toAbsolutePath, dsMetaType)))
      .flatMap(Unmarshal(_).to[EngineJob])

  def importFasta(path: Path,
                  name: String,
                  organism: String,
                  ploidy: String): Future[EngineJob] =
    http
      .singleRequest(Post(
        toUrl(ROOT_JOBS + "/" + JobTypeIds.CONVERT_FASTA_REFERENCE.id),
        ImportFastaJobOptions(toP(path), ploidy, organism, Some(name), None)))
      .flatMap(Unmarshal(_).to[EngineJob])

  def importFastaBarcodes(path: Path, name: String): Future[EngineJob] =
    http
      .singleRequest(
        Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.CONVERT_FASTA_BARCODES.id),
             ConvertImportFastaBarcodesOptions(toP(path), name)))
      .flatMap(Unmarshal(_).to[EngineJob])

  def mergeDataSets(datasetType: DataSetMetaTypes.DataSetMetaType,
                    ids: Seq[IdAble],
                    name: String) =
    http
      .singleRequest(
        Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.MERGE_DATASETS.id),
             MergeDataSetJobOptions(datasetType, ids, Some(name), None)))
      .flatMap(Unmarshal(_).to[EngineJob])

  def convertRsMovie(path: Path, name: String) =
    http
      .singleRequest(
        Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.CONVERT_RS_MOVIE.id),
             MovieMetadataToHdfSubreadOptions(toP(path), name)))
      .flatMap(Unmarshal(_).to[EngineJob])

  def exportDataSets(datasetType: DataSetMetaTypes.DataSetMetaType,
                     ids: Seq[Int],
                     outputPath: Path,
                     deleteAfterExport: Boolean = false): Future[EngineJob] =
    http
      .singleRequest(
        Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.EXPORT_DATASETS.id),
             DataSetExportServiceOptions(datasetType.toString,
                                         ids,
                                         toP(outputPath),
                                         Some(deleteAfterExport))))
      .flatMap(Unmarshal(_).to[EngineJob])

  def deleteDataSets(datasetType: DataSetMetaTypes.DataSetMetaType,
                     ids: Seq[Int],
                     removeFiles: Boolean = true): Future[EngineJob] =
    http
      .singleRequest(
        Post(
          toUrl(ROOT_JOBS + "/" + JobTypeIds.DELETE_DATASETS.id),
          DataSetDeleteServiceOptions(datasetType.toString, ids, removeFiles)))
      .flatMap(Unmarshal(_).to[EngineJob])

  def getPipelineTemplate(pipelineId: String): Future[PipelineTemplate] =
    http
      .singleRequest(Get(toUrl(ROOT_PT + "/" + pipelineId)))
      .flatMap(Unmarshal(_).to[PipelineTemplate])

  def getPipelineTemplates: Future[Seq[PipelineTemplate]] =
    http
      .singleRequest(Get(toUrl(ROOT_PT)))
      .flatMap(Unmarshal(_).to[Seq[PipelineTemplate]])

  def getPipelineTemplateViewRules: Future[Seq[PipelineTemplateViewRule]] =
    http
      .singleRequest(Get(toUrl(ROOT_PTRULES)))
      .flatMap(Unmarshal(_).to[Seq[PipelineTemplateViewRule]])

  def getPipelineTemplateViewRule(
      pipelineId: String): Future[PipelineTemplateViewRule] =
    http
      .singleRequest(Get(toUrl(ROOT_PTRULES + s"/$pipelineId")))
      .flatMap(Unmarshal(_).to[PipelineTemplateViewRule])

  def getPipelineDataStoreViewRules(
      pipelineId: String): Future[PipelineDataStoreViewRules] =
    http
      .singleRequest(Get(toUrl(ROOT_DS_RULES + s"/$pipelineId")))
      .flatMap(Unmarshal(_).to[PipelineDataStoreViewRules])

  def runAnalysisPipeline(
      pipelineOptions: PbSmrtPipeServiceOptions): Future[EngineJob] =
    http
      .singleRequest(
        Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.PBSMRTPIPE.id),
             pipelineOptions))
      .flatMap(Unmarshal(_).to[EngineJob])

  def createMultiAnalysisJob(
      multiAnalysisJobOptions: MultiAnalysisJobOptions): Future[EngineJob] =
    http
      .singleRequest(
        Post(toUrl(s"$ROOT_MULTI_JOBS/${JobTypeIds.MJOB_MULTI_ANALYSIS.id}"),
             multiAnalysisJobOptions))
      .flatMap(Unmarshal(_).to[EngineJob])

  def updateMultiAnalysisJobToSubmit(ix: IdAble): Future[MessageResponse] =
    getMessageResponse(Post(toUrl(
      s"$ROOT_MULTI_JOBS/${JobTypeIds.MJOB_MULTI_ANALYSIS.id}/${ix.toIdString}/submit")))

  def getMultiAnalysisChildrenJobs(ix: IdAble): Future[Seq[EngineJob]] =
    http
      .singleRequest(Get(toUrl(
        s"$ROOT_MULTI_JOBS/${JobTypeIds.MJOB_MULTI_ANALYSIS.id}/${ix.toIdString}/jobs")))
      .flatMap(Unmarshal(_).to[Seq[EngineJob]])

  // PacBio Data Bundle
  def getPacBioDataBundles(): Future[Seq[PacBioDataBundle]] =
    http
      .singleRequest(Get(toPacBioDataBundleUrl()))
      .flatMap(Unmarshal(_).to[Seq[PacBioDataBundle]])

  def getPacBioDataBundleByTypeId(
      typeId: String): Future[Seq[PacBioDataBundle]] =
    http
      .singleRequest(Get(toPacBioDataBundleUrl(Some(typeId))))
      .flatMap(Unmarshal(_).to[Seq[PacBioDataBundle]])

  def getPacBioDataBundleByTypeAndVersionId(typeId: String,
                                            versionId: String) =
    http
      .singleRequest(Get(toPacBioDataBundleUrl(Some(s"$typeId/$versionId"))))
      .flatMap(Unmarshal(_).to[PacBioDataBundle])

  def runTsSystemStatus(user: String, comment: String): Future[EngineJob] = {
    http
      .singleRequest(
        Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.TS_SYSTEM_STATUS.id),
             TsSystemStatusServiceOptions(user, comment)))
      .flatMap(Unmarshal(_).to[EngineJob])
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
    http
      .singleRequest(
        Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.TS_JOB.id),
             TsJobBundleJobServiceOptions(jobId, user, comment)))
      .flatMap(Unmarshal(_).to[EngineJob])

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
    http
      .singleRequest(
        Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.DB_BACKUP.id),
             DbBackUpServiceJobOptions(user, comment)))
      .flatMap(Unmarshal(_).to[EngineJob])

  /**
    * Start export of SMRT Link job(s)
    */
  def exportJobs(jobIds: Seq[IdAble],
                 outputPath: Path,
                 includeEntryPoints: Boolean = false,
                 name: Option[String] = None,
                 description: Option[String] = None): Future[EngineJob] =
    http
      .singleRequest(
        Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.EXPORT_JOBS.id),
             ExportSmrtLinkJobOptions(jobIds,
                                      outputPath,
                                      includeEntryPoints,
                                      name,
                                      description)))
      .flatMap(Unmarshal(_).to[EngineJob])

  /**
    * Start import of SMRT Link job from zip file
    *
    * @param zipPath   pathname of ZIP file exported by SMRT Link
    * @param mockJobId if true, job UUID will be re-generated
    * @return new EngineJob object
    */
  def importJob(zipPath: Path, mockJobId: Boolean = false): Future[EngineJob] =
    http
      .singleRequest(
        Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.IMPORT_JOB.id),
             ImportSmrtLinkJobOptions(zipPath, mockJobId = Some(mockJobId))))
      .flatMap(Unmarshal(_).to[EngineJob])

  def getAlarms(): Future[Seq[AlarmStatus]] =
    http
      .singleRequest(Get(toUrl(ROOT_ALARMS)))
      .flatMap(Unmarshal(_).to[Seq[AlarmStatus]])

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
