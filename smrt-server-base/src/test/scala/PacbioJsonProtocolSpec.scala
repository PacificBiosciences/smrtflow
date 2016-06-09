import com.pacbio.common.models._
import spray.json._
import org.specs2.mutable._

class PacbioJsonProtocolSpec extends Specification {

  import PacBioJsonProtocol._

  "Serialize Alert Spec" should {
    "Alert serialize to Json " in {
      val m = HealthMetricCreateMessage(
        "metric_id",
        "Metric",
        "Test Metric",
        TagCriteria(),
        MetricType.SUM,
        Map(
          HealthSeverity.CAUTION -> 1.0,
          HealthSeverity.ALERT -> 2.0,
          HealthSeverity.CRITICAL -> 3.0
        ),
        Some(60))
      m.name must beEqualTo("Metric")
      val x = m.toJson
      println(x)
      m.id must beEqualTo("metric_id")
      m.metricType must beEqualTo(MetricType.SUM)
    }
    "Manifest serialization" in {
      val m = PacBioComponentManifest("myid", "myname", "0.1.1", "description")
      m.id must beEqualTo("myid")
      val x = m.toJson
      println(x)
      m.version must beEqualTo("0.1.1")
    }
    "Manifest serialization with Components" in {
      val components = Seq(PacBioComponent("pacbio.tools.blasr", "0.2.1"), PacBioComponent("pacbio.tools.pbfilter", "0.2.1"))
      val m = PacBioComponentManifest("myid", "myname", "0.1.1", "description", components)
      m.id must beEqualTo("myid")
      val x = m.toJson
      println(x)
      m.version must beEqualTo("0.1.1")
    }
  }
}
