import java.io.File

import com.pacbio.common.actors.{InMemoryUserDaoProvider, UserDao}
import com.pacbio.common.auth._
import com.pacbio.common.database._
import com.pacbio.common.dependency.{InitializationComposer, SetBindings, Singleton}
import com.pacbio.common.models._
import com.pacbio.common.services.LogServiceProvider
import com.pacbio.common.time._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.time.NoTimeConversions
import spray.http.OAuth2BearerToken
import spray.httpx.SprayJsonSupport._
import spray.routing.{AuthorizationFailedRejection, AuthenticationFailedRejection, Directives, HttpService}
import spray.testkit.Specs2RouteTest

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

// TODO(smcclellan): Refactor this into multiple specs, for the spray routing, the DAO, and the database interactions
class LogSpec extends Specification with NoTimeConversions with Directives with Specs2RouteTest with HttpService with BaseRolesInit {
  sequential

  import PacBioJsonProtocol._
  import BaseRoles._

  def actorRefFactory = system

  implicit val routeTestTimeout = RouteTestTimeout(30.seconds)

  val typeId = "pacbio.my_component"
  val componentId1 = "pacbio.my_component.one"
  val componentId2 = "pacbio.my_component.two"

  val readUserLogin = "reader"
  val writeUserLogin = "writer"
  val adminUserLogin = "admin"

  val invalidJwt = "invalid.jwt"

  object TestProviders extends
      SetBindings with
      InitializationComposer with
      DatabaseProvider with
      LogServiceProvider with
      DatabaseLogDaoProvider with
      InMemoryUserDaoProvider with
      AuthenticatorImplProvider with
      JwtUtilsProvider with
      FakeClockProvider {

    // Provide a fake JwtUtils that uses the login as the JWT, and validates every JWT except for invalidJwt.
    override final val jwtUtils: Singleton[JwtUtils] = Singleton(() => new JwtUtils {
      override def getJwt(user: ApiUser): String = user.login
      override def validate(jwt: String): Option[String] = if (jwt == invalidJwt) None else Some(jwt)
    })

    override final val defaultRoles = Set.empty[Role]

    // Database config that uses a temporary database file
    val dbFile = File.createTempFile("log_spec_", ".db")
    dbFile.deleteOnExit()
    override val dbURI: Singleton[String] = Singleton(s"jdbc:sqlite:file:${dbFile.getCanonicalPath}?cache=shared")
  }

  val authenticator = TestProviders.authenticator()
  val userDao: UserDao = TestProviders.userDao()
  val logDao: DatabaseLogDao = TestProviders.logDao().asInstanceOf[DatabaseLogDao]

  Await.ready(
    userDao.createUser(readUserLogin, UserRecord("pass")) flatMap { _ =>
    userDao.createUser(writeUserLogin, UserRecord("pass"))} flatMap { _ =>
    userDao.addRole(writeUserLogin, HEALTH_AND_LOGS_WRITE)} flatMap { _ =>
    userDao.createUser(adminUserLogin, UserRecord("pass"))} flatMap { _ =>
    userDao.addRole(adminUserLogin, HEALTH_AND_LOGS_ADMIN)}, 10.seconds)

  val routes = TestProviders.logService().prefixedRoutes

  trait daoSetup extends Scope {
    val setup = for {
      i <- TestProviders.init()
      d <- logDao.deleteAll()
      p1 <- logDao.createLogResource(LogResourceRecord("Logger for Component 1", componentId1, "Component 1"))
      p2 <- logDao.createLogResource(LogResourceRecord("Logger for Component 2", componentId2, "Component 2"))
      p3 <- logDao.createLogMessage(
        componentId1, LogMessageRecord("This component has some info.", LogLevel.INFO, "test source"))
      p4 <- logDao.createLogMessage(
        componentId1, LogMessageRecord("This component has an error.", LogLevel.ERROR, "test source"))
      p5 <- logDao.createLogMessage(
        componentId2, LogMessageRecord("This component has some debug info.", LogLevel.DEBUG, "test source"))
    } yield ()

    Await.ready(setup, 10.seconds)
  }

