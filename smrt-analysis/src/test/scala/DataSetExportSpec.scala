
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.io.File
import java.util.UUID

import scala.util.Try

import org.apache.commons.io.{FileUtils,FilenameUtils}
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

import com.pacbio.secondary.analysis.jobs.{NullJobResultsWriter, AnalysisJobStates}
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobtypes.ExportDataSetsOptions
import com.pacbio.secondary.analysis.externaltools.PacBioTestData
import com.pacbio.secondary.analysis.datasets.io._
import com.pacbio.secondary.analysis.datasets._
import com.pacbio.secondary.analysis.constants.FileTypes


class DataSetExportSpec extends Specification with LazyLogging {

  sequential

  val writer = new NullJobResultsWriter
  val ds = "/dataset-references/example_reference_dataset/reference.dataset.xml"

  "Export Dataset" should {
    "Generate ZIP file from valid ReferenceSet" in {
      val url = getClass.getResource(ds)
      val datasets = Seq(Paths.get(url.getPath))
      val zipPath = Files.createTempFile("referencesets", ".zip")
      val dsType = DataSetMetaTypes.Reference
      val n = ExportDataSets(datasets, dsType, zipPath)
      n must beGreaterThan(0)
    }
    "Run via jobs API" in {
      val url = getClass.getResource(ds)
      val datasets = Seq(Paths.get(url.getPath))
      val zipPath = Files.createTempFile("referencesets", ".zip")
      val outputDir = Files.createTempDirectory("export-job-test")
      val dsType = DataSetMetaTypes.Reference
      val opts = ExportDataSetsOptions(dsType, datasets, zipPath)
      val job = JobResource(UUID.randomUUID, outputDir, AnalysisJobStates.CREATED)
      val j = opts.toJob
      val jobResult = j.run(job, writer)
      jobResult.isRight must beTrue
      val datastore = jobResult.right.get.asInstanceOf[PacBioDataStore]
      datastore.files(0).fileTypeId must beEqualTo(FileTypes.ZIP.fileTypeId)
    }
    "Failure mode: resource does not exist" in {
      val startPath = Paths.get(getClass.getResource(ds).getPath)
      val tmpPath = Files.createTempFile("referenceset", ".xml")
      Files.copy(startPath, tmpPath, StandardCopyOption.REPLACE_EXISTING)
      val zipPath = Files.createTempFile("referencesets", ".zip")
      val dsType = DataSetMetaTypes.Reference
      val result = Try {
        val n = ExportDataSets(Seq(tmpPath), DataSetMetaTypes.Reference, zipPath)
      }
      result.isSuccess must beFalse
      // repeat with skipMissingFiles = true
      val n = ExportDataSets(Seq(tmpPath), DataSetMetaTypes.Reference, zipPath, true)
      n must beGreaterThan(0)
    }
    "Failure mode: wrong metatype" in {
      val url = getClass.getResource(ds)
      val datasets = Seq(Paths.get(url.getPath))
      val zipPath = Files.createTempFile("referencesets", ".zip")
      val result = Try {
        val n = ExportDataSets(datasets, DataSetMetaTypes.Subread, zipPath)
      }
      result.isSuccess must beFalse
    }
  }
}

class DataSetExportSpecAdvanced extends Specification with LazyLogging {
  args(skipAll = !PacBioTestData.isAvailable)

  sequential

  private def getData(dsIds: Seq[String]): Seq[Path] = {
    val pbdata = PacBioTestData()
    dsIds.map(pbdata.getFile(_))
  }

  "Export Datasets from PacBioTestData" should {
    "Generate ZIP file from multiple SubreadSets" in {
      val datasets = getData(Seq("subreads-sequel", "subreads-xml"))
      val zipPath = Files.createTempFile("subreadsets", ".zip")
      //val zipPath = Paths.get("subreadsets.zip")
      val n = ExportDataSets(datasets, DataSetMetaTypes.Subread, zipPath)
      n must beGreaterThan(0)
    }
    "Export AlignmentSet" in {
      val datasets = getData(Seq("aligned-ds-2"))
      val zipPath = Files.createTempFile("alignmentsets", ".zip")
      val n = ExportDataSets(datasets, DataSetMetaTypes.Alignment, zipPath)
      n must beGreaterThan(0)
    }
    "Export ConsensusReadSet" in {
      val datasets = getData(Seq("rsii-ccs"))
      val zipPath = Files.createTempFile("ccssets", ".zip")
      val n = ExportDataSets(datasets, DataSetMetaTypes.CCS, zipPath)
      n must beGreaterThan(0)
    }
    "Export ConsensusAlignmentSet" in {
      val datasets = getData(Seq("rsii-ccs-aligned"))
      val zipPath = Files.createTempFile("ccsaligned", ".zip")
      val n = ExportDataSets(datasets, DataSetMetaTypes.AlignmentCCS, zipPath)
      n must beGreaterThan(0)
    }
    "Export HdfSubreadSet" in {
      val datasets = getData(Seq("hdfsubreads"))
      val zipPath = Files.createTempFile("hdfsubreads", ".zip")
      val n = ExportDataSets(datasets, DataSetMetaTypes.HdfSubread, zipPath)
      n must beGreaterThan(0)
    }
    "Export BarcodeSet" in {
      val datasets = getData(Seq("barcodeset"))
      val zipPath = Files.createTempFile("barcodes", ".zip")
      val n = ExportDataSets(datasets, DataSetMetaTypes.Barcode, zipPath)
      n must beGreaterThan(0)
    }
    "Export ContigSet" in {
      val datasets = getData(Seq("contigset"))
      val zipPath = Files.createTempFile("contigs", ".zip")
      val n = ExportDataSets(datasets, DataSetMetaTypes.Contig, zipPath)
      n must beGreaterThan(0)
    }
    "Export two SubreadSets that reference the same BarcodeSet" in {
      val pbdata = PacBioTestData()
      val tmpDir = Files.createTempDirectory("dataset-contents")
      println(tmpDir)
      val barcodesSrc = pbdata.getFile("barcodeset")
      val barcodesDir = barcodesSrc.getParent.toFile
      val barcodesDestDir = new File(tmpDir.toString + "/BarcodeSet")
      FileUtils.copyDirectory(barcodesDir, barcodesDestDir)
      val subreadsDir = pbdata.getFile("barcoded-subreadset").getParent.toFile
      Seq("barcode-1", "barcode-2").foreach { d =>
        val subreadsDestDir = new File(tmpDir.toString + "/" + d)
        subreadsDestDir.mkdir
        val prefix = "barcoded"
        subreadsDir.listFiles.foreach { f=>
          val filename = FilenameUtils.getName(f.toString)
          if (filename.startsWith(prefix)) {
            val dest = new File(subreadsDestDir.toString + "/" + filename)
            println(dest)
            FileUtils.copyFile(f, dest)
          }
        }
      }
      val url = getClass.getResource("/dataset-subreads/gathered_barcoded.subreadset.xml")
      val subreadsSrc = Paths.get(url.getPath)
      val subreadsTmp = Paths.get(tmpDir.toString + "/" +
                                  FilenameUtils.getName(subreadsSrc.toString))
      FileUtils.copyFile(subreadsSrc.toFile, subreadsTmp.toFile)
      val dsSubreads = DataSetLoader.loadAndResolveSubreadSet(subreadsTmp)
      val datasets = Seq(subreadsTmp)
      val zipPath = Files.createTempFile("subreadsets", ".zip")
      println(zipPath)
      val n = ExportDataSets(datasets, DataSetMetaTypes.Subread, zipPath)
      n must beGreaterThan(0)
    }
  }
}
