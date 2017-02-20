import java.util.UUID

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.httpx.SprayJsonSupport._
import akka.actor.ActorRefFactory
import com.pacbio.secondary.smrtlink.app._
import com.pacbio.secondary.smrtlink.models.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.models._
import spray.json._
import org.joda.time.{DateTime => JodaDateTime}

/**
  * Created by mkocher on 2/19/17.
  */
class EventServerSpec extends Specification with Specs2RouteTest{

  // Run Tests sequentially
  sequential

  import SmrtLinkJsonProtocols._

  val smrtLinkSystemId = UUID.randomUUID()
  val exampleMessage = SmrtLinkSystemEvent(smrtLinkSystemId, "test", UUID.randomUUID(), JodaDateTime.now(), JsObject.empty)


  val totalRoutes = SmrtEventServer.allRoutes

  SmrtEventServer.startSystem()

  "Event/Message Server tests" should {
    "Successfully call status endpoint" in {
      Get("/status") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "Successfully Post Event to Server" in {
      Post("/api/v1/events", exampleMessage) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
  }

}
