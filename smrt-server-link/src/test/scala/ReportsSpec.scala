import java.nio.file.Paths
import java.util.UUID

import com.pacbio.secondary.smrtlink.analysis.reports._
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._
import spray.json._

/* Test for Report model serialization */
class ReportsSpec
    extends Specification
    with ReportJsonProtocol
    with LazyLogging {
  import ReportModels._
  import ReportUtils._

  def loadTestReport(name: String) = {
    val px = Paths.get(getClass.getResource(s"reports/$name").toURI)
    loadReport(px)
  }

  sequential

  "Testing Report serialization" should {
    "Convert a Report to and from JSON" in {
      val rpt = ReportUtils.mockReport("unit_test", "Example Report")
      val s = rpt.toJson
      //println(s.prettyPrint)
      val rs = s.convertTo[Report]
      //println(rs)
      rs.tables.head.columns.head.values.length must beEqualTo(4)
      val uuid = rpt.uuid
      val uuidFromJson = rs.uuid
      uuid must beEqualTo(uuidFromJson)
      rs.attributes(0).asInstanceOf[ReportLongAttribute].value must beEqualTo(
        7)
      rs.getAttributeLongValue("nfiles") must beEqualTo(Some(7))
      rs.attributes(1)
        .asInstanceOf[ReportDoubleAttribute]
        .value must beEqualTo(123.4)
      rs.getAttributeDoubleValue("run_time") must beEqualTo(Some(123.4))
      rs.attributes(2).asInstanceOf[ReportStrAttribute].value must beEqualTo(
        "Report test")
      rs.getAttributeValue("job_name").get.asInstanceOf[String] must beEqualTo(
        "Report test")
      rs.attributes(3).asInstanceOf[ReportBooleanAttribute].value must beTrue
      rs.getAttributeValue("was_successful")
        .get
        .asInstanceOf[Boolean] must beEqualTo(true)
      rs.getPlot("unit_test.unit_test.plotgroups",
                 "unit_test.unit_test.plot_1") must beSome
      rs.getTableValueFromColumn("report_table", "col1").length must beEqualTo(
        4)
      // FIXME(mpkocher)(2017-7-17) The report values are [10, null, 5.931, "asdf"], which suggests there's a casting bug in the column
      rs.getFirstValueFromTableColumn("report_table", "col1")
        .map(_.asInstanceOf[Double]) must beSome(10.0)
    }
    "Load Report Version 1.0.0 schema " in {

      val name = "report_version_100.json"
      val rpt = loadTestReport(name)

      rpt.id must beEqualTo("adapter")
      rpt.title must beEqualTo("Example Report")
      rpt.uuid must beEqualTo(
        UUID.fromString("9376a0c8-4406-11e6-8e9f-3c15c2cc8f88"))
    }
    "Load Mapping Stats Report (legacy) " in {

      val name = "mapping_stats_report.json"
      val rpt = loadTestReport(name)

      rpt.id must beEqualTo("mapping_stats")
    }
    "Load Filter Report Stats Report (legacy) " in {

      val name = "filter_reports_filter_stats.json"
      val rpt = loadTestReport(name)

      rpt.id must beEqualTo("filtering_report")
    }
    "Sanity Load Report with Null Attribute" in {
      val name = "converage_report.json"
      val rpt = loadTestReport(name)
      // println(s"Report ${rpt.attributes}")
      rpt.id must beEqualTo("coverage")
    }
  }
}
