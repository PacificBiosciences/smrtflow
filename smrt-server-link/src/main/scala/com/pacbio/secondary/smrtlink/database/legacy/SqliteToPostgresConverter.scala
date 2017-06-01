package com.pacbio.secondary.smrtlink.database.legacy

import java.io.File
import java.nio.file.{Path, Paths}
import java.util.UUID

import com.pacbio.common.time.{Clock, PacBioDateTimeDatabaseFormat, SystemClock}
import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.analysis.tools.{CommandLineToolRunner, ToolFailure}
import com.pacbio.secondary.smrtlink.database.{DatabaseConfig, DatabaseUtils}
import com.pacbio.secondary.smrtlink.database.legacy.BaseLine.{AnalysisJobStates, DataModelAndUniqueId}
import com.typesafe.scalalogging.LazyLogging
// This is a problem
import com.pacificbiosciences.pacbiobasedatamodel.{SupportedAcquisitionStates, SupportedRunStates}

import org.apache.commons.dbcp2.BasicDataSource
import org.flywaydb.core.api.MigrationVersion
import org.joda.time.{DateTime => JodaDateTime}
import resource._
import scopt.OptionParser

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Try}

object SqliteToPostgresConverterApp extends App {
  import SqliteToPostgresConverter._
  runnerWithArgsAndExit(args)
}

case class SqliteToPostgresConverterOptions(sqliteFile: File,
                                            pgUsername: String,
                                            pgPassword: String,
                                            pgDbName: String,
                                            pgServer: String,
                                            pgPort: Int = 5432,
                                            maxTimeOut: FiniteDuration = 8.minutes) extends LoggerConfig

object SqliteToPostgresConverter extends CommandLineToolRunner[SqliteToPostgresConverterOptions] {
  import DatabaseUtils._

  val toolId = "pbscala.tools.sqlite_to_postgres_converter"
  val VERSION = "0.2.0"
  val DESCRIPTION =
    """
      |Migrate legacy SMRT Link 4.0.0 SQLite db to Postgres
    """.stripMargin

  // These defaults should be loaded from the conf
  val defaults = SqliteToPostgresConverterOptions(
    Paths.get("analysis_services.db").toFile,
    "smrtlink_user",
    "password",
    "smrtlink",
    "localhost")

  def toDefault(s: String) = s"(default: '$s')"

  lazy val parser = new OptionParser[SqliteToPostgresConverterOptions]("sqlite-to-postgres") {
    head("")
    note(DESCRIPTION)
    arg[File]("sqlite-file").action { (x, c) => c.copy(sqliteFile = x)}.text(s"Path to SMRT Link 4.0.0 Sqlite db file")
    opt[String]('u', "user").action { (x, c) => c.copy(pgUsername = x)}.text(s"Postgres user name ${toDefault(defaults.pgUsername)}")
    opt[String]('p', "password").action {(x, c) => c.copy(pgPassword = x)}.text(s"Postgres Password ${toDefault(defaults.pgPassword)}")
    opt[String]('s', "server").action {(x, c) => c.copy(pgServer = x)}.text(s"Postgres server ${toDefault(defaults.pgServer)}")
    opt[String]('n', "db-name").action {(x, c) => c.copy(pgDbName = x)}.text(s"Postgres Name ${toDefault(defaults.pgDbName)}")
    opt[Int]("port").action {(x, c) => c.copy(pgPort = x)}.text(s"Postgres port ${toDefault(defaults.pgPort.toString)}")
    opt[Int]("max-timeout").action {(x, c) => c.copy(maxTimeOut = x.minutes)}.text(s"Maximum timeout (in minutes) for SQLite to Postgres import ${toDefault(defaults.maxTimeOut.toString)}")

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage()
      sys.exit(0)
    } text "Show Options and exit"

