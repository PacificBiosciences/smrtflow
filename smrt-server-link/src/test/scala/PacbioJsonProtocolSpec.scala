import java.nio.file.Paths

import com.pacbio.secondary.smrtlink.loaders.ManifestLoader
import com.pacbio.secondary.smrtlink.models.{Alarm,PacBioComponentManifest}
import org.joda.time.{DateTime => JodaDateTime}
import org.specs2.mutable._
import spray.json._

class PacbioJsonProtocolSpec extends Specification {

  "Serialize Alert Spec" should {
    "Alert serialize to Json " in {
      val m = Alarm("id", "name", "desc")
      m.name must beEqualTo("name")
      val x = m.toJson
      m.id must beEqualTo("id")
    }
    "Manifest serialization" in {
      val m = PacBioComponentManifest("myid", "myname", "0.1.1", "description")
      m.id must beEqualTo("myid")
      val x = m.toJson
      m.version must beEqualTo("0.1.1")
    }
    "Manifest serialization with Components" in {
      val m = PacBioComponentManifest("myid", "myname", "0.1.1", "description")
      m.id must beEqualTo("myid")
      val x = m.toJson
      m.version must beEqualTo("0.1.1")
    }
    "Load Example PacBio Manifest.json " in {

      val p = Paths.get(getClass.getResource("pacbio-manifest.json").toURI)

      val manifests = ManifestLoader.loadFrom(p.toFile)

      manifests.length should beEqualTo(2)
    }
  }
}
