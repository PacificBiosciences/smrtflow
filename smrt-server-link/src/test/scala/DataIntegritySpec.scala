import java.net.URI
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import org.joda.time.{DateTime => JodaDataTime}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.EngineJob
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  PacBioIntJobResolver
}
import com.pacbio.secondary.smrtlink.actors.{JobsDao, SmrtLinkTestDalProvider}
import com.pacbio.secondary.smrtlink.dataintegrity.{
  DataSetIntegrityRunner,
  JobStateIntegrityRunner
}
import com.pacbio.secondary.smrtlink.models.{ImportAbleSubreadSet, _}
import com.pacbio.secondary.smrtlink.testkit.{MockFileUtils, TestUtils}
import com.pacbio.common.models.CommonModelImplicits._
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetLoader
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import spray.testkit.Specs2RouteTest

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class DataIntegritySpec
    extends Specification
    with Specs2RouteTest
    with NoTimeConversions
    with SmrtLinkTestDalProvider
    with TestUtils
    with LazyLogging {

  // Sequentially run the tests
  sequential

  val smrtLinkSystemVersion = "1.0.0"
  val tmpJobDir = Files.createTempDirectory("data-integrity-spec")
  val engineConfig = EngineConfig(1, None, tmpJobDir, debugMode = true)
  val resolver = new PacBioIntJobResolver(engineConfig.pbRootJobDir)

  val dao = new JobsDao(db(), resolver, None)

  val createdAt = JodaDataTime.now()
  val updatedAt = JodaDataTime.now()
  val now = JodaDataTime.now()
  val timeOut = 10.seconds
  val jobId = 1

  private def convertSubreads(name: String): SubreadServiceDataSet = {
    val p = PacBioTestUtils.getResourcePath(name)
    val ds = DataSetLoader.loadSubreadSet(p)
    Converters.convertSubreadSet(ds, p, None, jobId, 1)
  }

  val testdb = dbConfig.toDatabase
  step(setupDb(dbConfig))

  "Sanity Test for Job Integrity " should {
    "Test to Detect datasets where the paths have been deleted" in {

      val u1 = UUID.fromString("bee60b12-2c50-11e7-8ce2-3c15c2cc8f88")
      val u2 = UUID.fromString("456f0dec-2e7b-11e7-9a48-3c15c2cc8f88")

      // Set this to be the invalid path
      val s1 = convertSubreads("/dataset-subreads/example_01.xml").copy(
        path = "/path/to/does-not-exist")
      val s2 =
        convertSubreads(
          "/dataset-subreads/m54006_160224_002151.subreadset.xml")

      // This is a little bit painful because the path needs to be mutated, otherwise
      // the interface should just create a datastore file, create/import an EngineJob,
      // the import the DataSets
      def toImportAble(ds: SubreadServiceDataSet,
                       jobId: Int,
                       jobUUID: UUID,
                       projectId: Int): ImportAbleSubreadSet = {
        // There's overalapping models here. The project Id
        // needs to be consistent to get propagated as expected.
        val uds = ds.copy(jobId = jobId, projectId = projectId)
        val dsf =
          DataStoreServiceFile(ds.uuid,
                               FileTypes.DS_SUBREADS.fileTypeId.toString,
                               "mock-import",
                               0L,
                               now,
                               now,
                               now,
                               ds.path,
                               jobId,
                               jobUUID,
                               ds.name,
                               "description")
        val dsj = DsServiceJobFile(dsf, None, projectId)
        ImportAbleSubreadSet(dsj, uds)
      }

      val projectName = s"project-${UUID.randomUUID()}"
      val projectRequest =
        ProjectRequest(projectName, "", None, None, None, None)

      val runner = new DataSetIntegrityRunner(dao)

      def toEngine(state: AnalysisJobStates.JobStates,
                   project: Int): EngineJob = {
        MockFileUtils.toTestRawEngineJob("Test Job",
                                         None,
                                         None,
                                         None,
                                         projectId = Some(project))
      }

      val fx = for {
        project <- dao.createProject(projectRequest)
        rawEngineJob <- Future.successful(
          MockFileUtils.toTestRawEngineJob("Test Job",
                                           None,
                                           None,
                                           None,
                                           projectId = Some(project.id)))
        job <- dao.insertJob(rawEngineJob)
        si1 <- Future.successful(
          toImportAble(s1, job.id, job.uuid, project.id))
        si2 <- Future.successful(
          toImportAble(s2, job.id, job.uuid, project.id))
        m <- dao.importSubreadSet(si1)
        _ <- dao.importSubreadSet(si2)
        msg <- runner.run()
        dsMetaData <- dao.getDataSetById(s1.uuid)
      } yield (project, job, dsMetaData)

      val (createdProject, createdJob, dsMeta) = Await.result(fx, timeOut)
      dsMeta.isActive must beFalse

      val projects = Await.result(dao.getProjects(), timeOut)
      projects.length === 2

      // Make sure DataSets can be assigned to projects
      dsMeta.projectId === createdProject.id

      val dsMeta2 = Await.result(dao.getDataSetById(s2.uuid), timeOut)
      dsMeta2.isActive must beTrue
    }
    "Sanity Test to Detect Stuck Jobs that don't have the same SL version" in {

      val u1 = UUID.fromString("d221ac68-2c73-11e7-9243-3c15c2cc8f88")
      val u2 = UUID.fromString("217daf0a-2e7c-11e7-905d-3c15c2cc8f88")

      val smrtLinkVersion = "9.9.9"

      val j1 =
        MockFileUtils
          .toTestRawEngineJob("Test Job",
                              Some(u1),
                              None,
                              None,
                              AnalysisJobStates.RUNNING)
          .copy(smrtlinkVersion = Some(smrtLinkVersion))

      val j2 = j1.copy(state = AnalysisJobStates.SUCCESSFUL, uuid = u2)

      val runner = new JobStateIntegrityRunner(dao, Some("6.6.6"))

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
  step(cleanUpJobDir(engineConfig.pbRootJobDir))
}