    opt[Unit]("version") action { (x, c) =>
      showVersion
      sys.exit(0)
    } text "Show tool version and exit"

    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
  }

  /**
    * Util to expose only the Path to users and translate to a jdbc URI string
    * @param f Path to sqlite 4.0.0 File
    * @return
    */
  def toSqliteURI(f: File): String = {
    val sx = f.toPath.toAbsolutePath.toString
    if (sx.startsWith("jdbc:sqlite:")) sx
    else s"jdbc:sqlite:$sx"
  }

  /**
    *
    * Migration/Import Model
    *
    * 1. Use Postgres configuration to run migration to create necessary tables (if Necessary)
    * 2. Check for previously successful import Sqlite import (exit early if successful)
    * 3. Load all Sqlite data into memory
    * 4. Import into Postgres
    * 5. Write successful import message into MigrateStatus table (on Failure write error message to MigrationStatus table)
    *
    * Still need to clearly define error handling cases, specifically:
    *
    * - On "retry of import", should postgresql tables be dropped completed and re migration from scratch
    * - Pre validation test for peeking into the sqlite file to make sure it has the official/release 4.0.0 sqlite migration number
    * - Pre validation test to test for Sqlite connection to fail early with a reasonable message
    *
    * Returns a terse summary message of the successful db import.
    *
    * Note, this will throw. The main entry should be runTool.
    *
    * @param c Config
    * @return
    */
  def runImporter(c: SqliteToPostgresConverterOptions): String = {

    // Would be nice to have sqlite test connection here to fail early
    val sqliteURI = toSqliteURI(c.sqliteFile)

    val dbConfig = DatabaseConfig(c.pgDbName, c.pgUsername, c.pgPassword, c.pgServer, c.pgPort)

    for (dataSource <- managed(dbConfig.toDataSource)) {
      println(s"Attempting to connect to postgres db with $dbConfig")
      val message = TestConnection(dataSource)
      println(message)

      val dbURI = dbConfig.jdbcURI
      println(s"Postgres URL '$dbURI'")

      println("Attempting to run Postgres migrations (if necessary)")
      val targetVersion = MigrationVersion.fromVersion("1")
      val psqlMigrationStatus = Migrator(dataSource, targetVersion)
      println(psqlMigrationStatus)
    }

    managed(dbConfig.toDatabase) acquireAndGet { db =>
      val writer = new PostgresWriter(db, c.pgUsername, new SystemClock, c.maxTimeOut)
      val res = writer.checkForSuccessfulMigration().flatMap {
        case Some(status) =>
          val msg = s"Previous import at ${status.timestamp} was successful. Skipping importing."
          println(msg)
          Future.successful(msg)
        case _ =>
          writer
            .write(new LegacySqliteReader(sqliteURI).read())
            .map { _ => s"Successfully migrated data from ${c.sqliteFile} into Postgres" }
      }
      Await.result(res, c.maxTimeOut)
    }
  }

  override def runTool(c: SqliteToPostgresConverterOptions) = Try(runImporter(c))

  // Legacy interface
  override def run(config: SqliteToPostgresConverterOptions) = Left(ToolFailure(toolId, 1, "Not Supported"))
}

// Postgres Baseline Schema compatible models
case class MigrationData(projects: Seq[BaseLine.Project],
                         projectsUsers: Seq[BaseLine.ProjectUser],
                         engineJobs: Seq[BaseLine.EngineJob],
                         engineJobsDataSets: Seq[BaseLine.EngineJobEntryPoint],
                         jobEvents: Seq[BaseLine.JobEvent],
                         dsMetaData2: Seq[BaseLine.DataSetMetaDataSet],
                         dsSubread2: Seq[BaseLine.SubreadServiceSet],
                         dsHdfSubread2: Seq[BaseLine.HdfSubreadServiceSet],
                         dsReference2: Seq[BaseLine.ReferenceServiceSet],
                         dsAlignment2: Seq[BaseLine.AlignmentServiceSet],
                         dsBarcode2: Seq[BaseLine.BarcodeServiceSet],
                         dsCCSread2: Seq[BaseLine.ConsensusReadServiceSet],
                         dsGmapReference2: Seq[BaseLine.GmapReferenceServiceSet],
                         dsCCSAlignment2: Seq[BaseLine.ConsensusAlignmentServiceSet],
                         dsContig2: Seq[BaseLine.ContigServiceSet],
                         datastoreServiceFiles: Seq[BaseLine.DataStoreServiceFile],
                         runSummaries: Seq[BaseLine.RunSummary],
                         dataModels: Seq[BaseLine.DataModelAndUniqueId],
                         collectionMetadata: Seq[BaseLine.CollectionMetadata],
                         samples:Seq[BaseLine.Sample])

