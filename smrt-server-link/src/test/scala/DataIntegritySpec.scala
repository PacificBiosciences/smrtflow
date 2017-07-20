import java.nio.file.{Paths,Files}
import java.util.UUID

import org.joda.time.{DateTime => JodaDataTime}
import com.pacbio.secondary.analysis.engine.EngineConfig
import com.pacbio.secondary.analysis.jobs.JobModels.EngineJob
import com.pacbio.secondary.analysis.jobs.{AnalysisJobStates, PacBioIntJobResolver}
import com.pacbio.secondary.smrtlink.actors.{JobsDao, TestDalProvider}
import com.pacbio.secondary.smrtlink.dataintegrity.{DataSetIntegrityRunner, JobStateIntegrityRunner}
import com.pacbio.secondary.smrtlink.models.SubreadServiceDataSet
import com.pacbio.secondary.smrtlink.testkit.TestUtils
import com.pacbio.common.models.CommonModelImplicits._

import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import spray.testkit.Specs2RouteTest

import scala.concurrent.Await
import scala.concurrent.duration._


class DataIntegritySpec extends Specification with Specs2RouteTest with NoTimeConversions with TestDalProvider with TestUtils with LazyLogging{

  // Sequentially run the tests
  sequential

  val smrtLinkSystemVersion = "1.0.0"
  val engineConfig = EngineConfig(1, None, Paths.get("/tmp"), debugMode = true)
  val resolver = new PacBioIntJobResolver(engineConfig.pbRootJobDir)

  val dao = new JobsDao(db(), engineConfig, resolver, None)

  val createdAt = JodaDataTime.now()
  val updatedAt = JodaDataTime.now()
  val timeOut = 10.seconds

  val testdb = dbConfig.toDatabase
  step(setupDb(dbConfig))

  "Sanity Test for Job Integrity " should {
    "Test to Detect datasets where the paths have been deleted" in {

      val u1 = UUID.fromString("bee60b12-2c50-11e7-8ce2-3c15c2cc8f88")
      val u2 = UUID.fromString("456f0dec-2e7b-11e7-9a48-3c15c2cc8f88")

      val p1 = Paths.get("/tmp/path-that-does-not-exist.xml")
      val p2 = Files.createTempFile("subreadset", "s1")

      val s1 = SubreadServiceDataSet(-1, u1, "Test", p1.toString, createdAt, updatedAt, 1L, 1L,
        "1.0.0", "Comments", "tag,tag2", "md5", "inst-name", "ics-ctl-version", "mcontext", "well-s-name", "well-name",
        "bio-sample", 1, "cell-id", "run-name", None, 1, 1)

      val s2 = s1.copy(path = p2.toString, uuid = u2)


      val runner = new DataSetIntegrityRunner(dao)

      val fx = for {
        m <- dao.insertSubreadDataSet(s1)
        _ <- dao.insertSubreadDataSet(s2)
        msg <- runner.run()
        dsMetaData <- dao.getDataSetById(s1.uuid)
      } yield dsMetaData

      val dsMeta = Await.result(fx, timeOut)
      dsMeta.isActive must beFalse

      val dsMeta2 = Await.result(dao.getDataSetById(s2.uuid), timeOut)
      dsMeta2.isActive must beTrue
    }
    "Sanity Test to Detect Stuck Jobs that don't have the same SL version" in {

      val u1 = UUID.fromString("d221ac68-2c73-11e7-9243-3c15c2cc8f88")
      val u2 = UUID.fromString("217daf0a-2e7c-11e7-905d-3c15c2cc8f88")

      val j1 = EngineJob(-1, u1, "Test Job", "comment", createdAt, createdAt,
        AnalysisJobStates.CREATED, "import-dataset", "/tmp/job-dir", "{}", None, None,
        Some(smrtLinkSystemVersion))

      val j2 = j1.copy(state = AnalysisJobStates.SUCCESSFUL, uuid = u2)

      val runner = new JobStateIntegrityRunner(dao, Some("9.9.9"))

      val fx = for {
        ej1 <- dao.insertJob(j1)
        ej2 <- dao.insertJob(j2)
        m <- runner.run()
        updatedJob <- dao.getJobById(ej1.id)
      } yield updatedJob


      val uj1 = Await.result(fx, timeOut)

      uj1.state must beEqualTo(AnalysisJobStates.FAILED)

      val uj2 = Await.result(dao.getJobById(j2.uuid), timeOut)
      uj2.state must beEqualTo(j2.state)

    }
  }
}
