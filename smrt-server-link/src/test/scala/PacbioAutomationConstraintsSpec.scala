import java.nio.file.{Path, Paths}

import collection.JavaConverters._
import collection.JavaConversions._

import com.pacbio.secondary.smrtlink.loaders.PacBioAutomationConstraintsLoader

import org.specs2.mutable.Specification
import spray.json._

/**
  * Created by mkocher on 2/6/17.
  */
class PacbioAutomationConstraintsSpec extends Specification{

  sequential

  def getTestResource(name: String): Path =
    Paths.get(getClass.getResource(s"$name").toURI)

  def getExamplePath() = getTestResource("PacBioAutomationConstraints.xml")
  def getExample() =  PacBioAutomationConstraintsLoader.loadFrom(getExamplePath())

  "Sanity test for Loading PacBioAutomationConstraints XML" should {
    "Load an example file Successfully" in {
      val p = getExample()
      Option(p.getVersion) must beSome("3.0.0009")
    }
    "Test conversion to JSON" in {
      val p = getExample()
      val js = PacBioAutomationConstraintsLoader.pacBioAutomationToJson(p)
      //println(js.prettyPrint)
      val vx = js.asJsObject().fields("Version") match {
        case JsString(version) => Some(version)
        case _ => None
      }
      vx must beSome("3.0.0009")
      val constraints = p.getAutomationConstraints.getAutomationConstraint
      constraints.length === 5
    }
    "Load Demo/Example Chemistry Bundle" in {
      val p = PacBioAutomationConstraintsLoader.loadExample()
      Option(p.getVersion) must beSome("3.0.0009")
    }
  }
}
