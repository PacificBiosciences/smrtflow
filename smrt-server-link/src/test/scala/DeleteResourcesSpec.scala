import java.nio.file.{Files, Path, Paths}
import java.io.File
import java.util.UUID

import org.apache.commons.io.{FileUtils, FilenameUtils}
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

import com.pacbio.secondary.smrtlink.analysis.reports.ReportUtils
import com.pacbio.secondary.smrtlink.analysis.datasets.MockDataSetUtils
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  PrinterJobResultsWriter,
  JobModels,
  AnalysisJobStates
}
import com.pacbio.secondary.smrtlink.analysis.jobtypes.{
  DeleteResourcesOptions,
  DeleteResourcesJob,
  DeleteDatasetsOptions,
  DeleteDatasetsJob
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{
  JobResource,
  BoundEntryPoint
}
import com.pacbio.secondary.smrtlink.analysis.externaltools.PacBioTestData

class DeleteResourcesSpec extends Specification with LazyLogging {

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
      val job = JobResource(UUID.randomUUID, outputDir)
      val opts = DeleteResourcesOptions(targetDir, true)
      val j = new DeleteResourcesJob(opts)
      val jobResult = j.run(job, writer)
      logger.info("Running delete job")
      jobResult.isRight must beTrue
      Files.exists(targetFile.toPath) must beFalse
      Files.exists(targetSubFile.toPath) must beFalse
      val deleteFile = targetDir.resolve("DELETED")
      Files.exists(deleteFile) must beTrue
      val r =
        ReportUtils.loadReport(Paths.get(jobResult.right.get.files(1).path))
      r.attributes(0).value must beEqualTo(targetDir.toString)
    }
    "Test behavior when delete is turned off" in {
      val (targetDir, targetFile, targetSubFile) = createTempFiles
      val outputDir = Files.createTempDirectory("delete-job")
      val job = JobResource(UUID.randomUUID, outputDir)
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

class DeleteDatasetsSpec extends Specification with LazyLogging {

  val NBYTES_MIN_BARCODED_SUBREADS: Long = 16729 // this may be a bad idea

  args(skipAll = !PacBioTestData.isAvailable)
  sequential

  private def runJob(paths: Seq[Path]) = {
    val writer = new PrinterJobResultsWriter
    val outputDir = Files.createTempDirectory("delete-job")
    val job = JobResource(UUID.randomUUID, outputDir)
    val opts = DeleteDatasetsOptions(paths, true)
    val j = new DeleteDatasetsJob(opts)
    j.run(job, writer)
  }

  "DeleteDatasetsJob" should {
    "Remove a dataset and external resources" in {
      // dataset setup - need to copy one over from test data repo
      val (subreads, barcodes) = MockDataSetUtils.makeBarcodedSubreads
      subreads.toFile.exists must beTrue
      barcodes.toFile.exists must beTrue
      // run job
      val jobResult = runJob(Seq(subreads))
      logger.info("Running delete job")
      jobResult.isRight must beTrue
      subreads.toFile.exists must beFalse
      subreads.getParent.toFile.listFiles must beEmpty
      barcodes.toFile.exists must beTrue
      val rptPath = Paths.get(jobResult.right.get.files(1).path)
      val r = ReportUtils.loadReport(rptPath)
      r.attributes(0).value must beEqualTo(subreads.toString)
      r.attributes(1).value must beEqualTo(0)
      r.attributes(2).value.asInstanceOf[Long] must beGreaterThanOrEqualTo(
        NBYTES_MIN_BARCODED_SUBREADS)
      r.tables(0).columns(0).values.size must beEqualTo(5)
    }
    "Fail when a dataset path does not exist" in {
      //val (subreads, barcodes) = MockDataSetUtils.makeBarcodedSubreads
      val targetDir = Files.createTempDirectory("missing-dataset")
      val targetDs = Paths.get(targetDir.toString + "missing.subreadset.xml")
      val jobResult = runJob(Seq(targetDs))
      jobResult.isLeft must beTrue
    }
    "Remove a dataset with missing resources" in {
      val pbdata = PacBioTestData()
      val targetDsSrc = pbdata.getFile("subreads-sequel")
      val targetDir = Files.createTempDirectory("missing-resources")
      val targetDs = Paths.get(
        targetDir.toString + "/" +
          FilenameUtils.getName(targetDsSrc.toString))
      FileUtils.copyFile(targetDsSrc.toFile, targetDs.toFile)
      val jobResult = runJob(Seq(targetDs))
      jobResult.isRight must beTrue
      targetDs.toFile.exists must beFalse
      val rptPath = Paths.get(jobResult.right.get.files(1).path)
      val r = ReportUtils.loadReport(rptPath)
      r.attributes(1).value must beEqualTo(5)
      r.attributes(3).value must beEqualTo(5)
      r.tables(0).columns(0).values.size must beEqualTo(6) // includes sts.xml
    }
    "Successfully remove two out of three datasets after failing on the first" in {
      val (subreads, barcodes) = MockDataSetUtils.makeBarcodedSubreads
      val targetDir = Files.createTempDirectory("missing-dataset")
      val targetDs = Paths.get(targetDir.toString + "missing.subreadset.xml")
      val jobResult = runJob(Seq(targetDs, subreads, barcodes))
      jobResult.isRight must beTrue
      subreads.toFile.exists must beFalse
      subreads.getParent.toFile.listFiles must beEmpty
      barcodes.toFile.exists must beFalse // deleting it this time
      val rptPath = Paths.get(jobResult.right.get.files(1).path)
      val r = ReportUtils.loadReport(rptPath)
      r.attributes(1).value must beEqualTo(1)
      r.attributes(3).value must beEqualTo(1)
      r.tables(0).columns(0).values.size must beEqualTo(9)
    }
  }
}
