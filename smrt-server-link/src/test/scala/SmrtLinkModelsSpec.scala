import java.nio.file.Paths

import scala.io.Source

import spray.httpx.SprayJsonSupport._
import spray.json._
import org.specs2.mutable.Specification

import com.pacbio.secondary.smrtlink.analysis.jobs.{
  JobModels,
  SecondaryJobProtocols
}
import com.pacbio.secondary.smrtlink.models._

class SmrtLinkModelsSpec extends Specification {

  import JobModels._
  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._
  import com.pacbio.common.models.CommonModelImplicits._

  sequential

  val entryPointSubread =
    BoundServiceEntryPoint("e_01", "PacBio.DataSet.SubreadSet", 1)

  "Test serialization of smrtlink models" should {
    "PbSmrtPipeServiceOptions" in {
      val opts = PbSmrtPipeServiceOptions("test_job",
                                          "pbsmrtpipe.pipelines.mock_dev01",
                                          Seq(entryPointSubread),
                                          Nil,
                                          Nil)
      val j = opts.toJson
      val o = j.convertTo[PbSmrtPipeServiceOptions]
      o.projectId must beEqualTo(JobConstants.GENERAL_PROJECT_ID)
      o.name must beEqualTo("test_job")
      val opts2 = PbSmrtPipeServiceOptions("test_job2",
                                           "pbsmrtpipe.pipelines.mock_dev01",
                                           Seq(entryPointSubread),
                                           Nil,
                                           Nil,
                                           3)
      val j2 = opts2.toJson
      val o2 = j2.convertTo[PbSmrtPipeServiceOptions]
      o2.projectId must beEqualTo(3)
      val path = Paths.get(
        getClass.getResource(s"job-requests/pbsmrtpipe_01.json").toURI)
      val o3 = Source
        .fromFile(path.toFile)
        .getLines
        .mkString
        .parseJson
        .convertTo[PbSmrtPipeServiceOptions]
      o3.projectId must beEqualTo(JobConstants.GENERAL_PROJECT_ID)
    }
  }
}