class PostgresWriter(db: slick.driver.PostgresDriver.api.Database, pgUsername: String, clock: Clock, maxTimeOut: FiniteDuration) extends LazyLogging{
  import slick.driver.PostgresDriver.api._
  // The BaseLine Postgres V1 schema and models
  import BaseLine._

  val timestamp = clock.dateNow().toString("YYYY-MM-dd HH:mm:ss.SSS")

  def setAutoInc(tableName: String, idName: String, max: Option[Int]): DBIO[Int] = {
    val sequenceName = s"${tableName}_${idName}_seq"
    sql"SELECT setval($sequenceName, ${max.getOrElse(0) + 1});".as[Int].map(_.head)
  }

  def checkForSuccessfulMigration(): Future[Option[MigrationStatusRow]] = {
    val query = migrationStatus.filter(_.success === true).result.headOption
    db.run(query.transactionally)
  }

  def write(f: Future[MigrationData]): Future[String] = f.flatMap { d =>
    logger.info("Starting to write to postgres")
    // Terse summary of system
    logger.info(s"Number of SubreadSets    : ${d.dsSubread2.length}")
    logger.info(s"Number of ReferenceSets  : ${d.dsReference2.length}")
    logger.info(s"Number of Jobs           : ${d.engineJobs.length}")
    logger.info(s"Number of Runs           : ${d.dataModels.length}")
    logger.info(s"Number of DataStoreFiles : ${d.datastoreServiceFiles.length}")
    val w =
      (projects              forceInsertAll d.projects) >>
      projects.map(_.id).max.result.flatMap(m => setAutoInc(projects.baseTableRow.tableName, "project_id", m)) >>
      (projectsUsers         forceInsertAll d.projectsUsers) >>
      (engineJobs            forceInsertAll d.engineJobs) >>
      engineJobs.map(_.id).max.result.flatMap(m => setAutoInc(engineJobs.baseTableRow.tableName, "job_id", m)) >>
      (engineJobsDataSets    forceInsertAll d.engineJobsDataSets) >>
      (jobEvents             forceInsertAll d.jobEvents) >>
      (dsMetaData2           forceInsertAll d.dsMetaData2) >>
      dsMetaData2.map(_.id).max.result.flatMap(m => setAutoInc(dsMetaData2.baseTableRow.tableName, "id", m)) >>
      (dsSubread2            forceInsertAll d.dsSubread2) >>
      dsSubread2.map(_.id).max.result.flatMap(m => setAutoInc(dsSubread2.baseTableRow.tableName, "id", m)) >>
      (dsHdfSubread2         forceInsertAll d.dsHdfSubread2) >>
      dsHdfSubread2.map(_.id).max.result.flatMap(m => setAutoInc(dsHdfSubread2.baseTableRow.tableName, "id", m)) >>
      (dsReference2          forceInsertAll d.dsReference2) >>
      dsReference2.map(_.id).max.result.flatMap(m => setAutoInc(dsReference2.baseTableRow.tableName, "id", m)) >>
      (dsAlignment2          forceInsertAll d.dsAlignment2) >>
      dsAlignment2.map(_.id).max.result.flatMap(m => setAutoInc(dsAlignment2.baseTableRow.tableName, "id", m)) >>
      (dsBarcode2            forceInsertAll d.dsBarcode2) >>
      dsBarcode2.map(_.id).max.result.flatMap(m => setAutoInc(dsBarcode2.baseTableRow.tableName, "id", m)) >>
      (dsCCSread2            forceInsertAll d.dsCCSread2) >>
      dsCCSread2.map(_.id).max.result.flatMap(m => setAutoInc(dsCCSread2.baseTableRow.tableName, "id", m)) >>
      (dsGmapReference2      forceInsertAll d.dsGmapReference2) >>
      dsGmapReference2.map(_.id).max.result.flatMap(m => setAutoInc(dsGmapReference2.baseTableRow.tableName, "id", m)) >>
      (dsCCSAlignment2       forceInsertAll d.dsCCSAlignment2) >>
      dsCCSAlignment2.map(_.id).max.result.flatMap(m => setAutoInc(dsCCSAlignment2.baseTableRow.tableName, "id", m)) >>
      (dsContig2             forceInsertAll d.dsContig2) >>
      dsContig2.map(_.id).max.result.flatMap(m => setAutoInc(dsContig2.baseTableRow.tableName, "id", m)) >>
      (datastoreServiceFiles forceInsertAll d.datastoreServiceFiles) >>
      /* (eulas                 forceInsertAll d.eulas) >> */
      (runSummaries          forceInsertAll d.runSummaries) >>
      (dataModels            forceInsertAll d.dataModels) >>
      (collectionMetadata    forceInsertAll d.collectionMetadata) >>
      (samples               forceInsertAll d.samples) >>
      (migrationStatus       += MigrationStatusRow(timestamp, success = true, error = None))

    db.run(w.transactionally).map { _ => "Completed import" }.andThen {
      case Failure(NonFatal(e)) =>
        val status = MigrationStatusRow(timestamp, success = false, error = Some(e.toString))
        val w = migrationStatus += status
        Await.result(db.run(w.transactionally).map(_ => s"Failed import ${status.error.getOrElse("Unknown")}"), maxTimeOut)
    }
  }
}

