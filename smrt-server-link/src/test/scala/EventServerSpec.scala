import java.util.UUID
import java.nio.file.Files

import org.specs2.mutable.Specification
import com.typesafe.scalalogging.LazyLogging
import mbilski.spray.hmac.{DefaultSigner, SignerConfig}
import org.apache.commons.io.FileUtils

import spray.json._
import spray.http._
import spray.client.pipelining._
import spray.testkit.Specs2RouteTest
import spray.httpx.SprayJsonSupport._

import org.joda.time.{DateTime => JodaDateTime}

import scala.concurrent._
import scala.concurrent.duration._
import collection.JavaConversions._
import collection.JavaConverters._

import com.pacbio.common.utils.TarGzUtils
import com.pacbio.secondary.analysis.jobs.JobModels.TsSystemStatusManifest
import com.pacbio.secondary.analysis.techsupport.TechSupportConstants
import com.pacbio.secondary.smrtlink.app._
import com.pacbio.secondary.smrtlink.models.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.models._

/**
  * Created by mkocher on 2/19/17.
  */
class EventServerSpec extends Specification with Specs2RouteTest with LazyLogging with DefaultSigner with SignerConfig{

  // Run Tests sequentially
  sequential

  import SmrtLinkJsonProtocols._

  val smrtLinkSystemId = UUID.randomUUID()
  val exampleMessage = SmrtLinkSystemEvent(smrtLinkSystemId, "test", 1, UUID.randomUUID(), JodaDateTime.now(), JsObject.empty, None)


  lazy val totalRoutes = SmrtEventServer.allRoutes
  lazy val eventMessageDir = SmrtEventServer.eventMessageDir
  lazy val fileUploadOutputDir = SmrtEventServer.eventUploadFilesDir
  lazy val apiSecret = SmrtEventServer.apiSecret

  val exampleTsManifest = TsSystemStatusManifest(UUID.randomUUID(), "test_bundle", 1, JodaDateTime.now(),
    UUID.randomUUID(), None, None, "testuser", Some("Test/Mock Message"))

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

  val sender = sendReceive

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
    "Successfully Post Event to Server" in {
      Post("/api/v1/events", exampleMessage) ~> addHeader("Authentication", toAuth("POST", "/api/v1/events")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
      }
    }
    "Check files were written to file system and Delete tmp dir" in {

      val smrtLinkSystemEventsDir = eventMessageDir.resolve(smrtLinkSystemId.toString)
      Files.exists(smrtLinkSystemEventsDir)

      val messagePath = smrtLinkSystemEventsDir.resolve(s"${exampleMessage.uuid}.json")
      Files.exists(messagePath) must beTrue
    }
    "Sanity test for file uploading Service" in {
      val f = Files.createTempDirectory("example-upload")

      val tsManifestPath = f.resolve(TechSupportConstants.DEFAULT_TS_MANIFEST_JSON)

      FileUtils.write(tsManifestPath.toFile, exampleTsManifest.toJson.prettyPrint)

      val tgzPath = f.resolve(TechSupportConstants.DEFAULT_TS_BUNDLE_TGZ).toAbsolutePath

      val numTimes = 1000
      val data:CharSequence = (0 until numTimes).map(i => s"Line $i").reduce(_ + "\n" + _)

      TarGzUtils.createTarGzip(f, tgzPath.toFile)
      logger.info(s"Created tmp file $tgzPath")

      val multiForm = MultipartFormData(
        Seq(BodyPart(tgzPath.toFile, "techsupport_tgz", ContentType(MediaTypes.`application/octet-stream`)))
      )

      Post("/api/v1/files", multiForm) ~> addHeader("Authentication", toAuth("POST", "/api/v1/files")) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        FileUtils.deleteQuietly(f.toFile)
      }
    }
  }
  step(cleanupApp())

}
