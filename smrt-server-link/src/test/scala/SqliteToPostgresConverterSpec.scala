import java.io.File
import java.nio.file.Paths
import java.util.UUID

import com.pacbio.common.time.FakeClock
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes.Subread
import com.pacbio.secondary.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.actors.TestDalProvider
import com.pacbio.secondary.smrtlink.database.TableModels
import com.pacbio.secondary.smrtlink.database.TableModels.DataModelAndUniqueId
import com.pacbio.secondary.smrtlink.database.legacy._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.testkit.TestUtils
import com.pacificbiosciences.pacbiobasedatamodel.{SupportedAcquisitionStates, SupportedRunStates}
import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import resource._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.language.reflectiveCalls

class SqliteToPostgresConverterSpec extends Specification with Specs2RouteTest with TestDalProvider with TestUtils {

  sequential

  val clock = new FakeClock(1000000000L, 1, autoStep = false)
  val now = clock.dateNow()
  val jobId = 1
  val jobUUID = UUID.randomUUID()
  val projectId = 2
  val runId = UUID.randomUUID()

  val baseMetaData = DataSetMetaDataSet(1, UUID.randomUUID(), "name", "/path/to", now, now, 1, 1, "tags", "1.2.3", "comments", "md5", None, jobId, projectId, isActive = true)
  def idAbleToMetaData(idAble: {val id: Int; val uuid: UUID}) = baseMetaData.copy(id = idAble.id, uuid = idAble.uuid)
  def addMetaData(data: MigrationData) = {
    val meta = (data.dsSubread2 ++
      data.dsHdfSubread2 ++
      data.dsReference2 ++
      data.dsAlignment2 ++
      data.dsBarcode2 ++
      data.dsCCSread2 ++
      data.dsGmapReference2 ++
      data.dsCCSAlignment2 ++
      data.dsContig2).map(idAbleToMetaData)
    data.copy(dsMetaData2 = meta)
  }

  val data = addMetaData(MigrationData(
    Seq(EngineJob(jobId, jobUUID, "name", "comment", now, now, AnalysisJobStates.FAILED, 1, JobTypeIds.PBSMRTPIPE.id, "/path/to", "{}", Some("jsnow"), Some("1.2.3"), Some("3.2.1"), isActive = false, None)),
    Seq(EngineJobEntryPoint(jobId, UUID.randomUUID(), "type")),
    Seq(JobEvent(UUID.randomUUID(), jobId, AnalysisJobStates.FAILED, "oops", now, JobConstants.EVENT_TYPE_JOB_STATUS)),
    Seq(Project(projectId, "name", "description", ProjectState.UPDATED, now, now, isActive = false)),
    Seq(ProjectUser(projectId, "jsnow", ProjectUserRole.OWNER)),
    Nil, // Will be added by addMetaData
    Seq(SubreadServiceSet(1, UUID.randomUUID(), "cellId", "metadataContextId", "wellSampleName", "wellName", "bioSampleName", 1, "instrumentId", "instrumentName", "runName", "instrumentControlVersion")),
    Seq(HdfSubreadServiceSet(2, UUID.randomUUID(), "cellId", "metadataContextId", "wellSampleName", "wellName", "bioSampleName", 1, "instrumentId", "instrumentName", "runName", "instrumentControlVersion")),
    Seq(ReferenceServiceSet(3, UUID.randomUUID(), "ploidy", "organism")),
    Seq(AlignmentServiceSet(4, UUID.randomUUID())),
    Seq(BarcodeServiceSet(5, UUID.randomUUID())),
    Seq(ConsensusReadServiceSet(6, UUID.randomUUID())),
    Seq(GmapReferenceServiceSet(7, UUID.randomUUID(), "ploidy", "organism")),
    Seq(ConsensusAlignmentServiceSet(8, UUID.randomUUID())),
    Seq(ContigServiceSet(9, UUID.randomUUID())),
    Seq(DataStoreServiceFile(UUID.randomUUID(), Subread.toString, "sourceId", 1, now, now, now, "/path/to", jobId, jobUUID, "name", "description", isActive = false)),
    Seq(RunSummary(runId, "name", Some("summary"), Some("jsnow"), Some(now), Some(now), Some(now), Some(now), SupportedRunStates.COMPLETE, 1, 1, 0, Some("instrumentName"), Some("instrumentSerialNumber"), Some("instrumentSwVersion"), Some("primaryAnalysisSwVersion"), Some("context"), Some("terminationInfo"), reserved = false)),
    Seq(DataModelAndUniqueId("<xml></xml>", runId)),
    Seq(CollectionMetadata(runId, UUID.randomUUID(), "well", "name", Some("summary"), Some("context"), Some(Paths.get("/path/to")), SupportedAcquisitionStates.COMPLETE, Some("instrumentId"), Some("instrumentName"), 1.0, None, Some(now), Some(now), Some("terminationInfo"))),
    Seq(Sample("details", UUID.randomUUID(), "name", "jsnow", now))))