/**
  * The naming convention of LegacyX Refers to the last Sqlite system of model X
  */
object LegacyModels {
  case class LegacyEngineJob(
                          id: Int,
                          uuid: UUID,
                          name: String,
                          comment: String,
                          createdAt: JodaDateTime,
                          updatedAt: JodaDateTime,
                          state: AnalysisJobStates.JobStates,
                          jobTypeId: String,
                          path: String,
                          jsonSettings: String,
                          createdBy: Option[String],
                          smrtlinkVersion: Option[String],
                          smrtlinkToolsVersion: Option[String],
                          isActive: Boolean = true) {
    def toEngineJob: BaseLine.EngineJob = BaseLine.EngineJob(
      id,
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
      smrtlinkVersion,
      isActive,
      errorMessage = None,
      projectId = 1)
  }

  case class LegacyJobEvent(
                         eventId: UUID,
                         jobId: Int,
                         state: AnalysisJobStates.JobStates,
                         message: String,
                         createdAt: JodaDateTime) {
    def toJobEvent: BaseLine.JobEvent = BaseLine.JobEvent(eventId, jobId, state, message, createdAt)
  }

  case class LegacyCollectionMetadata(
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
                                       terminationInfo: Option[String]) {
    def toCollectionMetadata = BaseLine.CollectionMetadata(
      runId,
      uniqueId,
      well,
      name,
      summary,
      context,
      collectionPathUri,
      status,
      instrumentId,
      instrumentName,
      movieMinutes,
      createdBy = None,
      startedAt,
      completedAt,
      terminationInfo)
  }

  trait LegacyProjectAble {
    val userId: Int
    val jobId: Int
    val projectId: Int
    val isActive: Boolean
  }

  case class LegacyDataSetMetaDataSet(id: Int, uuid: UUID, name: String, path: String, createdAt: JodaDateTime, updatedAt: JodaDateTime, numRecords: Long, totalLength: Long, tags: String, version: String, comments: String, md5: String, userId: Int, jobId: Int, projectId: Int, isActive: Boolean) extends BaseLine.UniqueIdAble with LegacyProjectAble {

    def toDataSetaMetaDataSet = BaseLine.DataSetMetaDataSet(
      id,
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
      None,
      jobId,
      projectId,
      isActive
    )
  }

