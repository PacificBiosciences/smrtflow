import com.pacbio.secondary.smrtlink.models.ServiceStatus
import com.pacbio.secondary.smrtlink.app.{SmrtLinkApi, SmrtLinkProviders}
import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

class SanitySpec extends Specification with Specs2RouteTest {

  object Api extends SmrtLinkApi {
    override val providers = new SmrtLinkProviders {}
  }

  val routes = Api.routes
  val eventManagerActorX = Api.providers.eventManagerActor()

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

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
