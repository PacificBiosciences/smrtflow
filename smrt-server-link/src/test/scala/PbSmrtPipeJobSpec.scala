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
import com.pacbio.secondary.smrtlink.analysis.jobtypes.PbSmrtPipeJobOptions
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
    "Serialization smoke test for mock pbsmrtpipe job options" in {
      val entryPoints =
        Seq(BoundEntryPoint("e_01", Paths.get("/path/to/file.txt")))
      val taskOptions = Seq(ServiceTaskStrOption("option_01", "value_01"))
      val workflowOptions = Seq(ServiceTaskStrOption("option_02", "value_02"))
      val serviceUri = None
      val x = PbSmrtPipeJobOptions("pipeline-id",
                                   entryPoints,
                                   taskOptions,
                                   workflowOptions,
                                   None,
                                   serviceUri)

      //val j = x.toJson
      //logger.info(s"JSON converted to $j")

      val coreJob = CoreJob(UUID.randomUUID(), x)
      logger.info(s"Core jobOptions $coreJob")
      //val j2 = coreJob.jobOptions.toJson
      //logger.info(s"JSON core jobOptions to $j2")
      x.pipelineId must beEqualTo("pipeline-id")
    }
  }
}
