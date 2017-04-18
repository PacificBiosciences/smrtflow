package com.pacbio.secondary.smrtlink.models

import java.net.URL
import java.nio.file.{Path, Paths}
import java.util.UUID

import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.common.semver.SemVersion
import org.joda.time.{DateTime => JodaDateTime}
import com.pacificbiosciences.pacbiobasedatamodel.{SupportedAcquisitionStates, SupportedRunStates}
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes._
import spray.json.JsObject

object Models

// Runs

case class RunCreate(dataModel: String)

case class RunUpdate(dataModel: Option[String] = None, reserved: Option[Boolean] = None)

case class RunSummary(
    uniqueId: UUID,
    name: String,
    summary: Option[String],
    createdBy: Option[String],
    createdAt: Option[JodaDateTime],
    startedAt: Option[JodaDateTime],
    transfersCompletedAt: Option[JodaDateTime],
    completedAt: Option[JodaDateTime],
    status: SupportedRunStates,
    totalCells: Int,
    numCellsCompleted: Int,
    numCellsFailed: Int,
    instrumentName: Option[String],
    instrumentSerialNumber: Option[String],
    instrumentSwVersion: Option[String],
    primaryAnalysisSwVersion: Option[String],
    context: Option[String],
    terminationInfo: Option[String],
    reserved: Boolean) {

  def withDataModel(dataModel: String) = Run(
      dataModel,
      uniqueId,
      name,
      summary,
      createdBy,
      createdAt,
      startedAt,
      transfersCompletedAt,
      completedAt,
      status,
      totalCells,
      numCellsCompleted,
      numCellsFailed,
      instrumentName,
      instrumentSerialNumber,
      instrumentSwVersion,
      primaryAnalysisSwVersion,
      context,
      terminationInfo,
      reserved)
}

case class Run(
    dataModel: String,
    uniqueId: UUID,
    name: String,
    summary: Option[String],
    createdBy: Option[String],
    createdAt: Option[JodaDateTime],
    startedAt: Option[JodaDateTime],
    transfersCompletedAt: Option[JodaDateTime],
    completedAt: Option[JodaDateTime],
    status: SupportedRunStates,
    totalCells: Int,
    numCellsCompleted: Int,
    numCellsFailed: Int,
    instrumentName: Option[String],
    instrumentSerialNumber: Option[String],
    instrumentSwVersion: Option[String],
    primaryAnalysisSwVersion: Option[String],
    context: Option[String],
    terminationInfo: Option[String],
    reserved: Boolean) {

  def summarize = RunSummary(
      uniqueId,
      name,
      summary,
      createdBy,
      createdAt,
      startedAt,
      transfersCompletedAt,
      completedAt,
      status,
      totalCells,
      numCellsCompleted,
      numCellsFailed,
      instrumentName,
      instrumentSerialNumber,
      instrumentSwVersion,
      primaryAnalysisSwVersion,
      context,
      terminationInfo,
      reserved)
}

case class CollectionMetadata(
    runId: UUID,
    uniqueId: UUID,
    well: String,
    name: String,
    summary: Option[String],
    context: Option[String],
    collectionPathUri: Option[Path],
    status: SupportedAcquisitionStates,
    instrumentId: Option[String],
    instrumentName: Option[String],
    movieMinutes: Double,
    createdBy: Option[String],
    startedAt: Option[JodaDateTime],
    completedAt: Option[JodaDateTime],
    terminationInfo: Option[String])

// Samples

case class Sample(details: String, uniqueId: UUID, name: String, createdBy: String, createdAt: JodaDateTime)

case class SampleCreate(details: String, uniqueId: UUID, name: String)

case class SampleUpdate(details: Option[String], name: Option[String])

case class SampleTestExamples(count: Int)

// Registry

case class RegistryResource(createdAt: JodaDateTime, uuid: UUID, host: String, port: Int, resourceId: String, updatedAt: JodaDateTime)

case class RegistryResourceCreate(host: String, port: Int, resourceId: String)

case class RegistryResourceUpdate(host: Option[String], port: Option[Int])

case class RegistryProxyRequest(path: String, method: String, data: Option[Array[Byte]], headers: Option[Map[String, String]], params: Option[Map[String, String]])


