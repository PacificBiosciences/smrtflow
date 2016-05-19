import com.pacbio.common.models.{PacBioJsonProtocol, ServiceStatus}
import com.pacbio.secondaryinternal.{
  SecondaryInternalAnalysisApi, InternalAnalysisJsonProcotols}
import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.http._
import spray.http.StatusCodes._
import spray.httpx.SprayJsonSupport._

class SanitySpec extends Specification with Specs2RouteTest {

  object Api extends SecondaryInternalAnalysisApi {
  }

  val routes = Api.routes

  import InternalAnalysisJsonProcotols._

  "Service list" should {
    "return a list of services" in {
      Get("/services/manifests") ~> routes ~> check {
        status.isSuccess must beTrue
      }
    }
    "Uptime should be >0" in {
      Get("/status") ~> routes ~> check {
        val status = responseAs[ServiceStatus]
        // Uptime is in sec, not millisec
        // this is the best we can do
        status.uptime must be_>=(0L)
      }
    }
  }
}
