import com.pacbio.secondary.smrtlink.app.{BaseApi, CoreProviders}
import com.pacbio.secondary.smrtlink.models.{ServiceStatus, PacBioJsonProtocol}
import org.specs2.mutable.Specification
import spray.json._
import spray.http._
import spray.routing._
import spray.client.pipelining._
import spray.httpx.SprayJsonSupport._
import spray.testkit.Specs2RouteTest

class SanityBaseServiceSpec extends Specification with Directives with Specs2RouteTest {

  import PacBioJsonProtocol._

  object Api extends BaseApi {
    override val providers: CoreProviders = new CoreProviders {}
  }

  val routes = Api.routes

  "Service list" should {
    "Uptime should be >0" in {
      Get("/status") ~> routes ~> check {
        val status = responseAs[ServiceStatus]
        // TODO(smcclellan): Use FakeClock to test this
        status.uptime must be_>=(0L)
      }
    }
  }

}
