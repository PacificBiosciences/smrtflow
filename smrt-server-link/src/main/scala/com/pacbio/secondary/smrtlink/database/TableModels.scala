package com.pacbio.secondary.smrtlink.database

import java.util.UUID

import com.pacbio.common.time.PacBioDateTimeDatabaseFormat
import com.pacbio.secondary.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.analysis.jobs.JobModels.{EngineJob, JobEvent}
import com.pacbio.secondary.smrtlink.models._
import com.pacificbiosciences.pacbiobasedatamodel.{SupportedRunStates, SupportedAcquisitionStates}
import org.joda.time.{DateTime => JodaDateTime}

import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.lifted.ProvenShape


object TableModels extends PacBioDateTimeDatabaseFormat {

  class JobStatesT(tag: Tag) extends Table[(Int, String, String, JodaDateTime, JodaDateTime)](tag, "job_states") {

    def id: Column[Int] = column[Int]("job_state_id", O.PrimaryKey, O.AutoInc)

    def name: Column[String] = column[String]("name")

    def description: Column[String] = column[String]("description")

    def createdAt: Column[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Column[JodaDateTime] = column[JodaDateTime]("updated_at")

    def * : ProvenShape[(Int, String, String, JodaDateTime, JodaDateTime)] = (id, name, description, createdAt, updatedAt)

  }

  class JobEventsT(tag: Tag) extends Table[JobEvent](tag, "job_events") {

    def id: Column[UUID] = column[UUID]("job_event_id", O.PrimaryKey)

    def stateId: Column[Int] = column[Int]("state_id")

    def jobId: Column[Int] = column[Int]("job_id")

    def message: Column[String] = column[String]("message")

    def createdAt: Column[JodaDateTime] = column[JodaDateTime]("created_at")

    def stateFK = foreignKey("state_fk", stateId, jobStates)(_.id)

    def stateJoin = jobStates.filter(_.id === stateId)

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)

    def jobJoin = engineJobs.filter(_.id === jobId)

    def fromJobEvent(jobEvent: JobEvent): Option[(UUID, Int, Int, String, JodaDateTime)] =
      Some((jobEvent.eventId, jobEvent.jobId, jobEvent.state.stateId, jobEvent.message, jobEvent.createdAt))

    def intoJobEvent(tuple: (UUID, Int, Int, String, JodaDateTime)): JobEvent = {
      val state = AnalysisJobStates.intToState(tuple._3) getOrElse AnalysisJobStates.UNKNOWN
      JobEvent(tuple._1, tuple._2, state, tuple._4, tuple._5)
    }

    def * = (id, jobId, stateId, message, createdAt) <> (intoJobEvent, fromJobEvent)
  }

  class JobTags(tag: Tag) extends Table[(Int, String)](tag, "job_tags") {
    def id: Column[Int] = column[Int]("job_tag_id", O.PrimaryKey, O.AutoInc)

    def name: Column[String] = column[String]("name")

    def * : ProvenShape[(Int, String)] = (id, name)
  }

  /**
   * Many-to-Many table for tags for Jobs
   * @param tag General Tags for Jobs
   */
  class JobsTags(tag: Tag) extends Table[(Int, Int)](tag, "jobs_tags") {
    def jobId: Column[Int] = column[Int]("job_id")

    def tagId: Column[Int] = column[Int]("job_tag_id")

    def * : ProvenShape[(Int, Int)] = (jobId, tagId)

    def jobJoin = engineJobs.filter(_.id === jobId)

    def jobTagFK = foreignKey("job_tag_fk", tagId, jobTags)(a => a.id)

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(b => b.id)
  }


  class EngineJobsT(tag: Tag) extends Table[EngineJob](tag, "engine_jobs") {

    def id: Column[Int] = column[Int]("job_id", O.PrimaryKey, O.AutoInc)

    // FIXME. The process engine only uses UUID
    def uuid: Column[UUID] = column[UUID]("uuid")

    // this is really comment
    def pipelineId: Column[String] = column[String]("pipeline_id")

    def name: Column[String] = column[String]("name")

    def stateId: Column[Int] = column[Int]("state_id")

