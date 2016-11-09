
import java.nio.file.{Files, Paths}
import java.io.File
import java.util.UUID

import org.apache.commons.io.FileUtils
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

import com.pacbio.secondary.analysis.reports.ReportUtils
import com.pacbio.secondary.analysis.jobs.{PrinterJobResultsWriter, JobModels, AnalysisJobStates}
import com.pacbio.secondary.analysis.jobtypes.{DeleteResourcesOptions, DeleteResourcesJob}
import com.pacbio.secondary.analysis.jobs.JobModels.{JobResource, BoundEntryPoint}


class DeleteResourcesSpec extends Specification with LazyLogging{

  sequential

  private def createTempFiles = {
    val targetDir = Files.createTempDirectory("pbsmrtpipe-job")
    val targetFile = targetDir.resolve("job.txt").toFile
    FileUtils.writeStringToFile(targetFile, "Hello, world!")
    val targetSubDir = new File(targetDir.resolve("logs").toString)
    targetSubDir.mkdir()
    val targetSubFile = targetSubDir.toPath.resolve("master.log").toFile
    FileUtils.writeStringToFile(targetSubFile, "[INFO] Hello, world!")
    (targetDir, targetFile, targetSubFile)
  }

  val writer = new PrinterJobResultsWriter
  "Sanity test for deleting job directory" should {
    "Test basic use case" in {
      val (targetDir, targetFile, targetSubFile) = createTempFiles
      val outputDir = Files.createTempDirectory("delete-job")
      val job = JobResource(UUID.randomUUID, outputDir, AnalysisJobStates.CREATED)
      val opts = DeleteResourcesOptions(targetDir, true)
      val j = new DeleteResourcesJob(opts)
      val jobResult = j.run(job, writer)
      logger.info("Running delete job")
      jobResult.isRight must beTrue
      Files.exists(targetFile.toPath) must beFalse
      Files.exists(targetSubFile.toPath) must beFalse
      val deleteFile = targetDir.resolve("DELETED")
      Files.exists(deleteFile) must beTrue
      val r = ReportUtils.loadReport(Paths.get(jobResult.right.get.files(1).path))
      r.attributes(0).value must beEqualTo(targetDir.toString)
    }
    "Test behavior when delete is turned off" in {
      val (targetDir, targetFile, targetSubFile) = createTempFiles
      val outputDir = Files.createTempDirectory("delete-job")
      val job = JobResource(UUID.randomUUID, outputDir, AnalysisJobStates.CREATED)
      val opts = DeleteResourcesOptions(targetDir, false)
      val j = new DeleteResourcesJob(opts)
      val jobResult = j.run(job, writer)
      logger.info("Running delete job")
      jobResult.isRight must beTrue
      Files.exists(targetFile.toPath) must beTrue
      Files.exists(targetSubFile.toPath) must beTrue
    }
  }

}