// Jobs

// This is terse Status message used in sub-component endpoints root/my-endpoints/status
case class SimpleStatus(id: String, msg: String, uptime: Long)

case class JobTypeEndPoint(jobTypeId: String, description: String) {
  def globalId = s"jobtypes-$jobTypeId"
}

// Entry point use to create jobs from the Service layer. This will then be translated to a
// BoundEntryPoint with the resolved path of the DataSet
case class BoundServiceEntryPoint(entryId: String, fileTypeId: String, datasetId: Either[Int,UUID])

// Entry points that are have dataset types
case class EngineJobEntryPoint(jobId: Int, datasetUUID: UUID, datasetType: String)

case class EngineJobEntryPointRecord(datasetUUID: UUID, datasetType: String)

// Service related Job Tasks

/**
  * Service Request Format to create a TaskJob
  *
  * @param uuid Globally Unique Task UUID
  * @param taskId task id which is lo
  * @param taskTypeId task type (i.e., tool contract type id)
  * @param name Display name of the task
  * @param createdAt Time when the task was created
  */
case class CreateJobTaskRecord(uuid: UUID,
                               taskId: String,
                               taskTypeId: String,
                               name: String,
                               createdAt: JodaDateTime)

case class UpdateJobTaskRecord(uuid: UUID, state: String, message: String, errorMessage: Option[String])

// Need to find a better way to do this
case class PacBioSchema(id: String, content: String)

// "Resolvable" Service Job Options. These will get transformed into PbSmrtPipeOptions
// These are also used by the mock-pbsmrtpipe job options
case class PbSmrtPipeServiceOptions(
    name: String,
    pipelineId: String,
    entryPoints: Seq[BoundServiceEntryPoint],
    taskOptions: Seq[ServiceTaskOptionBase],
    workflowOptions: Seq[ServiceTaskOptionBase],
    projectId: Int = JobConstants.GENERAL_PROJECT_ID)


// New DataSet Service Models
trait UniqueIdAble {
  val id: Int
  val uuid: UUID
}

trait ProjectAble {
  val createdBy: Option[String]
  val jobId: Int
  val projectId: Int
  val isActive: Boolean
}

case class DataSetMetaDataSet(id: Int, uuid: UUID, name: String, path: String, createdAt: JodaDateTime, updatedAt: JodaDateTime, numRecords: Long, totalLength: Long, tags: String, version: String, comments: String, md5: String, createdBy: Option[String], jobId: Int, projectId: Int, isActive: Boolean) extends UniqueIdAble with ProjectAble

case class SubreadServiceSet(id: Int, uuid: UUID, cellId: String, metadataContextId: String, wellSampleName: String, wellName: String, bioSampleName: String, cellIndex: Int, instrumentId: String, instrumentName: String, runName: String, instrumentControlVersion: String) extends UniqueIdAble

case class SubreadServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: SubreadServiceSet)

case class HdfSubreadServiceSet(
    id: Int,
    uuid: UUID,
    cellId: String,
    metadataContextId: String,
    wellSampleName: String,
    wellName: String,
    bioSampleName: String,
    cellIndex: Int,
    instrumentId: String,
    instrumentName: String,
    runName: String,
    instrumentControlVersion: String) extends UniqueIdAble

case class HdfSubreadServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: HdfSubreadServiceSet)

case class ReferenceServiceSet(id: Int, uuid: UUID, ploidy: String, organism: String) extends UniqueIdAble

case class ReferenceServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: ReferenceServiceSet)

case class AlignmentServiceSet(id: Int, uuid: UUID) extends UniqueIdAble

case class AlignmentServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: AlignmentServiceSet)

case class BarcodeServiceSet(id: Int, uuid: UUID) extends UniqueIdAble

case class BarcodeServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: BarcodeServiceSet)

case class ConsensusReadServiceSet(id: Int, uuid: UUID) extends UniqueIdAble

case class ConsensusReadServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: ConsensusReadServiceSet)

case class GmapReferenceServiceSet(id: Int, uuid: UUID, ploidy: String, organism: String) extends UniqueIdAble

case class GmapReferenceServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: GmapReferenceServiceSet)