  case class LegacyProject(id: Int,
                           name: String,
                           description: String,
                           state: BaseLine.ProjectState.ProjectState, // This remained unchanged. Reusing this model
                           createdAt: JodaDateTime,
                           updatedAt: JodaDateTime,
                           isActive: Boolean) {
     def toProject = BaseLine.Project(
       id,
       name,
       description,
       state,
       createdAt,
       updatedAt,
       isActive,
       grantRoleToAll = None)
  }
}


object LegacySqliteReader extends PacBioDateTimeDatabaseFormat {

  // Any model that is the didn't have a change is referenced from the BaseLine
  import LegacyModels._
  import slick.driver.SQLiteDriver.api._

  implicit val jobStateType = MappedColumnType.base[AnalysisJobStates.JobStates, String](
  { s => s.toString }, { s => AnalysisJobStates.toState(s).getOrElse(AnalysisJobStates.UNKNOWN) }
  )


  class JobEventsT(tag: Tag) extends Table[LegacyModels.LegacyJobEvent](tag, "job_events") {
    def id: Rep[UUID] = column[UUID]("job_event_id", O.PrimaryKey)

    def state: Rep[AnalysisJobStates.JobStates] = column[AnalysisJobStates.JobStates]("state")

    def jobId: Rep[Int] = column[Int]("job_id")

    def message: Rep[String] = column[String]("message")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def jobFK = foreignKey("job_fk", jobId, engineJobs)(_.id)

    def jobJoin = engineJobs.filter(_.id === jobId)

    def * = (id, jobId, state, message, createdAt) <>(LegacyJobEvent.tupled, LegacyJobEvent.unapply)

    def idx = index("job_events_job_id", jobId)
  }

  class EngineJobsT(tag: Tag) extends Table[LegacyEngineJob](tag, "engine_jobs") {
    def id: Rep[Int] = column[Int]("job_id", O.PrimaryKey, O.AutoInc)

    def uuid: Rep[UUID] = column[UUID]("uuid")

    def pipelineId: Rep[String] = column[String]("pipeline_id")

    def name: Rep[String] = column[String]("name")

    def state: Rep[AnalysisJobStates.JobStates] = column[AnalysisJobStates.JobStates]("state")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    def jobTypeId: Rep[String] = column[String]("job_type_id")

    def path: Rep[String] = column[String]("path", O.Length(500, varying = true))

    def jsonSettings: Rep[String] = column[String]("json_settings")

    def createdBy: Rep[Option[String]] = column[Option[String]]("created_by")

    def smrtLinkVersion: Rep[Option[String]] = column[Option[String]]("smrtlink_version")

    def smrtLinkToolsVersion: Rep[Option[String]] = column[Option[String]]("smrtlink_tools_version")

    def isActive: Rep[Boolean] = column[Boolean]("is_active", O.Default(true))

    def findByUUID(uuid: UUID) = engineJobs.filter(_.uuid === uuid)

    def findById(i: Int) = engineJobs.filter(_.id === i)

    def * = (id, uuid, name, pipelineId, createdAt, updatedAt, state, jobTypeId, path, jsonSettings, createdBy, smrtLinkVersion, smrtLinkToolsVersion, isActive) <>(LegacyEngineJob.tupled, LegacyEngineJob.unapply)

    def uuidIdx = index("engine_jobs_uuid", uuid)

    def typeIdx = index("engine_jobs_job_type", jobTypeId)
  }

  implicit val projectStateType = MappedColumnType.base[BaseLine.ProjectState.ProjectState, String](
  { s => s.toString }, { s => BaseLine.ProjectState.fromString(s) }
  )

  class ProjectsT(tag: Tag) extends Table[LegacyProject](tag, "projects") {
    def id: Rep[Int] = column[Int]("project_id", O.PrimaryKey, O.AutoInc)

    def name: Rep[String] = column[String]("name")

    def nameUnique = index("project_name_unique", name, unique = true)

    def description: Rep[String] = column[String]("description")

