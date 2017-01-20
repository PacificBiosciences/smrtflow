package com.pacbio.secondary.smrtlink.database.legacy

import java.io.{PrintWriter, StringWriter}
import java.nio.file.{Paths, Path}
import java.util.UUID

import com.pacbio.common.time.PacBioDateTimeDatabaseFormat
import com.pacbio.logging.{LoggerOptions, LoggerConfig}
import com.pacbio.secondary.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.analysis.jobs.JobModels.{EngineJob, JobEvent}
import com.pacbio.secondary.analysis.tools.{ToolSuccess, ToolFailure, CommandLineToolRunner}
import com.pacbio.secondary.smrtlink.database.{DatabaseConfig, DatabaseUtils, TableModels}
import com.pacbio.secondary.smrtlink.models._
import com.pacificbiosciences.pacbiobasedatamodel.{SupportedAcquisitionStates, SupportedRunStates}
import org.apache.commons.dbcp2.BasicDataSource
import org.joda.time.{DateTime => JodaDateTime}
import scopt.OptionParser
import slick.lifted.ProvenShape

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

object SqliteToPostgresConverterApp extends App {
  import SqliteToPostgresConverter._

  runner(args)
}

case class SqliteToPostgresConverterOptions(legacyUri: String,
                                            pgUsername: String,
                                            pgPassword: String,
                                            pgDbName: String,
                                            pgServer: String,
                                            pgPort: Int = 5432) extends LoggerConfig

object SqliteToPostgresConverter extends CommandLineToolRunner[SqliteToPostgresConverterOptions] {
  import DatabaseUtils._

  val toolId = "pbscala.tools.sqlite_to_postgres_converter"
  val VERSION = "0.1.0"
  val DESCRIPTION =
    """
      |Migrate legacy SQLite db to Postgres
    """.stripMargin

  val defaults = SqliteToPostgresConverterOptions(
    "jdbc:sqlite:db/analysis_services.db",
    "smrtlink_user",
    "password",
    "smrtlink",
    "localhost")

  def toDefault(s: String) = s"(default: '$s')"

  val parser = new OptionParser[SqliteToPostgresConverterOptions]("sqlite-to-postgres") {
    head("")
    note(DESCRIPTION)
    opt[String]('l', "legacy").action { (x, c) => c.copy(legacyUri = x)}.text(s"Legacy SQLite URI ${toDefault(defaults.legacyUri)}")
    opt[String]('u', "user").action { (x, c) => c.copy(pgUsername = x)}.text(s"Postgres user name ${toDefault(defaults.pgUsername)}")
    opt[String]('p', "password").action {(x, c) => c.copy(pgPassword = x)}.text(s"Postgres Password ${toDefault(defaults.pgPassword)}")
    opt[String]('s', "server").action {(x, c) => c.copy(pgServer = x)}.text(s"Postgres server ${toDefault(defaults.pgServer)}")
    opt[String]('n', "db-name").action {(x, c) => c.copy(pgDbName = x)}.text(s"Postgres Name ${toDefault(defaults.pgDbName)}")
    opt[Int]("port").action {(x, c) => c.copy(pgPort = x)}.text(s"Postgres port ${toDefault(defaults.pgPort.toString)}")

    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
  }

  override def run(c: SqliteToPostgresConverterOptions): Either[ToolFailure, ToolSuccess] = {

    val dbConfig = DatabaseConfig(c.pgDbName, c.pgUsername, c.pgPassword, c.pgServer, c.pgPort)
    val startedAt = JodaDateTime.now()

    TestConnection(dbConfig.toDataSource)

    println(s"Attempting to connect to postgres db with $dbConfig")
    val message = TestConnection(dbConfig.toDataSource)
    println(message)

    val dbURI = dbConfig.jdbcURI
    println(s"Postgres URL '$dbURI'")

    val db = dbConfig.toDatabase

    val res = new PostgresWriter(db, c.pgUsername)
      .write(new LegacySqliteReader(c.legacyUri).read())
      .andThen { case _ => db.close() }
      .map { _ => Right(ToolSuccess(toolId, computeTimeDelta(JodaDateTime.now(), startedAt))) }
      .recover {
        case NonFatal(e) => Left(ToolFailure(toolId, computeTimeDelta(JodaDateTime.now(), startedAt), exceptionString(e)))
      }

    Await.result(res, Duration.Inf)
  }

  def exceptionString(e: Throwable): String = {
    val sw = new StringWriter
    e.printStackTrace(new PrintWriter(sw))
    sw.toString
  }
}

