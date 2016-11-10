package com.pacbio.secondary.smrtlink.models

import java.nio.file.{Path, Paths}
import java.util.UUID

import com.pacificbiosciences.pacbiobasedatamodel.{SupportedRunStates, SupportedAcquisitionStates}
import org.joda.time.{DateTime => JodaDateTime}

import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes._

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


// Need to find a better way to do this
case class PacBioSchema(id: String, content: String)


// Keep everything as strings. Let pbsmrtpipe sort it out
trait ServiceTaskOptionBase {
  type In
  val id: String
  val optionTypeId: String
  val value: In
}

trait ServiceTaskStrOptionBase extends ServiceTaskOptionBase{ type In = String }
trait ServiceTaskIntOptionBase extends ServiceTaskOptionBase{ type In = Int }
trait ServiceTaskBooleanOptionBase extends ServiceTaskOptionBase{ type In = Boolean }
trait ServiceTaskDoubleOptionBase extends ServiceTaskOptionBase{ type In = Double }
trait ServiceTaskFloatOptionBase extends ServiceTaskOptionBase{ type In = Float }

case class ServiceTaskStrOption(id: String, value: String, optionTypeId: String) extends ServiceTaskStrOptionBase
case class ServiceTaskIntOption(id: String, value: Int, optionTypeId: String) extends ServiceTaskIntOptionBase
case class ServiceTaskBooleanOption(id: String, value: Boolean, optionTypeId: String) extends ServiceTaskBooleanOptionBase
case class ServiceTaskDoubleOption(id: String, value: Double, optionTypeId: String) extends ServiceTaskDoubleOptionBase
case class ServiceTaskFloatOption(id: String, value: Float, optionTypeId: String) extends ServiceTaskFloatOptionBase

// "Resolvable" Service Job Options. These will get transformed into PbSmrtPipeOptions
// These are also used by the mock-pbsmrtpipe job options
case class PbSmrtPipeServiceOptions(
    name: String,
    pipelineId: String,
    entryPoints: Seq[BoundServiceEntryPoint],
    taskOptions: Seq[ServiceTaskOptionBase],
    workflowOptions: Seq[ServiceTaskOptionBase])


// New DataSet Service Models
trait UniqueIdAble {
  val id: Int
  val uuid: UUID
}

trait ProjectAble {
  val userId: Int
  val jobId: Int
  val projectId: Int
  val isActive: Boolean
}

case class DataSetMetaDataSet(id: Int, uuid: UUID, name: String, path: String, createdAt: JodaDateTime, updatedAt: JodaDateTime, numRecords: Long, totalLength: Long, tags: String, version: String, comments: String, md5: String, userId: Int, jobId: Int, projectId: Int, isActive: Boolean) extends UniqueIdAble with ProjectAble

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

//DataSet
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
  val userId: Int
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
    userId: Int,
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
    userId: Int,
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
    userId: Int,
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
    userId: Int,
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
    userId: Int,
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
    userId: Int,
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
    userId: Int,
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
    userId: Int,
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
    userId: Int,
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

// We have a simpler (cheaper to query) project case class for the API
// response that lists many projects,
case class Project(
    id: Int,
    name: String,
    description: String,
    state: String,
    createdAt: JodaDateTime,
    updatedAt: JodaDateTime,
    // isActive: false if the project has been deleted, true otherwise
    isActive: Boolean) {

  def makeFull(datasets: Seq[DataSetMetaDataSet], members: Seq[ProjectUserResponse]): FullProject =
    FullProject(
      id,
      name,
      description,
      state,
      createdAt,
      updatedAt,
      isActive,
      datasets,
      members)
}

// and a more detailed case class for the API responses involving
// individual projects.
case class FullProject(
    id: Int,
    name: String,
    description: String,
    state: String,
    createdAt: JodaDateTime,
    updatedAt: JodaDateTime,
    isActive: Boolean,
    datasets: Seq[DataSetMetaDataSet],
    members: Seq[ProjectUserResponse]) {

  def asRequest: ProjectRequest =
    ProjectRequest(
      name,
      description,
      Some(state),
      Some(datasets.map(ds => RequestId(ds.id))),
      Some(members.map(u => ProjectRequestUser(RequestUser(u.login), u.role))))
}

// the json structures required in client requests are a subset of the
// FullProject structure (the FullProject is a valid request, but many
// fields are optional in requests).
case class ProjectRequest(
    name: String,
    description: String,
    // if any of these are None in a PUT request, the corresponding
    // value will stay the same (i.e., the update will be skipped).
    state: Option[String],
    datasets: Option[Seq[RequestId]],
    members: Option[Seq[ProjectRequestUser]]) {

  // this returns a copy!
  def appendDataSet(dsId: Int): ProjectRequest = {
    val allDatasets = datasets.map(ds => ds ++ Seq(RequestId(dsId))).getOrElse(Seq(RequestId(dsId)))
    this.copy(datasets = Some(allDatasets))
  }
}

case class RequestId(id: Int)
case class RequestUser(login: String)

object ProjectUserRole {
  sealed trait ProjectUserRole
  case object OWNER extends ProjectUserRole
  case object CAN_EDIT extends ProjectUserRole
  case object CAN_VIEW extends ProjectUserRole

  def fromString(r: String): ProjectUserRole = {
    Seq(OWNER, CAN_EDIT, CAN_VIEW)
      .find(_.toString == r)
      .getOrElse(throw new IllegalArgumentException(s"Unknown project user role $r, acceptable values are $OWNER, $CAN_EDIT, $CAN_VIEW"))
  }
}
case class ProjectRequestUser(user: RequestUser, role: ProjectUserRole.ProjectUserRole)
case class ProjectUser(projectId: Int, login: String, role: ProjectUserRole.ProjectUserRole)

case class ProjectUserResponse(login: String, role: ProjectUserRole.ProjectUserRole)

case class UserProjectResponse(role: Option[ProjectUserRole.ProjectUserRole], project: Project)
 
case class ProjectDatasetResponse(project: Project, dataset: DataSetMetaDataSet, role: Option[ProjectUserRole.ProjectUserRole])


case class EulaRecord(user: String, acceptedAt: JodaDateTime, smrtlinkVersion: String, enableInstallMetrics: Boolean, enableJobMetrics: Boolean)
