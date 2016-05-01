package db.migration

import java.sql.SQLException
import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}

import com.typesafe.scalalogging.LazyLogging

import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.lifted.ProvenShape

import com.pacbio.common.time.PacBioDateTimeDatabaseFormat


class V1__InitialSchema extends JdbcMigration with SlickMigration with LazyLogging {

  override def slickMigrate(implicit session: Session) {

    session.withTransaction {
      val rdDdl = InitialSchema.runDesignTables.map(_.ddl).reduce(_ ++ _)
      rdDdl.create

      val serviceDdl = InitialSchema.serviceTables.map(_.ddl).reduce(_ ++ _)
      serviceDdl.create

      _populateDataSetTypes(session)
      _populateJobStates(session)
      _createSuperUserProject(session)
    }
  }

    /**
   * Create DataSet types, JobTypes and Admin User, a single Project
   * @return
   */
  def _populateDataSetTypes(implicit session: Session) = {
    val initRows = List(
      ("PacBio.DataSet.ReferenceSet", "Display name for PacBio.DataSet.ReferenceSet", "Description for PacBio.DataSet.ReferenceSet", JodaDateTime.now(), JodaDateTime.now(), "references"),
      ("PacBio.DataSet.ConsensusReadSet", "Display name for PacBio.DataSet.ConsensusReadSet", "Description for PacBio.DataSet.ConsensusReadSet", JodaDateTime.now(), JodaDateTime.now(), "ccsreads"),
      ("PacBio.DataSet.ContigSet", "Display name for PacBio.DataSet.ContigSet", "Description for PacBio.DataSet.ContigSet", JodaDateTime.now(), JodaDateTime.now(), "contigs"),
      ("PacBio.DataSet.SubreadSet", "Display name for PacBio.DataSet.SubreadSet", "Description for PacBio.DataSet.SubreadSet", JodaDateTime.now(), JodaDateTime.now(), "subreads"),
      ("PacBio.DataSet.BarcodeSet", "Display name for PacBio.DataSet.BarcodeSet", "Description for PacBio.DataSet.BarcodeSet", JodaDateTime.now(), JodaDateTime.now(), "barcodes"),
      ("PacBio.DataSet.ConsensusAlignmentSet", "Display name for PacBio.DataSet.ConsensusAlignmentSet", "Description for PacBio.DataSet.ConsensusAlignmentSet", JodaDateTime.now(), JodaDateTime.now(), "ccsalignments"),
      ("PacBio.DataSet.HdfSubreadSet", "Display name for PacBio.DataSet.HdfSubreadSet", "Description for PacBio.DataSet.HdfSubreadSet", JodaDateTime.now(), JodaDateTime.now(), "hdfsubreads"),
      ("PacBio.DataSet.AlignmentSet", "Display name for PacBio.DataSet.AlignmentSet", "Description for PacBio.DataSet.AlignmentSet", JodaDateTime.now(), JodaDateTime.now(), "alignments")
    )
    InitialSchema.datasetTypes ++= initRows
  }

  /**
   * Create the Default Job Types
   * @param session
   * @return
   */
  def _populateJobStates(implicit session: Session) = {
    val initRows = List(
      (1, "CREATED", "State CREATED description", JodaDateTime.now(), JodaDateTime.now()),
      (2, "SUBMITTED", "State SUBMITTED description", JodaDateTime.now(), JodaDateTime.now()),
      (3, "RUNNING", "State RUNNING description", JodaDateTime.now(), JodaDateTime.now()),
      (4, "TERMINATED", "State TERMINATED description", JodaDateTime.now(), JodaDateTime.now()),
      (5, "SUCCESSFUL", "State SUCCESSFUL description", JodaDateTime.now(), JodaDateTime.now()),
      (6, "FAILED", "State FAILED description", JodaDateTime.now(), JodaDateTime.now()),
      (7, "UNKNOWN", "State UNKNOWN description", JodaDateTime.now(), JodaDateTime.now())
    )
    InitialSchema.jobStates ++= initRows
  }

  /**
   * Create a single user and a project
   * @param session
   * @return
   */
  def _createSuperUserProject(implicit session: Session) = {
    // projects += Project(1, "Project 1", "Project 1 description", "CREATED", JodaDateTime.now(), JodaDateTime.now())
    // users += (1, "admin", "usertoken", JodaDateTime.now(), JodaDateTime.now())
    // projectsUsers += ProjectUser(1, "admin", "OWNER")
    InitialSchema.projects += (1, "Project 1", 1, "Project 1 description", JodaDateTime.now(), JodaDateTime.now())
    InitialSchema.users += (1, "admin", "usertoken", JodaDateTime.now(), JodaDateTime.now())
    InitialSchema.projectsUsers += (1, 1)
  }
}

object InitialSchema extends PacBioDateTimeDatabaseFormat {

  class JobStatesT(tag: Tag) extends Table[(Int, String, String, JodaDateTime, JodaDateTime)](tag, "job_states") {

