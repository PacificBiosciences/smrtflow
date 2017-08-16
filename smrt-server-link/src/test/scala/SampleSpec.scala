import java.util.UUID

import akka.testkit.TestActorRef
import com.pacbio.secondary.smrtlink.auth._
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.common.models._
import com.pacbio.common.services.PacBioServiceErrors
import com.pacbio.secondary.smrtlink.time.FakeClockProvider
import com.pacbio.secondary.smrtlink.actors.{InMemorySampleDao, InMemorySampleDaoProvider, SampleServiceActor, SampleServiceActorProvider}
import com.pacbio.secondary.smrtlink.models.{Sample, SampleCreate, SampleUpdate, SmrtLinkJsonProtocols}
import com.pacbio.secondary.smrtlink.services.{SampleService, ServiceComposer}
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.time.NoTimeConversions
import spray.http.HttpHeaders.RawHeader
import spray.httpx.SprayJsonSupport._
import spray.routing.Directives
import spray.testkit.Specs2RouteTest

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class SampleSpec extends
    Specification with
    Directives with
    Specs2RouteTest with
    LazyLogging with
    NoTimeConversions with
    PacBioServiceErrors {

  // run sequentially because of shared InMemoryDAO state
  sequential

  // for implicit json converters
  import SmrtLinkJsonProtocols._
  import Authenticator._

  //
  // Setup TestProvider to compose our actors with an InMemory DAO
  //

  val SAMPLE1_UUID = UUID.randomUUID()
  val SAMPLE2_UUID = UUID.randomUUID()
  val SAMPLE3_UUID = UUID.randomUUID()
  val READ_USER_LOGIN = "reader"
  val ADMIN_USER_1_LOGIN = "admin1"
  val ADMIN_USER_2_LOGIN = "admin2"
  val INVALID_JWT = "invalid.jwt"
  val FAKE_SAMPLE = "{Chemistry:S1, InputConcentration:23.1}"
  val READ_CREDENTIALS = RawHeader(JWT_HEADER, READ_USER_LOGIN)
  val ADMIN_CREDENTIALS_1 = RawHeader(JWT_HEADER, ADMIN_USER_1_LOGIN)

  val SAMPLE_PATH = "/smrt-link/samples"
  var SAMPLE_PATH_SLASH = SAMPLE_PATH + "/"

  object TestProviders extends
    ServiceComposer with
    SampleServiceActorProvider with
    InMemorySampleDaoProvider with
    AuthenticatorImplProvider with
    JwtUtilsProvider with
    FakeClockProvider {

    // Provide a fake JwtUtils that uses the login as the JWT, and validates every JWT except for invalidJwt.

    override final val jwtUtils: Singleton[JwtUtils] = Singleton(() => new JwtUtils {
      override def parse(jwt: String): Option[UserRecord] = if (jwt == INVALID_JWT) None else Some(UserRecord(jwt))
    })
  }

  val actorRef = TestActorRef[SampleServiceActor](TestProviders.sampleServiceActor())
  val authenticator = TestProviders.authenticator()
  val routes = new SampleService(actorRef, authenticator).prefixedRoutes

  //
  // Create a fake DB with three pre-populated samples
  //

  trait daoSetup extends Scope {
    TestProviders.sampleDao().asInstanceOf[InMemorySampleDao].clear()

    val sampleDao = TestProviders.sampleDao()

    val fx = for {
      _ <- sampleDao.createSample(ADMIN_USER_1_LOGIN, SampleCreate(FAKE_SAMPLE, SAMPLE1_UUID, "Sample One"))
      _ <- sampleDao.createSample(ADMIN_USER_2_LOGIN, SampleCreate(FAKE_SAMPLE, SAMPLE2_UUID, "Sample Two"))
      _ <- sampleDao.createSample(ADMIN_USER_2_LOGIN, SampleCreate(FAKE_SAMPLE, SAMPLE3_UUID, "Sample Three"))
    } yield "Completed inserting Test Samples"

    Await.result(fx, 10.seconds)
  }

  "Sample Service" should {
    "return a list of saved samples" in new daoSetup {
      Get(SAMPLE_PATH) ~> addHeader(READ_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue
        val samples = responseAs[Set[Sample]]
        samples.size === 3
        samples.map(_.createdBy) === Set(ADMIN_USER_1_LOGIN, ADMIN_USER_2_LOGIN, ADMIN_USER_2_LOGIN)
      }
    }

    "return a specific sample" in new daoSetup {
      Get(SAMPLE_PATH_SLASH + SAMPLE1_UUID) ~> addHeader(READ_CREDENTIALS) ~> routes ~> check {
        logger.info("request: GET " + SAMPLE_PATH_SLASH + SAMPLE1_UUID)
        status.isSuccess must beTrue
        val sample = responseAs[Sample]
        sample.name === "Sample One"
        sample.details === FAKE_SAMPLE
        sample.uniqueId === SAMPLE1_UUID
      }
    }
    "return a different sample" in new daoSetup {
      Get(SAMPLE_PATH_SLASH + SAMPLE2_UUID) ~> addHeader(READ_CREDENTIALS) ~> routes ~> check {
        logger.info("request: GET " + SAMPLE_PATH_SLASH + SAMPLE2_UUID)
        status.isSuccess must beTrue
        val sample = responseAs[Sample]
        sample.name === "Sample Two"
        sample.details === FAKE_SAMPLE
        sample.uniqueId === SAMPLE2_UUID
      }
    }

    "update a sample" in new daoSetup {
      val newDetails = "{Chemistry:S2, InputConcentration:21.1}"
      val update = SampleUpdate(details = Some(newDetails), name = None)
      Post(SAMPLE_PATH_SLASH + SAMPLE3_UUID, update) ~> addHeader(ADMIN_CREDENTIALS_1) ~> routes ~> check {
        logger.info("request: POST " + SAMPLE_PATH_SLASH + SAMPLE3_UUID)
        val sample = responseAs[Sample]
        sample.name === "Sample Three"
        sample.details === newDetails
      }
      Get(SAMPLE_PATH_SLASH + SAMPLE3_UUID) ~> addHeader(READ_CREDENTIALS) ~> routes ~> check {
        logger.info("request: GET " + SAMPLE_PATH_SLASH + SAMPLE3_UUID)
        status.isSuccess must beTrue
        val sample = responseAs[Sample]
        sample.name === "Sample Three"
        sample.details === newDetails
        sample.uniqueId === SAMPLE3_UUID
      }
    }

    "delete a sample" in new daoSetup {
      Delete(SAMPLE_PATH_SLASH + SAMPLE3_UUID) ~> addHeader(ADMIN_CREDENTIALS_1) ~> routes ~> check {
        logger.info("request: DELETE " + SAMPLE_PATH_SLASH + SAMPLE3_UUID)
        status.isSuccess must beTrue
      }
      Get(SAMPLE_PATH_SLASH + SAMPLE3_UUID) ~> addHeader(ADMIN_CREDENTIALS_1) ~> routes ~> check {
        status.isSuccess must beFalse
        status.intValue === 404
      }
      Get(SAMPLE_PATH) ~> addHeader(READ_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue
        val samples = responseAs[Set[Sample]]
        samples.size === 2
      }
    }

    "create a sample" in new daoSetup {
      val newSample = SampleCreate(FAKE_SAMPLE, UUID.randomUUID(), "Created Sample")
      Post(SAMPLE_PATH, newSample) ~> addHeader(ADMIN_CREDENTIALS_1) ~> routes ~> check {
        val sample = responseAs[Sample]
        sample.name === "Created Sample"
        sample.createdBy === ADMIN_USER_1_LOGIN
      }
    }
  }
}
