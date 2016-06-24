package db.migration

import java.util.UUID

import com.pacbio.common.time.PacBioDateTimeDatabaseFormat
import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import org.joda.time.{DateTime => JodaDateTime}
import slick.driver.H2Driver.api._
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.lifted.ProvenShape

import scala.concurrent.Future

// scalastyle:off
class V1__InitialSchema extends JdbcMigration with SlickMigration with LazyLogging {

  override def slickMigrate(db: DatabaseDef): Future[Any] = {
    val rdDdl = InitialSchema.runDesignTables.map(_.schema).reduce(_ ++ _)
    val serviceDdl = InitialSchema.serviceTables.map(_.schema).reduce(_ ++ _)

    db.run {
      (rdDdl ++ serviceDdl).create >> _populateDataSetTypes >> _populateJobStates >> _createSuperUserProject
    }
  }

  /**
   * Create DataSet types, JobTypes and Admin User, a single Project
   */
  def _populateDataSetTypes: DBIOAction[Any, NoStream, _ <: Effect] = {
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
   */
  def _populateJobStates: DBIOAction[Any, NoStream, _ <: Effect] = {
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
   */
  def _createSuperUserProject: DBIOAction[Any, NoStream, _ <: Effect] = {
    // projects += Project(1, "Project 1", "Project 1 description", "CREATED", JodaDateTime.now(), JodaDateTime.now())
    // users += (1, "admin", "usertoken", JodaDateTime.now(), JodaDateTime.now())
    // projectsUsers += ProjectUser(1, "admin", "OWNER")
    DBIO.seq(
      InitialSchema.projects += (1, "Project 1", 1, "Project 1 description", JodaDateTime.now(), JodaDateTime.now()),
      InitialSchema.users += (1, "admin", "usertoken", JodaDateTime.now(), JodaDateTime.now()),
      InitialSchema.projectsUsers += (1, 1)
    )
  }
}
// scalastyle:on

object InitialSchema extends PacBioDateTimeDatabaseFormat {

  class JobStatesT(tag: Tag) extends Table[(Int, String, String, JodaDateTime, JodaDateTime)](tag, "job_states") {

    def id: Rep[Int] = column[Int]("job_state_id", O.PrimaryKey, O.AutoInc)

    def name: Rep[String] = column[String]("name")

    def description: Rep[String] = column[String]("description")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    def * : ProvenShape[(Int, String, String, JodaDateTime, JodaDateTime)] = (id, name, description, createdAt, updatedAt)

  }

  class JobEventsT(tag: Tag) extends Table[(UUID, Int, Int, String, JodaDateTime)](tag, "job_events") {

    def id: Rep[UUID] = column[UUID]("job_event_id", O.PrimaryKey)

    def stateId: Rep[Int] = column[Int]("state_id")

    def jobId: Rep[Int] = column[Int]("job_id")

    def message: Rep[String] = column[String]("message")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def stateFK = foreignKey("state_fk", stateId, jobStates)(_.id)

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)

    def * : ProvenShape[(UUID, Int, Int, String, JodaDateTime)] = (id, jobId, stateId, message, createdAt)
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

    def jobTagFK = foreignKey("job_tag_fk", tagId, jobTags)(a => a.id)

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(b => b.id)
  }


  class EngineJobsT(tag: Tag) extends Table[(Int, UUID, String, String, JodaDateTime, JodaDateTime, Int, String, String, String)](tag, "engine_jobs") {

    def id: Rep[Int] = column[Int]("job_id", O.PrimaryKey, O.AutoInc)

    // FIXME. The process engine only uses UUID
    def uuid: Rep[UUID] = column[UUID]("uuid")

    // this is really comment
    def pipelineId: Rep[String] = column[String]("pipeline_id")

    def name: Rep[String] = column[String]("name")

    def stateId: Rep[Int] = column[Int]("state_id")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    def stateFK = foreignKey("state_fk", stateId, jobStates)(_.id)

    def stateJoin = jobStates.filter(_.id === stateId)

    // This should be a foreign key into a new table
    def jobTypeId: Rep[String] = column[String]("job_type_id")

    def path: Rep[String] = column[String]("path", O.Length(500, varying=true))

    def jsonSettings: Rep[String] = column[String]("json_settings")

    def * : ProvenShape[(Int, UUID, String, String, JodaDateTime, JodaDateTime, Int, String, String, String)] = (id, uuid, name, pipelineId, createdAt, updatedAt, stateId, jobTypeId, path, jsonSettings)

  }

  class JobResultT(tag: Tag) extends Table[(Int, String)](tag, "job_results") {

    def id: Rep[Int] = column[Int]("job_result_id")

    def host: Rep[String] = column[String]("host_name")

    def jobId: Rep[Int] = column[Int]("job_id")

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)

    def * : ProvenShape[(Int, String)] = (id, host)

  }

