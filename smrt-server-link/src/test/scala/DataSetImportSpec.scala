
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.io.File
import java.util.UUID

import scala.util.Try

import org.apache.commons.io.{FileUtils,FilenameUtils}
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs._
import com.pacbio.secondary.smrtlink.analysis.jobtypes.ImportDataSetOptions
import com.pacbio.secondary.smrtlink.analysis.externaltools.{PacBioTestData, PbReports}
import com.pacbio.secondary.smrtlink.analysis.datasets.io._
import com.pacbio.secondary.smrtlink.analysis.datasets._
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes


trait DataSetImports { self: Specification =>

  protected def runImportImpl(
      path: Path,
      dsType: DataSetMetaTypes.DataSetMetaType,
      resultWriter: JobResultWriter) = {
    val outputDir = Files.createTempDirectory("import-test")
    println(outputDir)
    val opts = ImportDataSetOptions(path, dsType)
    val job = JobResource(UUID.randomUUID, outputDir)
    val j = opts.toJob
    val jobResult = j.run(job, resultWriter)
    jobResult.isRight must beTrue
    jobResult.right.get
  }

  protected def checkNumReports(datastore: PacBioDataStore, nReports: Int) = {
    datastore.files.size must beEqualTo(nReports + 2)
    datastore.files.count(_.fileTypeId == FileTypes.REPORT.fileTypeId) must beEqualTo(nReports)
  }
}

class DataSetImportSpec
    extends Specification
    with DataSetImports
    with LazyLogging {
  args(skipAll = !PacBioTestData.isAvailable)

  sequential

  val writer = new NullJobResultsWriter

  private def getData(dsIds: Seq[String]): Seq[Path] = {
    val pbdata = PacBioTestData()
    dsIds.map(pbdata.getFile)
  }

  private def runImport(dsId: String, dsType: DataSetMetaTypes.DataSetMetaType) = {
    val pbdata = PacBioTestData()
    val path = pbdata.getFile(dsId).toAbsolutePath
    runImportImpl(path, dsType, writer)
  }

  "Import Datasets from PacBioTestData" should {
    // Each of these should produce 2 log files and at least 1 report
    "Import SubreadSet with sts.xml from 3.0.5 release" in {
      val datastore = runImport("subreads-sequel", DataSetMetaTypes.Subread)
      val N_REPORTS = if (PbReports.isAvailable()) 3 else 1
      checkNumReports(datastore, N_REPORTS)
    }
    "Import RSII SubreadSet" in {
      val datastore = runImport("subreads-xml", DataSetMetaTypes.Subread)
      checkNumReports(datastore, 1)
    }
    "Import ReferenceSet" in {
      val datastore = runImport("lambdaNEB", DataSetMetaTypes.Reference)
      checkNumReports(datastore, 1)
    }
    "Import HdfSubreadSet" in {
      val datastore = runImport("hdfsubreads", DataSetMetaTypes.HdfSubread)
      checkNumReports(datastore, 1)
    }
    "Import BarcodeSet" in {
      val datastore = runImport("barcodeset", DataSetMetaTypes.Barcode)
      checkNumReports(datastore, 1)
    }
    "Import AlignmentSet" in {
      val datastore = runImport("aligned-ds-2", DataSetMetaTypes.Alignment)
      checkNumReports(datastore, 1)
    }
    "Import ContigSet" in {
      val datastore = runImport("contigset", DataSetMetaTypes.Contig)
      checkNumReports(datastore, 1)
    }
    "Import ConsensusReadSet" in {
      val datastore = runImport("rsii-ccs", DataSetMetaTypes.CCS)
      checkNumReports(datastore, 1)
    }
    "Import ConsensusAlignmentSet" in {
      val datastore = runImport("rsii-ccs-aligned", DataSetMetaTypes.AlignmentCCS)
      checkNumReports(datastore, 1)
    }
  }
}

// XXX For internal testing and debugging
class DataSetImportAdvancedSpec
    extends Specification
    with DataSetImports
    with LazyLogging {
  val BASE_DIR = "/unknownpath" // replace with something more useful
  args(skipAll = !Paths.get(BASE_DIR).toFile.exists)

  sequential

  val writer = new PrinterJobResultsWriter
  "Import additional datasets" should {
    "Import recent SubreadSet" in {
      val path = Paths.get("/unknownpath")
      val datastore = runImportImpl(path, DataSetMetaTypes.Subread, writer)
      val N_REPORTS = if (PbReports.isAvailable()) 4 else 1
      checkNumReports(datastore, N_REPORTS)
    }
  }
}
