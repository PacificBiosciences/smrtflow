
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


class ExportUtilsSpec extends Specification with ExportUtils with LazyLogging {

  def toPaths(resource: String, basePath: String, destPath: String,
              archiveRoot: Option[String]): (Path, Path) =
    relativizeResourcePath(Paths.get(resource), Paths.get(basePath),
                           Paths.get(destPath), archiveRoot.map(Paths.get(_)))

  "Base functions" should {
    "Relativize resource paths" in {
      // relative, no archiveRoot
      val (resource, dest) = toPaths("subreads.bam",
                                     "/data/movie1",
                                     "exported",
                                     None)
      resource.toString must beEqualTo("subreads.bam")
      dest.toString must beEqualTo("exported/subreads.bam")
      // relative with subdir, no archiveRoot
      val (resource1, dest1) = toPaths("mydata/subreads.bam",
                                       "/data/movie1",
                                       "exported",
                                       None)
      resource1.toString must beEqualTo("mydata/subreads.bam")
      dest1.toString must beEqualTo("exported/mydata/subreads.bam")
      // relative, archiveRoot defined
      val (resource2, dest2) = toPaths("subreads.bam",
                                       "/data/movie1",
                                       "movie1",
                                       Some("/data"))
      resource2.toString must beEqualTo("subreads.bam")
      dest2.toString must beEqualTo("movie1/subreads.bam")
      // absolute, no archiveRoot
      val (resource3, dest3) = toPaths("/data/barcodes/2.xml",
                                       "/data/movie1",
                                       "movie1",
                                       None)
      resource3.toString must beEqualTo("./data/barcodes/2.xml")
      dest3.toString must beEqualTo("movie1/data/barcodes/2.xml")
      // absolute, archiveRoot includes path
      val (resource4, dest4) = toPaths("/data/barcodes/2.xml",
                                       "/data/movie1",
                                       "exported/movie1",
                                       Some("/data"))
      resource4.toString must beEqualTo("../barcodes/2.xml")
      dest4.toString must beEqualTo("barcodes/2.xml")
      // absolute, archiveRoot does *not* include path
      val (resource5, dest5) = toPaths("/data2/barcodes/2.xml",
                                       "/data/movie1",
                                       "movie1",
                                       Some("/data"))
      resource5.toString must beEqualTo("./data2/barcodes/2.xml")
      dest5.toString must beEqualTo("movie1/data2/barcodes/2.xml")
      // absolute, archiveRoot includes path
      val (resource6, dest6) = toPaths("/data/jobs/1/tasks/task-1/subreads.bam",
                                       "/data/jobs/1/tasks/gather-1",
                                       "tasks/gather-1",
                                       Some("/data/jobs/1"))
      resource6.toString must beEqualTo("../task-1/subreads.bam")
      dest6.toString must beEqualTo("tasks/task-1/subreads.bam")
      // relative with parent dir, subdir of archiveRoot
      val (resource7, dest7) = toPaths("../task-1/subreads.bam",
                                       "/data/jobs/1/tasks/gather-1",
                                       "tasks/gather-1",
                                       Some("/data/jobs/1"))
      resource7.toString must beEqualTo("../task-1/subreads.bam")
      dest7.toString must beEqualTo("tasks/task-1/subreads.bam")
      // relative with parent dir, *not* a subdir of archiveRoot
      // FIXME This is probably going to break, although I do not think it
      // is likely to happen in real-world uses like pbsmrtpipe job export
      /*
      val (resource8, dest8) = toPaths("../task-1/subreads.bam",
                                       "/data/jobs/1/tasks/gather-1",
                                       "exported",
                                       Some("/data/jobs/1/tasks/gather-1"))
      resource8.toString must beEqualTo("../task-1/subreads.bam")
      dest8.toString must beEqualTo("task-1/subreads.bam")
      */
    }
    "Get external resources from dataset" in {
      val REF = "/dataset-references/example_reference_dataset/reference.dataset.xml"
      val refXml = Paths.get(getClass.getResource(REF).getPath)
      val ds = DataSetLoader.loadAndResolveReferenceSet(refXml)
      val resources = getResources(ds)
      resources.size must beEqualTo(5)
    }
  }
}

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
      n must beGreaterThan(0L)
    }
    "Run via jobs API" in {
      val url = getClass.getResource(ds)
      val datasets = Seq(Paths.get(url.getPath))
      val zipPath = Files.createTempFile("referencesets", ".zip")
      val outputDir = Files.createTempDirectory("export-job-test")
      val dsType = DataSetMetaTypes.Reference
      val opts = ExportDataSetsOptions(dsType, datasets, zipPath)
      val job = JobResource(UUID.randomUUID, outputDir)
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
    with ExportUtils
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
    n must beGreaterThan(0L)
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
    n must beGreaterThan(0L)
  }

  "Extract external resources from datasets" should {
    "SubreadSet with scraps and stats xml" in {
      val ds = PacBioTestData().getFile("subreads-sequel")
      val subreads = DataSetLoader.loadAndResolveSubreadSet(ds)
      val resources = getResources(subreads)
      resources.size must beEqualTo(5)
    }
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
      n must beGreaterThan(0L)
    }
  }
}
