import java.nio.file.Files
import java.util.UUID

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

import org.joda.time.{DateTime => JodaDateTime}
import org.specs2.mutable.Specification
import slick.jdbc.PostgresProfile.api._

import com.pacbio.common.models.CommonModelImplicits._
import com.pacbio.secondary.smrtlink.actors.{JobsDao, SmrtLinkTestDalProvider}
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.io.{
  DataSetLoader,
  DataSetWriter
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{
  DataStoreFile,
  EngineJob,
  JobTypeIds
}
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  PacBioIntJobResolver
}
import com.pacbio.secondary.smrtlink.models.{
  DataSetSearchCriteria,
  JobSearchCriteria,
  ReferenceServiceDataSet,
  QueryOperators
}
import com.pacbio.secondary.smrtlink.testkit.{MockFileUtils, TestUtils}
import com.pacbio.secondary.smrtlink.tools.SetupMockData

class JobsDaoSpec extends Specification with TestUtils with SetupMockData {
  sequential

  object TestProviders extends SmrtLinkTestDalProvider {}

  val tmpJobDir = Files.createTempDirectory("jobs-dao-spec")
  val jobResolver = new PacBioIntJobResolver(tmpJobDir)

  val dao =
    new JobsDao(TestProviders.dbConfig.toDatabase, jobResolver)

  val db: Database = dao.db
  val timeout = FiniteDuration(10, "seconds")

  step(setupDb(TestProviders.dbConfig))
  step(runInsertAllMockData(dao))

  def validateSize(f: => Future[Int], x: Int) = {
    Await.result(f, timeout) === x
  }

