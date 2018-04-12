import java.nio.file.Paths

import com.pacbio.secondary.smrtlink.models.JsonAble
import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable.Specification
import spray.json._

/**
  *
  * Created by mkocher on 9/19/15.
  */
class PipelineTemplateViewRulesSpec
    extends Specification
    with SmrtLinkJsonProtocols
    with LazyLogging {

  sequential

  val RESOURCE_DIR = "pipeline-template-view-rules"

  def getTestResource(name: String) =
    getClass.getResource(s"$RESOURCE_DIR/$name")

  "Test pipeline template view rule loading" should {
    "Load JSON File" in {
      val name = "pipeline-template-view-rules-01.json"
      val px = getTestResource(name)
      val xs = scala.io.Source.fromURI(px.toURI).mkString
      val jx = xs.parseJson
      val ptvr = jx.convertTo[JsonAble]
      1 mustEqual 1
    }
  }
}
