package com.pacbio.secondary.smrtlink.database

import java.nio.file.{Path, Paths}
import java.util.UUID

import com.pacbio.secondary.smrtlink.time.PacBioDateTimeDatabaseFormat
import com.pacbio.secondary.smrtlink.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{
  EngineJob,
  JobEvent,
  JobTask
}
import com.pacbio.secondary.smrtlink.models._
import com.pacificbiosciences.pacbiobasedatamodel.{
  SupportedAcquisitionStates,
  SupportedRunStates
}
import org.joda.time.{DateTime => JodaDateTime}

import slick.driver.PostgresDriver.api._

object TableModels extends PacBioDateTimeDatabaseFormat {

  implicit val jobStateType =
    MappedColumnType.base[AnalysisJobStates.JobStates, String](
      { s =>
        s.toString
      }, { s =>
        AnalysisJobStates.toState(s).getOrElse(AnalysisJobStates.UNKNOWN)
      }
    )

  /**
    * Encapsulates status events sent from EngineJob during the runtime of the job. It does not strictly enforce
    * a statemachine and is loose "log" of event state changes.
    *
    * Future iterations should leverage a state machine to avoid nonsensical state changes, e.g.,
    * Running -> Failed -> Running -> Completed.
    *
    * This overlaps to some degree with JobTasks which are sub units of work from
    * the job.
    *
    * @param tag
    */
  class JobEventsT(tag: Tag) extends Table[JobEvent](tag, "job_events") {

    def id: Rep[UUID] = column[UUID]("job_event_id", O.PrimaryKey)

    def eventTypeId: Rep[String] = column[String]("event_type_id")

    def state: Rep[AnalysisJobStates.JobStates] =
      column[AnalysisJobStates.JobStates]("state")

    def jobId: Rep[Int] = column[Int]("job_id")

    def message: Rep[String] = column[String]("message")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)

    def jobJoin = engineJobs.filter(_.id === jobId)

    def * =
      (id, jobId, state, message, createdAt, eventTypeId) <> (JobEvent.tupled, JobEvent.unapply)

