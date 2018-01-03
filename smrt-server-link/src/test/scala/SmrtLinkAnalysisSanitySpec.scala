import com.pacbio.secondary.smrtlink.models.ServiceStatus
import com.pacbio.secondary.smrtlink.app.{SmrtLinkApi, SmrtLinkProviders}
import org.specs2.mutable.Specification
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.Specs2RouteTest

/**
  * This should be used as a sanity test to make sure routes are found and the
  * routes are correctly configured.
  *
  */
class SmrtLinkAnalysisSanitySpec extends Specification with Specs2RouteTest {

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  object TestProviders extends SmrtLinkApi {
    val totalRoutes = providers.routes
  }

  lazy val totalRoutes: Route = TestProviders.totalRoutes

  "Service list" should {
    "Uptime should be >0" in {
      Get("/status") ~> totalRoutes ~> check {
        val status = responseAs[ServiceStatus]
        // Uptime is in sec, not millisec
        // this is the best we can do
        status.uptime must be_>=(0L)
      }
    }
    "return a list of services" in {
      Get("/services/manifests") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
  }
}
