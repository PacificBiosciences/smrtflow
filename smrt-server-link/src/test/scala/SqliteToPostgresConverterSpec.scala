import java.io.File
import java.nio.file.Paths
import java.util.UUID

import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes.Subread
import com.pacbio.secondary.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.analysis.jobs.JobModels.{EngineJob, JobConstants, JobEvent, JobTypeIds}
import com.pacbio.secondary.smrtlink.actors.TestDalProvider
import com.pacbio.secondary.smrtlink.database.TableModels
import com.pacbio.secondary.smrtlink.database.TableModels.DataModelAndUniqueId
import com.pacbio.secondary.smrtlink.database.legacy._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.testkit.TestUtils
import com.pacificbiosciences.pacbiobasedatamodel.{SupportedAcquisitionStates, SupportedRunStates}
import org.joda.time.{DateTime => JodaDateTime}
import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class SqliteToPostgresConverterSpec extends Specification with Specs2RouteTest with TestDalProvider with TestUtils {

  val now = JodaDateTime.now()
  val jobId = 1
  val jobUUID = UUID.randomUUID()
  val runId = UUID.randomUUID()

  val data = MigrationData(
    Seq(EngineJob(jobId, jobUUID, "name", "comment", now, now, AnalysisJobStates.FAILED, JobTypeIds.PBSMRTPIPE.id, "/path/to", "{}", Some("jsnow"), Some("1.2.3"), Some("3.2.1"), isActive = false, Some("oops"))),
    Seq(EngineJobEntryPoint(jobId, UUID.randomUUID(), "type")),
    Seq(JobEvent(UUID.randomUUID(), jobId, AnalysisJobStates.FAILED, "oops", now, JobConstants.EVENT_TYPE_JOB_STATUS)),
    Seq(Project(1, "name", "description", ProjectState.UPDATED, now, now, isActive = false)),
    Seq(ProjectUser(1, "jsnow", ProjectUserRole.OWNER)),
    Seq(DataSetMetaDataSet(1, UUID.randomUUID(), "name", "/path/to", now, now, 1, 1, "tags", "1.2.3", "comments", "md5", Some("jsnow"), jobId, 1, isActive = false)),
    Seq(SubreadServiceSet(1, UUID.randomUUID(), "cellId", "metadataContextId", "wellSampleName", "wellName", "bioSampleName", 1, "instrumentId", "instrumentName", "runName", "instrumentControlVersion")),
    Seq(HdfSubreadServiceSet(1, UUID.randomUUID(), "cellId", "metadataContextId", "wellSampleName", "wellName", "bioSampleName", 1, "instrumentId", "instrumentName", "runName", "instrumentControlVersion")),
    Seq(ReferenceServiceSet(1, UUID.randomUUID(), "ploidy", "organism")),
    Seq(AlignmentServiceSet(1, UUID.randomUUID())),
    Seq(BarcodeServiceSet(1, UUID.randomUUID())),
    Seq(ConsensusReadServiceSet(1, UUID.randomUUID())),
    Seq(GmapReferenceServiceSet(1, UUID.randomUUID(), "ploidy", "organism")),
    Seq(ConsensusAlignmentServiceSet(1, UUID.randomUUID())),
    Seq(ContigServiceSet(1, UUID.randomUUID())),
    Seq(DataStoreServiceFile(UUID.randomUUID(), Subread.toString, "sourceId", 1, now, now, now, "/path/to", jobId, jobUUID, "name", "description", isActive = false)),
    Seq(RunSummary(runId, "name", Some("summary"), Some("jsnow"), Some(now), Some(now), Some(now), Some(now), SupportedRunStates.COMPLETE, 1, 1, 0, Some("instrumentName"), Some("instrumentSerialNumber"), Some("instrumentSwVersion"), Some("primaryAnalysisSwVersion"), Some("context"), Some("terminationInfo"), reserved = false)),
    Seq(DataModelAndUniqueId("<xml></xml>", runId)),
    Seq(CollectionMetadata(runId, UUID.randomUUID(), "well", "name", Some("summary"), Some("context"), Some(Paths.get("/path/to")), SupportedAcquisitionStates.COMPLETE, Some("instrumentId"), Some("instrumentName"), 1.0, Some("jsnow"), Some(now), Some(now), Some("terminationInfo"))),
    Seq(Sample("details", UUID.randomUUID(), "name", "jsnow", now)))

  val testdb = dbConfig.toDatabase
  step(setupDb(dbConfig))

  "SqliteToPostgresConverter" should {
    "read from sqlite" in {
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
        (engineJobs ++= data.engineJobs.map(toLegacyEngineJob)) >>
        (jobEvents ++= data.jobEvents.map(toLegacyJobEvent)) >>
        (projects ++= data.projects) >>
        (projectsUsers ++= data.projectsUsers) >>
        (dsMetaData2 ++= data.dsMetaData2.map(toLegacyDataSetMetaDataSet)) >>
        (dsSubread2 ++= data.dsSubread2) >>
        (dsHdfSubread2 ++= data.dsHdfSubread2) >>
        (dsReference2 ++= data.dsReference2) >>
        (dsAlignment2 ++= data.dsAlignment2) >>
        (dsBarcode2 ++= data.dsBarcode2) >>
        (dsCCSread2 ++= data.dsCCSread2) >>
        (dsGmapReference2 ++= data.dsGmapReference2) >>
        (dsCCSAlignment2 ++= data.dsCCSAlignment2) >>
        (dsContig2 ++= data.dsContig2) >>
        (datastoreServiceFiles ++= data.datastoreServiceFiles) >>
        (runSummaries ++= data.runSummaries) >>
        (dataModels ++= data.dataModels) >>
        (collectionMetadata ++= data.collectionMetadata.map(toLegacyCollectionMetadata)) >>
        (samples ++= data.samples)

      val sqliteDb = Database.forURL(dbUri)
      Await.result(sqliteDb.run(action).andThen { case _ => sqliteDb.close() }, Duration.Inf)

      val reader = new LegacySqliteReader(dbUri)
      val res = Await.result(reader.read(), Duration.Inf)

      res.engineJobs must beEqualTo(data.engineJobs)
      res.jobEvents must beEqualTo(data.jobEvents)
      res.projects must beEqualTo(data.projects)
      res.projectsUsers must beEqualTo(data.projectsUsers)
      res.dsMetaData2 must beEqualTo(data.dsMetaData2)
      res.dsSubread2 must beEqualTo(data.dsSubread2)
      res.dsHdfSubread2 must beEqualTo(data.dsHdfSubread2)
      res.dsReference2 must beEqualTo(data.dsReference2)
      res.dsAlignment2 must beEqualTo(data.dsAlignment2)
      res.dsBarcode2 must beEqualTo(data.dsBarcode2)
      res.dsCCSread2 must beEqualTo(data.dsCCSread2)
      res.dsGmapReference2 must beEqualTo(data.dsGmapReference2)
      res.dsCCSAlignment2 must beEqualTo(data.dsCCSAlignment2)
      res.dsContig2 must beEqualTo(data.dsContig2)
      res.datastoreServiceFiles must beEqualTo(data.datastoreServiceFiles)
      res.runSummaries must beEqualTo(data.runSummaries)
      res.dataModels must beEqualTo(data.dataModels)
      res.collectionMetadata must beEqualTo(data.collectionMetadata)
      res.samples must beEqualTo(data.samples)
    }

    "write to postgres" in {
      import TableModels._
      import slick.driver.PostgresDriver.api._

      val writer = new PostgresWriter(testdb, dbConfig.username)

      Await.result(writer.write(Future.successful(data)), Duration.Inf)

      Await.result(testdb.run(engineJobs.result), Duration.Inf) must beEqualTo(data.engineJobs)
      Await.result(testdb.run(engineJobsDataSets.result), Duration.Inf) must beEqualTo(data.engineJobsDataSets)
      Await.result(testdb.run(jobEvents.result), Duration.Inf) must beEqualTo(data.jobEvents)
      Await.result(testdb.run(projects.result), Duration.Inf) must beEqualTo(data.projects)
      Await.result(testdb.run(projectsUsers.result), Duration.Inf) must beEqualTo(data.projectsUsers)
      Await.result(testdb.run(dsMetaData2.result), Duration.Inf) must beEqualTo(data.dsMetaData2)
      Await.result(testdb.run(dsSubread2.result), Duration.Inf) must beEqualTo(data.dsSubread2)
      Await.result(testdb.run(dsHdfSubread2.result), Duration.Inf) must beEqualTo(data.dsHdfSubread2)
      Await.result(testdb.run(dsReference2.result), Duration.Inf) must beEqualTo(data.dsReference2)
      Await.result(testdb.run(dsAlignment2.result), Duration.Inf) must beEqualTo(data.dsAlignment2)
      Await.result(testdb.run(dsBarcode2.result), Duration.Inf) must beEqualTo(data.dsBarcode2)
      Await.result(testdb.run(dsCCSread2.result), Duration.Inf) must beEqualTo(data.dsCCSread2)
      Await.result(testdb.run(dsGmapReference2.result), Duration.Inf) must beEqualTo(data.dsGmapReference2)
      Await.result(testdb.run(dsCCSAlignment2.result), Duration.Inf) must beEqualTo(data.dsCCSAlignment2)
      Await.result(testdb.run(dsContig2.result), Duration.Inf) must beEqualTo(data.dsContig2)
      Await.result(testdb.run(datastoreServiceFiles.result), Duration.Inf) must beEqualTo(data.datastoreServiceFiles)
      Await.result(testdb.run(runSummaries.result), Duration.Inf) must beEqualTo(data.runSummaries)
      Await.result(testdb.run(dataModels.result), Duration.Inf) must beEqualTo(data.dataModels)
      Await.result(testdb.run(collectionMetadata.result), Duration.Inf) must beEqualTo(data.collectionMetadata)
      Await.result(testdb.run(samples.result), Duration.Inf) must beEqualTo(data.samples)
    }
  }
}
