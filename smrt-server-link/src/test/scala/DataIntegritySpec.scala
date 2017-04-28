import java.nio.file.Paths
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
  val uuid = UUID.fromString("bee60b12-2c50-11e7-8ce2-3c15c2cc8f88")
  val path = Paths.get("/tmp/subreadset.xml")

  val sset = SubreadServiceDataSet(-1, uuid, "Test", path.toString, createdAt, updatedAt, 1L, 1L,
    "1.0.0", "Comments", "tag,tag2", "md5", "inst-name", "mcontext", "well-s-name", "well-name",
    "bio-sample", 1, "run-name", None, 1, 1)

  val jobId = UUID.fromString("bee60b12-2c50-11e7-8ce2-3c15c2cc8f88")
  val engineJob = EngineJob(-1, jobId, "Test Job", "comment", createdAt, createdAt,
    AnalysisJobStates.CREATED, "import-dataset", "/tmp/job-dir", "{}", None,
    Some(smrtLinkSystemVersion))

  val testdb = dbConfig.toDatabase
  step(setupDb(dbConfig))

  "Sanity Test for Job Integrity " should {
    "Test to Detect datasets where the paths have been deleted" in {

      val runner = new DataSetIntegrityRunner(dao)

      val fx = for {
        m <- dao.insertSubreadDataSet(sset)
        msg <- runner.run()
        dsMetaData <- dao.getDataSetByUUID(sset.uuid)
      } yield dsMetaData

      val dsMeta = Await.result(fx, 10.seconds)
      dsMeta.isActive must beFalse
    }
    "Sanity Test to Detect Stuck Jobs that don't have the same SL version" in {

      val runner = new JobStateIntegrityRunner(dao, Some("9.9.9"))

      val fx = for {
        j <- dao.insertJob(engineJob)
        m <- runner.run()
        updatedJob <- dao.getJobByIdAble(j.id)
      } yield updatedJob

      val updatedJob = Await.result(fx, 10.seconds)

      updatedJob.state must beEqualTo(AnalysisJobStates.FAILED)
    }
  }
}
