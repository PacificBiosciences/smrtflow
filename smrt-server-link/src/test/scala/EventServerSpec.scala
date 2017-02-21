import java.util.UUID
import java.nio.file.Files

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.httpx.SprayJsonSupport._
import akka.actor.ActorRefFactory
import com.pacbio.secondary.smrtlink.app._
import com.pacbio.secondary.smrtlink.models.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.models._
import org.apache.commons.io.FileUtils
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
  val eventMessageDir = SmrtEventServer.eventMessageDir

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
    "Check files we're written to file system and Delete tmp dir" in {
      // Check that dir was created
      Files.exists(eventMessageDir) must beTrue

      val smrtLinkSystemEventsDir = eventMessageDir.resolve(smrtLinkSystemId.toString)
      Files.exists(smrtLinkSystemEventsDir)

      val messagePath = smrtLinkSystemEventsDir.resolve(s"${exampleMessage.uuid}.json")
      Files.exists(messagePath) must beTrue

      FileUtils.deleteQuietly(eventMessageDir.toFile)
      Files.exists(eventMessageDir) must beFalse
    }
  }

}