class PostgresWriter(db: slick.driver.PostgresDriver.api.Database, pgUsername: String) {
  import slick.driver.PostgresDriver.api._
  import TableModels._

  case class FlywayRow(installed_rank: Int,
                       version: Option[String],
                       description: String,
                       typ: String,
                       script: String,
                       checksum: Option[Int],
                       installed_by: String,
                       installed_on: String,
                       execution_time: Int,
                       success: Boolean)

  class FlywayT(tag: Tag) extends Table[FlywayRow](tag, "schema_version") {
    def installed_rank: Rep[Int] = column[Int]("installed_rank", O.PrimaryKey)
    def version: Rep[Option[String]] = column[Option[String]]("version", O.SqlType("VARCHAR"), O.Length(50))
    def description: Rep[String] = column[String]("description", O.SqlType("VARCHAR"), O.Length(200))
    def typ: Rep[String] = column[String]("type", O.SqlType("VARCHAR"), O.Length(20))
    def script: Rep[String] = column[String]("script", O.SqlType("VARCHAR"), O.Length(1000))
    def checksum: Rep[Option[Int]] = column[Option[Int]]("checksum")
    def installed_by: Rep[String] = column[String]("installed_by", O.SqlType("VARCHAR"), O.Length(100))
    def installed_on: Rep[String] = column[String]("installed_on", O.SqlType("TEXT NOT NULL DEFAULT (to_char(LOCALTIMESTAMP, 'YYYY-MM-DD HH24:MI:SS.MS'))")) // E.g.: 2016-04-28 23:32:09.294
    def execution_time: Rep[Int] = column[Int]("execution_time")
    def success: Rep[Boolean] = column[Boolean]("success")
    def schema_version_s_idx = index("schema_version_s_idx", success, unique = false)
    def * = (installed_rank, version, description, typ, script, checksum, installed_by, installed_on, execution_time, success) <>(FlywayRow.tupled, FlywayRow.unapply)
  }

  lazy val flyway = TableQuery[FlywayT]

  val flywayBaselineRow = FlywayRow(
    installed_rank = 1,
    version = Some("1"),
    description = "<< Flyway Baseline >>",
    typ = "BASELINE",
    script = "<< Flyway Baseline >>",
    checksum = None,
    installed_by = pgUsername,
    installed_on = JodaDateTime.now().toString("YYYY-MM-dd HH:mm:ss.SSS"),
    execution_time = 0,
    success = true)

  def write(f: Future[Seq[Any]]): Future[Unit] = f.flatMap { v =>
    val w = (schema ++ flyway.schema).create >>
      (engineJobs            forceInsertAll v(0).asInstanceOf[Seq[EngineJob]]) >>
      (engineJobsDataSets    forceInsertAll v(1).asInstanceOf[Seq[EngineJobEntryPoint]]) >>
      (jobEvents             forceInsertAll v(2).asInstanceOf[Seq[JobEvent]]) >>
      (jobTags               forceInsertAll v(3).asInstanceOf[Seq[(Int, String)]]) >>
      (jobsTags              forceInsertAll v(4).asInstanceOf[Seq[(Int, Int)]]) >>
      (projects              forceInsertAll v(5).asInstanceOf[Seq[Project]]) >>
      (projectsUsers         forceInsertAll v(6).asInstanceOf[Seq[ProjectUser]]) >>
      (dsMetaData2           forceInsertAll v(7).asInstanceOf[Seq[DataSetMetaDataSet]]) >>
      (dsSubread2            forceInsertAll v(8).asInstanceOf[Seq[SubreadServiceSet]]) >>
      (dsHdfSubread2         forceInsertAll v(9).asInstanceOf[Seq[HdfSubreadServiceSet]]) >>
      (dsReference2          forceInsertAll v(10).asInstanceOf[Seq[ReferenceServiceSet]]) >>
      (dsAlignment2          forceInsertAll v(11).asInstanceOf[Seq[AlignmentServiceSet]]) >>
      (dsBarcode2            forceInsertAll v(12).asInstanceOf[Seq[BarcodeServiceSet]]) >>
      (dsCCSread2            forceInsertAll v(13).asInstanceOf[Seq[ConsensusReadServiceSet]]) >>
      (dsGmapReference2      forceInsertAll v(14).asInstanceOf[Seq[GmapReferenceServiceSet]]) >>
      (dsCCSAlignment2       forceInsertAll v(15).asInstanceOf[Seq[ConsensusAlignmentServiceSet]]) >>
      (dsContig2             forceInsertAll v(16).asInstanceOf[Seq[ContigServiceSet]]) >>
      (datastoreServiceFiles forceInsertAll v(17).asInstanceOf[Seq[DataStoreServiceFile]]) >>
      /* (eulas                 forceInsertAll v(18).asInstanceOf[Seq[EulaRecord]]) >> */
      (runSummaries          forceInsertAll v(18).asInstanceOf[Seq[RunSummary]]) >>
      (dataModels            forceInsertAll v(19).asInstanceOf[Seq[DataModelAndUniqueId]]) >>
      (collectionMetadata    forceInsertAll v(20).asInstanceOf[Seq[CollectionMetadata]]) >>
      (samples               forceInsertAll v(21).asInstanceOf[Seq[Sample]]) >>
      (flyway                +=  flywayBaselineRow )

    db.run(w.transactionally).map { _ => () }
  }
}

