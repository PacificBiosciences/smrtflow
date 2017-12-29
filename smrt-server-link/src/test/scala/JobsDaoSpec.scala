import java.nio.file.Files
import java.util.UUID

import com.pacbio.common.models.CommonModelImplicits._
import com.pacbio.secondary.smrtlink.actors.{JobsDao, SmrtLinkTestDalProvider}
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.io.{
  DataSetLoader,
  DataSetWriter
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{
  DataStoreFile,
  EngineJob
}
import com.pacbio.secondary.smrtlink.analysis.jobs.PacBioIntJobResolver
import com.pacbio.secondary.smrtlink.models.ReferenceServiceDataSet
import com.pacbio.secondary.smrtlink.testkit.{MockFileUtils, TestUtils}
import com.pacbio.secondary.smrtlink.tools.SetupMockData
import org.specs2.mutable.Specification
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import org.joda.time.{DateTime => JodaDateTime}
import scala.concurrent.ExecutionContext.Implicits.global

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
      validateSize(dao.getSubreadDataSets().map(_.length), 2)
    }
    "Get ReferenceSets" in {
      validateSize(dao.getReferenceDataSets().map(_.length), 4)
    }
    "Get BarcodeSets" in {
      validateSize(dao.getBarcodeDataSets().map(_.length), 1)
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

  }

  step(cleanUpJobDir(tmpJobDir))
}
