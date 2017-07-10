import scala.concurrent.duration._

import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.testkit.Specs2RouteTest

import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

import com.pacbio.secondary.analysis.engine.CommonMessages.MessageResponse
import com.pacbio.common.models.LogLevel
import com.pacbio.common.services.ServiceComposer
import com.pacbio.secondary.smrtlink.models.{ClientLogMessage, SmrtLinkJsonProtocols}
import com.pacbio.secondary.smrtlink.services.SimpleLogServiceProvider

class SimpleLogSpec extends Specification
  with NoTimeConversions
  with Specs2RouteTest {

  import SmrtLinkJsonProtocols._

  implicit val routeTestTimeout = RouteTestTimeout(10.seconds)

  object TestProviders extends
      ServiceComposer with
      SimpleLogServiceProvider {
  }

  val routes = TestProviders.routes()

  val logMessage = ClientLogMessage(LogLevel.WARN, "test warning", "test")

  "Simple log service" should {
    "accept a log message" in {
      Post(s"/smrt-link/loggers", logMessage) ~> routes ~> check {
        status === StatusCodes.Created
        val resp = responseAs[MessageResponse]
        success
      }
    }
  }
}
