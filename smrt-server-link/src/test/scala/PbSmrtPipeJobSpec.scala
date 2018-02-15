import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import scala.util.Try

import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{
  BoundEntryPoint,
  JobResource,
  ServiceTaskOptionBase,
  ServiceTaskStrOption
}
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  CoreJob,
  PrinterJobResultsWriter,
  SecondaryJobJsonProtocol
}
import com.pacbio.secondary.smrtlink.analysis.pbsmrtpipe.PbsmrtpipeEngineOptions
import com.pacbio.secondary.smrtlink.jobtypes.MockPbsmrtpipeUtils

class PbSmrtPipeJobSpec
    extends Specification
    with MockPbsmrtpipeUtils
    with LazyLogging
    with SecondaryJobJsonProtocol {

  sequential
  val writer = new PrinterJobResultsWriter
  "Sanity test for running a mock pbsmrtpipe job" should {
    "Basic mock jobOptions to write datastore, jobOptions resources, report" in {
      val outputDir = Files.createTempDirectory("pbsmrtpipe-jobOptions")
      val entryPoints =
        Seq(("e_01", "file.txt"), ("e_02", "file2.txt")).map(x =>
          BoundEntryPoint(x._1, Paths.get(x._2)))
      // Path to source path.stuff.sh
      val envPath: Option[Path] = None
      val job = JobResource(UUID.randomUUID, outputDir)
      val taskOptions = Seq[ServiceTaskOptionBase]()
      Try(runMockJob(job, writer)) must beSuccessfulTry
    }
  }
}
