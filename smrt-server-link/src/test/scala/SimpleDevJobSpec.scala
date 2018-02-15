import java.nio.file.Files
import java.util.UUID

import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.JobResource
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  PrinterJobResultsWriter
}
import com.pacbio.secondary.smrtlink.jobtypes.{SimpleDevJob, SimpleJobOptions}
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

class SimpleDevJobSpec
    extends Specification
    with SimpleDevJob
    with LazyLogging {

  sequential

  val writer = new PrinterJobResultsWriter
  "Sanity test for running simple dev jobOptions" should {
    "Basic adding jobOptions " in {
      val outputDir = Files.createTempDirectory("pbsmrtpipe-jobOptions")
      val job = JobResource(UUID.randomUUID, outputDir)
      val ds = runDevJob(job, writer, 4)
      ds.files.size must beGreaterThan(0)
    }
  }
}
