import java.nio.file.{Files,Path}
import java.util.UUID
import com.pacbio.secondary.analysis.jobs.JobModels.{ServiceTaskOptionBase, BoundEntryPoint, JobResource}
import com.pacbio.secondary.analysis.jobs.{PrinterJobResultsWriter, AnalysisJobStates, SimpleJobRunner}
import com.pacbio.secondary.analysis.jobtypes.{MockPbSmrtPipeJobOptions, SimpleDevJobOptions}
import com.pacbio.secondary.analysis.pbsmrtpipe.PbsmrtpipeEngineOptions
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

class JobExecutorSpec extends Specification with LazyLogging{

  sequential
  val jobRunner = new SimpleJobRunner
   val resultsWriter = new PrinterJobResultsWriter
  "Sanity test for jobOptions executor" should {
    "Run Simple Dev jobOptions" in {
      val opts = SimpleDevJobOptions(5, 6)
      val outputDir = Files.createTempDirectory("simple-jobOptions")
      logger.info(s"Running simple-dev-jobOptions in ${outputDir.toString}")
      val pbJob = JobResource(UUID.randomUUID, outputDir, AnalysisJobStates.CREATED)

      val jobResult = jobRunner.runJobFromOpts(opts, pbJob, resultsWriter)
      jobResult.isRight must beTrue
    }
    "Run Mock PbSmrtPipe Job" in {
      val outputDir = Files.createTempDirectory("mock-pbsmrtpipe-jobOptions")
      val entryPoints = Seq(("e_01", "file.txt"), ("e_02", "file2.txt")).map(x => BoundEntryPoint(x._1, x._2))
      val pipelineId = "pbscala.job_types.mock_pbsmrtpipe"
      val env: Option[Path] = None
      val taskOptions = Seq[ServiceTaskOptionBase]()
      val opts = MockPbSmrtPipeJobOptions(pipelineId, entryPoints, taskOptions, PbsmrtpipeEngineOptions.defaultWorkflowOptions.map(_.asServiceOption), env)
      logger.info(s"Running mock-pbsmrtpipe-jobOptions in ${outputDir.toString}")
      val pbJob = JobResource(UUID.randomUUID, outputDir, AnalysisJobStates.CREATED)
        val jobResult = jobRunner.runJobFromOpts(opts, pbJob, resultsWriter)
      jobResult.isRight must beTrue
    }
  }
}