  class UsersT(tag: Tag) extends Table[(Int, String, String, JodaDateTime, JodaDateTime)](tag, "users") {

    def id: Rep[Int] = column[Int]("user_id", O.PrimaryKey, O.AutoInc)

    def name: Rep[String] = column[String]("name")

    def token: Rep[String] = column[String]("token")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    def * : ProvenShape[(Int, String, String, JodaDateTime, JodaDateTime)] = (id, name, token, createdAt, updatedAt)
  }

  class ProjectsT(tag: Tag) extends Table[(Int, String, Int, String, JodaDateTime, JodaDateTime)](tag, "projects") {

    def id: Rep[Int] = column[Int]("project_id", O.PrimaryKey, O.AutoInc)

    def name: Rep[String] = column[String]("name")

    def userId: Rep[Int] = column[Int]("user_id")

    def description: Rep[String] = column[String]("description")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    def userFK = foreignKey("user_fk", userId, users)(_.id)

    def * : ProvenShape[(Int, String, Int, String, JodaDateTime, JodaDateTime)] = (id, name, userId, description, createdAt, updatedAt)
  }

  class ProjectsUsersT(tag: Tag) extends Table[(Int, Int)](tag, "projects_users") {
 
    def projectId: Rep[Int] = column[Int]("project_id")
 
    def userId: Rep[Int] = column[Int]("user_id")
 
    def projectFK = foreignKey("project_fk", projectId, projects)(a => a.id)
 
    def userFK = foreignKey("user_fk", userId, users)(b => b.id)
 
    def * : ProvenShape[(Int, Int)] = (projectId, userId)
  }

  abstract class IdAbleTable[T](tag: Tag, tableName: String) extends Table[T](tag, tableName) {
    def id: Rep[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)

    def uuid: Rep[UUID] = column[UUID]("uuid")
  }

  class DataSetTypesT(tag: Tag) extends Table[(String, String, String, JodaDateTime, JodaDateTime, String)](tag, "dataset_types") {

    def id: Rep[String] = column[String]("dataset_type_id", O.PrimaryKey)

    def idx = index("index_id", id, unique = true)

    def name: Rep[String] = column[String]("name")

    def description: Rep[String] = column[String]("description")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    def shortName: Rep[String] = column[String]("short_name")

    def * : ProvenShape[(String, String, String, JodaDateTime, JodaDateTime, String)] = (id, name, description, createdAt, updatedAt, shortName)

  }

  class EngineJobDataSetT(tag: Tag) extends Table[(Int, UUID, String)](tag, "engine_jobs_datasets") {

    def jobId: Rep[Int] = column[Int]("job_id")
    def datasetUUID: Rep[UUID] = column[UUID]("dataset_uuid")
    def datasetType: Rep[String] = column[String]("dataset_type")
    def * : ProvenShape[(Int, UUID, String)] = (jobId, datasetUUID, datasetType)
  }

  class DataSetMetaT(tag: Tag) extends IdAbleTable[(Int, UUID, String, String, JodaDateTime, JodaDateTime, Long, Long, String, String, String, String, Int, Int, Int, Boolean)](tag, "dataset_metadata") {

    //def indexUUID = index("index_uuid", uuid, unique = true)

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

    def userId: Rep[Int] = column[Int]("user_id")

    def jobId: Rep[Int] = column[Int]("job_id")

