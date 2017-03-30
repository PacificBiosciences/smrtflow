import java.io.File

import com.pacbio.common.auth._
import com.pacbio.common.database._
import com.pacbio.common.dependency.{SetBindings, Singleton}
import com.pacbio.common.models._
import com.pacbio.common.services.LogServiceProvider
import com.pacbio.common.time._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.time.NoTimeConversions
import spray.http.HttpHeaders.RawHeader
import spray.httpx.SprayJsonSupport._
import spray.routing.{AuthorizationFailedRejection, AuthenticationFailedRejection, Directives, HttpService}
import spray.testkit.Specs2RouteTest

import scala.concurrent.Await
import scala.concurrent.duration._

// TODO(smcclellan): Refactor this into multiple specs, for the spray routing, the DAO, and the database interactions
class LogSpec
  extends Specification
  with NoTimeConversions
  with Directives
  with Specs2RouteTest
  with HttpService {

  sequential

  import PacBioJsonProtocol._
  import Authenticator._

  def actorRefFactory = system

  implicit val routeTestTimeout = RouteTestTimeout(30.seconds)

  val typeId = "pacbio.my_component"
  val componentId1 = "pacbio.my_component.one"
  val componentId2 = "pacbio.my_component.two"

  val readUserLogin = "reader"
  val adminUserLogin = "admin"

  val invalidJwt = "invalid.jwt"

  object TestProviders extends
      SetBindings with
      LogServiceProvider with
      DatabaseLogDaoProvider with
      AuthenticatorImplProvider with
      JwtUtilsProvider with
      FakeClockProvider {

    // Provide a fake JwtUtils that uses the login as the JWT, and validates every JWT except for invalidJwt.
    override final val jwtUtils: Singleton[JwtUtils] = Singleton(() => new JwtUtils {
      override def parse(jwt: String): Option[UserRecord] = if (jwt == invalidJwt) None else Some {
        if (jwt == adminUserLogin) UserRecord(jwt, Some("PbAdmin")) else UserRecord(jwt)
      }
    })
    
    override final val logDaoBufferSize = 4
    override val dbConf = "smrtflow.test-db"
  }

  val authenticator = TestProviders.authenticator()
  val logDao: DatabaseLogDao = TestProviders.logDao().asInstanceOf[DatabaseLogDao]

  val routes = TestProviders.logService().prefixedRoutes

  trait daoSetup extends Scope {
    Await.ready(for {
      _ <- logDao.clear()

      _ <- logDao.createLogResource(LogResourceRecord("Logger for Component 1", componentId1, "Component 1"))
      _ <- logDao.createLogResource(LogResourceRecord("Logger for Component 2", componentId2, "Component 2"))


      _ <- logDao.createLogMessage(componentId1, LogMessageRecord("This component has some info.", LogLevel.INFO, "test source"))
      _ <- logDao.createLogMessage(componentId1, LogMessageRecord("This component has an error.", LogLevel.ERROR, "test source"))

      _ <- logDao.createLogMessage(componentId2, LogMessageRecord("This component has some debug info.", LogLevel.DEBUG, "test source"))
    } yield (), 10.seconds)
  }

  "Log Service" should {

    "return a list of log resources" in new daoSetup {
      val credentials = RawHeader(JWT_HEADER, readUserLogin)
      Get("/smrt-base/loggers") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val resources = responseAs[Set[LogResource]]
        resources.size === 2
        resources.map { r => r.id } === Set(componentId1, componentId2)
      }
    }

    "return a specific log resource" in new daoSetup {
      val credentials = RawHeader(JWT_HEADER, readUserLogin)
      Get("/smrt-base/loggers/" + componentId1) ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        responseAs[LogResource].id === componentId1
      }
    }

    "create a new log resource" in new daoSetup {
      val credentials = RawHeader(JWT_HEADER, adminUserLogin)
      val newComponentId = "pacbio.my_component.new"
      val record = LogResourceRecord("Logger for New Component", newComponentId, "New Component")
      Post("/smrt-base/loggers", record) ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
      }
      Get("/smrt-base/loggers/" + newComponentId) ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        responseAs[LogResource].id === newComponentId
      }
    }

    "return recent messages from a log resource" in new daoSetup {
      val credentials = RawHeader(JWT_HEADER, readUserLogin)
      Get("/smrt-base/loggers/" + componentId1 + "/messages") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val messages = responseAs[Set[LogMessage]]
        messages.size === 2
        messages.map{ m => m.level } === Set(LogLevel.INFO, LogLevel.ERROR)
      }
    }

    "create a new log message" in new daoSetup {
      val credentials = RawHeader(JWT_HEADER, adminUserLogin)
      val record = LogMessageRecord("This component has critical info", LogLevel.CRITICAL, "test source")
      var message: LogMessage = null
      Post("/smrt-base/loggers/" + componentId2 + "/messages", record) ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        message = responseAs[LogMessage]
      }
      Get("/smrt-base/loggers/" + componentId2 + "/messages") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val messages = responseAs[Seq[LogMessage]]
        messages.filter{ m => m.level == LogLevel.CRITICAL } === Seq(message)
      }
    }

    "return recent messages from all resources" in new daoSetup {
      val credentials = RawHeader(JWT_HEADER, readUserLogin)
      Get("/smrt-base/loggers/system/messages") ~> addHeader(credentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val messages = responseAs[Set[LogMessage]]
        messages.size === 3
        messages.map{ m => m.level } === Set(LogLevel.INFO, LogLevel.ERROR, LogLevel.DEBUG)
      }
    }

    "search for messages in a log resource with a given substring" in new daoSetup {
      val read = RawHeader(JWT_HEADER, readUserLogin)
      val write = RawHeader(JWT_HEADER, adminUserLogin)
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
        Post("/smrt-base/loggers/" + componentId2 + "/messages", record) ~> addHeader(write) ~> routes ~> check {
          status.isSuccess must beTrue
        }
      }

      // This search should capture messages where i % 2 == 0, i % 3 == 0, and i % 5 == 1. To wit, 6, 36, 66, 96, etc.
      val searchString = "substring=even&sourceId=source0&startTime=100&endTime=200"
      Get("/smrt-base/loggers/" + componentId2 + "/search?" + searchString) ~> addHeader(read) ~> routes ~> check {
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
      val read = RawHeader(JWT_HEADER, readUserLogin)
      val write = RawHeader(JWT_HEADER, adminUserLogin)
      for (i <- 0 until 100) {
        val parity = if (i % 2 == 0) "even" else "odd"
        val message = "This is message number " + i + " and it is " + parity
        val source = "source" + (i % 3) // source0, source1, source2
        val timeMs = i + (i % 5) * 100 // 0, 101, 202, 303, 404, 5, 106, 207, 308, 409, 10, 111, 212, etc.

        // The system logs should contain messages written to every component
        val target = if (i < 50) componentId1 else componentId2

        TestProviders.clock().asInstanceOf[FakeClock].reset(timeMs)
        val record = LogMessageRecord(message, LogLevel.NOTICE, source)
        Post("/smrt-base/loggers/" + target + "/messages", record) ~> addHeader(write) ~> routes ~> check {
          status.isSuccess must beTrue
        }
      }

      // This search should capture messages where i % 2 == 0, i % 3 == 0, and i % 5 == 1. To wit, 6, 36, 66, 96, etc.
      val searchString = "substring=even&sourceId=source0&startTime=100&endTime=200"
      Get("/smrt-base/loggers/system/search?" + searchString) ~> addHeader(read) ~> routes ~> check {
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
      val message = LogMessageRecord("This component has critical info", LogLevel.CRITICAL, "test source")

      Post("/smrt-base/loggers/" + componentId2 + "/messages", message) ~> routes ~> check {
        handled must beFalse
        rejection must beAnInstanceOf[AuthenticationFailedRejection]
      }

      val invalid = RawHeader(JWT_HEADER, invalidJwt)
      Post("/smrt-base/loggers/" + componentId2 + "/messages", message) ~> addHeader(invalid) ~> routes ~> check {
        handled must beFalse
        rejection must beAnInstanceOf[AuthenticationFailedRejection]
      }
    }
  }
}