case class ConsensusAlignmentServiceSet(id: Int, uuid: UUID) extends UniqueIdAble

case class ConsensusAlignmentServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: ConsensusAlignmentServiceSet)

case class ContigServiceSet(id: Int, uuid: UUID) extends UniqueIdAble

case class ContigServiceMetaDataSet(metadata: DataSetMetaDataSet, dataset: ContigServiceSet)

// This is essentially just a flattening of the DataStoreJobFile + metadata specific to the
// /datastore-files endpoint
case class DataStoreServiceFile(
    uuid: UUID,
    fileTypeId: String,
    sourceId: String,
    fileSize: Long,
    createdAt: JodaDateTime,
    modifiedAt: JodaDateTime,
    importedAt: JodaDateTime,
    path: String,
    jobId: Int,
    jobUUID: UUID,
    name: String,
    description: String,
    isActive: Boolean = true) {

  def fileExists: Boolean = Paths.get(path).toFile.exists
}

// Files that have Reports
case class DataStoreReportFile(
    dataStoreFile: DataStoreServiceFile,
    reportTypeId: String)

/**
  * Service DataSet metadata
  *
  * See http://pacbiofileformats.readthedocs.io for more details
  *
  * @param id          dataset unique id (e.g., Pacbio.DataSet.SubreadSet)
  * @param name        Display name of dataset metadata
  * @param description Description of dataset metadata
  * @param createdAt   inserted into the database
  * @param updatedAt   last updated date in the database
  * @param shortName   short identifier (e.g., "subreads")
  */
case class ServiceDataSetMetaType(
    id: String,
    name: String,
    description: String,
    createdAt: JodaDateTime,
    updatedAt: JodaDateTime,
    shortName: String)

trait ServiceDataSetMetadata {
  val id: Int
  val name: String
  val uuid: UUID
  val path: String
  val createdAt: JodaDateTime
  val updatedAt: JodaDateTime
  val numRecords: Long
  val totalLength: Long
  val version: String
  val comments: String
  // Keeping this a string for now
  val tags: String
  val md5: String
  val createdBy: Option[String]
  val jobId: Int
  val projectId: Int
}

case class SubreadServiceDataSet(
    id: Int,
    uuid: UUID,
    name: String,
    path: String,
    createdAt: JodaDateTime,
    updatedAt: JodaDateTime,
    numRecords: Long,
    totalLength: Long,
    version: String,
    comments: String,
    tags: String,
    md5: String,
    instrumentName: String,
    metadataContextId: String,
    wellSampleName: String,
    wellName: String,
    bioSampleName: String,
    cellIndex: Int,
    runName: String,
    createdBy: Option[String],
    jobId: Int,
    projectId: Int,
    datasetType: String = Subread.toString())
    extends ServiceDataSetMetadata

case class HdfSubreadServiceDataSet(
    id: Int,
    uuid: UUID,
    name: String,
    path: String,
    createdAt: JodaDateTime,
    updatedAt: JodaDateTime,
    numRecords: Long,
    totalLength: Long,
    version: String,
    comments: String,
    tags: String,
    md5: String,
    instrumentName: String,
    metadataContextId: String,
    wellSampleName: String,
    wellName: String,
    bioSampleName: String,
    cellIndex: Int,
    runName: String,
    createdBy: Option[String],
    jobId: Int,
    projectId: Int,
    datasetType: String = HdfSubread.toString())
    extends ServiceDataSetMetadata

case class ReferenceServiceDataSet(
    id: Int,
    uuid: UUID,
    name: String,
    path: String,
    createdAt: JodaDateTime,
    updatedAt: JodaDateTime,
    numRecords: Long,
    totalLength: Long,
    version: String,
    comments: String,
    tags: String,
    md5: String,
    createdBy: Option[String],
    jobId: Int,
    projectId: Int,
    ploidy: String,
    organism: String,
    datasetType: String = Reference.toString())
    extends ServiceDataSetMetadata

