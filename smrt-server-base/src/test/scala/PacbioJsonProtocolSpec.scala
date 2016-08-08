import java.nio.file.Paths

import com.pacbio.common.loaders.ManifestLoader
import com.pacbio.common.models._
import spray.json._
import org.specs2.mutable._
import org.joda.time.{DateTime => JodaDateTime}

class PacbioJsonProtocolSpec extends Specification {

  import PacBioJsonProtocol._

  "Serialize Alert Spec" should {
    "Alert serialize to Json " in {
      val n = JodaDateTime.now()
      val m = HealthMetric(
        "id",
        "name",
        "desc",
        TagCriteria(hasAny = Set("tag")),
        MetricType.SUM,
        Map(HealthSeverity.CAUTION -> 1.0),
        Some(60),
        HealthSeverity.OK,
        0.5,
        n,
        None)
      m.name must beEqualTo("name")
      val x = m.toJson
      println(x)
      m.severity must beEqualTo(HealthSeverity.OK)
    }
    "Manifest serialization" in {
      val m = PacBioComponentManifest("myid", "myname", "0.1.1", "description")
      m.id must beEqualTo("myid")
      val x = m.toJson
      println(x)
      m.version must beEqualTo("0.1.1")
    }
    "Manifest serialization with Components" in {
      val m = PacBioComponentManifest("myid", "myname", "0.1.1", "description")
      m.id must beEqualTo("myid")
      val x = m.toJson
      println(x)
      m.version must beEqualTo("0.1.1")
    }
    "Load Example PacBio Manifest.json " in {

      val p = Paths.get(getClass.getResource("pacbio-manifest.json").toURI)

      val manifests = ManifestLoader.loadFrom(p.toFile)

      manifests.length should beEqualTo(2)
    }
  }
}
