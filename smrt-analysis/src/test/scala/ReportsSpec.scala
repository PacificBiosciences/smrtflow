
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
      val rs = s.convertTo[Report]
      val uuid = rpt.uuid.get.asInstanceOf[UUID]
      val uuidFromJson = rs.uuid.get.asInstanceOf[UUID]
      true must beEqualTo(uuid == uuidFromJson)
    }
  }
}
