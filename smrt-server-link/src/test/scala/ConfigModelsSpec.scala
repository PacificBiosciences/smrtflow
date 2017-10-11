import java.nio.file.{Path, Paths}

import com.pacbio.secondary.smrtlink.jsonprotocols.ConfigModelsJsonProtocol
import org.apache.commons.io.FileUtils
import org.specs2.mutable.Specification
import spray.json._
import com.pacbio.secondary.smrtlink.models.ConfigModels._

/**
  * Created by mkocher on 1/4/17.
  */
class ConfigModelsSpec extends Specification {

  import ConfigModelsJsonProtocol._

  sequential

  val RESOURCE_DIR = "smrtlink-system-configs"

  def getTestResource(name: String): Path =
    Paths.get(getClass.getResource(s"$RESOURCE_DIR/$name").toURI)

  "Sanity serialization of SL System config 2.0" should {
    "Load test file successfully" in {
      val name = "smrtlink-system-config.json"
      val p = getTestResource(name)
      val sx = FileUtils.readFileToString(p.toFile, "UTF-8")
      val jx = sx.parseJson
      val config = jx.convertTo[RootSmrtflowConfig]
      config.comment must beSome
      config.smrtflow.server.port === 8077
      config.pacBioSystem.enableCellReuse must beSome(true)
    }

    "Load credentials file successfully" in {
      val name = "wso2-credentials.json"
      val p = getTestResource(name)
      val sx = FileUtils.readFileToString(p.toFile, "UTF-8")
      val jx = sx.parseJson
      val creds = jx.convertTo[Wso2Credentials]
      creds.wso2User === "jsnow"
      creds.wso2Password === "r+l=j"
    }
  }
}
