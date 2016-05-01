import com.pacbio.common.models.{HealthGaugeMessage, PacBioComponentManifest, HealthSeverity, PacBioComponent}
import spray.json._
import org.specs2.mutable._
import com.pacbio.common.models.PacBioJsonProtocol
import org.joda.time.{DateTime => JodaDateTime}

class PacbioJsonProtocolSpec extends Specification {

  import PacBioJsonProtocol._

  "Serialize Alert Spec" should {
    "Alert serialize to Json " in {
      val u = java.util.UUID.randomUUID()
      val n = JodaDateTime.now()
      val m = HealthGaugeMessage( n, u, "message", HealthSeverity.ALERT, "source")
      m.message must beEqualTo("message")
      val x = m.toJson
      println(x)
      m.uuid must beEqualTo(u)
      m.severity must beEqualTo(HealthSeverity.ALERT)
    }
    "Manifest serialization" in {
      val m = PacBioComponentManifest("myid", "myname", "0.1.1", "description", None)
      m.id must beEqualTo("myid")
      val x = m.toJson
      println(x)
      m.version must beEqualTo("0.1.1")
    }
    "Manifest serialization with Components" in {
      val components = Seq(PacBioComponent("pacbio.tools.blasr", "0.2.1"), PacBioComponent("pacbio.tools.pbfilter", "0.2.1"))
      val m = PacBioComponentManifest("myid", "myname", "0.1.1", "description", Option(components))
      m.id must beEqualTo("myid")
      val x = m.toJson
      println(x)
      m.version must beEqualTo("0.1.1")
    }
  }
}