    def projectId: Rep[Int] = column[Int]("project_id")

    def isActive: Rep[Boolean] = column[Boolean]("is_active")

    def * : ProvenShape[(Int, UUID, String, String, JodaDateTime, JodaDateTime, Long, Long, String, String, String, String, Int, Int, Int, Boolean)] = (id, uuid, name, path, createdAt, updatedAt, numRecords, totalLength, tags, version, comments, md5, userId, jobId, projectId, isActive)

  }

  class SubreadDataSetT(tag: Tag) extends IdAbleTable[(Int, UUID, String, String, String, String, String, Int, String, String, String, String)](tag, "dataset_subreads") {

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

    def * : ProvenShape[(Int, UUID, String, String, String, String, String, Int, String, String, String, String)] = (id, uuid, cellId, metadataContextId, wellSampleName, wellName, bioSampleName, cellIndex, instrumentId, instrumentName, runName, instrumentControlVersion)

  }

  class HdfSubreadDataSetT(tag: Tag) extends IdAbleTable[(Int, UUID, String, String, String, String, String, Int, String, String, String, String)](tag, "dataset_hdfsubreads") {

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

    def * : ProvenShape[(Int, UUID, String, String, String, String, String, Int, String, String, String, String)] = (id, uuid, cellId, metadataContextId, wellSampleName, wellName, bioSampleName, cellIndex, instrumentId, instrumentName, runName, instrumentControlVersion)

  }

  class ReferenceDataSetT(tag: Tag) extends IdAbleTable[(Int, UUID, String, String)](tag, "dataset_references") {

    def ploidy: Rep[String] = column[String]("ploidy")

    def organism: Rep[String] = column[String]("organism")

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
    def uuid: Rep[UUID] = column[UUID]("uuid", O.PrimaryKey)

    def fileTypeId: Rep[String] = column[String]("file_type_id")

    def sourceId: Rep[String] = column[String]("source_id")

    def fileSize: Rep[Long] = column[Long]("file_size")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def modifiedAt: Rep[JodaDateTime] = column[JodaDateTime]("modified_at")

    def importedAt: Rep[JodaDateTime] = column[JodaDateTime]("imported_at")

    def path: Rep[String] = column[String]("path", O.Length(500, varying=true))

    def jobId: Rep[Int] = column[Int]("job_id")

    def jobUUID: Rep[UUID] = column[UUID]("job_uuid")

    def * : ProvenShape[(UUID, String, String, Long, JodaDateTime, JodaDateTime, JodaDateTime, String, Int, UUID)] = (uuid, fileTypeId, sourceId, fileSize, createdAt, modifiedAt, importedAt, path, jobId, jobUUID)
  }

  class RunDesignSummariesT(tag: Tag) extends Table[(String, String, String, Long, JodaDateTime, Boolean)](tag, "RUN_DESIGN_SUMMARIES") {
    def id: Rep[Long] = column[Long]("ID", O.PrimaryKey, O.AutoInc)

    def name: Rep[String] = column[String]("NAME")

    def summary: Rep[String] = column[String]("SUMMARY")

    def createdBy: Rep[String] = column[String]("CREATED_BY")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("CREATED_AT")

    def reserved: Rep[Boolean] = column[Boolean]("RESERVED")

    def * : ProvenShape[(String, String, String, Long, JodaDateTime, Boolean)] = (name, summary, createdBy, id, createdAt, reserved)
  }

  case class RunDataModelAndId(runDataModel: String, id: Long)
  class RunDataModelsT(tag: Tag) extends Table[RunDataModelAndId](tag, "RUN_DATA_MODELS") {
    def id: Rep[Long] = column[Long]("ID", O.PrimaryKey)

    // SQLite treats all String columns as TEXT. The size limit on such columns is given by
    // SQLITE_MAX_LENGTH, which defaults to one billion bytes. This should be enough to store
    // a run design model, but if necessary, this value can be raised or lowered at runtime with
    // -DSQLITE_MAX_LENGTH=123456789
    def runDataModel: Rep[String] = column[String]("RUN_DATA_MODEL")

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