  def createTestSqliteDb(): File = {
    import LegacyModels._
    import LegacySqliteReader._
    import slick.driver.SQLiteDriver.api._

    def toLegacyEngineJob(j: EngineJob) = LegacyEngineJob(j.id, j.uuid, j.name, j.comment, j.createdAt, j.updatedAt, j.state, j.jobTypeId, j.path, j.jsonSettings, j.createdBy, j.smrtlinkVersion, j.smrtlinkToolsVersion, j.isActive)
    def toLegacyJobEvent(e: JobEvent) = LegacyJobEvent(e.eventId, e.jobId, e.state, e.message, e.createdAt)
    def toLegacyDataSetMetaDataSet(s: DataSetMetaDataSet) = LegacyDataSetMetaDataSet(s.id, s.uuid, s.name, s.path, s.createdAt, s.updatedAt, s.numRecords, s.totalLength, s.tags, s.version, s.comments, s.md5, -1, s.jobId, s.projectId, s.isActive)
    def toLegacyCollectionMetadata(m: CollectionMetadata) = LegacyCollectionMetadata(m.runId, m.uniqueId, m.well, m.name, m.summary, m.context, m.collectionPathUri, m.status, m.instrumentId, m.instrumentName, m.movieMinutes, m.startedAt, m.completedAt, m.terminationInfo)

    val dbFile = File.createTempFile("sqlite-test", ".db")
    val dbUri = SqliteToPostgresConverter.toSqliteURI(dbFile)

    val schema = engineJobs.schema ++
      engineJobsDataSets.schema ++
      jobEvents.schema ++
      projects.schema ++
      projectsUsers.schema ++
      dsMetaData2.schema ++
      dsSubread2.schema ++
      dsHdfSubread2.schema ++
      dsReference2.schema ++
      dsAlignment2.schema ++
      dsBarcode2.schema ++
      dsCCSread2.schema ++
      dsGmapReference2.schema ++
      dsCCSAlignment2.schema ++
      dsContig2.schema ++
      datastoreServiceFiles.schema ++
      runSummaries.schema ++
      dataModels.schema ++
      collectionMetadata.schema ++
      samples.schema

    val action = schema.create >>
      // Add static rows
      (projects forceInsert Project(1, "General Project", "General SMRT Link project. By default all imported datasets and analysis jobs will be assigned to this project", ProjectState.CREATED, now, now, isActive = true)) >>
      (projectsUsers forceInsert ProjectUser(1, "admin", ProjectUserRole.OWNER)) >>
      // Add test data
      (engineJobs forceInsertAll data.engineJobs.map(toLegacyEngineJob)) >>
      (engineJobsDataSets forceInsertAll data.engineJobsDataSets) >>
      (jobEvents forceInsertAll data.jobEvents.map(toLegacyJobEvent)) >>
      (projects forceInsertAll data.projects) >>
      (projectsUsers forceInsertAll data.projectsUsers) >>
      (dsMetaData2 forceInsertAll data.dsMetaData2.map(toLegacyDataSetMetaDataSet)) >>
      (dsSubread2 forceInsertAll data.dsSubread2) >>
      (dsHdfSubread2 forceInsertAll data.dsHdfSubread2) >>
      (dsReference2 forceInsertAll data.dsReference2) >>
      (dsAlignment2 forceInsertAll data.dsAlignment2) >>
      (dsBarcode2 forceInsertAll data.dsBarcode2) >>
      (dsCCSread2 forceInsertAll data.dsCCSread2) >>
      (dsGmapReference2 forceInsertAll data.dsGmapReference2) >>
      (dsCCSAlignment2 forceInsertAll data.dsCCSAlignment2) >>
      (dsContig2 forceInsertAll data.dsContig2) >>
      (datastoreServiceFiles forceInsertAll data.datastoreServiceFiles) >>
      (runSummaries forceInsertAll data.runSummaries) >>
      (dataModels forceInsertAll data.dataModels) >>
      (collectionMetadata forceInsertAll data.collectionMetadata.map(toLegacyCollectionMetadata)) >>
      (samples forceInsertAll data.samples) >>
      // Add rows with broken foreign keys that should be ignored
      (jobEvents forceInsert LegacyJobEvent(UUID.randomUUID(), 999, AnalysisJobStates.RUNNING, "ignore", now)) >>
      (projectsUsers forceInsert ProjectUser(999, "ignore", ProjectUserRole.OWNER)) >>
      (dataModels forceInsert DataModelAndUniqueId("<xml>ignore</xml>", UUID.randomUUID())) >>
      (collectionMetadata forceInsert LegacyCollectionMetadata(UUID.randomUUID(), UUID.randomUUID(), "ignore", "ignore", None, None, None, SupportedAcquisitionStates.ABORTED, None, None, 1.0, None, None, None))

    val sqliteDb = Database.forURL(dbUri)
    Await.result(sqliteDb.run(action.transactionally).andThen { case _ => sqliteDb.close() }, Duration.Inf)

    dbFile
  }

