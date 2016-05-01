import java.nio.file.Files
import java.util.UUID

import com.pacbio.secondary.analysis.jobs.{PrinterJobResultsWriter, JobModels, AnalysisJobStates}
import com.pacbio.secondary.analysis.jobtypes.{SimpleDevJobOptions, SimpleDevJob}
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._
import com.pacbio.secondary.analysis.jobs.JobModels.{JobResource, BoundEntryPoint}


class SimpleDevJobSpec extends Specification with LazyLogging{

  sequential

  val writer = new PrinterJobResultsWriter
  "Sanity test for running simple dev jobOptions" should {
    "Basic adding jobOptions " in {
      val outputDir = Files.createTempDirectory("pbsmrtpipe-jobOptions")
      val job = JobResource(UUID.randomUUID, outputDir, AnalysisJobStates.CREATED)
      val opts = SimpleDevJobOptions(5, 7)
      val j = new SimpleDevJob(opts)
      val jobResult = j.run(job, writer)
      logger.info("Running simple jobOptions")
      jobResult.isRight must beTrue
    }
  }

}
