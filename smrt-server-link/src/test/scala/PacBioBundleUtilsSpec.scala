import java.nio.file.Paths

import com.pacbio.secondary.smrtlink.io.PacBioDataBundleIOUtils
import com.pacbio.secondary.smrtlink.models.PacBioDataBundle
import org.specs2.mutable.Specification
import org.joda.time.{DateTime => JodaDateTime}
import spray.json._

class PacBioBundleUtilsSpec extends Specification {

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  def loader(name: String) = {
    Paths.get(getClass.getResource(name).toURI)
  }

  "Bundle Utils tests" should {
    "Sort bundles by semVer" in {

      val createdBy: Option[String] = Some("user")
      val description: Option[String] = Some("A Description")
      val isActive = false

      def toBundle(v: String) =
        PacBioDataBundle("a",
                         v,
                         JodaDateTime.now(),
                         createdBy,
                         isActive = isActive,
                         description)

      val bundles =
        Seq("1.2.4", "1.0.0", "1.1.0", "1.0.1", "1.3.1").map(toBundle)

      val newestBundle =
        PacBioDataBundleIOUtils.getNewestBundleVersionByType(bundles, "a")

      newestBundle.map(_.version) must beSome("1.3.1")
    }
    "Convert manifest.xml to Bundle data model with a description" in {
      val b = PacBioDataBundleIOUtils.parseBundleManifestXml(
        loader("example-bundles/chemistry-0.1.2/manifest.xml").toFile)
      b.description must beSome
    }
    "Convert manifest.xml to Bundle data model without a description" in {
      val b = PacBioDataBundleIOUtils.parseBundleManifestXml(
        loader("example-bundles/chemistry-1.2.3/manifest.xml").toFile)
      b.description must beNone
    }
    "Load Legacy JSON Bundle without description" in {
      val sx =
        """{
                 "typeId": "chemistry",
                 "version": "5.0.0.6748",
                 "importedAt": "2017-08-14T22:59:02.271Z",
                 "isActive": true,
                 "createdBy": "User build created chemistry bundle 5.0.0+6748.be7e507"
                 }"""

      val b = sx.parseJson.convertTo[PacBioDataBundle]
      b.description must beNone
    }
    "Load JSON Bundle with description" in {
      val sx =
        """{
                 "typeId": "chemistry",
                 "version": "5.0.0.6748",
                 "importedAt": "2017-08-14T22:59:02.271Z",
                 "isActive": true,
                 "createdBy": "User build created chemistry bundle 5.0.0+6748.be7e507",
                 "description": "a Description"
                 }"""

      val b = sx.parseJson.convertTo[PacBioDataBundle]
      b.description must beSome
    }
  }

}