    def createdAt: Column[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Column[JodaDateTime] = column[JodaDateTime]("updated_at")

    def stateFK = foreignKey("state_fk", stateId, jobStates)(_.id)

    def stateJoin = jobStates.filter(_.id === stateId)

    // This should be a foreign key into a new table
    def jobTypeId: Column[String] = column[String]("job_type_id")

    def path: Column[String] = column[String]("path", O.Length(500, varying=true))

    def jsonSettings: Column[String] = column[String]("json_settings")

    def createdBy: Column[Option[String]] = column[Option[String]]("created_by")

    def findByUUID(uuid: UUID) = engineJobs.filter(_.uuid === uuid)

    def findById(i: Int) = engineJobs.filter(_.id === i)

    def * = (id, uuid, name, pipelineId, createdAt, updatedAt, stateId, jobTypeId, path, jsonSettings, createdBy) <> (intoEngineJob, fromEngineJob)

    //def indexUUID = index("index_uuid", uuid, unique = true)

    private def intoEngineJob(t: (Int, UUID, String, String, JodaDateTime, JodaDateTime, Int, String, String, String, Option[String])):EngineJob = {
      val state = AnalysisJobStates.intToState(t._7).getOrElse(AnalysisJobStates.UNKNOWN)
      EngineJob(t._1, t._2, t._3, t._4, t._5, t._6, state, t._8, t._9, t._10, t._11)
    }

    private def fromEngineJob(engineJob: EngineJob)  = {
      Some((engineJob.id,
        engineJob.uuid, engineJob.name, engineJob.comment,
        engineJob.createdAt, engineJob.updatedAt, engineJob.state.stateId,
        engineJob.jobTypeId, engineJob.path, engineJob.jsonSettings, engineJob.createdBy))
    }

  }

  class JobResultT(tag: Tag) extends Table[(Int, String)](tag, "job_results") {

    def id: Column[Int] = column[Int]("job_result_id")

    def host: Column[String] = column[String]("host_name")

    def jobId: Column[Int] = column[Int]("job_id")

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)

    def * : ProvenShape[(Int, String)] = (id, host)

  }

  class UsersT(tag: Tag) extends Table[(Int, String, String, JodaDateTime, JodaDateTime)](tag, "users") {

    def id: Column[Int] = column[Int]("user_id", O.PrimaryKey, O.AutoInc)

    def name: Column[String] = column[String]("name")

    def token: Column[String] = column[String]("token")

    def createdAt: Column[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Column[JodaDateTime] = column[JodaDateTime]("updated_at")

    def * : ProvenShape[(Int, String, String, JodaDateTime, JodaDateTime)] = (id, name, token, createdAt, updatedAt)
  }

  class ProjectsT(tag: Tag) extends Table[Project](tag, "projects") {

    def id: Column[Int] = column[Int]("project_id", O.PrimaryKey, O.AutoInc)

    def name: Column[String] = column[String]("name")

    def description: Column[String] = column[String]("description")

    def state: Column[String] = column[String]("state")

    def createdAt: Column[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Column[JodaDateTime] = column[JodaDateTime]("updated_at")

    def * = (id, name, description, state, createdAt, updatedAt) <> (Project.tupled, Project.unapply)
  }

  class ProjectsUsersT(tag: Tag) extends Table[ProjectUser](tag, "projects_users") {

    def projectId: Column[Int] = column[Int]("project_id")

    def login: Column[String] = column[String]("login")

    def role: Column[String] = column[String]("role")

    def projectFK = foreignKey("project_fk", projectId, projects)(a => a.id)

    def * = (projectId, login, role) <> (ProjectUser.tupled, ProjectUser.unapply)
  }

  abstract class IdAbleTable[T](tag: Tag, tableName: String) extends Table[T](tag, tableName) {
    def id: Column[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)

    def uuid: Column[UUID] = column[UUID]("uuid")
  }

  class DataSetTypesT(tag: Tag) extends Table[ServiceDataSetMetaType](tag, "dataset_types") {

    def id: Column[String] = column[String]("dataset_type_id", O.PrimaryKey)

    def idx = index("index_id", id, unique = true)

    def name: Column[String] = column[String]("name")

    def description: Column[String] = column[String]("description")

    def createdAt: Column[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Column[JodaDateTime] = column[JodaDateTime]("updated_at")

    def shortName: Column[String] = column[String]("short_name")

    def * = (id, name, description, createdAt, updatedAt, shortName) <> (ServiceDataSetMetaType.tupled, ServiceDataSetMetaType.unapply)

  }


  /*
  Table to capture the DataSet Entry points of a EngineJob

  - Job has many datasets
  - DataSet may belong to one or many jobs
   */
  class EngineJobDataSetT(tag: Tag) extends Table[EngineJobEntryPoint](tag, "engine_jobs_datasets") {