    def state: Rep[BaseLine.ProjectState.ProjectState] = column[BaseLine.ProjectState.ProjectState]("state")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def updatedAt: Rep[JodaDateTime] = column[JodaDateTime]("updated_at")

    def isActive: Rep[Boolean] = column[Boolean]("is_active")

    def * = (id, name, description, state, createdAt, updatedAt, isActive) <>(LegacyProject.tupled, LegacyProject.unapply)
  }

  implicit val projectUserRoleType = MappedColumnType.base[BaseLine.ProjectUserRole.ProjectUserRole, String](
  { r => r.toString }, { r => BaseLine.ProjectUserRole.fromString(r) }
  )

  class ProjectsUsersT(tag: Tag) extends Table[BaseLine.ProjectUser](tag, "projects_users") {
    def projectId: Rep[Int] = column[Int]("project_id")

    def login: Rep[String] = column[String]("login")

    def role: Rep[BaseLine.ProjectUserRole.ProjectUserRole] = column[BaseLine.ProjectUserRole.ProjectUserRole]("role")

    def projectFK = foreignKey("project_fk", projectId, projects)(a => a.id)

    def * = (projectId, login, role) <>(BaseLine.ProjectUser.tupled, BaseLine.ProjectUser.unapply)

    def loginIdx = index("projects_users_login", login)

    def projectIdIdx = index("projects_users_project_id", projectId)
  }

  abstract class IdAbleTable[T](tag: Tag, tableName: String) extends Table[T](tag, tableName) {
    def id: Rep[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)

    def uuid: Rep[UUID] = column[UUID]("uuid")
  }

  class EngineJobDataSetT(tag: Tag) extends Table[BaseLine.EngineJobEntryPoint](tag, "engine_jobs_datasets") {
    def jobId: Rep[Int] = column[Int]("job_id")

    def datasetUUID: Rep[UUID] = column[UUID]("dataset_uuid")

    def datasetType: Rep[String] = column[String]("dataset_type")

    def * = (jobId, datasetUUID, datasetType) <>(BaseLine.EngineJobEntryPoint.tupled, BaseLine.EngineJobEntryPoint.unapply)

    def idx = index("engine_jobs_datasets_job_id", jobId)
  }

  class DataSetMetaT(tag: Tag) extends IdAbleTable[LegacyDataSetMetaDataSet](tag, "dataset_metadata") {
    def name: Rep[String] = column[String]("name")

    def path: Rep[String] = column[String]("path", O.Length(500, varying = true))

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

    def * = (id, uuid, name, path, createdAt, updatedAt, numRecords, totalLength, tags, version, comments, md5, userId, jobId, projectId, isActive) <>(LegacyDataSetMetaDataSet.tupled, LegacyDataSetMetaDataSet.unapply)

    def uuidIdx = index("dataset_metadata_uuid", uuid)

    def projectIdIdx = index("dataset_metadata_project_id", projectId)
  }

  class SubreadDataSetT(tag: Tag) extends IdAbleTable[BaseLine.SubreadServiceSet](tag, "dataset_subreads") {
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

    def * = (id, uuid, cellId, metadataContextId, wellSampleName, wellName, bioSampleName, cellIndex, instrumentId, instrumentName, runName, instrumentControlVersion) <>(BaseLine.SubreadServiceSet.tupled, BaseLine.SubreadServiceSet.unapply)
  }

  class HdfSubreadDataSetT(tag: Tag) extends IdAbleTable[BaseLine.HdfSubreadServiceSet](tag, "dataset_hdfsubreads") {
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

    def * = (id, uuid, cellId, metadataContextId, wellSampleName, wellName, bioSampleName, cellIndex, instrumentId, instrumentName, runName, instrumentControlVersion) <>(BaseLine.HdfSubreadServiceSet.tupled, BaseLine.HdfSubreadServiceSet.unapply)
  }

  class ReferenceDataSetT(tag: Tag) extends IdAbleTable[BaseLine.ReferenceServiceSet](tag, "dataset_references") {
    def ploidy: Rep[String] = column[String]("ploidy")

    def organism: Rep[String] = column[String]("organism")

