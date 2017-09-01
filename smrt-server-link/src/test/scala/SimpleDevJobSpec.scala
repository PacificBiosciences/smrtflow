import java.nio.file.Files
import java.util.UUID

import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.JobResource
import com.pacbio.secondary.smrtlink.analysis.jobs.{AnalysisJobStates, PrinterJobResultsWriter}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.{SimpleDevJob, SimpleDevJobOptions}
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._


class SimpleDevJobSpec extends Specification with LazyLogging{

  sequential

  val writer = new PrinterJobResultsWriter
  "Sanity test for running simple dev jobOptions" should {
    "Basic adding jobOptions " in {
      val outputDir = Files.createTempDirectory("pbsmrtpipe-jobOptions")
      val job = JobResource(UUID.randomUUID, outputDir)
      val opts = SimpleDevJobOptions(5, 7)
      val j = new SimpleDevJob(opts)
      val jobResult = j.run(job, writer)
      logger.info("Running simple jobOptions")
      jobResult.isRight must beTrue
    }
  }

}
