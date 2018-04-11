import java.nio.file.Paths

import scala.io.Source

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json._
import org.specs2.mutable.Specification

import com.pacbio.secondary.smrtlink.analysis.jobs.{
  JobModels,
  SecondaryJobProtocols
}
import com.pacbio.secondary.smrtlink.jobtypes._
import com.pacbio.secondary.smrtlink.models._

class SmrtLinkModelsSpec extends Specification {

  import JobModels._
  import com.pacbio.secondary.smrtlink.jsonprotocols.ServiceJobTypeJsonProtocols._
  import com.pacbio.common.models.CommonModelImplicits._

  sequential

  val entryPointSubread =
    BoundServiceEntryPoint("e_01", "PacBio.DataSet.SubreadSet", 1)

  "Test serialization of smrtlink models" should {
    "HelloWorldJobOptions" in {
      val opts = HelloWorldJobOptions(4, Some("my job"), None, None)
      val opts2 = opts.toJson.convertTo[HelloWorldJobOptions]
      opts2.x === opts.x
      opts2.name must beSome
    }
    "PbsmrtpipeJobOptions" in {
      val opts = PbsmrtpipeJobOptions(Some("test_job"),
                                      None,
                                      "pbsmrtpipe.pipelines.mock_dev01",
                                      Seq(entryPointSubread),
                                      Nil,
                                      Nil)
      val j = opts.toJson
      val o = j.convertTo[PbsmrtpipeJobOptions]
      o.projectId must beEqualTo(Some(JobConstants.GENERAL_PROJECT_ID))
      o.name must beEqualTo(Some("test_job"))
      val opts2 = PbsmrtpipeJobOptions(Some("test_job2"),
                                       Some("description"),
                                       "pbsmrtpipe.pipelines.mock_dev01",
                                       Seq(entryPointSubread),
                                       Nil,
                                       Nil,
                                       Some(3))
      val j2 = opts2.toJson
      val o2 = j2.convertTo[PbsmrtpipeJobOptions]
      o2.projectId must beEqualTo(Some(3))
    }
  }
}