  def writeResultAssertions(db: slick.driver.PostgresDriver.api.Database) = {
    import TableModels._
    import slick.driver.PostgresDriver.api._

    Await.result(db.run(engineJobs.result), Duration.Inf) === data.engineJobs
    Await.result(db.run(engineJobsDataSets.result), Duration.Inf) === data.engineJobsDataSets
    Await.result(db.run(jobEvents.result), Duration.Inf) === data.jobEvents
    Await.result(db.run(projects.filter(_.id =!= 1).result), Duration.Inf) === data.projects
    Await.result(db.run(projectsUsers.filter(_.projectId =!= 1).result), Duration.Inf) === data.projectsUsers
    Await.result(db.run(dsMetaData2.result), Duration.Inf) === data.dsMetaData2
    Await.result(db.run(dsSubread2.result), Duration.Inf) === data.dsSubread2
    Await.result(db.run(dsHdfSubread2.result), Duration.Inf) === data.dsHdfSubread2
    Await.result(db.run(dsReference2.result), Duration.Inf) === data.dsReference2
    Await.result(db.run(dsAlignment2.result), Duration.Inf) === data.dsAlignment2
    Await.result(db.run(dsBarcode2.result), Duration.Inf) === data.dsBarcode2
    Await.result(db.run(dsCCSread2.result), Duration.Inf) === data.dsCCSread2
    Await.result(db.run(dsGmapReference2.result), Duration.Inf) === data.dsGmapReference2
    Await.result(db.run(dsCCSAlignment2.result), Duration.Inf) === data.dsCCSAlignment2
    Await.result(db.run(dsContig2.result), Duration.Inf) === data.dsContig2
    Await.result(db.run(datastoreServiceFiles.result), Duration.Inf) === data.datastoreServiceFiles
    Await.result(db.run(runSummaries.result), Duration.Inf) === data.runSummaries
    Await.result(db.run(dataModels.result), Duration.Inf) === data.dataModels
    Await.result(db.run(collectionMetadata.result), Duration.Inf) === data.collectionMetadata
    Await.result(db.run(samples.result), Duration.Inf) === data.samples

    // Test autoinc values
    Await.result(db.run(sql"SELECT last_value FROM engine_jobs_job_id_seq;".as[Int].map(_.head)), Duration.Inf) === 2
    Await.result(db.run(sql"SELECT last_value FROM projects_project_id_seq;".as[Int].map(_.head)), Duration.Inf) === 3
    Await.result(db.run(sql"SELECT last_value FROM dataset_metadata_id_seq;".as[Int].map(_.head)), Duration.Inf) === 10
    Await.result(db.run(sql"SELECT last_value FROM dataset_subreads_id_seq;".as[Int].map(_.head)), Duration.Inf) === 2
    Await.result(db.run(sql"SELECT last_value FROM dataset_hdfsubreads_id_seq;".as[Int].map(_.head)), Duration.Inf) === 3
    Await.result(db.run(sql"SELECT last_value FROM dataset_references_id_seq;".as[Int].map(_.head)), Duration.Inf) === 4
    Await.result(db.run(sql"SELECT last_value FROM datasets_alignments_id_seq;".as[Int].map(_.head)), Duration.Inf) === 5
    Await.result(db.run(sql"SELECT last_value FROM datasets_barcodes_id_seq;".as[Int].map(_.head)), Duration.Inf) === 6
    Await.result(db.run(sql"SELECT last_value FROM datasets_ccsreads_id_seq;".as[Int].map(_.head)), Duration.Inf) === 7
    Await.result(db.run(sql"SELECT last_value FROM dataset_gmapreferences_id_seq;".as[Int].map(_.head)), Duration.Inf) === 8
    Await.result(db.run(sql"SELECT last_value FROM datasets_ccsalignments_id_seq;".as[Int].map(_.head)), Duration.Inf) === 9
    Await.result(db.run(sql"SELECT last_value FROM datasets_contigs_id_seq;".as[Int].map(_.head)), Duration.Inf) === 10
  }