case class AlignmentServiceDataSet(
    id: Int,
    uuid: UUID,
    name: String,
    path: String,
    createdAt: JodaDateTime,
    updatedAt: JodaDateTime,
    numRecords: Long,
    totalLength: Long,
    version: String,
    comments: String,
    tags: String,
    md5: String,
    createdBy: Option[String],
    jobId: Int,
    projectId: Int,
    datasetType: String = Alignment.toString())
    extends ServiceDataSetMetadata

case class ConsensusReadServiceDataSet(
    id: Int,
    uuid: UUID,
    name: String,
    path: String,
    createdAt: JodaDateTime,
    updatedAt: JodaDateTime,
    numRecords: Long,
    totalLength: Long,
    version: String,
    comments: String,
    tags: String,
    md5: String,
    createdBy: Option[String],
    jobId: Int,
    projectId: Int,
    datasetType: String = CCS.toString())
    extends ServiceDataSetMetadata

case class ConsensusAlignmentServiceDataSet(
    id: Int,
    uuid: UUID,
    name: String,
    path: String,
    createdAt: JodaDateTime,
    updatedAt: JodaDateTime,
    numRecords: Long,
    totalLength: Long,
    version: String,
    comments: String,
    tags: String,
    md5: String,
    createdBy: Option[String],
    jobId: Int,
    projectId: Int,
    datasetType: String = AlignmentCCS.toString())
    extends ServiceDataSetMetadata

case class BarcodeServiceDataSet(
    id: Int,
    uuid: UUID,
    name: String,
    path: String,
    createdAt: JodaDateTime,
    updatedAt: JodaDateTime,
    numRecords: Long,
    totalLength: Long,
    version: String,
    comments: String,
    tags: String,
    md5: String,
    createdBy: Option[String],
    jobId: Int,
    projectId: Int,
    datasetType: String = Barcode.toString())
    extends ServiceDataSetMetadata

case class ContigServiceDataSet(
    id: Int,
    uuid: UUID,
    name: String,
    path: String,
    createdAt: JodaDateTime,
    updatedAt: JodaDateTime,
    numRecords: Long,
    totalLength: Long,
    version: String,
    comments: String,
    tags: String,
    md5: String,
    createdBy: Option[String],
    jobId: Int,
    projectId: Int,
    datasetType: String = Contig.toString())
  extends ServiceDataSetMetadata

case class GmapReferenceServiceDataSet(
    id: Int,
    uuid: UUID,
    name: String,
    path: String,
    createdAt: JodaDateTime,
    updatedAt: JodaDateTime,
    numRecords: Long,
    totalLength: Long,
    version: String,
    comments: String,
    tags: String,
    md5: String,
    createdBy: Option[String],
    jobId: Int,
    projectId: Int,
    ploidy: String,
    organism: String,
    datasetType: String = GmapReference.toString())
    extends ServiceDataSetMetadata

// Options used for Merging Datasets
case class DataSetMergeServiceOptions(datasetType: String, ids: Seq[Int], name: String)
case class DeleteJobServiceOptions(jobId: UUID, removeFiles: Boolean = false,
                                   dryRun: Option[Boolean] = None)

// Project models

object ProjectState {
  sealed trait ProjectState
  case object CREATED extends ProjectState
  case object ACTIVE extends ProjectState

  // LEGACY STATES
  // TODO(smcclellan): Clean/delete rows with UPDATED state?
  case object UPDATED extends ProjectState

  def fromString(s: String): ProjectState = {
    Seq(CREATED, ACTIVE, UPDATED)
      .find(_.toString == s)
      .getOrElse(throw new IllegalArgumentException(s"Unknown project state $s, acceptable values are $CREATED, $ACTIVE"))
  }
}

object ProjectUserRole {
  sealed abstract class ProjectUserRole(private val ordinal: Int) extends Ordered[ProjectUserRole] {
    override def compare(that: ProjectUserRole) = ordinal.compare(that.ordinal)
  }
  case object OWNER extends ProjectUserRole(ordinal = 3)
  case object CAN_EDIT extends ProjectUserRole(ordinal = 2)
  case object CAN_VIEW extends ProjectUserRole(ordinal = 1)

  def fromString(r: String): ProjectUserRole = {
    Seq(OWNER, CAN_EDIT, CAN_VIEW)
      .find(_.toString == r)
      .getOrElse(throw new IllegalArgumentException(s"Unknown project user role $r, acceptable values are $OWNER, $CAN_EDIT, $CAN_VIEW"))
  }
}