    def jobId: Column[Int] = column[Int]("job_id")
    def datasetUUID: Column[UUID] = column[UUID]("dataset_uuid")
    def datasetType: Column[String] = column[String]("dataset_type")
    def * = (jobId, datasetUUID, datasetType) <> (EngineJobEntryPoint.tupled, EngineJobEntryPoint.unapply)
  }

  class DataSetMetaT(tag: Tag) extends IdAbleTable[DataSetMetaDataSet](tag, "dataset_metadata") {

    //def indexUUID = index("index_uuid", uuid, unique = true)

    def name: Column[String] = column[String]("name")

    def path: Column[String] = column[String]("path", O.Length(500, varying=true))

    def createdAt: Column[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Column[JodaDateTime] = column[JodaDateTime]("updated_at")

    def numRecords: Column[Long] = column[Long]("num_records")

    def totalLength: Column[Long] = column[Long]("total_length")

    def tags: Column[String] = column[String]("tags")

    def version: Column[String] = column[String]("version")

    def comments: Column[String] = column[String]("comments")

    def md5: Column[String] = column[String]("md5")

    def userId: Column[Int] = column[Int]("user_id")

    def jobId: Column[Int] = column[Int]("job_id")

    def projectId: Column[Int] = column[Int]("project_id")

    def isActive: Column[Boolean] = column[Boolean]("is_active")

    def * = (id, uuid, name, path, createdAt, updatedAt, numRecords, totalLength, tags, version, comments, md5, userId, jobId, projectId, isActive) <>(DataSetMetaDataSet.tupled, DataSetMetaDataSet.unapply)

  }

  class SubreadDataSetT(tag: Tag) extends IdAbleTable[SubreadServiceSet](tag, "dataset_subreads") {

    def cellId: Column[String] = column[String]("cell_id")

    def metadataContextId: Column[String] = column[String]("metadata_context_id")

    def wellSampleName: Column[String] = column[String]("well_sample_name")

    def wellName: Column[String] = column[String]("well_name")

    def bioSampleName: Column[String] = column[String]("bio_sample_name")

    def cellIndex: Column[Int] = column[Int]("cell_index")

    def instrumentId: Column[String] = column[String]("instrument_id")

    def instrumentName: Column[String] = column[String]("instrument_name")

    def runName: Column[String] = column[String]("run_name")

    def instrumentControlVersion: Column[String] = column[String]("instrument_control_version")

    def * = (id, uuid, cellId, metadataContextId, wellSampleName, wellName, bioSampleName, cellIndex, instrumentId, instrumentName, runName, instrumentControlVersion) <>(SubreadServiceSet.tupled, SubreadServiceSet.unapply)

  }

  class HdfSubreadDataSetT(tag: Tag) extends IdAbleTable[HdfSubreadServiceSet](tag, "dataset_hdfsubreads") {

    def cellId: Column[String] = column[String]("cell_id")

    def metadataContextId: Column[String] = column[String]("metadata_context_id")

    def wellSampleName: Column[String] = column[String]("well_sample_name")

    def wellName: Column[String] = column[String]("well_name")

    def bioSampleName: Column[String] = column[String]("bio_sample_name")

    def cellIndex: Column[Int] = column[Int]("cell_index")

    def instrumentId: Column[String] = column[String]("instrument_id")

    def instrumentName: Column[String] = column[String]("instrument_name")

    def runName: Column[String] = column[String]("run_name")

    def instrumentControlVersion: Column[String] = column[String]("instrument_control_version")

    def * = (id, uuid, cellId, metadataContextId, wellSampleName, wellName, bioSampleName, cellIndex, instrumentId, instrumentName, runName, instrumentControlVersion) <>(HdfSubreadServiceSet.tupled, HdfSubreadServiceSet.unapply)

  }

  class ReferenceDataSetT(tag: Tag) extends IdAbleTable[ReferenceServiceSet](tag, "dataset_references") {

    def ploidy: Column[String] = column[String]("ploidy")

    def organism: Column[String] = column[String]("organism")

    def * = (id, uuid, ploidy, organism) <>(ReferenceServiceSet.tupled, ReferenceServiceSet.unapply)

  }

  class AlignmentDataSetT(tag: Tag) extends IdAbleTable[AlignmentServiceSet](tag, "datasets_alignments") {

    def * = (id, uuid) <>(AlignmentServiceSet.tupled, AlignmentServiceSet.unapply)

  }

  class BarcodeDataSetT(tag: Tag) extends IdAbleTable[BarcodeServiceSet](tag, "datasets_barcodes") {

    def * = (id, uuid) <>(BarcodeServiceSet.tupled, BarcodeServiceSet.unapply)

  }