    def idx = index("job_events_job_id", jobId)
  }

  /**
    * JobTask is a subcomputational unit of EngineJob. This can be used to propagate task level details such
    * as detailed error message or state.
    *
    * Note, the JobStates in pbsmrtpipe are NOT (?) the same enum as the AnalysisJobStates.
    *
    * @param tag
    */
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
    def errorMessage: Rep[Option[String]] =
      column[Option[String]]("error_message")

    def * =
      (uuid,
       jobId,
       taskId,
       taskTypeId,
       name,
       state,
       createdAt,
       updatedAt,
       errorMessage) <> (JobTask.tupled, JobTask.unapply)

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)

    def jobJoin = engineJobs.filter(_.id === jobId)

  }

  /**
    * Core computational unit of SL Services. Contains metadata of the job, such as name, created at and
    * description.
    *
    * Jobs are polymorphic in "jsonSettings".
    *
    * Given a specific version of SL and jobTypeId, this should map to a well-defined schema from the Job Options.
    *
    *
    * @param tag
    */
  class EngineJobsT(tag: Tag) extends Table[EngineJob](tag, "engine_jobs") {

    def id: Rep[Int] = column[Int]("job_id", O.PrimaryKey, O.AutoInc)

    def uuid: Rep[UUID] = column[UUID]("uuid")

    // This should have been description, but keeping with the Underlying EngineJob data model and
    // avoiding serialization backward compatibility issues
    def comment: Rep[String] = column[String]("comment")

    def name: Rep[String] = column[String]("name")

    def state: Rep[AnalysisJobStates.JobStates] =
      column[AnalysisJobStates.JobStates]("state")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    // This should be a foreign key into a new table
    def jobTypeId: Rep[String] = column[String]("job_type_id")

    def path: Rep[String] =
      column[String]("path", O.Length(500, varying = true))

    // This should be stored as JSON within slick-pg
    // https://github.com/tminglei/slick-pg
    def jsonSettings: Rep[String] = column[String]("json_settings")

    def createdBy: Rep[Option[String]] = column[Option[String]]("created_by")

    def createdByEmail: Rep[Option[String]] =
      column[Option[String]]("created_by_email")

    def smrtLinkVersion: Rep[Option[String]] =
      column[Option[String]]("smrtlink_version")

    def isActive: Rep[Boolean] = column[Boolean]("is_active")

    // Optional Error Message
    def errorMessage: Rep[Option[String]] =
      column[Option[String]]("error_message")

    def projectId: Rep[Int] = column[Int]("project_id")

    def isMultiJob: Rep[Boolean] =
      column[Boolean]("is_multi_job", O.Default(false))

    def workflow: Rep[String] =
      column[String]("json_workflow", O.Default("{}"))

    def parentMultiJobId: Rep[Option[Int]] =
      column[Option[Int]]("parent_multi_job_id", O.Default(None))

    def findByUUID(uuid: UUID) = engineJobs.filter(_.uuid === uuid)

    def findByState(state: AnalysisJobStates.JobStates) =
      engineJobs.filter(_.state === state)

    def findById(i: Int) = engineJobs.filter(_.id === i)

    def * =
      (id,
       uuid,
       name,
       comment,
       createdAt,
       updatedAt,
       state,
       jobTypeId,
       path,
       jsonSettings,
       createdBy,
       createdByEmail,
       smrtLinkVersion,
       isActive,
       errorMessage,
       projectId,
       isMultiJob,
       workflow,
       parentMultiJobId) <> (EngineJob.tupled, EngineJob.unapply)

    def uuidIdx = index("engine_jobs_uuid", uuid, unique = true)

    def typeIdx = index("engine_jobs_job_type", jobTypeId)

    def projectIdFK = foreignKey("project_id_fk", projectId, projects)(_.id)
  }

  implicit val projectStateType =
    MappedColumnType.base[ProjectState.ProjectState, String](
      { s =>
        s.toString
      }, { s =>
        ProjectState.fromString(s)
      }
    )
  implicit val projectUserRoleType =
    MappedColumnType.base[ProjectUserRole.ProjectUserRole, String](
      { r =>
        r.toString
      }, { r =>
        ProjectUserRole.fromString(r)
      }
    )

  /**
    * Container for grouping EngineJob(s) to user(s) with metadata.
    *
    * @param tag
    */
  class ProjectsT(tag: Tag) extends Table[Project](tag, "projects") {

    def id: Rep[Int] = column[Int]("project_id", O.PrimaryKey, O.AutoInc)

    def name: Rep[String] = column[String]("name")

    //Marketing request that the Project name must be unique
    def nameIdx = index("project_name", name, unique = true)

    def description: Rep[String] = column[String]("description")

    def state: Rep[ProjectState.ProjectState] =
      column[ProjectState.ProjectState]("state")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    def isActive: Rep[Boolean] = column[Boolean]("is_active")

    def grantRoleToAll: Rep[Option[ProjectUserRole.ProjectUserRole]] =
      column[Option[ProjectUserRole.ProjectUserRole]]("grant_role_to_all")

    def * =
      (id,
       name,
       description,
       state,
       createdAt,
       updatedAt,
       isActive,
       grantRoleToAll) <> (Project.tupled, Project.unapply)
  }

  class ProjectsUsersT(tag: Tag)
      extends Table[ProjectUser](tag, "projects_users") {
    def projectId: Rep[Int] = column[Int]("project_id")

    def login: Rep[String] = column[String]("login")

    def role: Rep[ProjectUserRole.ProjectUserRole] =
      column[ProjectUserRole.ProjectUserRole]("role")

    def projectFK = foreignKey("project_fk", projectId, projects)(a => a.id)

    def * =
      (projectId, login, role) <> (ProjectUser.tupled, ProjectUser.unapply)

    def loginIdx = index("projects_users_login", login)

    def projectIdIdx = index("projects_users_project_id", projectId)
  }

  abstract class IdAbleTable[T](tag: Tag, tableName: String)
      extends Table[T](tag, tableName) {
    def id: Rep[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)

    def uuid: Rep[UUID] = column[UUID]("uuid")
  }

  class DataSetTypesT(tag: Tag)
      extends Table[ServiceDataSetMetaType](tag, "pacbio_dataset_metatypes") {

    def id: Rep[String] = column[String]("dataset_type_id", O.PrimaryKey)

    def idx = index("index_id", id, unique = true)

    def name: Rep[String] = column[String]("name")

    def description: Rep[String] = column[String]("description")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    def shortName: Rep[String] = column[String]("short_name")

    def * =
      (id, name, description, createdAt, updatedAt, shortName) <> (ServiceDataSetMetaType.tupled, ServiceDataSetMetaType.unapply)
  }

  /*
  Table to capture the DataSet Entry points of a EngineJob

  - Job has many datasets
  - DataSet may belong to one or many jobs
   */
  class EngineJobDataSetT(tag: Tag)
      extends Table[EngineJobEntryPoint](tag, "engine_jobs_datasets") {

    def jobId: Rep[Int] = column[Int]("job_id")
    def datasetUUID: Rep[UUID] = column[UUID]("dataset_uuid")
    def datasetType: Rep[String] = column[String]("dataset_type")
    def * =
      (jobId, datasetUUID, datasetType) <> (EngineJobEntryPoint.tupled, EngineJobEntryPoint.unapply)
    def idx = index("engine_jobs_datasets_job_id", jobId)
  }

  /**
    * Base Metadata for all PacBio DataSets
    *
    * For each Subclass of this, consult the XSD for details on the core fields of the DataSet type.
    *
    * @param tag
    */
  class DataSetMetaT(tag: Tag)
      extends IdAbleTable[DataSetMetaDataSet](tag, "dataset_metadata") {

    def name: Rep[String] = column[String]("name")

    def path: Rep[String] =
      column[String]("path", O.Length(500, varying = true))

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    def numRecords: Rep[Long] = column[Long]("num_records")

    def totalLength: Rep[Long] = column[Long]("total_length")

    // Clarify this. This should be a comma separated value? Should this be removed until actually needed?
    // Should be done with an array/list natively in postgres?
    def tags: Rep[String] = column[String]("tags")

    // Version of the dataset schema spec? This isn't completely clear. Consult the PacBioFileFormat spec for clarification.
    def version: Rep[String] = column[String]("version")

    def comments: Rep[String] = column[String]("comments")

    def md5: Rep[String] = column[String]("md5")

    def createdBy: Rep[Option[String]] = column[Option[String]]("user_id")

    def jobId: Rep[Int] = column[Int]("job_id")

    def projectId: Rep[Int] = column[Int]("project_id")

    def isActive: Rep[Boolean] = column[Boolean]("is_active")

    def parentUuid: Rep[Option[UUID]] =
      column[Option[UUID]]("parent_uuid")

    def * =
      (id,
       uuid,
       name,
       path,
       createdAt,
       updatedAt,
       numRecords,
       totalLength,
       tags,
       version,
       comments,
       md5,
       createdBy,
       jobId,
       projectId,
       isActive,
       parentUuid) <> (DataSetMetaDataSet.tupled, DataSetMetaDataSet.unapply)

    def uuidIdx = index("dataset_metadata_uuid", uuid, unique = true)

    def projectIdIdx = index("dataset_metadata_project_id", projectId)
  }

  class SubreadDataSetT(tag: Tag)
      extends IdAbleTable[SubreadServiceSet](tag, "dataset_subreads") {

    def cellId: Rep[String] = column[String]("cell_id")

    def metadataContextId: Rep[String] = column[String]("metadata_context_id")

    def wellSampleName: Rep[String] = column[String]("well_sample_name")

    def wellName: Rep[String] = column[String]("well_name")

    def bioSampleName: Rep[String] = column[String]("bio_sample_name")

    def cellIndex: Rep[Int] = column[Int]("cell_index")

    def instrumentId: Rep[String] = column[String]("instrument_id")

    def instrumentName: Rep[String] = column[String]("instrument_name")

    def runName: Rep[String] = column[String]("run_name")

    def instrumentControlVersion: Rep[String] =
      column[String]("instrument_control_version")

    def dnaBarcodeName: Rep[Option[String]] =
      column[Option[String]]("dna_barcode_name")

    def * =
      (id,
       uuid,
       cellId,
       metadataContextId,
       wellSampleName,
       wellName,
       bioSampleName,
       cellIndex,
       instrumentId,
       instrumentName,
       runName,
       instrumentControlVersion,
       dnaBarcodeName) <> (SubreadServiceSet.tupled, SubreadServiceSet.unapply)
  }

  class HdfSubreadDataSetT(tag: Tag)
      extends IdAbleTable[HdfSubreadServiceSet](tag, "dataset_hdfsubreads") {

    def cellId: Rep[String] = column[String]("cell_id")

    def metadataContextId: Rep[String] = column[String]("metadata_context_id")

    def wellSampleName: Rep[String] = column[String]("well_sample_name")

    def wellName: Rep[String] = column[String]("well_name")

    def bioSampleName: Rep[String] = column[String]("bio_sample_name")

    def cellIndex: Rep[Int] = column[Int]("cell_index")

    def instrumentId: Rep[String] = column[String]("instrument_id")

    def instrumentName: Rep[String] = column[String]("instrument_name")

    def runName: Rep[String] = column[String]("run_name")

    def instrumentControlVersion: Rep[String] =
      column[String]("instrument_control_version")

    def * =
      (id,
       uuid,
       cellId,
       metadataContextId,
       wellSampleName,
       wellName,
       bioSampleName,
       cellIndex,
       instrumentId,
       instrumentName,
       runName,
       instrumentControlVersion) <> (HdfSubreadServiceSet.tupled, HdfSubreadServiceSet.unapply)
  }

  class ReferenceDataSetT(tag: Tag)
      extends IdAbleTable[ReferenceServiceSet](tag, "dataset_references") {

    def ploidy: Rep[String] = column[String]("ploidy")

    def organism: Rep[String] = column[String]("organism")

    def * =
      (id, uuid, ploidy, organism) <> (ReferenceServiceSet.tupled, ReferenceServiceSet.unapply)
  }

  class GmapReferenceDataSetT(tag: Tag)
      extends IdAbleTable[GmapReferenceServiceSet](tag,
                                                   "dataset_gmapreferences") {

    def ploidy: Rep[String] = column[String]("ploidy")

    def organism: Rep[String] = column[String]("organism")

    def * =
      (id, uuid, ploidy, organism) <> (GmapReferenceServiceSet.tupled, GmapReferenceServiceSet.unapply)
  }

  class AlignmentDataSetT(tag: Tag)
      extends IdAbleTable[AlignmentServiceSet](tag, "datasets_alignments") {
    def * =
      (id, uuid) <> (AlignmentServiceSet.tupled, AlignmentServiceSet.unapply)
  }

  class BarcodeDataSetT(tag: Tag)
      extends IdAbleTable[BarcodeServiceSet](tag, "datasets_barcodes") {
    def * = (id, uuid) <> (BarcodeServiceSet.tupled, BarcodeServiceSet.unapply)
  }

  class CCSreadDataSetT(tag: Tag)
      extends IdAbleTable[ConsensusReadServiceSet](tag, "datasets_ccsreads") {
    def * =
      (id, uuid) <> (ConsensusReadServiceSet.tupled, ConsensusReadServiceSet.unapply)
  }

  class ConsensusAlignmentDataSetT(tag: Tag)
      extends IdAbleTable[ConsensusAlignmentServiceSet](
        tag,
        "datasets_ccsalignments") {
    def * =
      (id, uuid) <> (ConsensusAlignmentServiceSet.tupled, ConsensusAlignmentServiceSet.unapply)
  }

  class ContigDataSetT(tag: Tag)
      extends IdAbleTable[ContigServiceSet](tag, "datasets_contigs") {
    def * = (id, uuid) <> (ContigServiceSet.tupled, ContigServiceSet.unapply)
  }

  /**
    * The general metadata container for DataStoreFile(s) produced by an EngineJob.
    *
    * A "DataStore" is a list of DataStoreFile(s) with a sourceId that is unique within the Job.
    *
    *
    * @param tag
    */
  class PacBioDataStoreFileT(tag: Tag)
      extends Table[DataStoreServiceFile](tag, "datastore_files") {
    def uuid: Rep[UUID] = column[UUID]("uuid", O.PrimaryKey)

    def fileTypeId: Rep[String] = column[String]("file_type_id")

    def sourceId: Rep[String] = column[String]("source_id")

    def fileSize: Rep[Long] = column[Long]("file_size")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def modifiedAt: Rep[JodaDateTime] = column[JodaDateTime]("modified_at")

    def importedAt: Rep[JodaDateTime] = column[JodaDateTime]("imported_at")

    def path: Rep[String] =
      column[String]("path", O.Length(500, varying = true))

    // job id output datastore. Perhaps need to define input for jobs that have datastore's as input
    // This needs to be rethought.
    def jobId: Rep[Int] = column[Int]("job_id")

    def jobUUID: Rep[UUID] = column[UUID]("job_uuid")

    def name: Rep[String] = column[String]("name")

    def description: Rep[String] = column[String]("description")

    def isActive: Rep[Boolean] = column[Boolean]("is_active")

    def * =
      (uuid,
       fileTypeId,
       sourceId,
       fileSize,
       createdAt,
       modifiedAt,
       importedAt,
       path,
       jobId,
       jobUUID,
       name,
       description,
       isActive) <> (DataStoreServiceFile.tupled, DataStoreServiceFile.unapply)

    def uuidIdx = index("datastore_files_uuid", uuid)

    def jobIdIdx = index("datastore_files_job_id", jobId)

    def jobUuidIdx = index("datastore_files_job_uuid", jobUUID)
  }

  implicit val runStatusType =
    MappedColumnType.base[SupportedRunStates, String](
      { s =>
        s.value()
      }, { s =>
        SupportedRunStates.fromValue(s)
      }
    )
  class RunSummariesT(tag: Tag)
      extends Table[RunSummary](tag, "run_summaries") {

    def uniqueId: Rep[UUID] = column[UUID]("unique_id", O.PrimaryKey)

    def name: Rep[String] = column[String]("name")

    def summary: Rep[Option[String]] = column[Option[String]]("summary")

    def createdBy: Rep[Option[String]] = column[Option[String]]("created_by")

    def createdAt: Rep[Option[JodaDateTime]] =
      column[Option[JodaDateTime]]("created_at")

    def startedAt: Rep[Option[JodaDateTime]] =
      column[Option[JodaDateTime]]("started_at")

    def transfersCompletedAt: Rep[Option[JodaDateTime]] =
      column[Option[JodaDateTime]]("transfers_completed_at")

    def completedAt: Rep[Option[JodaDateTime]] =
      column[Option[JodaDateTime]]("completed_at")

    def status: Rep[SupportedRunStates] = column[SupportedRunStates]("status")

    def totalCells: Rep[Int] = column[Int]("total_cells")

    def numCellsCompleted: Rep[Int] = column[Int]("num_cells_completed")

    def numCellsFailed: Rep[Int] = column[Int]("num_cells_failed")

    def instrumentName: Rep[Option[String]] =
      column[Option[String]]("instrument_name")

    def instrumentSerialNumber: Rep[Option[String]] =
      column[Option[String]]("instrument_serial_number")

    def instrumentSwVersion: Rep[Option[String]] =
      column[Option[String]]("instrument_sw_version")

    def primaryAnalysisSwVersion: Rep[Option[String]] =
      column[Option[String]]("primary_analysis_sw_version")

    def chemistrySwVersion: Rep[Option[String]] =
      column[Option[String]]("chemistry_sw_version")

    def context: Rep[Option[String]] = column[Option[String]]("context")

    def terminationInfo: Rep[Option[String]] =
      column[Option[String]]("termination_info")

    def reserved: Rep[Boolean] = column[Boolean]("reserved")

    def * =
      (uniqueId,
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
       chemistrySwVersion,
       context,
       terminationInfo,
       reserved) <> (RunSummary.tupled, RunSummary.unapply)
  }

  case class DataModelAndUniqueId(dataModel: String, uniqueId: UUID)
  class DataModelsT(tag: Tag)
      extends Table[DataModelAndUniqueId](tag, "pb_data_models") {
    def uniqueId: Rep[UUID] = column[UUID]("unique_id", O.PrimaryKey)

    def dataModel: Rep[String] =
      column[String]("data_model", O.SqlType("TEXT"))

    def * =
      (dataModel, uniqueId) <> (DataModelAndUniqueId.tupled, DataModelAndUniqueId.unapply)

    def summary = foreignKey("summary_fk", uniqueId, runSummaries)(_.uniqueId)
  }

  implicit val pathType =
    MappedColumnType.base[Path, String](_.toString, Paths.get(_))
  implicit val collectionStatusType =
    MappedColumnType.base[SupportedAcquisitionStates, String](
      _.value(),
      SupportedAcquisitionStates.fromValue)
  class CollectionMetadataT(tag: Tag)
      extends Table[CollectionMetadata](tag, "collection_metadata") {
    def runId: Rep[UUID] = column[UUID]("run_id")
    def run = foreignKey("run_fk", runId, runSummaries)(_.uniqueId)

    def uniqueId: Rep[UUID] = column[UUID]("unique_id", O.PrimaryKey)

    def well: Rep[String] = column[String]("well")

    def name: Rep[String] = column[String]("name")

    def summary: Rep[Option[String]] = column[Option[String]]("column")

    def context: Rep[Option[String]] = column[Option[String]]("context")

    def collectionPathUri: Rep[Option[Path]] =
      column[Option[Path]]("collection_path_uri")

    def status: Rep[SupportedAcquisitionStates] =
      column[SupportedAcquisitionStates]("status")

    def instrumentId: Rep[Option[String]] =
      column[Option[String]]("instrument_id")

    def instrumentName: Rep[Option[String]] =
      column[Option[String]]("instrument_name")

    def movieMinutes: Rep[Double] = column[Double]("movie_minutes")

    def createdBy: Rep[Option[String]] = column[Option[String]]("created_by")

    def startedAt: Rep[Option[JodaDateTime]] =
      column[Option[JodaDateTime]]("started_at")

    def completedAt: Rep[Option[JodaDateTime]] =
      column[Option[JodaDateTime]]("completed_at")

    def terminationInfo: Rep[Option[String]] =
      column[Option[String]]("termination_info")

    def * =
      (runId,
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
       terminationInfo) <> (CollectionMetadata.tupled, CollectionMetadata.unapply)

    def idx = index("collection_metadata_run_id", runId)
  }

  class SampleT(tag: Tag) extends Table[Sample](tag, "SAMPLE") {

    def details: Rep[String] = column[String]("DETAILS")

    def uniqueId: Rep[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    def name: Rep[String] = column[String]("NAME")

    def createdBy: Rep[String] = column[String]("CREATED_BY")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("CREATED_AT")

    def * =
      (details, uniqueId, name, createdBy, createdAt) <> (Sample.tupled, Sample.unapply)
  }

  /**
    * This is more wrong than it is correct. It's mixing up the System Config with the fundamental Message that
    * is (optionally) sent to back to Pacbio.
    *
    * At a minimum, there needs to be SystemConfig table that stores the current system settings, such as configuration
    * of the metrics to be sent back (enable_install_metrics, enable_job_metrics).
    *
    * Even if this is a snapshot of when the Eula is accepted, this is not clear what the options or OS version are
    * consistent with the current state or system configuration.
    *
    * For example, if the user accepts the Eula at T0, then changes the system config, should this be overwritten?
    * It seems like this should not be update-able after the acceptance of the Eula as been performed.
    *
    * @param tag
    */
  class EulaRecordT(tag: Tag) extends Table[EulaRecord](tag, "eula_record") {
    def user: Rep[String] = column[String]("user")
    def acceptedAt: Rep[JodaDateTime] = column[JodaDateTime]("accepted_at")
    def smrtlinkVersion: Rep[String] =
      column[String]("smrtlink_version", O.PrimaryKey)
    def enableInstallMetrics: Rep[Boolean] =
      column[Boolean]("enable_install_metrics")
    def enableJobMetrics: Rep[Boolean] = column[Boolean]("enable_job_metrics")
    def osVersion: Rep[String] = column[String]("os_version")
    def * =
      (user,
       acceptedAt,
       smrtlinkVersion,
       osVersion,
       enableInstallMetrics,
       enableJobMetrics) <> (EulaRecord.tupled, EulaRecord.unapply)
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
    eulas
  )

  lazy val runTables: Set[SlickTable] =
    Set(runSummaries, dataModels, collectionMetadata, samples)

  lazy val allTables: Set[SlickTable] = serviceTables ++ runTables

  lazy val schema = allTables.map(_.schema).reduce(_ ++ _)
}