  "SqliteToPostgresConverter" should {
    "read from sqlite" in {
      val dbFile = createTestSqliteDb()
      val dbUri = SqliteToPostgresConverter.toSqliteURI(dbFile)
      val reader = new LegacySqliteReader(dbUri)

      val res = Await.result(reader.read(), Duration.Inf)

      res.engineJobs === data.engineJobs
      res.engineJobsDataSets === data.engineJobsDataSets
      res.jobEvents === data.jobEvents
      res.projects === data.projects
      res.projectsUsers === data.projectsUsers
      res.dsMetaData2 === data.dsMetaData2
      res.dsSubread2 === data.dsSubread2
      res.dsHdfSubread2 === data.dsHdfSubread2
      res.dsReference2 === data.dsReference2
      res.dsAlignment2 === data.dsAlignment2
      res.dsBarcode2 === data.dsBarcode2
      res.dsCCSread2 === data.dsCCSread2
      res.dsGmapReference2 === data.dsGmapReference2
      res.dsCCSAlignment2 === data.dsCCSAlignment2
      res.dsContig2 === data.dsContig2
      res.datastoreServiceFiles === data.datastoreServiceFiles
      res.runSummaries === data.runSummaries
      res.dataModels === data.dataModels
      res.collectionMetadata === data.collectionMetadata
      res.samples === data.samples
    }

    "write to postgres" in {
      import TableModels._
      import slick.driver.PostgresDriver.api._

      setupDb(dbConfig)
      managed(dbConfig.toDatabase) acquireAndGet { db =>
        val writer = new PostgresWriter(db, dbConfig.username, clock)

        Await.result(writer.write(Future.successful(data)), Duration.Inf)

        writeResultAssertions(db)

        // Test migration status table
        Await.result(db.run(migrationStatus.result), Duration.Inf) === Seq(MigrationStatusRow(now.toString("YYYY-MM-dd HH:mm:ss.SSS"), success = true, error = None))

        // Test autoinc works with new insertions
        Await.result(db.run((dsContig2 returning dsContig2.map(_.id)) += ContigServiceSet(-1, UUID.randomUUID())), Duration.Inf) === 11
        Await.result(db.run(sql"SELECT last_value FROM datasets_contigs_id_seq;".as[Int].map(_.head)), Duration.Inf) === 11
      }
    }

    "read from sqlite and write to postgres" in {
      setupDb(dbConfig)

      val dbFile = createTestSqliteDb()

      val opts = SqliteToPostgresConverterOptions(dbFile, dbConfig.username, dbConfig.password, dbConfig.dbName, dbConfig.server, dbConfig.port)

      SqliteToPostgresConverter.runImporter(opts) === s"Successfully migrated data from $dbFile into Postgres"

      managed(dbConfig.toDatabase) acquireAndGet { db =>
        writeResultAssertions(db)
      }
    }

    "skip migration if already complete" in {
      val dbFile = createTestSqliteDb()

      val opts = SqliteToPostgresConverterOptions(dbFile, dbConfig.username, dbConfig.password, dbConfig.dbName, dbConfig.server, dbConfig.port)

      SqliteToPostgresConverter.runImporter(opts) must startWith("Previous import at")
    }

    "handle failed write" in {
      import TableModels._
      import slick.driver.PostgresDriver.api._

      setupDb(dbConfig)
      managed(dbConfig.toDatabase) acquireAndGet { db =>
        val writer = new PostgresWriter(db, dbConfig.username, clock)

        val dupId = UUID.randomUUID()
        val badData = MigrationData(Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil,
          Seq(DataModelAndUniqueId("<xml>bad</xml>", dupId), DataModelAndUniqueId("<xml>bad</xml>", dupId)), Nil, Nil)

        // Try to write bad data
        Await.ready(writer.write(Future.successful(badData)), Duration.Inf)

        // Test migration status table
        val row = Await.result(db.run(migrationStatus.result), Duration.Inf).head
        row.timestamp === now.toString("YYYY-MM-dd HH:mm:ss.SSS")
        row.success === false
        row.error must beSome

        // Write good data
        Await.result(writer.write(Future.successful(data)), Duration.Inf)

        writeResultAssertions(db)

        // Test migration status table
        val rows = Await.result(db.run(migrationStatus.result), Duration.Inf)
        rows.size === 2
        rows.filter(_.success == true) === Seq(MigrationStatusRow(now.toString("YYYY-MM-dd HH:mm:ss.SSS"), success = true, error = None))
      }
    }
  }
}