    def id: Column[Int] = column[Int]("job_state_id", O.PrimaryKey, O.AutoInc)

    def name: Column[String] = column[String]("name")

    def description: Column[String] = column[String]("description")

    def createdAt: Column[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Column[JodaDateTime] = column[JodaDateTime]("updated_at")

    def * : ProvenShape[(Int, String, String, JodaDateTime, JodaDateTime)] = (id, name, description, createdAt, updatedAt)

  }

  class JobEventsT(tag: Tag) extends Table[(UUID, Int, Int, String, JodaDateTime)](tag, "job_events") {

    def id: Column[UUID] = column[UUID]("job_event_id", O.PrimaryKey)

    def stateId: Column[Int] = column[Int]("state_id")

    def jobId: Column[Int] = column[Int]("job_id")

    def message: Column[String] = column[String]("message")

    def createdAt: Column[JodaDateTime] = column[JodaDateTime]("created_at")

    def stateFK = foreignKey("state_fk", stateId, jobStates)(_.id)

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)

    def * : ProvenShape[(UUID, Int, Int, String, JodaDateTime)] = (id, jobId, stateId, message, createdAt)
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

    def jobTagFK = foreignKey("job_tag_fk", tagId, jobTags)(a => a.id)

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(b => b.id)
  }


  class EngineJobsT(tag: Tag) extends Table[(Int, UUID, String, String, JodaDateTime, JodaDateTime, Int, String, String, String)](tag, "engine_jobs") {

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

    def * : ProvenShape[(Int, UUID, String, String, JodaDateTime, JodaDateTime, Int, String, String, String)] = (id, uuid, name, pipelineId, createdAt, updatedAt, stateId, jobTypeId, path, jsonSettings)

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

  class ProjectsT(tag: Tag) extends Table[(Int, String, Int, String, JodaDateTime, JodaDateTime)](tag, "projects") {

    def id: Column[Int] = column[Int]("project_id", O.PrimaryKey, O.AutoInc)

    def name: Column[String] = column[String]("name")

    def userId: Column[Int] = column[Int]("user_id")

    def description: Column[String] = column[String]("description")

    def createdAt: Column[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Column[JodaDateTime] = column[JodaDateTime]("updated_at")

    def userFK = foreignKey("user_fk", userId, users)(_.id)

    def * : ProvenShape[(Int, String, Int, String, JodaDateTime, JodaDateTime)] = (id, name, userId, description, createdAt, updatedAt)
  }

  class ProjectsUsersT(tag: Tag) extends Table[(Int, Int)](tag, "projects_users") {
 
    def projectId: Column[Int] = column[Int]("project_id")
 
    def userId: Column[Int] = column[Int]("user_id")
 
    def projectFK = foreignKey("project_fk", projectId, projects)(a => a.id)
 
    def userFK = foreignKey("user_fk", userId, users)(b => b.id)
 
    def * : ProvenShape[(Int, Int)] = (projectId, userId)
  }

  abstract class IdAbleTable[T](tag: Tag, tableName: String) extends Table[T](tag, tableName) {
    def id: Column[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)

    def uuid: Column[UUID] = column[UUID]("uuid")
  }

  class DataSetTypesT(tag: Tag) extends Table[(String, String, String, JodaDateTime, JodaDateTime, String)](tag, "dataset_types") {

    def id: Column[String] = column[String]("dataset_type_id", O.PrimaryKey)

    def idx = index("index_id", id, unique = true)

    def name: Column[String] = column[String]("name")

    def description: Column[String] = column[String]("description")

    def createdAt: Column[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Column[JodaDateTime] = column[JodaDateTime]("updated_at")

    def shortName: Column[String] = column[String]("short_name")

    def * : ProvenShape[(String, String, String, JodaDateTime, JodaDateTime, String)] = (id, name, description, createdAt, updatedAt, shortName)

  }

  class EngineJobDataSetT(tag: Tag) extends Table[(Int, UUID, String)](tag, "engine_jobs_datasets") {

    def jobId: Column[Int] = column[Int]("job_id")
    def datasetUUID: Column[UUID] = column[UUID]("dataset_uuid")
    def datasetType: Column[String] = column[String]("dataset_type")
    def * : ProvenShape[(Int, UUID, String)] = (jobId, datasetUUID, datasetType)
  }

  class DataSetMetaT(tag: Tag) extends IdAbleTable[(Int, UUID, String, String, JodaDateTime, JodaDateTime, Long, Long, String, String, String, String, Int, Int, Int, Boolean)](tag, "dataset_metadata") {

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

    def * : ProvenShape[(Int, UUID, String, String, JodaDateTime, JodaDateTime, Long, Long, String, String, String, String, Int, Int, Int, Boolean)] = (id, uuid, name, path, createdAt, updatedAt, numRecords, totalLength, tags, version, comments, md5, userId, jobId, projectId, isActive)

  }

  class SubreadDataSetT(tag: Tag) extends IdAbleTable[(Int, UUID, String, String, String, String, String, Int, String, String, String, String)](tag, "dataset_subreads") {

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