// We have a simpler (cheaper to query) project case class for the API
// response that lists many projects,
case class Project(
    id: Int,
    name: String,
    description: String,
    state: ProjectState.ProjectState,
    createdAt: JodaDateTime,
    updatedAt: JodaDateTime,
    // isActive: false if the project has been deleted, true otherwise
    isActive: Boolean,
    grantRoleToAll: Option[ProjectUserRole.ProjectUserRole] = None) {

  def makeFull(datasets: Seq[DataSetMetaDataSet], members: Seq[ProjectRequestUser]): FullProject =
    FullProject(
      id,
      name,
      description,
      state,
      createdAt,
      updatedAt,
      isActive,
      grantRoleToAll,
      datasets,
      members)
}

// and a more detailed case class for the API responses involving
// individual projects.
case class FullProject(
    id: Int,
    name: String,
    description: String,
    state: ProjectState.ProjectState,
    createdAt: JodaDateTime,
    updatedAt: JodaDateTime,
    isActive: Boolean,
    grantRoleToAll: Option[ProjectUserRole.ProjectUserRole],
    datasets: Seq[DataSetMetaDataSet],
    members: Seq[ProjectRequestUser]) {

  def asRequest: ProjectRequest =
    ProjectRequest(
      name,
      description,
      Some(state),
      Some(ProjectRequestRole.fromProjectUserRole(grantRoleToAll)),
      Some(datasets.map(ds => RequestId(ds.id))),
      Some(members.map(u => ProjectRequestUser(u.login, u.role))))
}

object ProjectRequestRole {
  sealed abstract class ProjectRequestRole(val role: Option[ProjectUserRole.ProjectUserRole])
  case object CAN_EDIT extends ProjectRequestRole(role = Some(ProjectUserRole.CAN_EDIT))
  case object CAN_VIEW extends ProjectRequestRole(role = Some(ProjectUserRole.CAN_VIEW))
  case object NONE extends ProjectRequestRole(role = None)

  private val ALL = Seq(CAN_EDIT, CAN_VIEW, NONE)

  def fromProjectUserRole(r: Option[ProjectUserRole.ProjectUserRole]): ProjectRequestRole = ALL
    .find(_.role == r)
    .getOrElse(throw new IllegalArgumentException(s"No corresponding ProjectRequestRole for ProjectUserRole $r"))

  def fromString(r: String): ProjectRequestRole = ALL
    .find(_.toString == r)
    .getOrElse(throw new IllegalArgumentException(s"Unknown project request role $r, acceptable values are $CAN_EDIT, $CAN_VIEW, $NONE"))
}

// the json structures required in client requests are a subset of the
// FullProject structure (the FullProject is a valid request, but many
// fields are optional in requests).
case class ProjectRequest(
    name: String,
    description: String,
    // if any of these are None in a PUT request, the corresponding
    // value will stay the same (i.e., the update will be skipped).
    state: Option[ProjectState.ProjectState],
    grantRoleToAll: Option[ProjectRequestRole.ProjectRequestRole],
    datasets: Option[Seq[RequestId]],
    members: Option[Seq[ProjectRequestUser]]) {

  // this returns a copy!
  def appendDataSet(dsId: Int): ProjectRequest = {
    val allDatasets = datasets.map(ds => ds ++ Seq(RequestId(dsId))).getOrElse(Seq(RequestId(dsId)))
    this.copy(datasets = Some(allDatasets))
  }
}

case class RequestId(id: Int)

case class ProjectRequestUser(login: String, role: ProjectUserRole.ProjectUserRole)
case class ProjectUser(projectId: Int, login: String, role: ProjectUserRole.ProjectUserRole)

case class UserProjectResponse(role: ProjectUserRole.ProjectUserRole, project: Project)

case class ProjectDatasetResponse(project: Project, dataset: DataSetMetaDataSet, role: ProjectUserRole.ProjectUserRole)


case class EulaRecord(user: String, acceptedAt: JodaDateTime, smrtlinkVersion: String, osVersion: String, enableInstallMetrics: Boolean, enableJobMetrics: Boolean)

