package com.pacbio.secondary.smrtlink.database

import java.nio.file.{Path, Paths}
import java.util.UUID

import com.pacbio.common.time.PacBioDateTimeDatabaseFormat
import com.pacbio.secondary.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.analysis.jobs.JobModels.{EngineJob, JobEvent, JobTask}
import com.pacbio.secondary.smrtlink.models._
import com.pacificbiosciences.pacbiobasedatamodel.{SupportedAcquisitionStates, SupportedRunStates}
import org.joda.time.{DateTime => JodaDateTime}

import slick.driver.PostgresDriver.api._
import slick.lifted.ProvenShape
// This must be added otherwise an PSQLException: ERROR: integer out of range will occur
import com.github.tototoshi.slick.PostgresJodaSupport._


object TableModels extends PacBioDateTimeDatabaseFormat {

  implicit val jobStateType = MappedColumnType.base[AnalysisJobStates.JobStates, String](
    {s => s.toString},
    {s => AnalysisJobStates.toState(s).getOrElse(AnalysisJobStates.UNKNOWN)}
  )

  class JobEventsT(tag: Tag) extends Table[JobEvent](tag, "job_events") {

    def id: Rep[UUID] = column[UUID]("job_event_id", O.PrimaryKey)

    def eventTypeId: Rep[String] = column[String]("event_type_id")

    def state: Rep[AnalysisJobStates.JobStates] = column[AnalysisJobStates.JobStates]("state")

    def jobId: Rep[Int] = column[Int]("job_id")

    def message: Rep[String] = column[String]("message")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)

    def jobJoin = engineJobs.filter(_.id === jobId)

    def * = (id, jobId, state, message, createdAt, eventTypeId) <> (JobEvent.tupled, JobEvent.unapply)

