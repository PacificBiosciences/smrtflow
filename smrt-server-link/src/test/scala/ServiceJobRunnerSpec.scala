import java.nio.file.Files
import java.util.UUID
import org.joda.time.{DateTime => JodaDateTime}

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
  JobTypeIds,
  PacBioDataStore
}
import com.pacbio.secondary.smrtlink.analysis.jobs.PacBioIntJobResolver
import com.pacbio.secondary.smrtlink.analysis.pbsmrtpipe.PbsmrtpipeEngineOptions
import com.pacbio.secondary.smrtlink.database.legacy.BaseLine.JobTypeId
import com.pacbio.secondary.smrtlink.jobtypes.ServiceJobRunner
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.models.ReferenceServiceDataSet
import com.pacbio.secondary.smrtlink.testkit.{MockFileUtils, TestUtils}
import com.pacbio.secondary.smrtlink.tools.SetupMockData
import org.specs2.mutable.Specification
import slick.jdbc.PostgresProfile.api._

import scala.concurrent._
import scala.concurrent.duration._
import org.joda.time.{DateTime => JodaDateTime}

class ServiceJobRunnerSpec
    extends Specification
    with TestUtils
    with SetupMockData {
  sequential

  object TestProviders extends SmrtLinkTestDalProvider {}

  val tmpJobDir = Files.createTempDirectory("jobs-dao-spec")
  val jobResolver = new PacBioIntJobResolver(tmpJobDir)

  val dao =
    new JobsDao(TestProviders.dbConfig.toDatabase, jobResolver, None)

  // This is pretty brutal to mock out.
  val opts = PbsmrtpipeEngineOptions(Nil)
  val config = SystemJobConfig(
    opts,
    "localhost",
    8080,
    Some("9.8.7"),
    UUID.randomUUID(),
    Some(tmpJobDir),
    externalEveUrl = None,
    dbConfig = TestProviders.dbConfig,
    wso2Port = 8234,
    mail = None,
    rootDbBackUp = None,
    eveApiSecret = "test-key"
  )

  val serviceJobRunner = new ServiceJobRunner(dao, config)

  val db: Database = dao.db
  val timeout = FiniteDuration(10, "seconds")

  def writeTestFiles(n: Int): Seq[DataStoreFile] = {
    (0 until n)
      .map(i => tmpJobDir.resolve(s"${UUID.randomUUID()}.fasta"))
      .map(f => MockFileUtils.writeMockFastaDataStoreFile(100, f))
  }

  step(setupDb(TestProviders.dbConfig))

  "Service Job Runner " should {
    "Should import only non-chunked files" in {

      val jobUUID = UUID.randomUUID()
      val testJob =
        MockFileUtils.toTestRawEngineJob("test-job",
                                         Some(jobUUID),
                                         Some(JobTypeIds.PBSMRTPIPE),
                                         config.smrtLinkVersion)

      val now = JodaDateTime.now()
      val results = Await.result(dao.insertJob(testJob), timeout)

      val numUnchunkedFiles = 5
      val numChunkedFiles = 3

      val unchunkedDataStoreFiles = writeTestFiles(numUnchunkedFiles)
      val chunkedDataStoreFiles =
        writeTestFiles(numChunkedFiles).map(f => f.copy(isChunked = true))
      val files = unchunkedDataStoreFiles ++ chunkedDataStoreFiles
      val datastore = PacBioDataStore(now, now, "0.2.1", files)

      serviceJobRunner.importer(jobUUID, datastore, timeout)

      val dsFiles =
        Await.result(dao.getDataStoreFilesByJobId(jobUUID), timeout)

      dsFiles.length === numUnchunkedFiles
    }
  }

  step(cleanUpJobDir(tmpJobDir))
}