  "Sanity Test" should {
    "Can connect to database" in {
      true === true
    }
    "Sanity DAO insertion test" in {
      validateSize(dao.getDataStoreFiles().map(_.length), 8)
    }
    "Get DataSetMeta" in {
      validateSize(dao.getDataSetMetas(activity = None).map(_.length), 7)
    }
    "Get SubreadSets" in {
      validateSize(
        dao.getSubreadDataSets(DataSetSearchCriteria.default).map(_.length),
        2)
    }
    "Get ReferenceSets" in {
      validateSize(
        dao.getReferenceDataSets(DataSetSearchCriteria.default).map(_.length),
        4)
    }
    "Get BarcodeSets" in {
      validateSize(
        dao.getBarcodeDataSets(DataSetSearchCriteria.default).map(_.length),
        1)
    }
    "Get Projects" in {
      // Should have the GENERAL Project and the Test project created from
      // importing the mock data
      validateSize(dao.getProjects().map(_.length), 2)
    }
    "Sanity Import of Simple DataStore Fasta file" in {
      val now = JodaDateTime.now()
      val tmpFastaFile = Files.createTempFile(tmpJobDir, "mock", ".fasta")
      val dsFile =
        MockFileUtils.writeMockFastaDataStoreFile(1000, tmpFastaFile)

      val jobUUID = UUID.randomUUID()
      val rawEngineJob = MockFileUtils.toTestRawEngineJob("fasta-import-test",
                                                          Some(jobUUID),
                                                          None,
                                                          None)

      val fx = for {
        project <- dao.getProjectByName(TEST_PROJECT_NAME)
        importedJob <- dao.importRawEngineJob(rawEngineJob, rawEngineJob)
        msg <- dao.importDataStoreFile(dsFile,
                                       jobUUID,
                                       projectId = Some(project.id))
        importedDataStore <- dao.getDataStoreFile(dsFile.uniqueId)
      } yield (importedJob, importedDataStore)

      val (importedJob, dsServiceFile) = Await.result(fx, timeout)
      //dsDataStoreFile.jobId ===
      dsServiceFile.dataStoreFile.path === tmpFastaFile.toAbsolutePath.toString
      dsServiceFile.jobId === importedJob.uuid
    }

    "Sanity Reference DataStore File" in {

      val rsetPath = Files.createTempFile(tmpJobDir, "reference", "set.xml")

      val name = "dataset-references/example_01.xml"

      val px = PacBioTestUtils.getResourcePath(name)
      val rset = DataSetLoader.loadAndResolveReferenceSet(px)

      val rsetUUID = UUID.randomUUID()

      rset.setUniqueId(rsetUUID.toString)

      DataSetWriter.writeReferenceSet(rset, rsetPath)

      val dsFile = MockFileUtils.toTestDataStoreFile(rsetUUID,
                                                     FileTypes.DS_REFERENCE,
                                                     rsetPath)

      val jobUUID = UUID.randomUUID()
      val rawEngineJob = MockFileUtils.toTestRawEngineJob("fasta-import-test",
                                                          Some(jobUUID),
                                                          None,
                                                          None)

      val fxx: Future[(EngineJob, ReferenceServiceDataSet, Int)] = for {
        project <- dao.getProjectByName(TEST_PROJECT_NAME)
        importedJob <- dao.importRawEngineJob(rawEngineJob, rawEngineJob)
        msg <- dao.importDataStoreFile(dsFile,
                                       jobUUID,
                                       projectId = Some(project.id))
        rSetFile <- dao.getReferenceDataSetById(dsFile.uniqueId)
      } yield (importedJob, rSetFile, project.id)

      val (importedJob, rSetFile, projectId) =
        Await.result(fxx, timeout)

      rSetFile.path === dsFile.path
      rSetFile.projectId === projectId
      rSetFile.jobId === importedJob.id
    }

    "Job search API" in {
      val prefix = Random.alphanumeric.take(10).mkString("")
      def toJobName(x: Int) = s"${prefix}-job$x"
      def toJobNames(xx: Seq[Int]) = xx.map(toJobName).toSet
      val job1 =
        MockFileUtils
          .toTestRawEngineJob("pbsmrtpipe-test",
                              None,
                              Some(JobTypeIds.PBSMRTPIPE),
                              None)
          .copy(name = s"${prefix}-job1")
      val job2 = job1.copy(name = s"${prefix}-job2",
                           uuid = UUID.randomUUID(),
                           state = AnalysisJobStates.SUCCESSFUL)
      val job3 = job2.copy(name = s"${prefix}-job3",
                           uuid = UUID.randomUUID(),
                           smrtlinkVersion = Some("5.1.0"),
                           isActive = false)
      val job4 = job1.copy(name = s"${prefix}-job4",
                           uuid = UUID.randomUUID(),
                           jobTypeId = JobTypeIds.CONVERT_RS_MOVIE.id)
      val c1 = JobSearchCriteria.allAnalysisJobs.copy(
        name = Some(QueryOperators.StringMatchQueryOperator(s"${prefix}-job")))
      val c2 =
        c1.copy(
          state = Some(
            QueryOperators.JobStateEqOperator(AnalysisJobStates.SUCCESSFUL)))
      val c3 = c1.copy(
        smrtlinkVersion = Some(QueryOperators.StringEqQueryOperator("5.1.0")))
      val c4 = c1.copy(isActive = Some(true))
      val c5 = c1.copy(jobTypeId = Some(
        QueryOperators.StringEqQueryOperator(JobTypeIds.CONVERT_RS_MOVIE.id)))
      val c6 = c1.copy(jobTypeId = None)
      val pbsmrtpipeJobs = Seq(job1, job2, job3, job4)
      val searchCriteria = Seq(c1, c2, c3, c4, c5, c6)
      val expectedResults = Seq(
        Seq(3, 2, 1), Seq(3, 2), Seq(3), Seq(2, 1), Seq(4), Seq(4, 3, 2, 1)
      ).map(toJobNames)
      val fx = for {
        jobs <- Future.sequence(pbsmrtpipeJobs.map { job =>
          dao.importRawEngineJob(job, job)
        })
        queries <- Future.sequence(searchCriteria.map(c => dao.getJobs(c)))
      } yield queries.map(_.map(_.name).toSet)
      val jobNames = Await.result(fx, timeout)
      jobNames.zip(expectedResults).map { case (names1, names2) =>
        names1 === names2
      }
    }

  }

  step(cleanUpJobDir(tmpJobDir))
}
