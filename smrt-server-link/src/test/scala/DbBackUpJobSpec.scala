import java.io.File
import java.nio.file.{Files, Path}
import java.util.UUID

import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  LogJobResultsWriter,
  SecondaryJobJsonProtocol
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{
  JobResource,
  PacBioDataStore
}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.{
  DbBackUpJob,
  DbBackUpJobOptions
}
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.specs2.mutable._
import spray.json._

import scala.util.{Success, Try}

// Override the runBackUp for testing Class for Testing Db
class MockedDbBackUpJob(opts: DbBackUpJobOptions)
    extends DbBackUpJob(opts: DbBackUpJobOptions) {

  override def runBackUp(output: Path,
                         dbName: String,
                         port: Int,
                         user: String,
                         password: String,
                         stdout: Path,
                         stderr: Path,
                         exe: String = "pg_dumpall"): Try[String] = {
    FileUtils.write(output.toFile, "Mock DB Backup")
    Success("Successfully wrote mock db")
  }
}

class DbBackUpJobSpec
    extends Specification
    with SecondaryJobJsonProtocol
    with LazyLogging {

  sequential

  val backUpDir = Files.createTempDirectory("db-backup")
  val jobDir = Files.createTempDirectory("job-dir")

  val jobOptions = DbBackUpJobOptions(backUpDir,
                                      dbName = "test",
                                      dbPort = 1234,
                                      dbUser = "test-user",
                                      dbPassword = "test-password")

  def cleanUp(): Unit = {
    val files = Seq(backUpDir, jobDir)
    files.map(_.toFile).foreach(FileUtils.deleteQuietly)
  }

  "Sanity test for DB BackUp job with Mocked runBackUp cmd" should {
    "Generate a datastore" in {

      // Create a mock backup example
      val numPreviousBackUps = 10
      val previousBackUps = (1 until numPreviousBackUps)
        .map(n =>
          s"${jobOptions.baseBackUpName}-file-$n${jobOptions.backUpExt}")
        .map(x => backUpDir.resolve(x))

      def writeBackUps(f: File): File = {
        FileUtils.write(f, "MOCK BackUp Dir")
        f
      }

      previousBackUps.foreach(p => writeBackUps(p.toFile))

      val job = new MockedDbBackUpJob(jobOptions)

      val jResource = JobResource(UUID.randomUUID(), jobDir)
      val jWriter = new LogJobResultsWriter()

      val pbJob = job.run(jResource, jWriter)

      pbJob.isRight must beTrue

      val dataStorePath = jobDir.resolve("datastore.json")

      val dataStore = FileUtils
        .readFileToString(dataStorePath.toFile)
        .parseJson
        .convertTo[PacBioDataStore]

      // Log and a Report in the DataStore
      dataStore.files.length must beEqualTo(2)

      // There should only be maNumBackUps after the job is run
      val maNumBackUps = backUpDir.toFile.list().length
      maNumBackUps must beEqualTo(jobOptions.maxNumBackups)
    }
  }
  step(cleanUp())
}
