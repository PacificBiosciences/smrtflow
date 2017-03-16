
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.io.File
import java.util.UUID

import scala.util.Try

import org.apache.commons.io.{FileUtils,FilenameUtils}
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

import com.pacbio.secondary.analysis.jobs.{NullJobResultsWriter, AnalysisJobStates}
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobtypes.ImportDataSetOptions
import com.pacbio.secondary.analysis.externaltools.{PacBioTestData, PbReports}
import com.pacbio.secondary.analysis.datasets.io._
import com.pacbio.secondary.analysis.datasets._
import com.pacbio.secondary.analysis.constants.FileTypes


class DataSetImportSpec extends Specification with LazyLogging {
  args(skipAll = !PacBioTestData.isAvailable)

  sequential

  private def getData(dsIds: Seq[String]): Seq[Path] = {
    val pbdata = PacBioTestData()
    dsIds.map(pbdata.getFile(_))
  }

  def runImport(dsId: String, dsType: DataSetMetaTypes.DataSetMetaType) = {
    val pbdata = PacBioTestData()
    val path = pbdata.getFile(dsId).toAbsolutePath.toString
    val outputDir = Files.createTempDirectory("import-test")
    val opts = ImportDataSetOptions(path, dsType)
    val job = JobResource(UUID.randomUUID, outputDir, AnalysisJobStates.CREATED)
    val j = opts.toJob
    val writer = new NullJobResultsWriter
    val jobResult = j.run(job, writer)
    jobResult.isRight must beTrue
    jobResult.right.get.asInstanceOf[PacBioDataStore]
  }

  def checkNumReports(datastore: PacBioDataStore, nReports: Int) = {
    datastore.files.size must beEqualTo(nReports + 2)
    datastore.files.filter(_.fileTypeId == FileTypes.REPORT.fileTypeId).size must beEqualTo(nReports)
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
