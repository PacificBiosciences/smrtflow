import java.nio.file.{Files, Paths}

import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.externaltools.{PacBioTestData, PbReports}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{JobTypeId, JobTypeIds}
import com.pacbio.secondary.smrtlink.analysis.jobs.NullJobResultsWriter
import com.pacbio.secondary.smrtlink.analysis.reports.DataSetReports
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

/**
 * Tests for calling pbreports on an sts.xml file
 *
 * The tests will be skipped if the pbreports python modules aren't available
 */
class PbReportsSpec extends Specification with LazyLogging {

  args(skipAll = !PbReports.isAvailable())

  "pbreports wrapper" should {
    "create filter json" in {
      val name = "m54006_160113_202609.sts.xml"
      val stsXml = getClass.getResource(name)
      val tmpDir = Files.createTempDirectory("PbReportsStsXmlSpec")
      val outPath = Paths.get(tmpDir.toString(), "filter.json")
      PbReports.FilterStatsXml.run(Paths.get(stsXml.toURI()), outPath)
      Files.exists(outPath) must beTrue
    }
  }
}

class PbReportsSubreadsSpec extends Specification with LazyLogging {

  args(skipAll = !(PbReports.isAvailable() && PacBioTestData.isAvailable))

  // For the interface to work
  val jobTypeId = JobTypeIds.SIMPLE

  val log = new NullJobResultsWriter
  "Utility functions" should {
    "Verify sts.xml in Sequel dataset" in {
      val pbdata = PacBioTestData()
      val f = pbdata.getFile("subreads-sequel")
      val hasStats = DataSetReports.hasStatsXml(f, DataSetMetaTypes.Subread)
      hasStats must beTrue
    }
    "Verify NO sts.xml in RSII dataset" in {
      val pbdata = PacBioTestData()
      val f = pbdata.getFile("subreads-xml")
      val hasStats = DataSetReports.hasStatsXml(f, DataSetMetaTypes.Subread)
      hasStats must beFalse
    }
  }
  "SubreadSet report generation" should {
    "create three reports for Sequel dataset with sts.xml" in {
      val pbdata = PacBioTestData()
      val f = pbdata.getFile("subreads-sequel")
      val tmpDir = Files.createTempDirectory("reports")
      val rpts = DataSetReports.runAll(f, DataSetMetaTypes.Subread,
                                       tmpDir, jobTypeId, log)
      rpts.size must beEqualTo(3)
      val reportIds = rpts.map(_.sourceId).sorted
      reportIds must beEqualTo(
        Seq("pbreports.tasks.adapter_report_xml",
            "pbreports.tasks.filter_stats_report_xml",
            "pbreports.tasks.loading_report_xml"))
    }
    "create a single fallback report for converted RSII dataset" in {
      val pbdata = PacBioTestData()
      val f = pbdata.getFile("subreads-xml")
      val tmpDir = Files.createTempDirectory("reports")
      val rpts = DataSetReports.runAll(f, DataSetMetaTypes.Subread,
                                       tmpDir, jobTypeId, log)
      rpts.size must beEqualTo(1)
    }
  }
}

// FIXME the dataset needs to go into PacBioTestData, and then we merge this
// with the previous class
class PbReportsSubreadsControlSpec extends Specification with LazyLogging {
  val log = new NullJobResultsWriter

  val DS_PATH = Paths.get("/pbi/dept/secondary/siv/testdata/SA3-Sequel/lambda/314/3140099/r54099_20160830_222805/3_C01_control/m54099_160831_134818.subreadset.xml")

  args(skipAll = !(PbReports.isAvailable() && DS_PATH.toFile.exists))
  "SubreadSet report generation" should {
    "create four reports for a newer Sequel dataset with control" in {
      val tmpDir = Files.createTempDirectory("reports")
      val rpts = DataSetReports.runAll(DS_PATH, DataSetMetaTypes.Subread,
                                       tmpDir, JobTypeIds.SIMPLE, log)
      rpts.size must beEqualTo(4)
      val reportIds = rpts.map(_.sourceId).sorted
      reportIds must beEqualTo(
        Seq("pbreports.tasks.adapter_report_xml",
            "pbreports.tasks.control_report",
            "pbreports.tasks.filter_stats_report_xml",
            "pbreports.tasks.loading_report_xml"))
    }
  }
}