  class CCSreadDataSetT(tag: Tag) extends IdAbleTable[CCSreadServiceSet](tag, "datasets_ccsreads") {

    def * = (id, uuid) <>(CCSreadServiceSet.tupled, CCSreadServiceSet.unapply)

  }

  class PacBioDataStoreFileT(tag: Tag) extends Table[DataStoreServiceFile](tag, "datastore_files") {
    def uuid: Column[UUID] = column[UUID]("uuid", O.PrimaryKey)

    def fileTypeId: Column[String] = column[String]("file_type_id")

    def sourceId: Column[String] = column[String]("source_id")

    def fileSize: Column[Long] = column[Long]("file_size")

    def createdAt: Column[JodaDateTime] = column[JodaDateTime]("created_at")

    def modifiedAt: Column[JodaDateTime] = column[JodaDateTime]("modified_at")

    def importedAt: Column[JodaDateTime] = column[JodaDateTime]("imported_at")

    def path: Column[String] = column[String]("path", O.Length(500, varying=true))

    // job id output datastore. Perhaps need to define input for jobs that have datastore's as input
    // This needs to be rethought.
    def jobId: Column[Int] = column[Int]("job_id")

    def jobUUID: Column[UUID] = column[UUID]("job_uuid")

    def name: Column[String] = column[String]("name")

    def description: Column[String] = column[String]("description")

    def * = (uuid, fileTypeId, sourceId, fileSize, createdAt, modifiedAt, importedAt, path, jobId, jobUUID, name, description) <>(DataStoreServiceFile.tupled, DataStoreServiceFile.unapply)
  }

  implicit val runStatusType = MappedColumnType.base[SupportedRunStates, String](
    {s => s.value()},
    {s => SupportedRunStates.fromValue(s)}
  )
  class RunSummariesT(tag: Tag) extends Table[RunSummary](tag, "RUN_SUMMARIES") {