    def * : ProvenShape[(Int, UUID, String, String, String, String, String, Int, String, String, String, String)] = (id, uuid, cellId, metadataContextId, wellSampleName, wellName, bioSampleName, cellIndex, instrumentId, instrumentName, runName, instrumentControlVersion)

  }

  class HdfSubreadDataSetT(tag: Tag) extends IdAbleTable[(Int, UUID, String, String, String, String, String, Int, String, String, String, String)](tag, "dataset_hdfsubreads") {

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

    def * : ProvenShape[(Int, UUID, String, String, String, String, String, Int, String, String, String, String)] = (id, uuid, cellId, metadataContextId, wellSampleName, wellName, bioSampleName, cellIndex, instrumentId, instrumentName, runName, instrumentControlVersion)

  }

  class ReferenceDataSetT(tag: Tag) extends IdAbleTable[(Int, UUID, String, String)](tag, "dataset_references") {

    def ploidy: Column[String] = column[String]("ploidy")

    def organism: Column[String] = column[String]("organism")

    def * : ProvenShape[(Int, UUID, String, String)] = (id, uuid, ploidy, organism)

  }

  class AlignmentDataSetT(tag: Tag) extends IdAbleTable[(Int, UUID)](tag, "datasets_alignments") {

    def * : ProvenShape[(Int, UUID)] = (id, uuid)

  }

  class BarcodeDataSetT(tag: Tag) extends IdAbleTable[(Int, UUID)](tag, "datasets_barcodes") {

    def * : ProvenShape[(Int, UUID)] = (id, uuid)

  }

  class CCSreadDataSetT(tag: Tag) extends IdAbleTable[(Int, UUID)](tag, "datasets_ccsreads") {

    def * : ProvenShape[(Int, UUID)] = (id, uuid)

  }

  class PacBioDataStoreFileT(tag: Tag) extends Table[(UUID, String, String, Long, JodaDateTime, JodaDateTime, JodaDateTime, String, Int, UUID)](tag, "datastore_files") {
    def uuid: Column[UUID] = column[UUID]("uuid", O.PrimaryKey)

    def fileTypeId: Column[String] = column[String]("file_type_id")

    def sourceId: Column[String] = column[String]("source_id")

    def fileSize: Column[Long] = column[Long]("file_size")

    def createdAt: Column[JodaDateTime] = column[JodaDateTime]("created_at")

    def modifiedAt: Column[JodaDateTime] = column[JodaDateTime]("modified_at")

    def importedAt: Column[JodaDateTime] = column[JodaDateTime]("imported_at")

    def path: Column[String] = column[String]("path", O.Length(500, varying=true))

    def jobId: Column[Int] = column[Int]("job_id")

    def jobUUID: Column[UUID] = column[UUID]("job_uuid")

    def * : ProvenShape[(UUID, String, String, Long, JodaDateTime, JodaDateTime, JodaDateTime, String, Int, UUID)] = (uuid, fileTypeId, sourceId, fileSize, createdAt, modifiedAt, importedAt, path, jobId, jobUUID)
  }

  class RunDesignSummariesT(tag: Tag) extends Table[(String, String, String, Long, JodaDateTime, Boolean)](tag, "RUN_DESIGN_SUMMARIES") {
    def id: Column[Long] = column[Long]("ID", O.PrimaryKey, O.AutoInc)

    def name: Column[String] = column[String]("NAME")

    def summary: Column[String] = column[String]("SUMMARY")

    def createdBy: Column[String] = column[String]("CREATED_BY")

    def createdAt: Column[JodaDateTime] = column[JodaDateTime]("CREATED_AT")

    def reserved: Column[Boolean] = column[Boolean]("RESERVED")

    def * : ProvenShape[(String, String, String, Long, JodaDateTime, Boolean)] = (name, summary, createdBy, id, createdAt, reserved)
  }

  case class RunDataModelAndId(runDataModel: String, id: Long)
  class RunDataModelsT(tag: Tag) extends Table[RunDataModelAndId](tag, "RUN_DATA_MODELS") {
    def id: Column[Long] = column[Long]("ID", O.PrimaryKey)

    // SQLite treats all String columns as TEXT. The size limit on such columns is given by
    // SQLITE_MAX_LENGTH, which defaults to one billion bytes. This should be enough to store
    // a run design model, but if necessary, this value can be raised or lowered at runtime with
    // -DSQLITE_MAX_LENGTH=123456789
    def runDataModel: Column[String] = column[String]("RUN_DATA_MODEL")

    def * = (runDataModel, id) <> (RunDataModelAndId.tupled, RunDataModelAndId.unapply)

    def summary = foreignKey("SUMMARY_FK", id, runDesignSummaries)(_.id)
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

  // RunDesign
  lazy val runDesignSummaries = TableQuery[RunDesignSummariesT]
  lazy val runDataModels = TableQuery[RunDataModelsT]

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

  lazy val runDesignTables: Set[SlickTable] = Set(runDesignSummaries, runDataModels)
}
