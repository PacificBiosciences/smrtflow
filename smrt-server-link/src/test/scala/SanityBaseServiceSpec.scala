import com.pacbio.secondary.smrtlink.app.{BaseApi, CoreProviders}
import com.pacbio.secondary.smrtlink.models.ServiceStatus
import org.specs2.mutable.Specification
import spray.json._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server._

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.testkit.Specs2RouteTest

class SanityBaseServiceSpec
    extends Specification
    with Directives
    with Specs2RouteTest {

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

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
