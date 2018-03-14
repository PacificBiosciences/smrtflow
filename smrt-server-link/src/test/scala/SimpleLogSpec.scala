import scala.concurrent.duration._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.{RouteTestTimeout, Specs2RouteTest}
import spray.json._
import org.specs2.mutable.Specification
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.models.{LogLevels, LogMessageRecord}
import com.pacbio.secondary.smrtlink.services.{
  ServiceComposer,
  SimpleLogServiceProvider
}

class SimpleLogSpec extends Specification with Specs2RouteTest {

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  implicit val routeTestTimeout = RouteTestTimeout(10.seconds)

  object TestProviders extends ServiceComposer with SimpleLogServiceProvider {}

  val routes = TestProviders.routes()

  val sourceId = "test"

  val logMessage =
    LogMessageRecord("test warning message", LogLevels.WARN, sourceId)

  "Simple log service" should {
    "accept a log message" in {
      Post("/smrt-link/loggers", logMessage) ~> routes ~> check {
        status === StatusCodes.Created
        val resp = responseAs[MessageResponse]
        success
      }
    }
  }
}
