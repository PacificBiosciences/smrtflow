import java.util.UUID

import akka.testkit.TestActorRef
import com.pacbio.common.actors.InMemoryUserDaoProvider
import com.pacbio.common.auth.{Role, _}
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.services.{PacBioServiceErrors, ServiceComposer}
import com.pacbio.common.time.FakeClockProvider
import com.pacbio.secondary.smrtlink.actors.{InMemorySampleDao, InMemorySampleDaoProvider, SampleServiceActor, SampleServiceActorProvider}
import com.pacbio.secondary.smrtlink.models.{Sample, SampleCreate, SampleUpdate, SmrtLinkJsonProtocols}
import com.pacbio.secondary.smrtlink.services.SampleService
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import spray.httpx.SprayJsonSupport._
import spray.http.OAuth2BearerToken
import spray.routing.Directives
import spray.testkit.Specs2RouteTest


class SampleSpec extends
    Specification with
    Directives with
    Specs2RouteTest with
    BaseRolesInit with
    LazyLogging with
    PacBioServiceErrors {

  // run sequentially because of shared InMemoryDAO state
  sequential

  // for implicit json converters
  import SmrtLinkJsonProtocols._

  //
  // Setup TestProvider to compose our actors with an InMemory DAO
  //

  val SAMPLE1_UUID = UUID.randomUUID()
  val SAMPLE2_UUID = UUID.randomUUID()
  val SAMPLE3_UUID = UUID.randomUUID()
  val READ_USER_LOGIN = "reader"
  val WRITE_USER_1_LOGIN = "root"
  val WRITE_USER_2_LOGIN = "writer2"
  val INVALID_JWT = "invalid.jwt"
  val FAKE_SAMPLE = "{Chemistry:S1, InputConcentration:23.1}"
  val READ_CREDENTIALS = OAuth2BearerToken(READ_USER_LOGIN)
  val WRITE_CREDENTIALS_1 = OAuth2BearerToken(WRITE_USER_1_LOGIN)

  val SAMPLE_PATH = "/smrt-link/samples"
  var SAMPLE_PATH_SLASH = SAMPLE_PATH + "/"

  object TestProviders extends
    ServiceComposer with
    SampleServiceActorProvider with
    InMemorySampleDaoProvider with
    InMemoryUserDaoProvider with
    AuthenticatorImplProvider with
    JwtUtilsProvider with
    FakeClockProvider {

    // Provide a fake JwtUtils that uses the login as the JWT, and validates every JWT except for invalidJwt.

    override final val jwtUtils: Singleton[JwtUtils] = Singleton(() => new JwtUtils {
      override def getJwt(user: ApiUser): String = user.login
      override def validate(jwt: String): Option[String] = if (jwt == INVALID_JWT) None else Some(jwt)
    })

    override final val defaultRoles = Set.empty[Role]
  }

  val actorRef = TestActorRef[SampleServiceActor](TestProviders.sampleServiceActor())
  val authenticator = TestProviders.authenticator()
  val routes = new SampleService(actorRef, authenticator).prefixedRoutes

  //
  // Create a fake DB with three pre-populated samples
  //

  trait daoSetup extends Scope {
    TestProviders.sampleDao().asInstanceOf[InMemorySampleDao].clear()
    TestProviders.sampleDao()
      .createSample(WRITE_USER_1_LOGIN, SampleCreate(FAKE_SAMPLE, SAMPLE1_UUID, "Sample One"))
    TestProviders.sampleDao()
      .createSample(WRITE_USER_2_LOGIN, SampleCreate(FAKE_SAMPLE, SAMPLE2_UUID, "Sample Two"))
    TestProviders.sampleDao()
      .createSample(WRITE_USER_2_LOGIN, SampleCreate(FAKE_SAMPLE, SAMPLE3_UUID, "Sample Three"))
  }

  "Sample Service" should {
    "return a list of saved samples" in new daoSetup {
      Get(SAMPLE_PATH) ~> addCredentials(READ_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue
        val samples = responseAs[Set[Sample]]
        samples.size === 3
        samples.map(_.createdBy) === Set(WRITE_USER_1_LOGIN, WRITE_USER_2_LOGIN, WRITE_USER_2_LOGIN)
      }
    }

    "return a specific sample" in new daoSetup {
      Get(SAMPLE_PATH_SLASH + SAMPLE1_UUID) ~> addCredentials(READ_CREDENTIALS) ~> routes ~> check {
        logger.info("request: GET " + SAMPLE_PATH_SLASH + SAMPLE1_UUID)
        status.isSuccess must beTrue
        val sample = responseAs[Sample]
        sample.name === "Sample One"
        sample.details === FAKE_SAMPLE
        sample.uniqueId === SAMPLE1_UUID
      }
    }
    "return a different sample" in new daoSetup {
      Get(SAMPLE_PATH_SLASH + SAMPLE2_UUID) ~> addCredentials(READ_CREDENTIALS) ~> routes ~> check {
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
      Post(SAMPLE_PATH_SLASH + SAMPLE3_UUID, update) ~> addCredentials(WRITE_CREDENTIALS_1) ~> routes ~> check {
        logger.info("request: POST " + SAMPLE_PATH_SLASH + SAMPLE3_UUID)
        val sample = responseAs[Sample]
        sample.name === "Sample Three"
        sample.details === newDetails
      }
      Get(SAMPLE_PATH_SLASH + SAMPLE3_UUID) ~> addCredentials(READ_CREDENTIALS) ~> routes ~> check {
        logger.info("request: GET " + SAMPLE_PATH_SLASH + SAMPLE3_UUID)
        status.isSuccess must beTrue
        val sample = responseAs[Sample]
        sample.name === "Sample Three"
        sample.details === newDetails
        sample.uniqueId === SAMPLE3_UUID
      }
    }

    "delete a sample" in new daoSetup {
      Delete(SAMPLE_PATH_SLASH + SAMPLE3_UUID) ~> addCredentials(WRITE_CREDENTIALS_1) ~> routes ~> check {
        logger.info("request: DELETE " + SAMPLE_PATH_SLASH + SAMPLE3_UUID)
        status.isSuccess must beTrue
      }
      Get(SAMPLE_PATH_SLASH + SAMPLE3_UUID) ~> addCredentials(WRITE_CREDENTIALS_1) ~> routes ~> check {
        status.isSuccess must beFalse
        status.intValue === 404
      }
      Get(SAMPLE_PATH) ~> addCredentials(READ_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue
        val samples = responseAs[Set[Sample]]
        samples.size === 2
      }
    }
  }
}