  "Log Service" should {
    "return a list of log resources" in new daoSetup {
      val credentials = OAuth2BearerToken(readUserLogin)
      Get("/smrt-base/loggers") ~> addCredentials(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val resources = responseAs[Set[LogResource]]
        resources.size === 2
        resources.map { r => r.id } === Set(componentId1, componentId2)
      }
    }

    "return a specific log resource" in new daoSetup {
      val credentials = OAuth2BearerToken(readUserLogin)
      Get("/smrt-base/loggers/" + componentId1) ~> addCredentials(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        responseAs[LogResource].id === componentId1
      }
    }

    "create a new log resource" in new daoSetup {
      val credentials = OAuth2BearerToken(adminUserLogin)
      val newComponentId = "pacbio.my_component.new"
      val record = LogResourceRecord("Logger for New Component", newComponentId, "New Component")
      Post("/smrt-base/loggers", record) ~> addCredentials(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
      }
      Get("/smrt-base/loggers/" + newComponentId) ~> addCredentials(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        responseAs[LogResource].id === newComponentId
      }
    }

    "return recent messages from a log resource" in new daoSetup {
      val credentials = OAuth2BearerToken(readUserLogin)
      Get("/smrt-base/loggers/" + componentId1 + "/messages") ~> addCredentials(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val messages = responseAs[Set[LogMessage]]
        messages.size === 2
        messages.map{ m => m.level } === Set(LogLevel.INFO, LogLevel.ERROR)
      }
    }

    "create a new log message" in new daoSetup {
      val credentials = OAuth2BearerToken(writeUserLogin)
      val record = LogMessageRecord("This component has critical info", LogLevel.CRITICAL, "test source")
      var message: LogMessage = null
      Post("/smrt-base/loggers/" + componentId2 + "/messages", record) ~> addCredentials(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        message = responseAs[LogMessage]
      }
      Get("/smrt-base/loggers/" + componentId2 + "/messages") ~> addCredentials(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val messages = responseAs[Seq[LogMessage]]
        messages.filter{ m => m.level == LogLevel.CRITICAL } === Seq(message)
      }
    }

    "return recent messages from all resources" in new daoSetup {
      val credentials = OAuth2BearerToken(readUserLogin)
      Get("/smrt-base/loggers/system/messages") ~> addCredentials(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val messages = responseAs[Set[LogMessage]]
        messages.size === 3
        messages.map{ m => m.level } === Set(LogLevel.INFO, LogLevel.ERROR, LogLevel.DEBUG)
      }
    }

    "search for messages in a log resource with a given substring" in new daoSetup {
      val read = OAuth2BearerToken(readUserLogin)
      val write = OAuth2BearerToken(writeUserLogin)
      for (i <- 0 until 100) {
        val parity = i % 2 match {
          case 0 => "even"
          case 1 => "odd"
        }
        val message = "This is message number " + i + " and it is " + parity
        val source = "source" + (i % 3) // source0, source1, source2
        val timeMs = i + (i % 5) * 100 // 0, 101, 202, 303, 404, 5, 106, 207, 308, 409, 10, 111, 212, etc.

        TestProviders.clock().asInstanceOf[FakeClock].reset(timeMs)
        val record = LogMessageRecord(message, LogLevel.NOTICE, source)
        Post("/smrt-base/loggers/" + componentId2 + "/messages", record) ~> addCredentials(write) ~> routes ~> check {
          status.isSuccess must beTrue
        }
      }

      // This search should capture messages where i % 2 == 0, i % 3 == 0, and i % 5 == 1. To wit, 6, 36, 66, 96, etc.
      val searchString = "substring=even&sourceId=source0&startTime=100&endTime=200"
      Get("/smrt-base/loggers/" + componentId2 + "/search?" + searchString) ~> addCredentials(read) ~> routes ~> check {
        status.isSuccess must beTrue
        val messages = responseAs[Seq[LogMessage]]
        messages.size === 4
        messages(0).message === "This is message number 6 and it is even"
        messages(1).message === "This is message number 36 and it is even"
        messages(2).message === "This is message number 66 and it is even"
        messages(3).message === "This is message number 96 and it is even"
      }
    }

    "search for messages in all resources with a given substring" in new daoSetup {
      val read = OAuth2BearerToken(readUserLogin)
      val write = OAuth2BearerToken(writeUserLogin)
      for (i <- 0 until 100) {
        val parity = i % 2 match {
          case 0 => "even"
          case 1 => "odd"
        }
        val message = "This is message number " + i + " and it is " + parity
        val source = "source" + (i % 3) // source0, source1, source2
        val timeMs = i + (i % 5) * 100 // 0, 101, 202, 303, 404, 5, 106, 207, 308, 409, 10, 111, 212, etc.

        // The system logs should contain messages written to every component
        val target = i < 50 match {
          case true => componentId1
          case false => componentId2
        }

        TestProviders.clock().asInstanceOf[FakeClock].reset(timeMs)
        val record = LogMessageRecord(message, LogLevel.NOTICE, source)
        Post("/smrt-base/loggers/" + target + "/messages", record) ~> addCredentials(write) ~> routes ~> check {
          status.isSuccess must beTrue
        }
      }

      // This search should capture messages where i % 2 == 0, i % 3 == 0, and i % 5 == 1. To wit, 6, 36, 66, 96, etc.
      val searchString = "substring=even&sourceId=source0&startTime=100&endTime=200"
      Get("/smrt-base/loggers/system/search?" + searchString) ~> addCredentials(read) ~> routes ~> check {
        status.isSuccess must beTrue
        val messages = responseAs[Seq[LogMessage]]
        messages.size === 4
        messages(0).message === "This is message number 6 and it is even"
        messages(1).message === "This is message number 36 and it is even"
        messages(2).message === "This is message number 66 and it is even"
        messages(3).message === "This is message number 96 and it is even"
      }
    }

    "reject unauthorized users" in new daoSetup {
      Get("/smrt-base/loggers") ~> routes ~> check {
        handled must beFalse
        rejection must beAnInstanceOf[AuthenticationFailedRejection]
      }

      val invalid = OAuth2BearerToken(invalidJwt)
      Get("/smrt-base/loggers") ~> addCredentials(invalid) ~> routes ~> check {
        handled must beFalse
        rejection must beAnInstanceOf[AuthenticationFailedRejection]
      }

      val noWrite = OAuth2BearerToken(readUserLogin)
      val message = LogMessageRecord("This component has critical info", LogLevel.CRITICAL, "test source")
      Post("/smrt-base/loggers/" + componentId2 + "/messages", message) ~> addCredentials(noWrite) ~> routes ~> check {
        handled must beFalse
        rejection === AuthorizationFailedRejection
      }

      val noAdmin = OAuth2BearerToken(writeUserLogin)
      val resource = LogResourceRecord("Logger for New Component", "pacbio.new_component", "New Component")
      Post("/smrt-base/loggers", resource) ~> addCredentials(noAdmin) ~> routes ~> check {
        handled must beFalse
        rejection === AuthorizationFailedRejection
      }
    }
  }
}
