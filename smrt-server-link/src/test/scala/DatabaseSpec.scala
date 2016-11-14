import java.io.File
import java.nio.file.Paths
import java.util.UUID

import com.pacbio.common.dependency.Singleton
import com.pacbio.common.time.FakeClockProvider
import com.pacbio.database.Database
import com.pacbio.secondary.analysis.jobs.{AnalysisJobStates, JobModels}
import com.pacbio.secondary.smrtlink.actors.TestDalProvider
import com.pacbio.secondary.smrtlink.database.TableModels
import com.pacbio.secondary.smrtlink.models._
import com.pacificbiosciences.pacbiobasedatamodel.{SupportedAcquisitionStates, SupportedRunStates}
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import slick.driver.SQLiteDriver.api._
import spray.testkit.Specs2RouteTest

import scala.concurrent.Await
import scala.concurrent.duration._


class DatabaseSpec extends Specification with Specs2RouteTest with NoTimeConversions {
  import JobModels._
  import TableModels._

  val EXPECTED_MIGRATIONS = 19

  object TestProvider extends TestDalProvider with FakeClockProvider {
    override val db: Singleton[Database] = Singleton(() => {
      val file = File.createTempFile("database_spec_", ".db")
      file.deleteOnExit()
      val filename = file.getCanonicalPath
      new Database(s"jdbc:sqlite:$filename")
    })
  }

  val clock = TestProvider.clock()