class LegacySqliteReader(legacyDbUri: String) extends PacBioDateTimeDatabaseFormat {
  import slick.driver.SQLiteDriver.api._

  implicit val jobStateType = MappedColumnType.base[AnalysisJobStates.JobStates, String](
    {s => s.toString},
    {s => AnalysisJobStates.toState(s).getOrElse(AnalysisJobStates.UNKNOWN)}
  )

  class JobEventsT(tag: Tag) extends Table[JobEvent](tag, "job_events") {
    def id: Rep[UUID] = column[UUID]("job_event_id", O.PrimaryKey)
    def state: Rep[AnalysisJobStates.JobStates] = column[AnalysisJobStates.JobStates]("state")
    def jobId: Rep[Int] = column[Int]("job_id")
    def message: Rep[String] = column[String]("message")
    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")
    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)
    def jobJoin = engineJobs.filter(_.id === jobId)
    def * = (id, jobId, state, message, createdAt) <> (JobEvent.tupled, JobEvent.unapply)
  }

  class JobTags(tag: Tag) extends Table[(Int, String)](tag, "job_tags") {
    def id: Rep[Int] = column[Int]("job_tag_id", O.PrimaryKey, O.AutoInc)
    def name: Rep[String] = column[String]("name")
    def * : ProvenShape[(Int, String)] = (id, name)
  }

  class JobsTags(tag: Tag) extends Table[(Int, Int)](tag, "jobs_tags") {
    def jobId: Rep[Int] = column[Int]("job_id")
    def tagId: Rep[Int] = column[Int]("job_tag_id")
    def * : ProvenShape[(Int, Int)] = (jobId, tagId)
    def jobJoin = engineJobs.filter(_.id === jobId)
    def jobTagFK = foreignKey("job_tag_fk", tagId, jobTags)(a => a.id)
    def jobFK = foreignKey("job_fk", jobId, engineJobs)(b => b.id)
  }

  class EngineJobsT(tag: Tag) extends Table[EngineJob](tag, "engine_jobs") {
    def id: Rep[Int] = column[Int]("job_id", O.PrimaryKey, O.AutoInc)
    def uuid: Rep[UUID] = column[UUID]("uuid")
    def pipelineId: Rep[String] = column[String]("pipeline_id")
    def name: Rep[String] = column[String]("name")
    def state: Rep[AnalysisJobStates.JobStates] = column[AnalysisJobStates.JobStates]("state")
    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")
    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")
    def jobTypeId: Rep[String] = column[String]("job_type_id")
    def path: Rep[String] = column[String]("path", O.Length(500, varying=true))
    def jsonSettings: Rep[String] = column[String]("json_settings")
    def createdBy: Rep[Option[String]] = column[Option[String]]("created_by")
    def smrtLinkVersion: Rep[Option[String]] = column[Option[String]]("smrtlink_version")
    def smrtLinkToolsVersion: Rep[Option[String]] = column[Option[String]]("smrtlink_tools_version")
    def isActive: Rep[Boolean] = column[Boolean]("is_active")
    def findByUUID(uuid: UUID) = engineJobs.filter(_.uuid === uuid)
    def findById(i: Int) = engineJobs.filter(_.id === i)
    def * = (id, uuid, name, pipelineId, createdAt, updatedAt, state, jobTypeId, path, jsonSettings, createdBy, smrtLinkVersion, smrtLinkToolsVersion, isActive) <> (EngineJob.tupled, EngineJob.unapply)
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
    def nameUnique = index("project_name_unique", name, unique = true)
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
  }

  abstract class IdAbleTable[T](tag: Tag, tableName: String) extends Table[T](tag, tableName) {
    def id: Rep[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def uuid: Rep[UUID] = column[UUID]("uuid")
  }

  class EngineJobDataSetT(tag: Tag) extends Table[EngineJobEntryPoint](tag, "engine_jobs_datasets") {
    def jobId: Rep[Int] = column[Int]("job_id")
    def datasetUUID: Rep[UUID] = column[UUID]("dataset_uuid")
    def datasetType: Rep[String] = column[String]("dataset_type")
    def * = (jobId, datasetUUID, datasetType) <> (EngineJobEntryPoint.tupled, EngineJobEntryPoint.unapply)
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
    def userId: Rep[Int] = column[Int]("user_id")
    def jobId: Rep[Int] = column[Int]("job_id")
    def projectId: Rep[Int] = column[Int]("project_id")
    def isActive: Rep[Boolean] = column[Boolean]("is_active")
    def * = (id, uuid, name, path, createdAt, updatedAt, numRecords, totalLength, tags, version, comments, md5, userId, jobId, projectId, isActive) <>(DataSetMetaDataSet.tupled, DataSetMetaDataSet.unapply)
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
    def jobId: Rep[Int] = column[Int]("job_id")
    def jobUUID: Rep[UUID] = column[UUID]("job_uuid")
    def name: Rep[String] = column[String]("name")
    def description: Rep[String] = column[String]("description")
    def isActive: Rep[Boolean] = column[Boolean]("is_active")
    def * = (uuid, fileTypeId, sourceId, fileSize, createdAt, modifiedAt, importedAt, path, jobId, jobUUID, name, description, isActive) <>(DataStoreServiceFile.tupled, DataStoreServiceFile.unapply)
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

  import TableModels.DataModelAndUniqueId
  class DataModelsT(tag: Tag) extends Table[DataModelAndUniqueId](tag, "DATA_MODELS") {
    def uniqueId: Rep[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)
    def dataModel: Rep[String] = column[String]("DATA_MODEL")
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
      startedAt,
      completedAt,
      terminationInfo) <>(CollectionMetadata.tupled, CollectionMetadata.unapply)
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

  // Runs
  lazy val runSummaries = TableQuery[RunSummariesT]
  lazy val dataModels = TableQuery[DataModelsT]
  lazy val collectionMetadata = TableQuery[CollectionMetadataT]

  // Samples
  lazy val samples = TableQuery[SampleT]

  // EULA
  lazy val eulas = TableQuery[EulaRecordT]

  final type SlickTable = TableQuery[_ <: Table[_]]

  // DBCP for connection pooling and caching prepared statements for use in SQLite
  private val connectionPool = new BasicDataSource()
  connectionPool.setDriverClassName("org.sqlite.JDBC")
  connectionPool.setUrl(legacyDbUri)
  connectionPool.setInitialSize(1)
  connectionPool.setMaxTotal(1)
  connectionPool.setPoolPreparedStatements(true)

  private val db = Database.forDataSource(
    connectionPool,
    executor = AsyncExecutor("db-executor", 1, -1))

  def read(): Future[Seq[Any]] = {
    val action = for {
      ej  <- engineJobs.result
      ejd <- engineJobsDataSets.result
      je  <- (jobEvents join engineJobs on (_.jobId === _.id)).result
      jt  <- jobTags.result
      jst <- (jobsTags join engineJobs on (_.jobId === _.id) join jobTags on (_._1.tagId === _.id)).result
      ps  <- projects.result
      psu <- (projectsUsers join projects on (_.projectId === _.id)).result
      dmd <- dsMetaData2.result
      dsu <- dsSubread2.result
      dhs <- dsHdfSubread2.result
      dre <- dsReference2.result
      dal <- dsAlignment2.result
      dba <- dsBarcode2.result
      dcc <- dsCCSread2.result
      dgr <- dsGmapReference2.result
      dca <- dsCCSAlignment2.result
      dco <- dsContig2.result
      dsf <- datastoreServiceFiles.result
      /* eu  <- eulas.result */
      rs  <- runSummaries.result
      dm  <- (dataModels join runSummaries on (_.uniqueId === _.uniqueId)).result
      cm  <- (collectionMetadata join runSummaries on (_.runId === _.uniqueId)).result
      sa  <- samples.result
    } yield Seq(ej, ejd, je.map(_._1), jt, jst.map(_._1), ps, psu.map(_._1), dmd, dsu, dhs, dre, dal, dba, dcc, dgr, dca, dco, dsf, /* eu, */ rs, dm.map(_._1), cm.map(_._1), sa)
    db.run(action).andThen { case _ => db.close() }
  }
}
