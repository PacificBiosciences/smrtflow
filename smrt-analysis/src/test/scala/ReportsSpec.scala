
import com.pacbio.secondary.analysis.reports._

import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._
import spray.json._
import java.util.UUID


/* Test for Report model serialization */
class ReportSpec extends Specification with ReportJsonProtocol with LazyLogging {
  import ReportModels._

  sequential

  "Testing Report serialization" should {
    "Convert a Report to and from JSON" in {
      val rpt = MockReportUtils.mockReport("unit_test")
      val s = rpt.toJson
      println(s.prettyPrint)
      val rs = s.convertTo[Report]
      println(rs)
      true must beEqualTo(rs.tables(0).columns(0).values.length == 4)
      val uuid = rpt.uuid.get.asInstanceOf[UUID]
      val uuidFromJson = rs.uuid.get.asInstanceOf[UUID]
      true must beEqualTo(uuid == uuidFromJson)
    }
  }
}
