
// FIXME we want to drop this as quickly as possible

import java.nio.file.Paths
import java.util.UUID

import com.pacbio.secondary.analysis.jobs._
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._
import spray.json._
import scala.io.Source


/* Test for EngineJob model serialization with backwards compatibility */
class EngineJobSpec extends Specification with EngineJobJsonProtocol with LazyLogging {
  import JobModels._

  final val VERSIONED = "misc-models/engine_job_versioned.json"
  final val UNVERSIONED = "misc-models/engine_job_unversioned.json"

  sequential

  "Testing EngineJob serialization" should {
    "Read in complete JSON matching current model" in {
      val path = Paths.get(getClass.getResource(VERSIONED).toURI)
      val json = Source.fromFile(path.toFile).getLines.mkString.parseJson
      val job = json.convertTo[EngineJob]
      job.state must beEqualTo(AnalysisJobStates.SUCCESSFUL)
      job.smrtlinkVersion must beEqualTo(Some("3.2.0"))
    }
    "Read in JSON generated from older model" in {
      val path = Paths.get(getClass.getResource(UNVERSIONED).toURI)
      val json = Source.fromFile(path.toFile).getLines.mkString.parseJson
      val job = json.convertTo[EngineJob]
      job.state must beEqualTo(AnalysisJobStates.SUCCESSFUL)
      job.smrtlinkVersion must beEqualTo(None)
    }
  }
}