case class EulaAcceptance(user: String, enableInstallMetrics: Boolean)

case class DataSetUpdateRequest(isActive: Boolean)

// Bundle Related Models

/**
  * PacBio Data Bundle. A general contain for config files, or data files.
  *
  * @param typeId     identifier for the bundle type
  * @param version    version of the bundle (Should use Semver format, 1.2.3.12334 is supported)
  * @param importedAt When the data bundle was imported
  * @param createdBy  User that created (not imported) the bundle
  * @param isActive   If the bundle is active (Only a single bundle type should be active at a given time)
  */
case class PacBioDataBundle(typeId: String,
                            version: String,
                            importedAt: JodaDateTime,
                            createdBy: Option[String],
                            isActive: Boolean = false) {
  // This is bad OO to duplicate data (version and semVersion)
  // However, this is used to sort bundles and is
  // exposed publicly, but we don't want this to leak to serialization layers (e.g., jsonFormat).
  // Given that case classes are immutable .copy(version="1.2.3") will work
  // as expected. This seems like a reasonable
  // trade off.
  val semVersion = allowSlop(version)

  /**
    * Add Some slop until we all components strictly use SemVer
    *
    * Only supported is the 1.2.3.1234 format used by ICS.
    *
    * @param rawVersion
    * @return
    */
  private def allowSlop(rawVersion: String): SemVersion = {
    val rx = """(\d+).(\d+).(\d+).(\d+)""".r

    rawVersion match {
      case rx(major, minor, patch, extra) => SemVersion.fromString(s"$major.$minor.$patch+$extra")
      case _ => SemVersion.fromString(rawVersion)
    }
  }
}

object PacBioDataBundle {
  implicit val orderBySemVer = SemVersion.orderBySemVersion
  val orderByBundleVersion = Ordering.by((a: PacBioDataBundle) => a.semVersion)
}


/**
  *
  * This is to keep the object model and the IO layer separate.
  *
  * @param tarGzPath Path to the tgz or *.tar.gz data bundle
  * @param path Path to the unzip, untarred Data Bundle
  * @param bundle MetaData About the bundle
  */
case class PacBioDataBundleIO(tarGzPath: Path, path: Path, bundle: PacBioDataBundle)


case class PacBioDataBundleUpgrade(bundle: Option[PacBioDataBundle])

// Use to create a bundle record. All Metadata will be extracted from
// the bundle metadata after it's been extracted
case class PacBioBundleRecord(url: URL)

//MK This is duplicated concept
case class ExternalServerStatus(msg: String, status: String)


// SmrtLink Events/Messages

/**
  * General SmrtLink Event data model
  *
  * @param eventTypeId event Type id. This must be globally unique and map to schema defined in the message (as Json)
  * @param eventTypeVersion Version of the eventTypeId. A tuple of (eventTypeId, eventTypeVersion) must be resolvable to schema defined in *message*
  * @param uuid        globally unique identifier for event message. Assigned by the creator
  * @param createdAt   when the message/event was created at
  * @param message     Json of the message
  */
case class SmrtLinkEvent(eventTypeId: String,
                         eventTypeVersion: Int = 1,
                         uuid: UUID,
                         createdAt: JodaDateTime,
                         message: JsObject)

object EventTypes {
  val INST_UPGRADE_NOTIFICATION = "smrtlink_inst_upgrade_notification"
  val SERVER_STARTUP = "smrt_server_startup"
  val IMPORT_BUNDLE = "techsupport_import_bundle"
}

case class SmrtLinkSystemEvent(smrtLinkId: UUID,
                               eventTypeId: String,
                               eventTypeVersion: Int = 1,
                               uuid: UUID,
                               createdAt: JodaDateTime,
                               message: JsObject,
                               dnsName: Option[String] = None)


// This should be removed. Only the URL should be used
case class ExternalEventServerConfig(host: String, port: Int) {
  def toUrl(): URL = new URL(s"http://$host:$port")
}

// Request to create a System Status bundle
case class TechSupportSystemStatusRecord(name: String, comment: String)

// Request to create a Job (any job type is supported) bundle
case class TechSupportJobRecord(name: String, comment: String, jobId: Int)
