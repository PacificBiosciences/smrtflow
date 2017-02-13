import com.pacbio.common.models.ServiceStatus
import com.pacbio.secondary.smrtlink.models.{PacBioBundle, SmrtLinkJsonProtocols}
import com.pacbio.secondary.smrtlink.app.{SmrtLinkApi, SmrtLinkProviders}
import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.httpx.SprayJsonSupport._


class PacBioBundleServiceSpec extends Specification with Specs2RouteTest {

  object Api extends SmrtLinkApi {
    override val providers = new SmrtLinkProviders {}
  }

  val routes = Api.routes

  import SmrtLinkJsonProtocols._

  "Bundle Service tests" should {
    "Uptime should be >0" in {
      Get("/status") ~> routes ~> check {
        val status = responseAs[ServiceStatus]
        // Uptime is in sec, not millisec
        // this is the best we can do
        status.uptime must be_>=(0L)
      }
    }
    "Bundle Sanity check" in {
      Get("/smrt-link/bundles") ~> routes ~> check {
        status.isSuccess must beTrue
      }
    }
    "Get bundle type id 'example' " in {
      Get("/smrt-link/bundles/example") ~> routes ~> check {
        val bundles = responseAs[Seq[PacBioBundle]]
        println(s"Example bundles $bundles")
        status.isSuccess must beTrue
      }
    }
    "Get lastest bundle type id 'example' " in {
      Get("/smrt-link/bundles/example/latest") ~> routes ~> check {
        status.isSuccess must beTrue
      }
    }
    "Get bundle type id 'example' by version id" in {
      Get("/smrt-link/bundles/example/4.0.0+188835") ~> routes ~> check {
        status.isSuccess must beTrue
      }
    }
  }
}
