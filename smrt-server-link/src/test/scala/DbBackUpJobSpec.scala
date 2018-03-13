import java.io.File
import java.nio.file.{Files, Path}
import java.util.UUID

import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  LogJobResultsWriter,
  SecondaryJobJsonProtocol
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{
  DataStoreFile,
  JobConstants,
  JobResource,
  PacBioDataStore
}
import com.pacbio.secondary.smrtlink.jobtypes.{
  DbBackUpBase,
  DbBackUpJobOptions
}
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.specs2.mutable._
import spray.json._

import scala.util.{Success, Try}

class DbBackUpJobSpec
    extends Specification
    with DbBackUpBase
    with SecondaryJobJsonProtocol
    with LazyLogging {

  sequential

  val backUpDir = Files.createTempDirectory("db-backup")
  val jobDir = Files.createTempDirectory("job-dir")

  val jobOptions = DbBackUpJobOptions("admin", "test spec", None, None)

  def cleanUp(): Unit = {
    val files = Seq(backUpDir, jobDir)
    files.map(_.toFile).foreach(FileUtils.deleteQuietly)
  }

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

  "Sanity test for DB BackUp job with Mocked runBackUp cmd" should {
    "Generate a datastore" in {

      // Create a mock backup example
      val numPreviousBackUps = 10
      val previousBackUps = (1 until numPreviousBackUps)
        .map(n => s"${BASE_BACKUP_NAME}-file-$n${BACKUP_EXT}")
        .map(x => backUpDir.resolve(x))

      def writeBackUps(f: File): File = {
        FileUtils.write(f, "MOCK BackUp Dir")
        f
      }

      previousBackUps.foreach(p => writeBackUps(p.toFile))

      val jResource = JobResource(UUID.randomUUID(), jobDir)
      val jWriter = new LogJobResultsWriter()

      val logFile = jobDir.resolve(JobConstants.JOB_STDOUT)
      val pbJob = runCoreJob(jResource,
                             jWriter,
                             backUpDir,
                             "test",
                             1234,
                             "test_user",
                             "test_password",
                             "localhost",
                             DataStoreFile.fromMaster(logFile))
      pbJob.isRight must beTrue

      val dataStorePath = jobDir.resolve("datastore.json")

      val dataStore = FileUtils
        .readFileToString(dataStorePath.toFile)
        .parseJson
        .convertTo[PacBioDataStore]

      // Log and a Report in the DataStore
      dataStore.files.length must beEqualTo(2)

      // There should only be maxNumBackUps after the job is run
      val maxNumBackUps = backUpDir.toFile.list().length
      maxNumBackUps must beEqualTo(MAX_NUM_BACKUPS)
    }
  }
  step(cleanUp())
}
