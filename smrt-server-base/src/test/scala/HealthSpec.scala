import akka.testkit.TestActorRef
import com.pacbio.common.actors._
import com.pacbio.common.auth._
import com.pacbio.common.dependency.SetBindings
import com.pacbio.common.models._
import com.pacbio.common.services.HealthService
import com.pacbio.common.time.FakeClockProvider
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.time.NoTimeConversions
import spray.http.OAuth2BearerToken
import spray.httpx.SprayJsonSupport._
import spray.routing._
import spray.testkit.Specs2RouteTest

import scala.concurrent.Await
import scala.concurrent.duration._

// TODO(smcclellan): Refactor this into multiple specs, for the spray routing, the DAO, and the database interactions
class HealthSpec
  extends Specification
  with Directives
  with Specs2RouteTest
  with HttpService
  with NoTimeConversions
  with BaseRolesInit  {

  sequential

  import BaseRoles._
  import PacBioJsonProtocol._

  def actorRefFactory = system

  val typeId = "pacbio.my_component"
  val componentId1 = "pacbio.my_component.one"
  val componentId2 = "pacbio.my_component.two"

  val readUserLogin = "reader"
  val writeUserLogin = "writer"
  val adminUserLogin = "admin"

  val invalidJwt = "invalid.jwt"

  object TestProviders extends
      SetBindings with
      HealthServiceActorProvider with
      InMemoryHealthDaoProvider with
      InMemoryUserDaoProvider with
      AuthenticatorImplProvider with
      JwtUtilsProvider with
      FakeClockProvider {

    import com.pacbio.common.dependency.Singleton

    // Provide a fake JwtUtils that uses the login as the JWT, and validates every JWT except for invalidJwt.
    override final val jwtUtils: Singleton[JwtUtils] = Singleton(() => new JwtUtils {
      override def getJwt(user: ApiUser): String = user.login
      override def validate(jwt: String): Option[String] = if (jwt == invalidJwt) None else Some(jwt)
    })

    override final val defaultRoles: Set[Role] = Set.empty[Role]
  }

  val actorRef = TestActorRef[HealthServiceActor](TestProviders.healthServiceActor())
  val authenticator = TestProviders.authenticator()

  Await.ready(for {
    _ <- TestProviders.userDao().createUser(readUserLogin, UserRecord("pass"))
    _ <- TestProviders.userDao().createUser(writeUserLogin, UserRecord("pass"))
    _ <- TestProviders.userDao().addRole(writeUserLogin, HEALTH_AND_LOGS_WRITE)
    _ <- TestProviders.userDao().createUser(adminUserLogin, UserRecord("pass"))
    _ <- TestProviders.userDao().addRole(adminUserLogin, HEALTH_AND_LOGS_ADMIN)
  } yield (), 10.seconds)

  val routes = new HealthService(actorRef, authenticator).prefixedRoutes

  trait daoSetup extends Scope {
    TestProviders.healthDao().asInstanceOf[InMemoryHealthDao].clear()

    TestProviders.healthDao().createHealthGauge(HealthGaugeRecord(componentId1, "Component One"))
    TestProviders.healthDao().createHealthGauge(HealthGaugeRecord(componentId2, "Component Two"))

    TestProviders.healthDao().createHealthMessage(
      componentId1, HealthGaugeMessageRecord("This service has problems", HealthSeverity.CAUTION, "test source"))
    TestProviders.healthDao().createHealthMessage(
      componentId1, HealthGaugeMessageRecord("This service is in trouble", HealthSeverity.CRITICAL, "test source"))

    TestProviders.healthDao().createHealthMessage(
      componentId2, HealthGaugeMessageRecord("This service is ok", HealthSeverity.OK, "test source"))
  }

  "Health Service" should {

    "return a list of health gauges" in new daoSetup {
      val credentials = OAuth2BearerToken(readUserLogin)
      Get("/smrt-base/health/gauges") ~> addCredentials(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val gauges = responseAs[Set[HealthGauge]]
        gauges.map { g => g.id } === Set(componentId1, componentId2)

      }
    }

    "return a specific health gauge" in new daoSetup {
      val credentials = OAuth2BearerToken(readUserLogin)
      Get("/smrt-base/health/gauges/" + componentId1) ~> addCredentials(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        responseAs[HealthGauge].id === componentId1
      }
    }

    "create a new health gauge" in new daoSetup {
      val credentials = OAuth2BearerToken(adminUserLogin)
      val newComponentId = "pacbio.my_component.new"
      val record = HealthGaugeRecord(newComponentId, "New Component")
      Post("/smrt-base/health/gauges", record) ~> addCredentials(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
      }
      Get("/smrt-base/health/gauges/" + newComponentId) ~> addCredentials(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        responseAs[HealthGauge].id === newComponentId
      }
    }

    "return all health messages" in new daoSetup {
      val credentials = OAuth2BearerToken(readUserLogin)
      Get("/smrt-base/health/gauges/" + componentId1 + "/messages") ~> addCredentials(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val messages = responseAs[Set[HealthGaugeMessage]]
        messages.map{ m => m.severity } === Set(HealthSeverity.CAUTION, HealthSeverity.CRITICAL)
      }
    }

    "create a new health message" in new daoSetup {
      val credentials = OAuth2BearerToken(writeUserLogin)
      val record = HealthGaugeMessageRecord("This service has an alert", HealthSeverity.ALERT, "test source")
      var message: HealthGaugeMessage = null
      Post("/smrt-base/health/gauges/" + componentId2 + "/messages", record) ~> addCredentials(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        message = responseAs[HealthGaugeMessage]
      }
      Get("/smrt-base/health/gauges/" + componentId2 + "/messages") ~> addCredentials(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val messages = responseAs[Seq[HealthGaugeMessage]]
        messages.filter{ m => m.severity == HealthSeverity.ALERT } === Seq(message)
      }
    }

    "return the most severe gauges" in new daoSetup {
      val credentials = OAuth2BearerToken(readUserLogin)
      Get("/smrt-base/health") ~> addCredentials(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val gauges = responseAs[Seq[HealthGauge]]
        gauges.length === 1
        gauges.head.id === componentId1
        gauges.head.severity === HealthSeverity.CRITICAL
      }
    }

    "reject unauthorized users" in new daoSetup {
      Get("/smrt-base/health/gauges") ~> routes ~> check {
        handled must beFalse
        rejection must beAnInstanceOf[AuthenticationFailedRejection]
      }

      val invalid = OAuth2BearerToken(invalidJwt)
      Get("/smrt-base/health/gauges") ~> addCredentials(invalid) ~> routes ~> check {
        handled must beFalse
        rejection must beAnInstanceOf[AuthenticationFailedRejection]
      }

      val noWrite = OAuth2BearerToken(readUserLogin)
      val message = HealthGaugeMessageRecord("This service has an alert", HealthSeverity.ALERT, "test source")
      Post("/smrt-base/health/gauges/" + componentId2 + "/messages", message) ~> addCredentials(noWrite) ~> routes ~> check {
        handled must beFalse
        rejection === AuthorizationFailedRejection
      }

      val noAdmin = OAuth2BearerToken(writeUserLogin)
      val gauge = HealthGaugeRecord("pacbio.new_component", "New Component")
      Post("/smrt-base/health/gauges", gauge) ~> addCredentials(noAdmin) ~> routes ~> check {
        handled must beFalse
        rejection === AuthorizationFailedRejection
      }
    }
  }
}
