import com.pacbio.common.actors._
import com.pacbio.common.auth._
import com.pacbio.common.models._
import com.pacbio.common.services.{PacBioServiceErrors, UserService}
import com.pacbio.common.time.FakeClockProvider
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.time.NoTimeConversions
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import spray.http.{BasicHttpCredentials, OAuth2BearerToken}
import spray.httpx.SprayJsonSupport._
import spray.routing._
import spray.testkit.Specs2RouteTest

// TODO(smcclellan): Refactor this into multiple specs, for the spray routing, the DAO, the JWT utils, etc.
class UserSpec extends Specification with Directives with Specs2RouteTest with HttpService with BaseRolesInit with NoTimeConversions with PacBioServiceErrors {
  // Tests must be run in sequence because of shared state in InMemoryUserDao
  sequential

  import PacBioJsonProtocol._
  import BaseRoles._

  implicit val routeTestTimeout = RouteTestTimeout(10.seconds)

  def actorRefFactory = system

  val basicUserLogin = "basic"
  val basicUserPass = "basicPass"
  val rootUserLogin = "root"
  val rootUserPass = "rootPass"

  object TestProviders extends
    InMemoryUserDaoProvider with
    AuthenticatorImplProvider with
    JwtUtilsImplProvider with
    FakeClockProvider {
      override lazy val defaultRoles = Set.empty[Role]
  }

  val userDao = TestProviders.userDao()
  val authenticator = TestProviders.authenticator()
  val routes = new UserService(userDao, authenticator).prefixedRoutes

  trait daoSetup extends Scope {
    userDao.asInstanceOf[InMemoryUserDao].clear()

    Await.ready(
      userDao.createUser(basicUserLogin, UserRecord(basicUserPass)) flatMap { _ =>
      userDao.createUser(rootUserLogin, UserRecord(rootUserPass))} flatMap { _ =>
      userDao.addRole(rootUserLogin, ROOT)}, 10.seconds)
  }

  "User Service" should {
    "return info about a specific user" in new daoSetup {
      Get("/smrt-base/user/" + rootUserLogin) ~> routes ~> check {
        status.isSuccess must beTrue
        val resp = responseAs[UserResponse]
        resp === UserResponse(rootUserLogin, rootUserLogin, None, None, None, Set(ROOT))
      }
    }

    "create a new user" in new daoSetup {
      val newLogin = "jsmith"
      val newUserRecord = UserRecord("foo")
      Put("/smrt-base/user/" + newLogin, newUserRecord) ~> routes ~> check {
        status.isSuccess must beTrue
        val resp = responseAs[UserResponse]
        resp === UserResponse(newLogin, newLogin, None, None, None, Set.empty[Role])
      }
      Get("/smrt-base/user/" + newLogin) ~> routes ~> check {
        status.isSuccess must beTrue
        val resp = responseAs[UserResponse]
        resp === UserResponse(newLogin, newLogin, None, None, None, Set.empty[Role])
      }
    }

    "delete a user" in new daoSetup {
      // Get a JWT to act as root user
      val userPassCredentials = BasicHttpCredentials(rootUserLogin, rootUserPass)
      var jwtCredentials: OAuth2BearerToken = null
      Get("/smrt-base/user/" + rootUserLogin + "/token") ~> addCredentials(userPassCredentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val resp = responseAs[String]
        jwtCredentials = OAuth2BearerToken(resp)
      }
      Delete("/smrt-base/user/" + basicUserLogin) ~> addCredentials(jwtCredentials) ~> routes ~> check {
        status.isSuccess must beTrue
      }
      Get("/smrt-base/user/" + basicUserLogin) ~> routes ~> check {
        status.isSuccess must beFalse
        status.intValue === 404
      }
    }

    "add a role to a user" in new daoSetup {
      val userPassCredentials = BasicHttpCredentials(rootUserLogin, rootUserPass)
      var jwtCredentials: OAuth2BearerToken = null
      Get("/smrt-base/user/" + rootUserLogin + "/token") ~> addCredentials(userPassCredentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val resp = responseAs[String]
        jwtCredentials = OAuth2BearerToken(resp)
      }
      Post("/smrt-base/user/" + basicUserLogin + "/role/add", "ROOT") ~> addCredentials(jwtCredentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val resp = responseAs[UserResponse]
        resp === UserResponse(basicUserLogin, basicUserLogin, None, None, None, Set(ROOT))
      }
      Get("/smrt-base/user/" + basicUserLogin) ~> routes ~> check {
        status.isSuccess must beTrue
        val resp = responseAs[UserResponse]
        resp === UserResponse(basicUserLogin, basicUserLogin, None, None, None, Set(ROOT))
      }
    }

    "remove a role from a user" in new daoSetup {
      val userPassCredentials = BasicHttpCredentials(rootUserLogin, rootUserPass)
      var jwtCredentials: OAuth2BearerToken = null
      Get("/smrt-base/user/" + rootUserLogin + "/token") ~> addCredentials(userPassCredentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val resp = responseAs[String]
        jwtCredentials = OAuth2BearerToken(resp)
      }
      Post("/smrt-base/user/" + rootUserLogin + "/role/remove", "ROOT") ~> addCredentials(jwtCredentials) ~> routes ~> check {
        status.isSuccess must beTrue
        val resp = responseAs[UserResponse]
        resp === UserResponse(rootUserLogin, rootUserLogin, None, None, None, Set.empty[Role])
      }
      Get("/smrt-base/user/" + rootUserLogin) ~> routes ~> check {
        status.isSuccess must beTrue
        val resp = responseAs[UserResponse]
        resp === UserResponse(rootUserLogin, rootUserLogin, None, None, None, Set.empty[Role])
      }
    }

    "reject unauthorized users" in new daoSetup {
      Get("/smrt-base/user/" + basicUserLogin + "/token") ~> routes ~> check {
        handled must beFalse
        rejection must beAnInstanceOf[AuthenticationFailedRejection]
      }

      val invalidUserPass = BasicHttpCredentials(basicUserLogin, "foo")
      Get("/smrt-base/user/" + basicUserLogin + "/token") ~> addCredentials(invalidUserPass) ~> routes ~> check {
        handled must beFalse
        rejection must beAnInstanceOf[AuthenticationFailedRejection]
      }

      val userPass = BasicHttpCredentials(basicUserLogin, basicUserPass)
      var jwtCredentials: OAuth2BearerToken = null
      Get("/smrt-base/user/" + basicUserLogin + "/token") ~> addCredentials(userPass) ~> routes ~> check {
        status.isSuccess must beTrue
        val resp = responseAs[String]
        jwtCredentials = OAuth2BearerToken(resp)
      }
      Delete("/smrt-base/user/" + rootUserLogin) ~> addCredentials(jwtCredentials) ~> routes ~> check {
        handled must beFalse
        rejection === AuthorizationFailedRejection
      }
      Post("/smrt-base/user/" + basicUserLogin + "/role/add", "ROOT") ~> addCredentials(jwtCredentials) ~> routes ~> check {
        handled must beFalse
        rejection === AuthorizationFailedRejection
      }
      Post("/smrt-base/user/" + rootUserLogin + "/role/remove", "ROOT") ~> addCredentials(jwtCredentials) ~> routes ~> check {
        handled must beFalse
        rejection === AuthorizationFailedRejection
      }
    }
  }
}
