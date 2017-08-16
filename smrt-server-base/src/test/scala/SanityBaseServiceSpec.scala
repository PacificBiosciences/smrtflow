import com.pacbio.common.actors.InMemoryLogDaoProvider
import com.pacbio.common.app.BaseApi
import com.pacbio.common.models.ServiceStatus
import com.pacbio.common.services.PacBioServiceErrors
import com.pacbio.secondary.smrtlink.app.{BaseApi, CoreProviders}
import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.routing._
import spray.httpx.SprayJsonSupport._

class SanityBaseServiceSpec extends Specification with Directives with Specs2RouteTest {
  import PacBioJsonProtocol._

  object Api extends BaseApi {
    override val providers: CoreProviders = new CoreProviders with InMemoryLogDaoProvider {}
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
