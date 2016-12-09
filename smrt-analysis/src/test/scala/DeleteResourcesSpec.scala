
import java.nio.file.{Files, Paths}
import java.io.File
import java.util.UUID

import org.apache.commons.io.{FileUtils,FilenameUtils}
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

import com.pacbio.secondary.analysis.reports.ReportUtils
import com.pacbio.secondary.analysis.jobs.{PrinterJobResultsWriter, JobModels, AnalysisJobStates}
import com.pacbio.secondary.analysis.jobtypes.{DeleteResourcesOptions, DeleteResourcesJob, DeleteDatasetOptions, DeleteDatasetJob}
import com.pacbio.secondary.analysis.jobs.JobModels.{JobResource, BoundEntryPoint}
import com.pacbio.secondary.analysis.externaltools.PacBioTestData


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
  "DeleteResourcesJob" should {
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

class DeleteDatasetSpec extends Specification with LazyLogging{

  val NBYTES_BARCODED_SUBREADS = 16729 // this may be a bad idea

  args(skipAll = !PacBioTestData.isAvailable)
  sequential

  val writer = new PrinterJobResultsWriter
  "DeleteDatasetJob" should {
    "Remove a dataset and external resources" in {
      // dataset setup - need to copy one over from test data repo
      val pbdata = PacBioTestData()
      val targetDir = Files.createTempDirectory("dataset-contents")
      val subreadsDestDir = new File(targetDir.toString + "/SubreadSet")
      val barcodesDestDir = new File(targetDir.toString + "/BarcodeSet")
      val subreadsSrc = pbdata.getFile("barcoded-subreadset")
      val subreadsDir = subreadsSrc.getParent.toFile
      val barcodesSrc = pbdata.getFile("barcodeset")
      val barcodesDir = barcodesSrc.getParent.toFile
      // only copy the files we need for this SubreadSet, that way we can check
      // for an empty directory
      for (f <- subreadsDir.listFiles) {
        val filename = FilenameUtils.getName(f.toString)
        if (filename.startsWith("barcoded")) {
          val dest = new File(subreadsDestDir.toString + "/" + filename)
          FileUtils.copyFile(f, dest)
        }
      }
      FileUtils.copyDirectory(barcodesDir, barcodesDestDir)
      val subreads = Paths.get(subreadsDestDir.toString + "/" +
                               FilenameUtils.getName(subreadsSrc.toString))
      var barcodes = Paths.get(barcodesDestDir.toString + "/" +
                               FilenameUtils.getName(barcodesSrc.toString))
      subreads.toFile.exists must beTrue
      barcodes.toFile.exists must beTrue
      // run job
      val outputDir = Files.createTempDirectory("delete-job")
      val job = JobResource(UUID.randomUUID, outputDir, AnalysisJobStates.CREATED)
      val opts = DeleteDatasetOptions(Seq(subreads), true)
      val j = new DeleteDatasetJob(opts)
      val jobResult = j.run(job, writer)
      logger.info("Running delete job")
      jobResult.isRight must beTrue
      subreads.toFile.exists must beFalse
      subreadsDestDir.listFiles must beEmpty
      barcodes.toFile.exists must beTrue
      val rptPath = Paths.get(jobResult.right.get.files(1).path)
      val r = ReportUtils.loadReport(rptPath)
      r.attributes(0).value must beEqualTo(subreads.toString)
      r.attributes(1).value must beEqualTo(0)
      r.attributes(2).value must beEqualTo(NBYTES_BARCODED_SUBREADS)
      r.tables(0).columns(0).values.size must beEqualTo(5)
    }
  }
}
