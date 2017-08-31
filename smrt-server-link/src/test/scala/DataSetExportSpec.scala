
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.io.File
import java.util.UUID

import scala.util.Try
import scala.collection.JavaConversions._

import org.apache.commons.io.{FileUtils,FilenameUtils}
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

import com.pacbio.secondary.smrtlink.analysis.externaltools.ExternalToolsUtils
import com.pacbio.secondary.smrtlink.analysis.jobs.{NullJobResultsWriter, AnalysisJobStates}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobtypes.ExportDataSetsOptions
import com.pacbio.secondary.smrtlink.analysis.externaltools.PacBioTestData
import com.pacbio.secondary.smrtlink.analysis.datasets.validators.ValidateSubreadSet
import com.pacbio.secondary.smrtlink.analysis.datasets.io._
import com.pacbio.secondary.smrtlink.analysis.datasets._
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes


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

class DataSetExportSpecAdvanced
    extends Specification
    with DataSetFileUtils
    with ExternalToolsUtils
    with LazyLogging {
  args(skipAll = !PacBioTestData.isAvailable)

  sequential

  private def getData(dsIds: Seq[String]): Seq[Path] = {
    val pbdata = PacBioTestData()
    dsIds.map(pbdata.getFile(_))
  }

  private def zipAndUnzip(ds: Path) = {
    val zipPath = Files.createTempFile("subreadsets", ".zip")
    val n = ExportDataSets(Seq(ds), DataSetMetaTypes.Subread, zipPath)
    n must beGreaterThan(0)
    val uuid = getDataSetMiniMeta(ds).uuid
    FileUtils.deleteDirectory(ds.getParent.toFile)
    val dest = Files.createTempDirectory("subreads-extracted")
    runSimpleCmd(Seq("unzip", zipPath.toString, "-d", dest.toString)) must beNone
    val basename = FilenameUtils.getName(ds.toString)
    val dsUnzip = dest.resolve(s"${uuid}/${basename}")
    val subreads = DataSetLoader.loadSubreadSet(dsUnzip)
    val resPaths = subreads.getExternalResources.getExternalResource.map(_.getResourceId)
    resPaths.forall(Paths.get(_).isAbsolute) must beFalse
    val subreads2 = DataSetLoader.loadAndResolveSubreadSet(dsUnzip)
    ValidateSubreadSet.validator(subreads2).isSuccess must beTrue
  }

  private def exportDataSets(dsIds: Seq[String],
                             dsType: DataSetMetaTypes.DataSetMetaType) = {
    val datasets = getData(dsIds)
    val zipPath = Files.createTempFile("DataSets", ".zip")
    val n = ExportDataSets(datasets, dsType, zipPath)
    n must beGreaterThan(0)
  }

  "Export Datasets from PacBioTestData" should {
    "Export SubreadSet with relative paths" in {
      val ds = PacBioTestData().getFile("subreads-sequel")
      val dsTmp = MockDataSetUtils.makeTmpDataset(ds, DataSetMetaTypes.Subread)
      zipAndUnzip(dsTmp)
    }
    "Export SubreadSet with absolute paths" in {
      val ds = PacBioTestData().getFile("subreads-sequel")
      val dsTmp = MockDataSetUtils.makeTmpDataset(ds, DataSetMetaTypes.Subread,
                                                  copyFiles = false)
      zipAndUnzip(dsTmp)
    }
    "Export SubreadSet with relative paths converted to absolute" in {
      val ds = PacBioTestData().getFile("subreads-sequel")
      val dsTmp = MockDataSetUtils.makeTmpDataset(ds, DataSetMetaTypes.Subread)
      val subreadsTmp = DataSetLoader.loadAndResolveSubreadSet(dsTmp)
      DataSetWriter.writeSubreadSet(subreadsTmp, dsTmp)
      zipAndUnzip(dsTmp)
    }
    "Export SubreadSet with relative paths (with spaces)" in {
      val ds = PacBioTestData().getFile("subreads-sequel")
      val dsTmp = MockDataSetUtils.makeTmpDataset(ds, DataSetMetaTypes.Subread,
                                                  tmpDirBase = "dataset contents")
      zipAndUnzip(dsTmp)
    }
    "Export SubreadSet with absolute paths (with spaces)" in {
      val ds = PacBioTestData().getFile("subreads-sequel")
      val dsTmp = MockDataSetUtils.makeTmpDataset(ds, DataSetMetaTypes.Subread,
                                                  copyFiles = false,
                                                  tmpDirBase = "dataset contents")
      zipAndUnzip(dsTmp)
    }
    "Export SubreadSet with relative paths converted to absolute (with spaces)" in {
      val ds = PacBioTestData().getFile("subreads-sequel")
      val dsTmp = MockDataSetUtils.makeTmpDataset(ds, DataSetMetaTypes.Subread,
                                                  tmpDirBase = "dataset contents")
      val subreadsTmp = DataSetLoader.loadAndResolveSubreadSet(dsTmp)
      DataSetWriter.writeSubreadSet(subreadsTmp, dsTmp)
      zipAndUnzip(dsTmp)
    }
    "Generate ZIP file from multiple SubreadSets" in {
      exportDataSets(Seq("subreads-sequel", "subreads-xml"),
                     DataSetMetaTypes.Subread)
    }
    "Export AlignmentSet" in {
      exportDataSets(Seq("aligned-ds-2"), DataSetMetaTypes.Alignment)
    }
    "Export ConsensusReadSet" in {
      exportDataSets(Seq("rsii-ccs"), DataSetMetaTypes.CCS)
    }
    "Export ConsensusAlignmentSet" in {
      exportDataSets(Seq("rsii-ccs-aligned"), DataSetMetaTypes.AlignmentCCS)
    }
    "Export HdfSubreadSet" in {
      exportDataSets(Seq("hdfsubreads"), DataSetMetaTypes.HdfSubread)
    }
    "Export BarcodeSet" in {
      exportDataSets(Seq("barcodeset"), DataSetMetaTypes.Barcode)
    }
    "Export ContigSet" in {
      exportDataSets(Seq("contigset"), DataSetMetaTypes.Contig)
    }
    "Export two SubreadSets that reference the same BarcodeSet" in {
      val pbdata = PacBioTestData()
      val tmpDir = Files.createTempDirectory("dataset-contents")
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
      val n = ExportDataSets(datasets, DataSetMetaTypes.Subread, zipPath)
      n must beGreaterThan(0)
    }
  }
}
