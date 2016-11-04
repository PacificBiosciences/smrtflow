
import java.nio.file.Paths
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable._
import spray.json._
import scala.io.Source

import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobs.SecondaryJobJsonProtocol


class JsonCompatibilitySpec extends Specification with SecondaryJobJsonProtocol with LazyLogging {

  def getPath(name: String) = Paths.get(getClass.getResource(s"misc-json/$name").toURI)

  "Testing EngineJob unmarshalling" should {
    "Load 3.1.1 model from JSON" in {
      val path = getPath("engine_job_01.json")
      val job = Source.fromFile(path.toFile).getLines.mkString.parseJson.convertTo[EngineJob]
      job.smrtlinkVersion must beEqualTo(None)
      job.id must beEqualTo(3)
      job.isActive must beEqualTo(true)
      val s = job.toJson
      val job2 = s.convertTo[EngineJob]
      job2.isActive must beEqualTo(true)
      job2.createdBy must beNone
    }
    "Load 3.2.0 model from JSON" in {
      val path = getPath("engine_job_02.json")
      val job = Source.fromFile(path.toFile).getLines.mkString.parseJson.convertTo[EngineJob]
      job.smrtlinkVersion must beSome("3.2.0.187627")
      job.id must beEqualTo(3)
      job.isActive must beEqualTo(true)
    }
    "Load 3.3+ model from JSON" in {
      val path = getPath("engine_job_03.json")
      val job = Source.fromFile(path.toFile).getLines.mkString.parseJson.convertTo[EngineJob]
      job.smrtlinkVersion must beSome("3.3.0.187723")
      job.id must beEqualTo(3)
      job.createdBy must beSome("root")
      job.isActive must beEqualTo(false)
      val s = job.toJson
      val job2 = s.convertTo[EngineJob]
      job2.isActive must beEqualTo(false)
      job2.smrtlinkVersion must beSome("3.3.0.187723")
      job2.createdAt must beEqualTo(job.createdAt)
    }
  }

}