  "Database" should {
    "migrate from 0 -> current" in {
      val db = TestProvider.db()
      if (db.migrationsApplied() == 0) db.migrate()
      db.migrationsApplied() === EXPECTED_MIGRATIONS

      val now = clock.dateNow()
      val username = "user-name"
      val datasetTypeId = "dataset-type-id"

      val job = EngineJob(
        id = -1,
        UUID.randomUUID(),
        "job-name",
        "job-comment",
        createdAt = now,
        updatedAt = now,
        AnalysisJobStates.CREATED,
        "job-type-id",
        "/job/path",
        "{\"foo\":true}",
        Some("jsnow"), Some("0.1.0-SL"), Some("0.1.1-SL-TOOLS"))
      val event = JobEvent(UUID.randomUUID(), jobId = -1, AnalysisJobStates.CREATED, "job-created", createdAt = now)
      val tag = (-1, "tag-name")
      val jTag = (-1, -1)
      val project = Project(
        id = -1,
        "project-name",
        "project-description",
        ProjectState.CREATED,
        createdAt = now,
        updatedAt = now,
        true)
      val projectUser = ProjectUser(projectId = -1, username, ProjectUserRole.OWNER)
      val dataset = EngineJobEntryPoint(jobId = -1, UUID.randomUUID(), datasetTypeId)
      val metadata = DataSetMetaDataSet(
        id = -1,
        UUID.randomUUID(),
        "dataset-name",
        "/dataset/path",
        createdAt = now,
        updatedAt = now,
        numRecords = 1,
        totalLength = 1,
        "tags",
        "version",
        "comments",
        "md5",
        userId = 1,
        jobId = -1,
        projectId = -1,
        isActive = true)
      val subread = SubreadServiceSet(
        id = -1,
        UUID.randomUUID(),
        "cell-id",
        "metadata-context-id",
        "well-sample-name",
        "well-name",
        "bio-sample-name",
        cellIndex = 1,
        "instrument-id",
        "instrument-name",
        "run-name",
        "instrument-control-version")
      val hdf = HdfSubreadServiceSet(
        id = -1,
        UUID.randomUUID(),
        "cell-id",
        "metadata-context-id",
        "well-sample-name",
        "well-name",
        "bio-sample-name",
        cellIndex = 1,
        "instrument-id",
        "instrument-name",
        "run-name",
        "instrument-control-version")
      val reference = ReferenceServiceSet(id = -1, UUID.randomUUID(), "ploidy", "organism")
      val gmap = GmapReferenceServiceSet(id = -1, UUID.randomUUID(), "ploidy", "organism")
      val alignment = AlignmentServiceSet(id = -1, UUID.randomUUID())
      val barcode = BarcodeServiceSet(id = -1, UUID.randomUUID())
      val ccs = ConsensusReadServiceSet(id = -1, UUID.randomUUID())
      val consensus = ConsensusAlignmentServiceSet(id = -1, UUID.randomUUID())
      val contig = ContigServiceSet(id = -1, UUID.randomUUID())
      val datastoreFile = DataStoreServiceFile(
        UUID.randomUUID(),
        "file-type-id",
        "source-id",
        fileSize = 1,
        createdAt = now,
        modifiedAt = now,
        importedAt = now,
        "/datastore/file/path",
        jobId = -1,
        job.uuid,
        "name",
        "description")
      val runSummary = RunSummary(
        UUID.randomUUID(),
        "run-name",
        Some("summary"),
        Some(username),
        createdAt = Some(now),
        startedAt = Some(now),
        transfersCompletedAt = Some(now),
        completedAt = Some(now),
        SupportedRunStates.COMPLETE,
        totalCells = 1,
        numCellsCompleted = 1,
        numCellsFailed = 0,
        Some("instrument-name"),
        Some("instrument-serial-number"),
        Some("instrument-sw-version"),
        Some("primary-analysis-sw-version"),
        Some("context"),
        Some("termination-info"),
        reserved = false)
      val runDataModel = DataModelAndUniqueId("<run>data</run>", runSummary.uniqueId)
      val collection = CollectionMetadata(
        runSummary.uniqueId,
        UUID.randomUUID(),
        "well",
        "name",
        Some("summary"),
        Some("context"),
        Some(Paths.get("/collection/path")),
        SupportedAcquisitionStates.COMPLETE,
        Some("instrument-id"),
        Some("instrument-name"),
        movieMinutes = 30.0,
        startedAt = Some(now),
        completedAt = Some(now),
        Some("termination-info"))
      val sample = Sample("details", UUID.randomUUID(), "name", username, createdAt = now)

      // TODO(smcclellan): JobResults table does not appear to be real?
      // TODO(smcclellan): Users table does not appear to be real?
      val putAll = db.run(
        for {
          jid <- engineJobs returning engineJobs.map(_.id) += job
          _   <- jobEvents += event.copy(jobId = jid)
          tid <- jobTags returning jobTags.map(_.id) += tag
          _   <- jobsTags += jTag.copy(_1 = jid, _2 = tid)
          pid <- projects returning projects.map(_.id) += project
          _   <- projectsUsers += projectUser.copy(projectId = pid)
          _   <- engineJobsDataSets += dataset.copy(jobId = jid)
          _   <- dsMetaData2 += metadata.copy(jobId = jid, projectId = pid)
          _   <- dsSubread2 += subread
          _   <- dsHdfSubread2 += hdf
          _   <- dsReference2 += reference
          _   <- dsGmapReference2 += gmap
          _   <- dsAlignment2 += alignment
          _   <- dsBarcode2 += barcode
          _   <- dsCCSread2 += ccs
          _   <- dsCCSAlignment2 += consensus
          _   <- dsContig2 += contig
          _   <- datastoreServiceFiles += datastoreFile.copy(jobId = jid)
          _   <- runSummaries += runSummary
          _   <- dataModels += runDataModel
          _   <- collectionMetadata += collection
          _   <- samples += sample
        } yield ()
      )

      Await.ready(putAll, 10.seconds)

      val ej = Await.result(db.run(engineJobs.result.head), 1.second)
      val je = Await.result(db.run(jobEvents.result.head), 1.second)
      val ta = Await.result(db.run(jobTags.result.head), 1.second)
      val jt = Await.result(db.run(jobsTags.result.head), 1.second)
      val gp = Await.result(db.run(projects.filter(_.name === "General Project").result.head), 1.second)
      val pr = Await.result(db.run(projects.filter(_.name =!= "General Project").result.head), 1.second)
      val pu = Await.result(db.run(projectsUsers.result.head), 1.second)
      val rt = Await.result(db.run(datasetTypes.filter(_.shortName === "references").result.head), 1.second)
      val ds = Await.result(db.run(engineJobsDataSets.result.head), 1.second)
      val md = Await.result(db.run(dsMetaData2.result.head), 1.second)
      val su = Await.result(db.run(dsSubread2.result.head), 1.second)
      val hd = Await.result(db.run(dsHdfSubread2.result.head), 1.second)
      val re = Await.result(db.run(dsReference2 .result.head), 1.second)
      val gm = Await.result(db.run(dsGmapReference2.result.head), 1.second)
      val al = Await.result(db.run(dsAlignment2.result.head), 1.second)
      val ba = Await.result(db.run(dsBarcode2.result.head), 1.second)
      val cc = Await.result(db.run(dsCCSread2.result.head), 1.second)
      val ca = Await.result(db.run(dsCCSAlignment2.result.head), 1.second)
      val co = Await.result(db.run(dsContig2.result.head), 1.second)
      val df = Await.result(db.run(datastoreServiceFiles.result.head), 1.second)
      val rs = Await.result(db.run(runSummaries.result.head), 1.second)
      val dm = Await.result(db.run(dataModels.result.head), 1.second)
      val cm = Await.result(db.run(collectionMetadata.result.head), 1.second)
      val sa = Await.result(db.run(samples.result.head), 1.second)

      val jobId = ej.id
      val tagId = ta._1
      val projectId = pr.id
      val metadataId = md.id
      val subreadId = su.id
      val hdfId = hd.id
      val referenceId = re.id
      val gmapId = gm.id
      val alignmentId = al.id
      val barcodeId = ba.id
      val ccsId = cc.id
      val consensusId = ca.id
      val contigId = co.id

      ej === job.copy(id = jobId)
      je === event.copy(jobId = jobId)
      ta === tag.copy(_1 = tagId)
      jt === jTag.copy(_1 = jobId, _2 = tagId)
      gp.description === "General Project"
      pr === project.copy(id = projectId)
      pu === projectUser.copy(projectId = projectId)
      rt.id === "PacBio.DataSet.ReferenceSet"
      ds === dataset.copy(jobId = jobId)
      md === metadata.copy(id = metadataId, jobId = jobId, projectId = projectId)
      su === subread.copy(id = subreadId)
      hd === hdf.copy(id = hdfId)
      re === reference.copy(id = referenceId)
      gm === gmap.copy(id = gmapId)
      al === alignment.copy(id = alignmentId)
      ba === barcode.copy(id = barcodeId)
      cc === ccs.copy(id = ccsId)
      ca === consensus.copy(id = consensusId)
      co === contig.copy(id = contigId)
      df === datastoreFile.copy(jobId = jobId)
      rs === runSummary
      dm === runDataModel
      cm === collection
      sa === sample
    }
  }
}
