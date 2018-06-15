import java.nio.file.{Files, Path, Paths}
import java.io.File

import scala.util.Try
import org.apache.commons.io.{FileUtils, FilenameUtils}
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

import com.pacbio.secondary.smrtlink.analysis.datasets.MockDataSetUtils
import com.pacbio.secondary.smrtlink.io.DeleteResourcesUtils
import com.pacbio.secondary.smrtlink.analysis.externaltools.PacBioTestResourcesLoader
import com.pacbio.secondary.smrtlink.testkit.TestDataResourcesUtils

class DeleteJobUtilsSpec
    extends Specification
    with DeleteResourcesUtils
    with LazyLogging {

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

  "Delete Job Utils" should {
    "Test basic use case" in {
      val (targetDir, targetFile, targetSubFile) = createTempFiles
      val outputDir = Files.createTempDirectory("delete-job")
      val rptPath = outputDir.resolve("delete_report.json")
      val rpt = deleteJobDirFiles(targetDir, true, rptPath)
      Files.exists(targetFile.toPath) must beFalse
      Files.exists(targetSubFile.toPath) must beFalse
      val deleteFile = targetDir.resolve("DELETED")
      Files.exists(deleteFile) must beTrue
      rpt.attributes(0).value must beEqualTo(targetDir.toString)
    }
    "Test behavior when delete is turned off" in {
      val (targetDir, targetFile, targetSubFile) = createTempFiles
      val outputDir = Files.createTempDirectory("delete-job")
      val rptPath = outputDir.resolve("delete_report.json")
      val rpt = deleteJobDirFiles(targetDir, false, rptPath)
      Files.exists(targetFile.toPath) must beTrue
      Files.exists(targetSubFile.toPath) must beTrue
    }
  }
}

// This inherits from the same DeleteResourcesUtils trait as the previous
// spec, but it requires PacBioTestData so it's a separate class
class DeleteDatasetsSpec
    extends Specification
    with DeleteResourcesUtils
    with LazyLogging
    with TestDataResourcesUtils {

  val NBYTES_MIN_BARCODED_SUBREADS: Long = 16729 // this may be a bad idea

  args(skipAll = !PacBioTestResourcesLoader.isAvailable)

  sequential

  private def runToReport(paths: Seq[Path], removeFiles: Boolean = true) = {
    val outputDir = Files.createTempDirectory("delete-job")
    val rptPath = outputDir.resolve("delete_report.json")
    deleteDataSetFiles(paths, removeFiles)
  }

  "DeleteDatasetsJob" should {
    "Remove a dataset and external resources" in {
      // dataset setup - need to copy one over from test data repo
      val (subreads, barcodes) =
        MockDataSetUtils.makeBarcodedSubreads(testResources)
      subreads.toFile.exists must beTrue
      barcodes.toFile.exists must beTrue
      val r = runToReport(Seq(subreads), true)
      subreads.toFile.exists must beFalse
      subreads.getParent.toFile.listFiles must beEmpty
      barcodes.toFile.exists must beTrue
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
      Try(runToReport(Seq(targetDs))).toOption must beNone
    }
    "Remove a dataset with missing resources" in {
      val targetDsSrc = testResources.findById("subreads-sequel").get.path
      val targetDir = Files.createTempDirectory("missing-resources")
      val targetDs = Paths.get(
        targetDir.toString + "/" +
          FilenameUtils.getName(targetDsSrc.toString))
      FileUtils.copyFile(targetDsSrc.toFile, targetDs.toFile)
      val r = runToReport(Seq(targetDs))
      targetDs.toFile.exists must beFalse
      r.attributes(1).value must beEqualTo(5)
      r.attributes(3).value must beEqualTo(5)
      r.tables(0).columns(0).values.size must beEqualTo(6) // includes sts.xml
    }
    "Successfully remove two out of three datasets after failing on the first" in {
      val (subreads, barcodes) =
        MockDataSetUtils.makeBarcodedSubreads(testResources)
      val targetDir = Files.createTempDirectory("missing-dataset")
      val targetDs = Paths.get(targetDir.toString + "missing.subreadset.xml")
      val r = runToReport(Seq(targetDs, subreads, barcodes))
      subreads.toFile.exists must beFalse
      subreads.getParent.toFile.listFiles must beEmpty
      barcodes.toFile.exists must beFalse // deleting it this time
      r.attributes(1).value must beEqualTo(1)
      r.attributes(3).value must beEqualTo(1)
      r.tables(0).columns(0).values.size must beEqualTo(9)
    }
  }
}
