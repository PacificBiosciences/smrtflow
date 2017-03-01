import java.util.UUID
import java.io.File
import java.nio.file.{Files, Paths}

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.httpx.SprayJsonSupport._
import akka.actor.ActorRefFactory
import com.pacbio.secondary.smrtlink.app._
import com.pacbio.secondary.smrtlink.models.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.models._
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import spray.json._
import spray.http._
import org.joda.time.{DateTime => JodaDateTime}

import scala.concurrent._
import scala.concurrent.duration._

import collection.JavaConversions._
import collection.JavaConverters._

/**
  * Created by mkocher on 2/19/17.
  */
class EventServerSpec extends Specification with Specs2RouteTest with LazyLogging{

  // Run Tests sequentially
  sequential

  import SmrtLinkJsonProtocols._

  val smrtLinkSystemId = UUID.randomUUID()
  val exampleMessage = SmrtLinkSystemEvent(smrtLinkSystemId, "test", 1, UUID.randomUUID(), JodaDateTime.now(), JsObject.empty)


  lazy val totalRoutes = SmrtEventServer.allRoutes
  lazy val eventMessageDir = SmrtEventServer.eventMessageDir
  lazy val fileUploadOutputDir = SmrtEventServer.eventUploadFilesDir

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
      Post("/api/v1/events", exampleMessage) ~> totalRoutes ~> check {
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
      val tgzPath = f.resolve("example.tgz").toAbsolutePath
      val numTimes = 1000
      val data:CharSequence = (0 until numTimes).map(i => s"Line $i").reduce(_ + "\n" + _)
      FileUtils.write(tgzPath.toFile, data, "UTF-8")
      logger.info(s"Created tmp file $tgzPath")

      val multiForm = MultipartFormData(
        Seq(BodyPart(tgzPath.toFile, "techsupport_tgz", ContentType(MediaTypes.`application/octet-stream`)))
      )

      Post("/api/v1/files", multiForm) ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        FileUtils.deleteQuietly(f.toFile)
      }
    }
  }
  step(cleanupApp())

}