    def * = (id, uuid, ploidy, organism) <>(BaseLine.ReferenceServiceSet.tupled, BaseLine.ReferenceServiceSet.unapply)
  }

  class GmapReferenceDataSetT(tag: Tag) extends IdAbleTable[BaseLine.GmapReferenceServiceSet](tag, "dataset_gmapreferences") {
    def ploidy: Rep[String] = column[String]("ploidy")

    def organism: Rep[String] = column[String]("organism")

    def * = (id, uuid, ploidy, organism) <>(BaseLine.GmapReferenceServiceSet.tupled, BaseLine.GmapReferenceServiceSet.unapply)
  }

  class AlignmentDataSetT(tag: Tag) extends IdAbleTable[BaseLine.AlignmentServiceSet](tag, "datasets_alignments") {
    def * = (id, uuid) <>(BaseLine.AlignmentServiceSet.tupled, BaseLine.AlignmentServiceSet.unapply)
  }

  class BarcodeDataSetT(tag: Tag) extends IdAbleTable[BaseLine.BarcodeServiceSet](tag, "datasets_barcodes") {
    def * = (id, uuid) <>(BaseLine.BarcodeServiceSet.tupled, BaseLine.BarcodeServiceSet.unapply)
  }

  class CCSreadDataSetT(tag: Tag) extends IdAbleTable[BaseLine.ConsensusReadServiceSet](tag, "datasets_ccsreads") {
    def * = (id, uuid) <>(BaseLine.ConsensusReadServiceSet.tupled, BaseLine.ConsensusReadServiceSet.unapply)
  }

  class ConsensusAlignmentDataSetT(tag: Tag) extends IdAbleTable[BaseLine.ConsensusAlignmentServiceSet](tag, "datasets_ccsalignments") {
    def * = (id, uuid) <>(BaseLine.ConsensusAlignmentServiceSet.tupled, BaseLine.ConsensusAlignmentServiceSet.unapply)
  }

  class ContigDataSetT(tag: Tag) extends IdAbleTable[BaseLine.ContigServiceSet](tag, "datasets_contigs") {
    def * = (id, uuid) <>(BaseLine.ContigServiceSet.tupled, BaseLine.ContigServiceSet.unapply)
  }

  class PacBioDataStoreFileT(tag: Tag) extends Table[BaseLine.DataStoreServiceFile](tag, "datastore_files") {
    def uuid: Rep[UUID] = column[UUID]("uuid", O.PrimaryKey)

    def fileTypeId: Rep[String] = column[String]("file_type_id")

    def sourceId: Rep[String] = column[String]("source_id")

    def fileSize: Rep[Long] = column[Long]("file_size")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("created_at")

    def modifiedAt: Rep[JodaDateTime] = column[JodaDateTime]("modified_at")

    def importedAt: Rep[JodaDateTime] = column[JodaDateTime]("imported_at")

    def path: Rep[String] = column[String]("path", O.Length(500, varying = true))

    def jobId: Rep[Int] = column[Int]("job_id")

    def jobUUID: Rep[UUID] = column[UUID]("job_uuid")

    def name: Rep[String] = column[String]("name")

    def description: Rep[String] = column[String]("description")

    def isActive: Rep[Boolean] = column[Boolean]("is_active", O.Default(true))

    def * = (uuid, fileTypeId, sourceId, fileSize, createdAt, modifiedAt, importedAt, path, jobId, jobUUID, name, description, isActive) <>(BaseLine.DataStoreServiceFile.tupled, BaseLine.DataStoreServiceFile.unapply)

    def uuidIdx = index("datastore_files_uuid", uuid)

    def jobIdIdx = index("datastore_files_job_id", jobId)

    def jobUuidIdx = index("datastore_files_job_uuid", jobUUID)
  }

  implicit val runStatusType = MappedColumnType.base[SupportedRunStates, String](
  { s => s.value() }, { s => SupportedRunStates.fromValue(s) }
  )