    def uniqueId: Column[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    def name: Column[String] = column[String]("NAME")

    def summary: Column[Option[String]] = column[Option[String]]("SUMMARY")

    def createdBy: Column[Option[String]] = column[Option[String]]("CREATED_BY")

    def createdAt: Column[Option[JodaDateTime]] = column[Option[JodaDateTime]]("CREATED_AT")

    def startedAt: Column[Option[JodaDateTime]] = column[Option[JodaDateTime]]("STARTED_AT")

    def completedAt: Column[Option[JodaDateTime]] = column[Option[JodaDateTime]]("COMPLETED_AT")

    def status: Column[SupportedRunStates] = column[SupportedRunStates]("STATUS")

    def totalCells: Column[Int] = column[Int]("TOTAL_CELLS")

    def numCellsCompleted: Column[Int] = column[Int]("NUM_CELLS_COMPLETED")

    def numCellsFailed: Column[Int] = column[Int]("NUM_CELLS_FAILED")

    def instrumentName: Column[Option[String]] = column[Option[String]]("INSTRUMENT_NAME")

    def instrumentSerialNumber: Column[Option[String]] = column[Option[String]]("INSTRUMENT_SERIAL_NUMBER")

    def instrumentSwVersion: Column[Option[String]] = column[Option[String]]("INSTRUMENT_SW_VERSION")

    def primaryAnalysisSwVersion: Column[Option[String]] = column[Option[String]]("PRIMARY_ANALYSIS_SW_VERSION")

    def context: Column[Option[String]] = column[Option[String]]("CONTEXT")

    def terminationInfo: Column[Option[String]] = column[Option[String]]("TERMINATION_INFO")

    def reserved: Column[Boolean] = column[Boolean]("RESERVED")

    def * = (
        uniqueId,
        name,
        summary,
        createdBy,
        createdAt,
        startedAt,
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
    def uniqueId: Column[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    // SQLite treats all String columns as TEXT. The size limit on such columns is given by
    // SQLITE_MAX_LENGTH, which defaults to one billion bytes. This should be enough to store
    // a run design model, but if necessary, this value can be raised or lowered at runtime with
    // -DSQLITE_MAX_LENGTH=123456789
    def dataModel: Column[String] = column[String]("DATA_MODEL")

    def * = (dataModel, uniqueId) <> (DataModelAndUniqueId.tupled, DataModelAndUniqueId.unapply)

    def summary = foreignKey("SUMMARY_FK", uniqueId, runSummaries)(_.uniqueId)
  }

  implicit val collectionStatusType = MappedColumnType.base[SupportedAcquisitionStates, String](
    {s => s.value()} ,
    {s => SupportedAcquisitionStates.fromValue(s)}
  )
  class CollectionMetadataT(tag: Tag) extends Table[CollectionMetadata](tag, "COLLECTION_METADATA") {
    def runId: Column[UUID] = column[UUID]("RUN_ID")
    def run = foreignKey("RUN_FK", runId, runSummaries)(_.uniqueId)

    def uniqueId: Column[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    def well: Column[String] = column[String]("WELL")

    def name: Column[String] = column[String]("NAME")

    def summary: Column[Option[String]] = column[Option[String]]("COLUMN")

    def context: Column[Option[String]] = column[Option[String]]("CONTEXT")

    def status: Column[SupportedAcquisitionStates] = column[SupportedAcquisitionStates]("STATUS")

    def instrumentId: Column[Option[String]] = column[Option[String]]("INSTRUMENT_ID")

    def instrumentName: Column[Option[String]] = column[Option[String]]("INSTRUMENT_NAME")

    def movieMinutes: Column[Double] = column[Double]("MOVIE_MINUTES")

    def startedAt: Column[Option[JodaDateTime]] = column[Option[JodaDateTime]]("STARTED_AT")

    def completedAt: Column[Option[JodaDateTime]] = column[Option[JodaDateTime]]("COMPLETED_AT")

    def terminationInfo: Column[Option[String]] = column[Option[String]]("TERMINATION_INFO")

    def * = (
        runId,
        uniqueId,
        name,
        well,
        summary,
        context,
        status,
        instrumentId,
        instrumentName,
        movieMinutes,
        startedAt,
        completedAt,
        terminationInfo) <>(CollectionMetadata.tupled, CollectionMetadata.unapply)
  }

  class SampleT(tag: Tag) extends Table[Sample](tag, "SAMPLE") {

    def details: Column[String] = column[String]("DETAILS")

    def uniqueId: Column[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    def name: Column[String] = column[String]("NAME")

    def createdBy: Column[String] = column[String]("CREATED_BY")

    def createdAt: Column[JodaDateTime] = column[JodaDateTime]("CREATED_AT")

    def * = (details, uniqueId, name, createdBy, createdAt) <> (Sample.tupled, Sample.unapply)
  }

  // DataSet types
  lazy val dsMetaData2 = TableQuery[DataSetMetaT]
  lazy val dsSubread2 = TableQuery[SubreadDataSetT]
  lazy val dsHdfSubread2 = TableQuery[HdfSubreadDataSetT]
  lazy val dsReference2 = TableQuery[ReferenceDataSetT]
  lazy val dsAlignment2 = TableQuery[AlignmentDataSetT]
  lazy val dsBarcode2 = TableQuery[BarcodeDataSetT]
  lazy val dsCCSread2 = TableQuery[CCSreadDataSetT]

  lazy val datastoreServiceFiles = TableQuery[PacBioDataStoreFileT]

  // Users and Projects
  lazy val users = TableQuery[UsersT]
  lazy val projects = TableQuery[ProjectsT]
  lazy val projectsUsers = TableQuery[ProjectsUsersT]

  lazy val engineJobs = TableQuery[EngineJobsT]
  lazy val engineJobsDataSets = TableQuery[EngineJobDataSetT]
  lazy val jobEvents = TableQuery[JobEventsT]
  lazy val jobStates = TableQuery[JobStatesT]
  lazy val jobTags = TableQuery[JobTags]
  lazy val jobsTags = TableQuery[JobsTags]

  // DataSet types
  lazy val datasetTypes = TableQuery[DataSetTypesT]

  // Runs
  lazy val runSummaries = TableQuery[RunSummariesT]
  lazy val dataModels = TableQuery[DataModelsT]
  lazy val collectionMetadata = TableQuery[CollectionMetadataT]

  // Samples
  lazy val samples = TableQuery[SampleT]

  final type SlickTable = TableQuery[_ <: Table[_]]

  lazy val serviceTables: Set[SlickTable] = Set(
    engineJobs,
    datasetTypes,
    engineJobsDataSets,
    jobEvents,
    jobStates,
    jobTags,
    jobsTags,
    users,
    projectsUsers,
    projects,
    dsMetaData2,
    dsSubread2,
    dsHdfSubread2,
    dsReference2,
    dsAlignment2,
    dsBarcode2,
    dsCCSread2,
    datastoreServiceFiles)

  lazy val runTables: Set[SlickTable] = Set(runSummaries, dataModels, collectionMetadata)
}
