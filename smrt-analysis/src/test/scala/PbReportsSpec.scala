import java.nio.file.{Paths, Path, Files}

import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._

import com.pacbio.secondary.analysis.externaltools.{PbReports, PacBioTestData}
import com.pacbio.secondary.analysis.reports.DataSetReports
import com.pacbio.secondary.analysis.jobs.JobModels.JobTypeId
import com.pacbio.secondary.analysis.jobs.NullJobResultsWriter
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes

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

  val log = new NullJobResultsWriter
  "SubreadSet report generation" should {
    "create three reports for Sequel dataset with sts.xml" in {
      val pbdata = PacBioTestData()
      val f = pbdata.getFile("subreads-sequel")
      val tmpDir = Files.createTempDirectory("reports")
      val rpts = DataSetReports.runAll(f, DataSetMetaTypes.Subread,
                                       tmpDir, JobTypeId("reports"), log)
      rpts.size must beEqualTo(3)
    }
    "create a single fallback report for converted RSII dataset" in {
      val pbdata = PacBioTestData()
      val f = pbdata.getFile("subreads-xml")
      val tmpDir = Files.createTempDirectory("reports")
      val rpts = DataSetReports.runAll(f, DataSetMetaTypes.Subread,
                                       tmpDir, JobTypeId("reports"), log)
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
                                       tmpDir, JobTypeId("reports"), log)
      rpts.size must beEqualTo(4)
    }
  }
}
