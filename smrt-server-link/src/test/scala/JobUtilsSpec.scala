
import java.nio.file.{Files,Path,Paths}
import java.util.UUID

import org.apache.commons.io.FileUtils
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._
import org.joda.time.{DateTime => JodaDateTime}

import com.pacbio.secondary.smrtlink.analysis.externaltools.PacBioTestData
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, JobModels, JobUtils}


class JobUtilsSpec extends Specification with JobUtils with LazyLogging {

  import JobModels._

  private def setupFakeJob: EngineJob = {
    val jobPath = Files.createTempDirectory("export-job")
    val fastaPath = jobPath.resolve("contigs.fasta")
    FileUtils.writeStringToFile(fastaPath.toFile, ">chr1\nacgtacgt", "UTF-8")
    val logDirPath = jobPath.resolve("logs")
    logDirPath.toFile.mkdir
    val logPath = logDirPath.resolve("master.log")
    FileUtils.writeStringToFile(logPath.toFile, "Hello world!", "UTF-8")
    EngineJob(1, UUID.randomUUID(), "My job", "Test job",
      JodaDateTime.now(), JodaDateTime.now(), AnalysisJobStates.SUCCESSFUL,
      "pbsmrtpipe", jobPath.toString, "{}", Some("smrtlinktest"), None,
      Some("4.0.0"), projectId = 10)
  }

  val REF_PATH = "/dataset-references/example_reference_dataset/reference.dataset.xml"

  "Job Utils" should {
    "Export minimal fake job directory" in {
      val job = setupFakeJob
      val zipPath = Files.createTempFile("job", ".zip")
      val result = exportJob(job, zipPath)
      result.toOption.get.nBytes must beGreaterThan(0)
    }
    /*
    "Export with datastore JSON" in {
    }
    "Export with referenceset XML" in {
    }
    */
  }
}
/*
class JobUtilsAdvancedSpec extends Specification with JobUtils with LazyLogging {

  args(skipAll = !PacBioTestData.isAvailable)

  "Job Export" should {
    "Export job containing complete SubreadSet" in {
    }
  }
   
}*/
