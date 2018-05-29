import java.util.UUID
import java.nio.file.Files

import org.specs2.mutable.Specification
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import spray.json._
import akka.http.scaladsl.server._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.testkit.Specs2RouteTest
import org.joda.time.{DateTime => JodaDateTime}

import scala.concurrent._
import scala.concurrent.duration._
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{
  BundleTypes,
  TsSystemStatusManifest
}
import com.pacbio.secondary.smrtlink.auth.hmac.{DefaultSigner, SignerConfig}
import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtservereve.app.{EveFileUtils, SmrtEventServer}

/**
  * Created by mkocher on 2/19/17.
  */
class EventServerSpec
    extends Specification
    with Specs2RouteTest
    with LazyLogging
    with DefaultSigner
    with SignerConfig
    with EveFileUtils {

  // Run Tests sequentially
  sequential

  import SmrtLinkJsonProtocols._

  val smrtLinkSystemId = UUID.randomUUID()
  val exampleMessage = SmrtLinkSystemEvent(smrtLinkSystemId,
                                           EventTypes.TEST,
                                           1,
                                           UUID.randomUUID(),
                                           JodaDateTime.now(),
                                           JsObject.empty,
                                           None)

  lazy val totalRoutes = SmrtEventServer.allRoutes
  lazy val eventMessageDir = SmrtEventServer.eventMessageDir
  lazy val fileUploadOutputDir = SmrtEventServer.eventUploadFilesDir
  lazy val apiSecret = SmrtEventServer.apiSecret

  val exampleTsManifest = TsSystemStatusManifest(UUID.randomUUID(),
                                                 BundleTypes.TEST,
                                                 1,
                                                 JodaDateTime.now(),
                                                 UUID.randomUUID(),
                                                 None,
                                                 None,
                                                 "testuser",
                                                 Some("Test/Mock Message"))

  // This is blocking
  def setupApp() = {
    logger.info(s"Event output dir $eventMessageDir")
    logger.info(s"Upload file output dir $fileUploadOutputDir")
    SmrtEventServer.startSystem()
  }

  def cleanupApp() = {
    logger.info("Running cleanup. Deleting temp dirs")
    FileUtils.deleteQuietly(eventMessageDir.toFile)
    FileUtils.deleteQuietly(fileUploadOutputDir.toFile)
  }

  def toAuth(method: String, segment: String): String = {
    val key = generate(apiSecret, s"$method+$segment", timestamp)
    s"hmac uid:$key"
  }

  step(setupApp())

  "Event/Message Server tests" should {
    "tmp Resource directories for events and uploaded files are setup" in {
      Files.exists(eventMessageDir) must beTrue
      Files.exists(fileUploadOutputDir) must beTrue
    }
    "Successfully call status endpoint" in {
      Get("/status") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "Successfully call swagger endpoint" in {
      Get("/api/v1/swagger") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "Successfully Post Event to Server" in {
      Post("/api/v1/events", exampleMessage) ~> addHeader(
        "Authentication",
        toAuth("POST", "/api/v1/events")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "Check files were written to file system and Delete tmp dir" in {

      val smrtLinkSystemEventsDir =
        eventMessageDir.resolve(smrtLinkSystemId.toString)

      Files.exists(smrtLinkSystemEventsDir) must beTrue

      // This is a bit sloppy for testing
      val dateTimeDir = createDateTimeSubDir(smrtLinkSystemEventsDir)

      val messagePath = dateTimeDir.resolve(s"${exampleMessage.uuid}.json")

      Files.exists(messagePath) must beTrue
    }
  }
  step(cleanupApp())

}