  class RunSummariesT(tag: Tag) extends Table[BaseLine.RunSummary](tag, "RUN_SUMMARIES") {
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
      reserved) <>(BaseLine.RunSummary.tupled, BaseLine.RunSummary.unapply)
  }

  class DataModelsT(tag: Tag) extends Table[DataModelAndUniqueId](tag, "DATA_MODELS") {
    def uniqueId: Rep[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    def dataModel: Rep[String] = column[String]("DATA_MODEL")

    def * = (dataModel, uniqueId) <>(DataModelAndUniqueId.tupled, DataModelAndUniqueId.unapply)

    def summary = foreignKey("SUMMARY_FK", uniqueId, runSummaries)(_.uniqueId)
  }

  implicit val pathType = MappedColumnType.base[Path, String](_.toString, Paths.get(_))
  implicit val collectionStatusType =
    MappedColumnType.base[SupportedAcquisitionStates, String](_.value(), SupportedAcquisitionStates.fromValue)

  class CollectionMetadataT(tag: Tag) extends Table[LegacyCollectionMetadata](tag, "COLLECTION_METADATA") {
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
      terminationInfo) <>(LegacyCollectionMetadata.tupled, LegacyCollectionMetadata.unapply)

    def idx = index("collection_metadata_run_id", runId)
  }

  class SampleT(tag: Tag) extends Table[BaseLine.Sample](tag, "SAMPLE") {
    def details: Rep[String] = column[String]("DETAILS")

    def uniqueId: Rep[UUID] = column[UUID]("UNIQUE_ID", O.PrimaryKey)

    def name: Rep[String] = column[String]("NAME")

    def createdBy: Rep[String] = column[String]("CREATED_BY")

    def createdAt: Rep[JodaDateTime] = column[JodaDateTime]("CREATED_AT")

    def * = (details, uniqueId, name, createdBy, createdAt) <>(BaseLine.Sample.tupled, BaseLine.Sample.unapply)
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

  // Runs
  lazy val runSummaries = TableQuery[RunSummariesT]
  lazy val dataModels = TableQuery[DataModelsT]
  lazy val collectionMetadata = TableQuery[CollectionMetadataT]

  // Samples
  lazy val samples = TableQuery[SampleT]


  final type SlickTable = TableQuery[_ <: Table[_]]
}

class LegacySqliteReader(legacyDbUri: String) {
  import LegacySqliteReader._
  import slick.driver.SQLiteDriver.api._

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

  // Removed the partial unique index on name
  /**
    * Removed the partial unique index on name that was based on isActive
    *
    * To avoid making more changes to project related entities, take
    * a simple/stupid fix of name to make it unique with a UUID
    *
    * All non Active project names will get converted to {name} => {name}_{UUID}
    * to create a globally unique project name.
    *
    * @param projects
    */
  def fixProjectNames(projects: Seq[BaseLine.Project]):Seq[BaseLine.Project] = {
    projects.map { p =>
      if (p.isActive) p
      else p.copy(name = s"${p.name}_${UUID.randomUUID()}")
    }
  }

  def read(): Future[MigrationData] = {
    val adminProject = BaseLine.ProjectUser(1, "admin", BaseLine.ProjectUserRole.OWNER)
    val action = for {
      ps  <- projects.result
      psu <- (projectsUsers join projects on (_.projectId === _.id)).result
      ej  <- engineJobs.result
      ejd <- engineJobsDataSets.result
      je  <- (jobEvents join engineJobs on (_.jobId === _.id)).result
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
    } yield MigrationData(fixProjectNames(ps.filter(_.id != 1).map(_.toProject)), psu.map(_._1).filter(_ != adminProject), ej.map(_.toEngineJob), ejd, je.map(_._1.toJobEvent), dmd.map(_.toDataSetaMetaDataSet), dsu, dhs, dre, dal, dba, dcc, dgr, dca, dco, dsf, /* eu, */ rs, dm.map(_._1), cm.map(_._1.toCollectionMetadata), sa)
    db.run(action).andThen { case _ => db.close() }.andThen { case _ => connectionPool.close() }
  }
}
