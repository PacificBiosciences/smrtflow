import java.nio.file.{Files, Path}
import java.util.UUID
<<<<<<< HEAD

import com.pacbio.secondary.analysis.jobs.JobModels.{BoundEntryPoint, JobResource, PipelineBaseOption, PipelineStrOption}
import com.pacbio.secondary.analysis.jobs.{AnalysisJobStates, CoreJob, PrinterJobResultsWriter, SecondaryJobJsonProtocol}
import com.pacbio.secondary.analysis.jobtypes.{MockPbSmrtPipeJobOptions, PbSmrtPipeJobOptions, PbSmrtpipeMockJob}
=======
import com.pacbio.secondary.analysis.jobs.JobModels.{BoundEntryPoint, JobResource, ServiceTaskOptionBase, ServiceTaskStrOption}
import com.pacbio.secondary.analysis.jobs.{PrinterJobResultsWriter, CoreJob, AnalysisJobStates, SecondaryJobJsonProtocol}
import com.pacbio.secondary.analysis.jobtypes.{PbSmrtPipeJobOptions, MockPbSmrtPipeJobOptions, PbSmrtpipeMockJob}
>>>>>>> master
import com.pacbio.secondary.analysis.pbsmrtpipe.PbsmrtpipeEngineOptions
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._
import spray.json._

class PbSmrtPipeJobSpec extends Specification
with LazyLogging
with SecondaryJobJsonProtocol {

  sequential
    val writer = new PrinterJobResultsWriter
  "Sanity test for running a mock pbsmrtpipe jobOptions" should {
    "Basic mock jobOptions to write datastore, jobOptions resources, report" in {
      val outputDir = Files.createTempDirectory("pbsmrtpipe-jobOptions")
      val entryPoints = Seq(("e_01", "file.txt"), ("e_02", "file2.txt")).map(x => BoundEntryPoint(x._1, x._2))
      // Path to source path.stuff.sh
      val envPath:Option[Path] = None
      val job = JobResource(UUID.randomUUID, outputDir, AnalysisJobStates.CREATED)
      val taskOptions = Seq[ServiceTaskOptionBase]()
      val opts = MockPbSmrtPipeJobOptions("pbscala.pipelines.mock_dev_01", entryPoints, taskOptions, PbsmrtpipeEngineOptions.defaultWorkflowOptions.map(_.asServiceOption), envPath)
      val s = new PbSmrtpipeMockJob(opts)
      logger.debug(s"Running jobOptions in ${job.path.toString}")
      logger.debug(s"Converting to JSON $job")

      val jobResult = s.run(job, writer)
      jobResult.isRight must beTrue
    }
    "Serialization smoke test for mock pbsmrtpipe jobOptions option" in {
      val entryPoints = Seq(BoundEntryPoint("e_01", "/path/to/file.txt"))
<<<<<<< HEAD
      val taskOptions = Seq(PipelineStrOption("option_01", "My Opt 01", "value_01", "Desc"))
      val workflowOptions = Seq(PipelineStrOption("option_02", "My Opt 02", "value_02", "desc"))
=======
      val taskOptions = Seq(ServiceTaskStrOption("option_01", "value_01"))
      val workflowOptions = Seq(ServiceTaskStrOption("option_02", "value_02"))
      val envPath = "/path/to/env.sh"
>>>>>>> master
      val serviceUri = None
      val x = PbSmrtPipeJobOptions("pipeline-id", entryPoints, taskOptions, workflowOptions, None, serviceUri)

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
