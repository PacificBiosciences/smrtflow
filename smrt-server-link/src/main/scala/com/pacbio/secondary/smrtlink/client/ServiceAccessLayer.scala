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
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.unmarshalling.FromResponseUnmarshaller
import com.typesafe.scalalogging.LazyLogging
import com.pacificbiosciences.pacbiodatasets._
import com.pacbio.secondary.smrtlink.auth.Authenticator._
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

  // Pipelines and serialization
  protected def getDataSetMetaDataPipeline
    : HttpRequest => Future[DataSetMetaDataSet] =
    requestPipe ~> unmarshal[DataSetMetaDataSet]

  private def getDataSetPipeline[T <: ServiceDataSetMetadata](
      implicit fmt: FromResponseUnmarshaller[T]): HttpRequest => Future[T] =
    requestPipe ~> unmarshal[T]

  protected def getSubreadSetPipeline =
    getDataSetPipeline[SubreadServiceDataSet]

  protected def getHdfSubreadSetPipeline =
    getDataSetPipeline[HdfSubreadServiceDataSet]

  protected def getReferenceSetPipeline =
    getDataSetPipeline[ReferenceServiceDataSet]

  protected def getGmapReferenceSetPipeline =
    getDataSetPipeline[GmapReferenceServiceDataSet]

  protected def getBarcodeSetPipeline =
    getDataSetPipeline[BarcodeServiceDataSet]

  protected def getAlignmentSetPipeline =
    getDataSetPipeline[AlignmentServiceDataSet]

  protected def getConsensusReadSetPipeline =
    getDataSetPipeline[ConsensusReadServiceDataSet]

  protected def getConsensusAlignmentSetPipeline =
    getDataSetPipeline[ConsensusAlignmentServiceDataSet]

  protected def getContigSetPipeline = getDataSetPipeline[ContigServiceDataSet]

  // DATASET DETAILS (full object parsed from XML)
  private def getDataSetDetailsPipeline[T <: DataSetType](
      implicit fmt: FromResponseUnmarshaller[T]): HttpRequest => Future[T] =
    requestPipe ~> unmarshal[T]

  protected def getSubreadSetDetailsPipeline =
    getDataSetDetailsPipeline[SubreadSet]

  protected def getHdfSubreadSetDetailsPipeline =
    getDataSetDetailsPipeline[HdfSubreadSet]

  protected def getReferenceSetDetailsPipeline =
    getDataSetDetailsPipeline[ReferenceSet]

  protected def getGmapReferenceSetDetailsPipeline =
    getDataSetDetailsPipeline[GmapReferenceSet]

  protected def getBarcodeSetDetailsPipeline =
    getDataSetDetailsPipeline[BarcodeSet]

  protected def getAlignmentSetDetailsPipeline =
    getDataSetDetailsPipeline[AlignmentSet]

  protected def getConsensusReadSetDetailsPipeline =
    getDataSetDetailsPipeline[ConsensusReadSet]

  protected def getConsensusAlignmentSetDetailsPipeline =
    getDataSetDetailsPipeline[ConsensusAlignmentSet]

  protected def getContigSetDetailsPipeline =
    getDataSetDetailsPipeline[ContigSet]

  private def getDataSetsPipeline[T <: ServiceDataSetMetadata](
      implicit fmt: FromResponseUnmarshaller[Seq[T]])
    : HttpRequest => Future[Seq[T]] = requestPipe ~> unmarshal[Seq[T]]

  protected def getSubreadSetsPipeline =
    getDataSetsPipeline[SubreadServiceDataSet]

  protected def getHdfSubreadSetsPipeline =
    getDataSetsPipeline[HdfSubreadServiceDataSet]

  protected def getReferenceSetsPipeline =
    getDataSetsPipeline[ReferenceServiceDataSet]

  protected def getGmapReferenceSetsPipeline =
    getDataSetsPipeline[GmapReferenceServiceDataSet]

  protected def getBarcodeSetsPipeline =
    getDataSetsPipeline[BarcodeServiceDataSet]

  protected def getAlignmentSetsPipeline =
    getDataSetsPipeline[AlignmentServiceDataSet]

  protected def getConsensusReadSetsPipeline =
    getDataSetsPipeline[ConsensusReadServiceDataSet]

  protected def getConsensusAlignmentSetsPipeline =
    getDataSetsPipeline[ConsensusAlignmentServiceDataSet]

  protected def getContigSetsPipeline =
    getDataSetsPipeline[ContigServiceDataSet]

  protected def getDataStorePipeline
    : HttpRequest => Future[Seq[DataStoreServiceFile]] =
    requestPipe ~> unmarshal[Seq[DataStoreServiceFile]]

  protected def getEntryPointsPipeline
    : HttpRequest => Future[Seq[EngineJobEntryPoint]] =
    requestPipe ~> unmarshal[Seq[EngineJobEntryPoint]]

  protected def getReportsPipeline
    : HttpRequest => Future[Seq[DataStoreReportFile]] =
    requestPipe ~> unmarshal[Seq[DataStoreReportFile]]

  protected def getReportPipeline: HttpRequest => Future[Report] =
    requestPipe ~> unmarshal[Report]

  protected def getJobTasksPipeline: HttpRequest => Future[Seq[JobTask]] =
    requestPipe ~> unmarshal[Seq[JobTask]]

  protected def getJobTaskPipeline: HttpRequest => Future[JobTask] =
    requestPipe ~> unmarshal[JobTask]

  protected def getJobEventsPipeline: HttpRequest => Future[Seq[JobEvent]] =
    requestPipe ~> unmarshal[Seq[JobEvent]]

  protected def getJobOptionsPipeline
    : HttpRequest => Future[PipelineTemplatePreset] =
    requestPipe ~> unmarshal[PipelineTemplatePreset]

  protected def getRunsPipeline: HttpRequest => Future[Seq[RunSummary]] =
    requestPipe ~> unmarshal[Seq[RunSummary]]

  protected def getRunSummaryPipeline: HttpRequest => Future[RunSummary] =
    requestPipe ~> unmarshal[RunSummary]

  protected def getRunPipeline: HttpRequest => Future[Run] =
    requestPipe ~> unmarshal[Run]

  protected def getCollectionsPipeline
    : HttpRequest => Future[Seq[CollectionMetadata]] =
    requestPipe ~> unmarshal[Seq[CollectionMetadata]]

  protected def getCollectionPipeline
    : HttpRequest => Future[CollectionMetadata] =
    requestPipe ~> unmarshal[CollectionMetadata]

  protected def getProjectsPipeline: HttpRequest => Future[Seq[Project]] =
    requestPipe ~> unmarshal[Seq[Project]]

  protected def getProjectPipeline: HttpRequest => Future[FullProject] =
    requestPipe ~> unmarshal[FullProject]

  protected def getEulaPipeline: HttpRequest => Future[EulaRecord] =
    requestPipe ~> unmarshal[EulaRecord]

  protected def getEulasPipeline: HttpRequest => Future[Seq[EulaRecord]] =
    requestPipe ~> unmarshal[Seq[EulaRecord]]

  protected def getMessageResponsePipeline
    : HttpRequest => Future[MessageResponse] =
    requestPipe ~> unmarshal[MessageResponse]

  def getJobPipeline: HttpRequest => Future[EngineJob] =
    requestPipe ~> unmarshal[EngineJob]

  // XXX this fails when createdBy is an object instead of a string
  def getJobsPipeline: HttpRequest => Future[Seq[EngineJob]] =
    requestPipe ~> unmarshal[Seq[EngineJob]]

  def runJobPipeline: HttpRequest => Future[EngineJob] =
    requestPipe ~> unmarshal[EngineJob]

  def getReportViewRulesPipeline: HttpRequest => Future[Seq[ReportViewRule]] =
    requestPipe ~> unmarshal[Seq[ReportViewRule]]

  def getReportViewRulePipeline: HttpRequest => Future[ReportViewRule] =
    requestPipe ~> unmarshal[ReportViewRule]

  def getPipelineTemplatePipeline: HttpRequest => Future[PipelineTemplate] =
    requestPipe ~> unmarshal[PipelineTemplate]

  def getPipelineTemplatesPipeline
    : HttpRequest => Future[Seq[PipelineTemplate]] =
    requestPipe ~> unmarshal[Seq[PipelineTemplate]]

  def getPipelineTemplateViewRulesPipeline
    : HttpRequest => Future[Seq[PipelineTemplateViewRule]] =
    requestPipe ~> unmarshal[Seq[PipelineTemplateViewRule]]

  def getPipelineTemplateViewRulePipeline
    : HttpRequest => Future[PipelineTemplateViewRule] =
    requestPipe ~> unmarshal[PipelineTemplateViewRule]

  def getPipelineDataStoreViewRulesPipeline
    : HttpRequest => Future[PipelineDataStoreViewRules] =
    requestPipe ~> unmarshal[PipelineDataStoreViewRules]

  def getPacBioDataBundlesPipeline
    : HttpRequest => Future[Seq[PacBioDataBundle]] =
    requestPipe ~> unmarshal[Seq[PacBioDataBundle]]

  def getPacBioDataBundlePipeline: HttpRequest => Future[PacBioDataBundle] =
    requestPipe ~> unmarshal[PacBioDataBundle]

  def getServiceManifestsPipeline
    : HttpRequest => Future[Seq[PacBioComponentManifest]] =
    requestPipe ~> unmarshal[Seq[PacBioComponentManifest]]

  def getServiceManifestPipeline
    : HttpRequest => Future[PacBioComponentManifest] =
    requestPipe ~> unmarshal[PacBioComponentManifest]

  def getAlarmsPipeline: HttpRequest => Future[Seq[AlarmStatus]] =
    requestPipe ~> unmarshal[Seq[AlarmStatus]]

  def getDataSet(datasetId: IdAble): Future[DataSetMetaDataSet] =
    getDataSetMetaDataPipeline {
      logger.debug(s"Retrieving metadata for dataset ${datasetId.toIdString}")
      Get(toUrl(ROOT_DS + "/" + datasetId.toIdString))
    }

  def deleteDataSet(datasetId: IdAble): Future[MessageResponse] =
    getMessageResponsePipeline {
      logger.debug(s"Soft-deleting dataset ${datasetId.toIdString}")
      Put(toUrl(ROOT_DS + "/" + datasetId.toIdString),
          DataSetUpdateRequest(Some(false)))
    }

  def getSubreadSets: Future[Seq[SubreadServiceDataSet]] =
    getSubreadSetsPipeline {
      logger.debug(s"Retrieving all active SubreadSet records")
      Get(toDataSetsUrl(DataSetMetaTypes.Subread.shortName))
    }

  def getSubreadSet(dsId: IdAble): Future[SubreadServiceDataSet] =
    getSubreadSetPipeline {
      Get(toDataSetUrl(DataSetMetaTypes.Subread.shortName, dsId))
    }

  def getSubreadSetDetails(dsId: IdAble): Future[SubreadSet] =
    getSubreadSetDetailsPipeline {
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.Subread.shortName,
                              dsId,
                              "details"))
    }

  def getSubreadSetReports(dsId: IdAble): Future[Seq[DataStoreReportFile]] =
    getReportsPipeline {
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.Subread.shortName,
                              dsId,
                              JOB_REPORT_PREFIX))
    }

  def updateSubreadSetDetails(dsId: IdAble,
                              isActive: Option[Boolean] = None,
                              bioSampleName: Option[String] = None,
                              wellSampleName: Option[String] = None) =
    getMessageResponsePipeline {
      Put(toDataSetUrl(DataSetMetaTypes.Subread.shortName, dsId),
          DataSetUpdateRequest(isActive, bioSampleName, wellSampleName))
    }

  def getHdfSubreadSets: Future[Seq[HdfSubreadServiceDataSet]] =
    getHdfSubreadSetsPipeline {
      logger.debug(s"Retrieving all active HdfSubreadSet records")
      Get(toDataSetsUrl(DataSetMetaTypes.HdfSubread.shortName))
    }

  def getHdfSubreadSet(dsId: IdAble): Future[HdfSubreadServiceDataSet] =
    getHdfSubreadSetPipeline {
      Get(toDataSetUrl(DataSetMetaTypes.HdfSubread.shortName, dsId))
    }

  def getHdfSubreadSetDetails(dsId: IdAble): Future[HdfSubreadSet] =
    getHdfSubreadSetDetailsPipeline {
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.HdfSubread.shortName,
                              dsId,
                              "details"))
    }

  def getBarcodeSets: Future[Seq[BarcodeServiceDataSet]] =
    getBarcodeSetsPipeline {
      logger.debug(s"Retrieving all active BarcodeSet records")
      Get(toDataSetsUrl(DataSetMetaTypes.Barcode.shortName))
    }

  def getBarcodeSet(dsId: IdAble): Future[BarcodeServiceDataSet] =
    getBarcodeSetPipeline {
      Get(toDataSetUrl(DataSetMetaTypes.Barcode.shortName, dsId))
    }

  def getBarcodeSetDetails(dsId: IdAble): Future[BarcodeSet] =
    getBarcodeSetDetailsPipeline {
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.Barcode.shortName,
                              dsId,
                              "details"))
    }

  def getReferenceSets: Future[Seq[ReferenceServiceDataSet]] =
    getReferenceSetsPipeline {
      logger.debug(s"Retrieving all active ReferenceSet records")
      Get(toDataSetsUrl(DataSetMetaTypes.Reference.shortName))
    }

  def getReferenceSet(dsId: IdAble): Future[ReferenceServiceDataSet] =
    getReferenceSetPipeline {
      Get(toDataSetUrl(DataSetMetaTypes.Reference.shortName, dsId))
    }

  def getReferenceSetDetails(dsId: IdAble): Future[ReferenceSet] =
    getReferenceSetDetailsPipeline {
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.Reference.shortName,
                              dsId,
                              "details"))
    }

  def getGmapReferenceSets: Future[Seq[GmapReferenceServiceDataSet]] =
    getGmapReferenceSetsPipeline {
      logger.debug(s"Retrieving all active GmapReferenceSet records")
      Get(toDataSetsUrl(DataSetMetaTypes.GmapReference.shortName))
    }

  def getGmapReferenceSet(dsId: IdAble): Future[GmapReferenceServiceDataSet] =
    getGmapReferenceSetPipeline {
      Get(toDataSetUrl(DataSetMetaTypes.GmapReference.shortName, dsId))
    }

  def getGmapReferenceSetDetails(dsId: IdAble): Future[GmapReferenceSet] =
    getGmapReferenceSetDetailsPipeline {
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.GmapReference.shortName,
                              dsId,
                              "details"))
    }

  def getAlignmentSets: Future[Seq[AlignmentServiceDataSet]] =
    getAlignmentSetsPipeline {
      logger.debug(s"Retrieving all active AlignmentSet records")
      Get(toDataSetsUrl(DataSetMetaTypes.Alignment.shortName))
    }

  def getAlignmentSet(dsId: IdAble): Future[AlignmentServiceDataSet] =
    getAlignmentSetPipeline {
      Get(toDataSetUrl(DataSetMetaTypes.Alignment.shortName, dsId))
    }

  def getAlignmentSetDetails(dsId: IdAble): Future[AlignmentSet] =
    getAlignmentSetDetailsPipeline {
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.Alignment.shortName,
                              dsId,
                              "details"))
    }

  def getConsensusReadSets: Future[Seq[ConsensusReadServiceDataSet]] =
    getConsensusReadSetsPipeline {
      Get(toDataSetsUrl(DataSetMetaTypes.CCS.shortName))
    }

  def getConsensusReadSet(dsId: IdAble): Future[ConsensusReadServiceDataSet] =
    getConsensusReadSetPipeline {
      Get(toDataSetUrl(DataSetMetaTypes.CCS.shortName, dsId))
    }

  def getConsensusReadSetDetails(dsId: IdAble): Future[ConsensusReadSet] =
    getConsensusReadSetDetailsPipeline {
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.CCS.shortName, dsId, "details"))
    }

  def getConsensusAlignmentSets
    : Future[Seq[ConsensusAlignmentServiceDataSet]] =
    getConsensusAlignmentSetsPipeline {
      Get(toDataSetsUrl(DataSetMetaTypes.AlignmentCCS.shortName))
    }

  def getConsensusAlignmentSet(
      dsId: IdAble): Future[ConsensusAlignmentServiceDataSet] =
    getConsensusAlignmentSetPipeline {
      Get(toDataSetUrl(DataSetMetaTypes.AlignmentCCS.shortName, dsId))
    }

  def getConsensusAlignmentSetDetails(
      dsId: IdAble): Future[ConsensusAlignmentSet] =
    getConsensusAlignmentSetDetailsPipeline {
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.AlignmentCCS.shortName,
                              dsId,
                              "details"))
    }

  def getContigSets: Future[Seq[ContigServiceDataSet]] =
    getContigSetsPipeline {
      Get(toDataSetsUrl(DataSetMetaTypes.Contig.shortName))
    }

  def getContigSet(dsId: IdAble): Future[ContigServiceDataSet] =
    getContigSetPipeline {
      Get(toDataSetUrl(DataSetMetaTypes.Contig.shortName, dsId))
    }

  def getContigSetDetails(dsId: IdAble): Future[ContigSet] =
    getContigSetDetailsPipeline {
      Get(
        toDataSetResourcesUrl(DataSetMetaTypes.Contig.shortName,
                              dsId,
                              "details"))
    }

  protected def getJobDataStore(
      jobType: String,
      jobId: IdAble): Future[Seq[DataStoreServiceFile]] =
    getDataStorePipeline {
      Get(toJobResourceUrl(jobType, jobId, JOB_DATASTORE_PREFIX))
    }

  def getImportDatasetJobDataStore(jobId: IdAble) =
    getJobDataStore(JobTypeIds.IMPORT_DATASET.id, jobId)

  def getMergeDatasetJobDataStore(jobId: IdAble) =
    getJobDataStore(JobTypeIds.MERGE_DATASETS.id, jobId)

  // FIXME how to convert to String?
  def getDataStoreFile(fileId: UUID): Future[HttpResponse] = respPipeline {
    Get(toUrl(ROOT_DATASTORE + s"/${fileId}/download"))
  }

  def updateDataStoreFile(fileId: UUID,
                          isActive: Boolean = true,
                          path: Option[Path] = None,
                          fileSize: Option[Long] = None) =
    getMessageResponsePipeline {
      Put(toUrl(ROOT_DATASTORE + s"/$fileId"),
          DataStoreFileUpdateRequest(isActive, path.map(_.toString), fileSize))
    }

  /*def getDataStoreFileBinary(fileId: UUID): Future[Array[Byte]] = rawDataPipeline {
    Get(toUrl(ROOT_DATASTORE + s"/${fileId}/download"))
  }*/

  def getReport(reportId: UUID): Future[Report] = getReportPipeline {
    Get(toUrl(ROOT_DATASTORE + s"/${reportId}/download"))
  }

  protected def getJobReports(
      jobId: IdAble,
      jobType: String): Future[Seq[DataStoreReportFile]] = getReportsPipeline {
    logger.debug(s"Getting reports for job ${jobId.toIdString}")
    Get(toJobResourceUrl(jobType, jobId, JOB_REPORT_PREFIX))
  }

  def getImportJobReports(jobId: IdAble) =
    getJobReports(jobId, JobTypeIds.IMPORT_DATASET.id)

  def getDataStoreFileResource(fileId: UUID,
                               relpath: String): Future[HttpResponse] =
    respPipeline {
      Get(toUrl(ROOT_DATASTORE + s"/${fileId}/resources?relpath=${relpath}"))
    }

  protected def getJobTasks(jobType: String,
                            jobId: IdAble): Future[Seq[JobTask]] =
    getJobTasksPipeline {
      Get(toJobResourceUrl(jobType, jobId, JOB_TASK_PREFIX))
    }

  protected def getJobTask(jobType: String,
                           jobId: IdAble,
                           taskId: UUID): Future[JobTask] =
    getJobTaskPipeline {
      Get(
        toJobResourceUrl(jobType,
                         jobId,
                         JOB_TASK_PREFIX + "/" + taskId.toString))
    }

  protected def getJobEvents(jobType: String,
                             jobId: Int): Future[Seq[JobEvent]] =
    getJobEventsPipeline {
      Get(toJobResourceUrl(jobType, jobId, JOB_EVENT_PREFIX))
    }

  protected def getJobOptions(jobType: String,
                              jobId: Int): Future[PipelineTemplatePreset] =
    getJobOptionsPipeline {
      Get(toJobResourceUrl(jobType, jobId, JOB_OPTIONS))
    }

  protected def createJobTask(jobType: String,
                              jobId: IdAble,
                              task: CreateJobTaskRecord): Future[JobTask] =
    getJobTaskPipeline {
      Post(toJobResourceUrl(jobType, jobId, JOB_TASK_PREFIX), task)
    }

  protected def updateJobTask(jobType: String,
                              jobId: IdAble,
                              update: UpdateJobTaskRecord): Future[JobTask] =
    getJobTaskPipeline {
      Put(toJobResourceUrl(jobType,
                           jobId,
                           JOB_TASK_PREFIX + "/" + update.uuid.toString),
          update)
    }

  // Runs

  protected def getRunUrl(runId: UUID): String = toUrl(s"${ROOT_RUNS}/$runId")

  protected def getCollectionsUrl(runId: UUID): String =
    toUrl(s"${ROOT_RUNS}/$runId/collections")

  protected def getCollectionUrl(runId: UUID, collectionId: UUID): String =
    toUrl(s"${ROOT_RUNS}/$runId/collections/$collectionId")

  def getRuns: Future[Seq[RunSummary]] = getRunsPipeline {
    Get(toUrl(ROOT_RUNS))
  }

  def getRun(runId: UUID): Future[Run] = getRunPipeline {
    Get(getRunUrl(runId))
  }

  def getCollections(runId: UUID): Future[Seq[CollectionMetadata]] =
    getCollectionsPipeline {
      Get(getCollectionsUrl(runId))
    }

  def getCollection(runId: UUID,
                    collectionId: UUID): Future[CollectionMetadata] =
    getCollectionPipeline {
      Get(getCollectionUrl(runId, collectionId))
    }

  def createRun(dataModel: String): Future[RunSummary] =
    getRunSummaryPipeline {
      Post(toUrl(ROOT_RUNS), RunCreate(dataModel))
    }

  def updateRun(runId: UUID,
                dataModel: Option[String] = None,
                reserved: Option[Boolean] = None): Future[RunSummary] =
    getRunSummaryPipeline {
      Post(getRunUrl(runId), RunUpdate(dataModel, reserved))
    }

  def deleteRun(runId: UUID): Future[MessageResponse] =
    getMessageResponsePipeline {
      Delete(getRunUrl(runId))
    }

  def getProjects: Future[Seq[Project]] = getProjectsPipeline {
    Get(toUrl(ROOT_PROJECTS)).withHeaders(headers: _*)
  }

  def getProject(projectId: Int): Future[FullProject] = getProjectPipeline {
    Get(toUrl(ROOT_PROJECTS + s"/$projectId")).withHeaders(headers: _*)
  }

  def createProject(name: String, description: String): Future[FullProject] =
    getProjectPipeline {
      Post(toUrl(ROOT_PROJECTS),
           ProjectRequest(name, description, None, None, None, None))
        .withHeaders(headers: _*)
    }

  def updateProject(projectId: Int,
                    request: ProjectRequest): Future[FullProject] =
    getProjectPipeline {
      Put(toUrl(ROOT_PROJECTS + s"/$projectId"), request)
        .withHeaders(headers: _*)
    }

  // User agreements (not really a EULA)
  def getEula(version: String): Future[EulaRecord] = getEulaPipeline {
    Get(toUrl(ROOT_EULA + s"/$version"))
  }

  def getEulas: Future[Seq[EulaRecord]] = getEulasPipeline {
    Get(toUrl(ROOT_EULA))
  }

  def acceptEula(user: String, enableInstallMetrics: Boolean = true) =
    getEulaPipeline {
      Post(toUrl(ROOT_EULA), EulaAcceptance(user, enableInstallMetrics))
    }

  def deleteEula(version: String) = getMessageResponsePipeline {
    Delete(toUrl(ROOT_EULA + s"/$version"))
  }

  def getJobsByType(jobType: String,
                    showAll: Boolean = false,
                    projectId: Option[Int] = None): Future[Seq[EngineJob]] =
    getJobsPipeline {
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

  def getJobsByProject(projectId: Int): Future[Seq[EngineJob]] =
    getJobsPipeline {
      Get(toUrl(ROOT_JOBS + s"?projectId=$projectId"))
    }

  def getPacBioComponentManifests: Future[Seq[PacBioComponentManifest]] =
    getServiceManifestsPipeline {
      Get(toUrl(ROOT_SERVICE_MANIFESTS))
    }

  // Added in smrtflow 0.1.11 and SA > 3.2.0
  def getPacBioComponentManifestById(
      manifestId: String): Future[PacBioComponentManifest] =
    getServiceManifestPipeline {
      Get(toUrl(ROOT_SERVICE_MANIFESTS + "/" + manifestId))
    }

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

  def getJob(jobId: IdAble): Future[EngineJob] = getJobPipeline {
    Get(toUrl(ROOT_JOBS + "/" + jobId.toIdString))
  }

  def deleteJob(jobId: UUID,
                removeFiles: Boolean = true,
                dryRun: Boolean = false,
                force: Boolean = false): Future[EngineJob] = getJobPipeline {
    logger.debug(s"Deleting job $jobId")
    Post(toUrl(ROOT_JOBS + "/delete-job"),
         DeleteSmrtLinkJobOptions(jobId,
                                  Some(s"Delete job $jobId name"),
                                  None,
                                  removeFiles,
                                  dryRun = Some(dryRun),
                                  force = Some(force)))
  }

  def getJobChildren(jobId: IdAble): Future[Seq[EngineJob]] = getJobsPipeline {
    Get(toUrl(ROOT_JOBS + "/" + jobId.toIdString + "/children"))
  }

  def getJobByTypeAndId(jobType: String, jobId: IdAble): Future[EngineJob] =
    getJobPipeline {
      Get(toJobUrl(jobType, jobId))
    }

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
    getEntryPointsPipeline {
      Get(
        toJobResourceUrl(JobTypeIds.PBSMRTPIPE.id, jobId, ENTRY_POINTS_PREFIX))
    }

  protected def getJobReport(jobType: String,
                             jobId: IdAble,
                             reportId: UUID): Future[Report] =
    getReportPipeline {
      logger.debug(s"Getting report $reportId for job ${jobId.toIdString}")
      Get(toJobResourceIdUrl(jobType, jobId, JOB_REPORT_PREFIX, reportId))
    }

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
    getMessageResponsePipeline {
      Post(toJobResourceUrl(JobTypeIds.PBSMRTPIPE.id, jobId, TERMINATE_JOB))
    }

  def getReportViewRules: Future[Seq[ReportViewRule]] =
    getReportViewRulesPipeline {
      Get(toUrl(ROOT_REPORT_RULES))
    }

  def getReportViewRule(reportId: String): Future[ReportViewRule] =
    getReportViewRulePipeline {
      Get(toUrl(ROOT_REPORT_RULES + s"/$reportId"))
    }

  def importDataSet(
      path: Path,
      dsMetaType: DataSetMetaTypes.DataSetMetaType): Future[EngineJob] =
    runJobPipeline {
      logger.debug(s"Submitting import-dataset job for $path")
      Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.IMPORT_DATASET.id),
           ImportDataSetOptions(path.toAbsolutePath, dsMetaType))
    }

  def importFasta(path: Path,
                  name: String,
                  organism: String,
                  ploidy: String): Future[EngineJob] = runJobPipeline {
    logger.debug(s"Importing reference $name from FASTA file $path")
    Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.CONVERT_FASTA_REFERENCE.id),
         ImportFastaJobOptions(toP(path), ploidy, organism, Some(name), None))
  }

  def importFastaBarcodes(path: Path, name: String): Future[EngineJob] =
    runJobPipeline {
      logger.debug(s"Importing barcodes $name from FASTA file $path")
      Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.CONVERT_FASTA_BARCODES.id),
           ConvertImportFastaBarcodesOptions(toP(path), name))
    }

  def mergeDataSets(datasetType: DataSetMetaTypes.DataSetMetaType,
                    ids: Seq[IdAble],
                    name: String) = runJobPipeline {
    logger.debug(s"Submitting merge-datasets job for ${ids.size} datasets")
    Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.MERGE_DATASETS.id),
         MergeDataSetJobOptions(datasetType, ids, Some(name), None))
  }

  def convertRsMovie(path: Path, name: String) = runJobPipeline {
    logger.debug(s"Importing RS II metadata from $path")
    Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.CONVERT_RS_MOVIE.id),
         MovieMetadataToHdfSubreadOptions(toP(path), name))
  }

  def exportDataSets(datasetType: DataSetMetaTypes.DataSetMetaType,
                     ids: Seq[Int],
                     outputPath: Path,
                     deleteAfterExport: Boolean = false) = runJobPipeline {
    logger.debug(s"Exporting ${ids.size} datasets")
    Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.EXPORT_DATASETS.id),
         DataSetExportServiceOptions(datasetType.toString,
                                     ids,
                                     toP(outputPath),
                                     Some(deleteAfterExport)))
  }

  def deleteDataSets(datasetType: DataSetMetaTypes.DataSetMetaType,
                     ids: Seq[Int],
                     removeFiles: Boolean = true) = runJobPipeline {
    logger.debug(s"Deleting ${ids.size} datasets")
    Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.DELETE_DATASETS.id),
         DataSetDeleteServiceOptions(datasetType.toString, ids, removeFiles))
  }

  def getPipelineTemplate(pipelineId: String): Future[PipelineTemplate] =
    getPipelineTemplatePipeline {
      Get(toUrl(ROOT_PT + "/" + pipelineId))
    }

  def getPipelineTemplates: Future[Seq[PipelineTemplate]] =
    getPipelineTemplatesPipeline {
      Get(toUrl(ROOT_PT))
    }

  def getPipelineTemplateViewRules: Future[Seq[PipelineTemplateViewRule]] =
    getPipelineTemplateViewRulesPipeline {
      Get(toUrl(ROOT_PTRULES))
    }

  def getPipelineTemplateViewRule(
      pipelineId: String): Future[PipelineTemplateViewRule] =
    getPipelineTemplateViewRulePipeline {
      Get(toUrl(ROOT_PTRULES + s"/$pipelineId"))
    }

  def getPipelineDataStoreViewRules(
      pipelineId: String): Future[PipelineDataStoreViewRules] =
    getPipelineDataStoreViewRulesPipeline {
      Get(toUrl(ROOT_DS_RULES + s"/$pipelineId"))
    }

  def runAnalysisPipeline(
      pipelineOptions: PbSmrtPipeServiceOptions): Future[EngineJob] =
    runJobPipeline {
      logger.debug(s"Submitting analysis job '${pipelineOptions.name}'")
      Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.PBSMRTPIPE.id), pipelineOptions)
    }

  def createMultiAnalysisJob(
      multiAnalysisJobOptions: MultiAnalysisJobOptions): Future[EngineJob] = {
    runJobPipeline {
      logger.info(
        s"Submitting $JOB_MULTI_ROOT_PREFIX name:${multiAnalysisJobOptions.name
          .getOrElse("")}")
      Post(toUrl(s"$ROOT_MULTI_JOBS/${JobTypeIds.MJOB_MULTI_ANALYSIS.id}"),
           multiAnalysisJobOptions)
    }
  }

  def updateMultiAnalysisJobToSubmit(ix: IdAble): Future[MessageResponse] = {
    getMessageResponsePipeline {
      logger.info(
        s"Attempting to change MultiJob ${ix.toIdString} state to SUBMITTED")
      Post(toUrl(
        s"$ROOT_MULTI_JOBS/${JobTypeIds.MJOB_MULTI_ANALYSIS.id}/${ix.toIdString}/submit"))
    }
  }

  def getMultiAnalysisChildrenJobs(ix: IdAble): Future[Seq[EngineJob]] = {
    getJobsPipeline {
      Get(toUrl(
        s"$ROOT_MULTI_JOBS/${JobTypeIds.MJOB_MULTI_ANALYSIS.id}/${ix.toIdString}/jobs"))
    }
  }

  // PacBio Data Bundle
  def getPacBioDataBundles() = getPacBioDataBundlesPipeline {
    Get(toPacBioDataBundleUrl())
  }

  def getPacBioDataBundleByTypeId(typeId: String) =
    getPacBioDataBundlesPipeline {
      Get(toPacBioDataBundleUrl(Some(typeId)))
    }

  def getPacBioDataBundleByTypeAndVersionId(typeId: String,
                                            versionId: String) =
    getPacBioDataBundlePipeline {
      Get(toPacBioDataBundleUrl(Some(s"$typeId/$versionId")))
    }

  def runTsSystemStatus(user: String, comment: String) = runJobPipeline {
    logger.debug("Submitting tech support status job")
    Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.TS_SYSTEM_STATUS.id),
         TsSystemStatusServiceOptions(user, comment))
  }

  /**
    * Create a Failed TechSupport Job
    *
    * @param jobId   Failed Job id that is in the FAILED state is supported
    * @param user    User that has created the job TGZ bundle
    * @param comment Comment
    * @return
    */
  def runTsJobBundle(jobId: Int, user: String, comment: String) =
    runJobPipeline {
      logger.debug("Submitting tech support job bundle")
      Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.TS_JOB.id),
           TsJobBundleJobServiceOptions(jobId, user, comment))
    }

  /**
    * Submit a request to create a DB BackUp Job
    *
    * The server must be configured with a backup path, otherwise, there should be an error raised.
    *
    * @param user    User that is creating the request to backup the db
    * @param comment A comment from the user
    * @return
    */
  def runDbBackUpJob(user: String, comment: String) = runJobPipeline {
    logger.debug("Submitting database backup job")
    Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.DB_BACKUP.id),
         DbBackUpServiceJobOptions(user, comment))
  }

  /**
    * Start export of SMRT Link job(s)
    */
  def exportJobs(jobIds: Seq[IdAble],
                 outputPath: Path,
                 includeEntryPoints: Boolean = false,
                 name: Option[String] = None,
                 description: Option[String] = None) = runJobPipeline {
    logger.debug("Submitting export-jobs job")
    Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.EXPORT_JOBS.id),
         ExportSmrtLinkJobOptions(jobIds,
                                  outputPath,
                                  includeEntryPoints,
                                  name,
                                  description))
  }

  /**
    * Start import of SMRT Link job from zip file
    *
    * @param zipPath   pathname of ZIP file exported by SMRT Link
    * @param mockJobId if true, job UUID will be re-generated
    * @return new EngineJob object
    */
  def importJob(zipPath: Path, mockJobId: Boolean = false) = runJobPipeline {
    logger.debug("Submitting import-job job")
    Post(toUrl(ROOT_JOBS + "/" + JobTypeIds.IMPORT_JOB.id),
         ImportSmrtLinkJobOptions(zipPath, mockJobId = Some(mockJobId)))
  }

  def getAlarms() = getAlarmsPipeline {
    Get(toUrl(ROOT_ALARMS))
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