    def idx = index("job_events_job_id", jobId)
  }

  class JobTags(tag: Tag) extends Table[(Int, String)](tag, "job_tags") {
    def id: Rep[Int] = column[Int]("job_tag_id", O.PrimaryKey, O.AutoInc)

    def name: Rep[String] = column[String]("name")

    def * : ProvenShape[(Int, String)] = (id, name)
  }

  /**
   * Many-to-Many table for tags for Jobs
   * @param tag General Tags for Jobs
   */
  class JobsTags(tag: Tag) extends Table[(Int, Int)](tag, "jobs_tags") {
    def jobId: Rep[Int] = column[Int]("job_id")

    def tagId: Rep[Int] = column[Int]("job_tag_id")

    def * : ProvenShape[(Int, Int)] = (jobId, tagId)

    def jobJoin = engineJobs.filter(_.id === jobId)

    def jobTagFK = foreignKey("job_tag_fk", tagId, jobTags)(a => a.id)

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(b => b.id)
  }

  class JobTasks(tag: Tag) extends Table[JobTask](tag, "job_tasks") {

    def uuid: Rep[UUID] = column[UUID]("task_uuid", O.PrimaryKey)

    def idx = index("index_uuid", uuid, unique = true)

    def jobId: Rep[Int] = column[Int]("job_id")

    // Both the taskId and uuid should really be in the Resolved Tool Contract
    // This is only unique to the Job
    def taskId: Rep[String] = column[String]("task_id")

    // This is the ToolContract Id
    def taskTypeId: Rep[String] = column[String]("task_type_id")

    // Ideally, this should use the states defined here AnalysisJobStates.JobStates for tasks
    def state: Rep[String] = column[String]("task_state")

    // Display Name of the Task
    def name: Rep[String] = column[String]("task_name")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    // Should there be a runTime ? Because a task created, doesn't necessarily
    // mean the task has started to run. It can be waiting in the Scheduler (e.g., SGE) queue

    // Optional Error Message
    def errorMessage: Rep[Option[String]] = column[Option[String]]("error_message")

    def * = (uuid, jobId, taskId, taskTypeId, name, state, createdAt, updatedAt, errorMessage) <> (JobTask.tupled, JobTask.unapply)

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)

    def jobJoin = engineJobs.filter(_.id === jobId)


  }

  class EngineJobsT(tag: Tag) extends Table[EngineJob](tag, "engine_jobs") {

    def id: Rep[Int] = column[Int]("job_id", O.PrimaryKey, O.AutoInc)

    // FIXME. The process engine only uses UUID
    def uuid: Rep[UUID] = column[UUID]("uuid")

    // this is really comment
    def pipelineId: Rep[String] = column[String]("pipeline_id")

    def name: Rep[String] = column[String]("name")

    def state: Rep[AnalysisJobStates.JobStates] = column[AnalysisJobStates.JobStates]("state")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    // This should be a foreign key into a new table
    def jobTypeId: Rep[String] = column[String]("job_type_id")

    def path: Rep[String] = column[String]("path", O.Length(500, varying=true))

    def jsonSettings: Rep[String] = column[String]("json_settings")

    def createdBy: Rep[Option[String]] = column[Option[String]]("created_by")

    def smrtLinkVersion: Rep[Option[String]] = column[Option[String]]("smrtlink_version")

    def smrtLinkToolsVersion: Rep[Option[String]] = column[Option[String]]("smrtlink_tools_version")

    def isActive: Rep[Boolean] = column[Boolean]("is_active")

    // Optional Error Message
    def errorMessage: Rep[Option[String]] = column[Option[String]]("error_message")

    def findByUUID(uuid: UUID) = engineJobs.filter(_.uuid === uuid)

    def findById(i: Int) = engineJobs.filter(_.id === i)

    def * = (id, uuid, name, pipelineId, createdAt, updatedAt, state, jobTypeId, path, jsonSettings, createdBy, smrtLinkVersion, smrtLinkToolsVersion, isActive, errorMessage) <> (EngineJob.tupled, EngineJob.unapply)

    def uuidIdx = index("engine_jobs_uuid", uuid)

    def typeIdx = index("engine_jobs_job_type", jobTypeId)
  }

  class JobResultT(tag: Tag) extends Table[(Int, String)](tag, "job_results") {

    def id: Rep[Int] = column[Int]("job_result_id")

    def host: Rep[String] = column[String]("host_name")

    def jobId: Rep[Int] = column[Int]("job_id")

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)

    def * : ProvenShape[(Int, String)] = (id, host)
  }

  implicit val projectStateType = MappedColumnType.base[ProjectState.ProjectState, String](
    {s => s.toString},
    {s => ProjectState.fromString(s)}
  )
  class ProjectsT(tag: Tag) extends Table[Project](tag, "projects") {

    def id: Rep[Int] = column[Int]("project_id", O.PrimaryKey, O.AutoInc)

    def name: Rep[String] = column[String]("name")

    // Because slick does not support partial indexes, we index this table manually like so:
    // sqlu"create unique index project_name_unique on projects (name) where is_active;"

    def description: Rep[String] = column[String]("description")

    def state: Rep[ProjectState.ProjectState] = column[ProjectState.ProjectState]("state")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    def isActive: Rep[Boolean] = column[Boolean]("is_active")

    def * = (id, name, description, state, createdAt, updatedAt, isActive) <> (Project.tupled, Project.unapply)
  }

  implicit val projectUserRoleType = MappedColumnType.base[ProjectUserRole.ProjectUserRole, String](
    {r => r.toString},
    {r => ProjectUserRole.fromString(r)}
  )
  class ProjectsUsersT(tag: Tag) extends Table[ProjectUser](tag, "projects_users") {
    def projectId: Rep[Int] = column[Int]("project_id")

    def login: Rep[String] = column[String]("login")

    def role: Rep[ProjectUserRole.ProjectUserRole] = column[ProjectUserRole.ProjectUserRole]("role")

    def projectFK = foreignKey("project_fk", projectId, projects)(a => a.id)

    def * = (projectId, login, role) <> (ProjectUser.tupled, ProjectUser.unapply)

    def loginIdx = index("projects_users_login", login)

    def projectIdIdx = index("projects_users_project_id", projectId)
  }

  abstract class IdAbleTable[T](tag: Tag, tableName: String) extends Table[T](tag, tableName) {
    def id: Rep[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)

    def uuid: Rep[UUID] = column[UUID]("uuid")
  }

  class DataSetTypesT(tag: Tag) extends Table[ServiceDataSetMetaType](tag, "pacbio_dataset_metatypes") {

    def id: Rep[String] = column[String]("dataset_type_id", O.PrimaryKey)

    def idx = index("index_id", id, unique = true)

    def name: Rep[String] = column[String]("name")

    def description: Rep[String] = column[String]("description")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    def shortName: Rep[String] = column[String]("short_name")

    def * = (id, name, description, createdAt, updatedAt, shortName) <> (ServiceDataSetMetaType.tupled, ServiceDataSetMetaType.unapply)
  }

  /*
  Table to capture the DataSet Entry points of a EngineJob

  - Job has many datasets
  - DataSet may belong to one or many jobs
   */
  class EngineJobDataSetT(tag: Tag) extends Table[EngineJobEntryPoint](tag, "engine_jobs_datasets") {

    def jobId: Rep[Int] = column[Int]("job_id")
    def datasetUUID: Rep[UUID] = column[UUID]("dataset_uuid")
    def datasetType: Rep[String] = column[String]("dataset_type")
    def * = (jobId, datasetUUID, datasetType) <> (EngineJobEntryPoint.tupled, EngineJobEntryPoint.unapply)
    def idx = index("engine_jobs_datasets_job_id", jobId)
  }

  class DataSetMetaT(tag: Tag) extends IdAbleTable[DataSetMetaDataSet](tag, "dataset_metadata") {

    def name: Rep[String] = column[String]("name")

    def path: Rep[String] = column[String]("path", O.Length(500, varying=true))

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    def numRecords: Rep[Long] = column[Long]("num_records")

    def totalLength: Rep[Long] = column[Long]("total_length")

    def tags: Rep[String] = column[String]("tags")

    def version: Rep[String] = column[String]("version")

    def comments: Rep[String] = column[String]("comments")

    def md5: Rep[String] = column[String]("md5")

    def createdBy: Rep[Option[String]] = column[Option[String]]("user_id")

    def jobId: Rep[Int] = column[Int]("job_id")

    def projectId: Rep[Int] = column[Int]("project_id")

    def isActive: Rep[Boolean] = column[Boolean]("is_active")

    def * = (id, uuid, name, path, createdAt, updatedAt, numRecords, totalLength, tags, version, comments, md5, createdBy, jobId, projectId, isActive) <>(DataSetMetaDataSet.tupled, DataSetMetaDataSet.unapply)

    def uuidIdx = index("dataset_metadata_uuid", uuid)

    def projectIdIdx = index("dataset_metadata_project_id", projectId)
  }

  class SubreadDataSetT(tag: Tag) extends IdAbleTable[SubreadServiceSet](tag, "dataset_subreads") {

    def cellId: Rep[String] = column[String]("cell_id")

    def metadataContextId: Rep[String] = column[String]("metadata_context_id")

    def wellSampleName: Rep[String] = column[String]("well_sample_name")

    def wellName: Rep[String] = column[String]("well_name")

    def bioSampleName: Rep[String] = column[String]("bio_sample_name")

    def cellIndex: Rep[Int] = column[Int]("cell_index")

    def instrumentId: Rep[String] = column[String]("instrument_id")

    def instrumentName: Rep[String] = column[String]("instrument_name")

    def runName: Rep[String] = column[String]("run_name")

    def instrumentControlVersion: Rep[String] = column[String]("instrument_control_version")

    def * = (id, uuid, cellId, metadataContextId, wellSampleName, wellName, bioSampleName, cellIndex, instrumentId, instrumentName, runName, instrumentControlVersion) <>(SubreadServiceSet.tupled, SubreadServiceSet.unapply)
  }

  class HdfSubreadDataSetT(tag: Tag) extends IdAbleTable[HdfSubreadServiceSet](tag, "dataset_hdfsubreads") {

    def cellId: Rep[String] = column[String]("cell_id")

    def metadataContextId: Rep[String] = column[String]("metadata_context_id")

    def wellSampleName: Rep[String] = column[String]("well_sample_name")

    def wellName: Rep[String] = column[String]("well_name")

    def bioSampleName: Rep[String] = column[String]("bio_sample_name")

    def cellIndex: Rep[Int] = column[Int]("cell_index")

    def instrumentId: Rep[String] = column[String]("instrument_id")

    def instrumentName: Rep[String] = column[String]("instrument_name")

    def runName: Rep[String] = column[String]("run_name")

    def instrumentControlVersion: Rep[String] = column[String]("instrument_control_version")

    def * = (id, uuid, cellId, metadataContextId, wellSampleName, wellName, bioSampleName, cellIndex, instrumentId, instrumentName, runName, instrumentControlVersion) <>(HdfSubreadServiceSet.tupled, HdfSubreadServiceSet.unapply)
  }

  class ReferenceDataSetT(tag: Tag) extends IdAbleTable[ReferenceServiceSet](tag, "dataset_references") {

    def ploidy: Rep[String] = column[String]("ploidy")

    def organism: Rep[String] = column[String]("organism")

    def * = (id, uuid, ploidy, organism) <>(ReferenceServiceSet.tupled, ReferenceServiceSet.unapply)
  }

  class GmapReferenceDataSetT(tag: Tag) extends IdAbleTable[GmapReferenceServiceSet](tag, "dataset_gmapreferences") {

    def ploidy: Rep[String] = column[String]("ploidy")

    def organism: Rep[String] = column[String]("organism")

    def * = (id, uuid, ploidy, organism) <>(GmapReferenceServiceSet.tupled, GmapReferenceServiceSet.unapply)
  }

  class AlignmentDataSetT(tag: Tag) extends IdAbleTable[AlignmentServiceSet](tag, "datasets_alignments") {
    def * = (id, uuid) <>(AlignmentServiceSet.tupled, AlignmentServiceSet.unapply)
  }

  class BarcodeDataSetT(tag: Tag) extends IdAbleTable[BarcodeServiceSet](tag, "datasets_barcodes") {
    def * = (id, uuid) <>(BarcodeServiceSet.tupled, BarcodeServiceSet.unapply)
  }

  class CCSreadDataSetT(tag: Tag) extends IdAbleTable[ConsensusReadServiceSet](tag, "datasets_ccsreads") {
    def * = (id, uuid) <>(ConsensusReadServiceSet.tupled, ConsensusReadServiceSet.unapply)
  }

  class ConsensusAlignmentDataSetT(tag: Tag) extends IdAbleTable[ConsensusAlignmentServiceSet](tag, "datasets_ccsalignments") {
    def * = (id, uuid) <>(ConsensusAlignmentServiceSet.tupled, ConsensusAlignmentServiceSet.unapply)
  }

  class ContigDataSetT(tag: Tag) extends IdAbleTable[ContigServiceSet](tag, "datasets_contigs") {
    def * = (id, uuid) <>(ContigServiceSet.tupled, ContigServiceSet.unapply)
  }

  class PacBioDataStoreFileT(tag: Tag) extends Table[DataStoreServiceFile](tag, "datastore_files") {
    def uuid: Rep[UUID] = column[UUID]("uuid", O.PrimaryKey)

    def fileTypeId: Rep[String] = column[String]("file_type_id")

    def sourceId: Rep[String] = column[String]("source_id")

    def fileSize: Rep[Long] = column[Long]("file_size")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def modifiedAt: Rep[JodaDateTime] = column[JodaDateTime]("modified_at")

    def importedAt: Rep[JodaDateTime] = column[JodaDateTime]("imported_at")

    def path: Rep[String] = column[String]("path", O.Length(500, varying=true))

    // job id output datastore. Perhaps need to define input for jobs that have datastore's as input
    // This needs to be rethought.
    def jobId: Rep[Int] = column[Int]("job_id")

    def jobUUID: Rep[UUID] = column[UUID]("job_uuid")

    def name: Rep[String] = column[String]("name")

    def description: Rep[String] = column[String]("description")

    def isActive: Rep[Boolean] = column[Boolean]("is_active")

    def * = (uuid, fileTypeId, sourceId, fileSize, createdAt, modifiedAt, importedAt, path, jobId, jobUUID, name, description, isActive) <>(DataStoreServiceFile.tupled, DataStoreServiceFile.unapply)

    def uuidIdx = index("datastore_files_uuid", uuid)

    def jobIdIdx = index("datastore_files_job_id", jobId)

    def jobUuidIdx = index("datastore_files_job_uuid", jobUUID)
  }

  implicit val runStatusType = MappedColumnType.base[SupportedRunStates, String](
    {s => s.value()},
    {s => SupportedRunStates.fromValue(s)}
  )
  class RunSummariesT(tag: Tag) extends Table[RunSummary](tag, "RUN_SUMMARIES") {

    def uniqueId: Rep[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    def name: Rep[String] = column[String]("NAME")

    def summary: Rep[Option[String]] = column[Option[String]]("SUMMARY")

    def createdBy: Rep[Option[String]] = column[Option[String]]("CREATED_BY")

    def createdAt: Rep[Option[JodaDateTime]] = column[Option[JodaDateTime]]("CREATED_AT")

    def startedAt: Rep[Option[JodaDateTime]] = column[Option[JodaDateTime]]("STARTED_AT")

    def transfersCompletedAt: Rep[Option[JodaDateTime]] = column[Option[JodaDateTime]]("TRANSFERS_COMPLETED_AT")

    def completedAt: Rep[Option[JodaDateTime]] = column[Option[JodaDateTime]]("COMPLETED_AT")

    def status: Rep[SupportedRunStates] = column[SupportedRunStates]("STATUS")

    def totalCells: Rep[Int] = column[Int]("TOTAL_CELLS")

    def numCellsCompleted: Rep[Int] = column[Int]("NUM_CELLS_COMPLETED")

    def numCellsFailed: Rep[Int] = column[Int]("NUM_CELLS_FAILED")

    def instrumentName: Rep[Option[String]] = column[Option[String]]("INSTRUMENT_NAME")

    def instrumentSerialNumber: Rep[Option[String]] = column[Option[String]]("INSTRUMENT_SERIAL_NUMBER")

    def instrumentSwVersion: Rep[Option[String]] = column[Option[String]]("INSTRUMENT_SW_VERSION")

    def primaryAnalysisSwVersion: Rep[Option[String]] = column[Option[String]]("PRIMARY_ANALYSIS_SW_VERSION")

    def context: Rep[Option[String]] = column[Option[String]]("CONTEXT")

    def terminationInfo: Rep[Option[String]] = column[Option[String]]("TERMINATION_INFO")

    def reserved: Rep[Boolean] = column[Boolean]("RESERVED")

    def * = (
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
        reserved) <>(RunSummary.tupled, RunSummary.unapply)
  }

  case class DataModelAndUniqueId(dataModel: String, uniqueId: UUID)
  class DataModelsT(tag: Tag) extends Table[DataModelAndUniqueId](tag, "DATA_MODELS") {
    def uniqueId: Rep[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    def dataModel: Rep[String] = column[String]("DATA_MODEL", O.SqlType("TEXT"))

    def * = (dataModel, uniqueId) <> (DataModelAndUniqueId.tupled, DataModelAndUniqueId.unapply)

    def summary = foreignKey("SUMMARY_FK", uniqueId, runSummaries)(_.uniqueId)
  }

  implicit val pathType = MappedColumnType.base[Path, String](_.toString, Paths.get(_))
  implicit val collectionStatusType =
    MappedColumnType.base[SupportedAcquisitionStates, String](_.value(), SupportedAcquisitionStates.fromValue)
  class CollectionMetadataT(tag: Tag) extends Table[CollectionMetadata](tag, "COLLECTION_METADATA") {
    def runId: Rep[UUID] = column[UUID]("RUN_ID")
    def run = foreignKey("RUN_FK", runId, runSummaries)(_.uniqueId)

    def uniqueId: Rep[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    def well: Rep[String] = column[String]("WELL")

    def name: Rep[String] = column[String]("NAME")

    def summary: Rep[Option[String]] = column[Option[String]]("COLUMN")

    def context: Rep[Option[String]] = column[Option[String]]("CONTEXT")

    def collectionPathUri: Rep[Option[Path]] = column[Option[Path]]("COLLECTION_PATH_URI")

    def status: Rep[SupportedAcquisitionStates] = column[SupportedAcquisitionStates]("STATUS")

    def instrumentId: Rep[Option[String]] = column[Option[String]]("INSTRUMENT_ID")

    def instrumentName: Rep[Option[String]] = column[Option[String]]("INSTRUMENT_NAME")

    def movieMinutes: Rep[Double] = column[Double]("MOVIE_MINUTES")

    def createdBy: Rep[Option[String]] = column[Option[String]]("CREATED_BY")

    def startedAt: Rep[Option[JodaDateTime]] = column[Option[JodaDateTime]]("STARTED_AT")

    def completedAt: Rep[Option[JodaDateTime]] = column[Option[JodaDateTime]]("COMPLETED_AT")

    def terminationInfo: Rep[Option[String]] = column[Option[String]]("TERMINATION_INFO")

    def * = (
        runId,
        uniqueId,
        name,
        well,
        summary,
        context,
        collectionPathUri,
        status,
        instrumentId,
        instrumentName,
        movieMinutes,
        createdBy,
        startedAt,
        completedAt,
        terminationInfo) <>(CollectionMetadata.tupled, CollectionMetadata.unapply)

    def idx = index("collection_metadata_run_id", runId)
  }

  class SampleT(tag: Tag) extends Table[Sample](tag, "SAMPLE") {

    def details: Rep[String] = column[String]("DETAILS")

    def uniqueId: Rep[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    def name: Rep[String] = column[String]("NAME")

    def createdBy: Rep[String] = column[String]("CREATED_BY")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("CREATED_AT")

    def * = (details, uniqueId, name, createdBy, createdAt) <> (Sample.tupled, Sample.unapply)
  }

  class EulaRecordT(tag: Tag) extends Table[EulaRecord](tag, "eula_record") {
    def user: Rep[String] = column[String]("user")
    def acceptedAt: Rep[JodaDateTime] = column[JodaDateTime]("accepted_at")
    def smrtlinkVersion: Rep[String] = column[String]("smrtlink_version", O.PrimaryKey)
    def enableInstallMetrics: Rep[Boolean] = column[Boolean]("enable_install_metrics")
    def enableJobMetrics: Rep[Boolean] = column[Boolean]("enable_job_metrics")
    def osVersion: Rep[String] = column[String]("os_version")
    def * = (user, acceptedAt, smrtlinkVersion, osVersion, enableInstallMetrics, enableJobMetrics) <> (EulaRecord.tupled, EulaRecord.unapply)
  }

  // DataSet types
  lazy val dsMetaData2 = TableQuery[DataSetMetaT]
  lazy val dsSubread2 = TableQuery[SubreadDataSetT]
  lazy val dsHdfSubread2 = TableQuery[HdfSubreadDataSetT]
  lazy val dsReference2 = TableQuery[ReferenceDataSetT]
  lazy val dsAlignment2 = TableQuery[AlignmentDataSetT]
  lazy val dsBarcode2 = TableQuery[BarcodeDataSetT]
  lazy val dsCCSread2 = TableQuery[CCSreadDataSetT]
  lazy val dsGmapReference2 = TableQuery[GmapReferenceDataSetT]
  lazy val dsCCSAlignment2 = TableQuery[ConsensusAlignmentDataSetT]
  lazy val dsContig2 = TableQuery[ContigDataSetT]

  lazy val datastoreServiceFiles = TableQuery[PacBioDataStoreFileT]

  // Users and Projects
  lazy val projects = TableQuery[ProjectsT]
  lazy val projectsUsers = TableQuery[ProjectsUsersT]

  lazy val engineJobs = TableQuery[EngineJobsT]
  lazy val engineJobsDataSets = TableQuery[EngineJobDataSetT]
  lazy val jobEvents = TableQuery[JobEventsT]
  lazy val jobTags = TableQuery[JobTags]
  lazy val jobsTags = TableQuery[JobsTags]
  lazy val jobTasks = TableQuery[JobTasks]

  // DataSet types
  lazy val datasetMetaTypes = TableQuery[DataSetTypesT]

  // Runs
  lazy val runSummaries = TableQuery[RunSummariesT]
  lazy val dataModels = TableQuery[DataModelsT]
  lazy val collectionMetadata = TableQuery[CollectionMetadataT]

  // Samples
  lazy val samples = TableQuery[SampleT]

  // EULA
  lazy val eulas = TableQuery[EulaRecordT]

  final type SlickTable = TableQuery[_ <: Table[_]]

  lazy val serviceTables: Set[SlickTable] = Set(
    engineJobs,
    datasetMetaTypes,
    engineJobsDataSets,
    jobEvents,
    jobTags,
    jobsTags,
    jobTasks,
    projectsUsers,
    projects,
    dsMetaData2,
    dsSubread2,
    dsHdfSubread2,
    dsReference2,
    dsAlignment2,
    dsBarcode2,
    dsCCSread2,
    dsGmapReference2,
    dsCCSAlignment2,
    dsContig2,
    datastoreServiceFiles,
    eulas)

  lazy val runTables: Set[SlickTable] = Set(runSummaries, dataModels, collectionMetadata, samples)

  lazy val allTables: Set[SlickTable] = serviceTables ++ runTables

  lazy val schema = allTables.map(_.schema).reduce(_ ++ _)
}
