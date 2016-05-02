import java.net.URL
import java.util.UUID

import akka.testkit.TestActorRef
import com.pacbio.common.actors.InMemoryUserDaoProvider
import com.pacbio.common.auth._
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.UserRecord
import com.pacbio.common.services.{PacBioServiceErrors, ServiceComposer}
import com.pacbio.common.time.{FakeClock, FakeClockProvider}
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.auth.SmrtLinkRoles
import com.pacbio.secondary.smrtlink.models.{RegistryResourceUpdate, RegistryResource, RegistryResourceCreate, SmrtLinkJsonProtocols}
import com.pacbio.secondary.smrtlink.services.RegistryService
import org.specs2.mock._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import spray.http.OAuth2BearerToken
import spray.httpx.SprayJsonSupport._
import spray.routing.Directives
import spray.testkit.Specs2RouteTest

import scalaj.http.{HttpResponse, HttpConstants, HttpRequest, BaseHttp}

class RegistryServiceSpec extends Specification with Directives with Specs2RouteTest with Mockito with PacBioServiceErrors {
  sequential

  import SmrtLinkJsonProtocols._
  import SmrtLinkRoles._

  val FAKE_HOST = "fake.server.com"
  val FAKE_PORT = 1234
  val RESOURCE_ID = "resource_id"
  
  val NOW = 1000000000L

  val READ_USER_LOGIN = "reader"
  val WRITE_USER_LOGIN = "writer"
  val INVALID_JWT = "invalid.jwt"
  val READ_CREDENTIALS = OAuth2BearerToken(READ_USER_LOGIN)
  val WRITE_CREDENTIALS = OAuth2BearerToken(WRITE_USER_LOGIN)
  val INVALID_CREDENTIALS = OAuth2BearerToken(INVALID_JWT)

  trait daoSetup extends Scope {

    val mockHttp = mock[BaseHttp]

    object TestProviders extends
      ServiceComposer with
      RegistryServiceActorProvider with
      InMemoryRegistryDaoProvider with
      InMemoryUserDaoProvider with
      AuthenticatorImplProvider with
      JwtUtilsProvider with
      FakeClockProvider {

        // Provide a fake JwtUtils that uses the login as the JWT, and validates every JWT except for invalidJwt.
        override val jwtUtils: Singleton[JwtUtils] = Singleton(() => new JwtUtils {
          override def getJwt(user: ApiUser): String = user.login
          override def validate(jwt: String): Option[String] = if (jwt == INVALID_JWT) None else Some(jwt)
        })

        // Users initially have no permissions.
        override val defaultRoles = Set.empty[Role]

        // Mock http connections
        override val registryProxyHttp = Singleton(mockHttp)
    }

    val actorRef = TestActorRef[RegistryServiceActor](TestProviders.registryServiceActor())
    val authenticator = TestProviders.authenticator()

    val routes = new RegistryService(actorRef, authenticator).prefixedRoutes

    TestProviders.userDao().createUser(READ_USER_LOGIN, UserRecord("pass"))
    TestProviders.userDao().createUser(WRITE_USER_LOGIN, UserRecord("pass"))
    TestProviders.userDao().addRole(WRITE_USER_LOGIN, REGISTRY_WRITE)

    TestProviders.clock().asInstanceOf[FakeClock].reset(NOW)
    TestProviders.registryDao().asInstanceOf[InMemoryRegistryDao].clear()
    TestProviders.registryDao().createResource(RegistryResourceCreate(FAKE_HOST, FAKE_PORT, RESOURCE_ID))
  }

  "Registry Service" should {
    "return a list of all resources" in new daoSetup {
      Get("/smrt-link/registry-service/resources") ~> addCredentials(READ_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue
        val resources = responseAs[Set[RegistryResource]]
        resources.size === 1
        resources.head.host === FAKE_HOST
        resources.head.port === FAKE_PORT
        resources.head.resourceId === RESOURCE_ID
        resources.head.createdAt.getMillis === NOW
        resources.head.updatedAt.getMillis === NOW
      }
    }

    "return a resource by id" in new daoSetup {
      Get("/smrt-link/registry-service/resources?resourceId=" + RESOURCE_ID) ~> addCredentials(READ_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue
        val resources = responseAs[Set[RegistryResource]]
        resources.size === 1
        resources.head.host === FAKE_HOST
        resources.head.port === FAKE_PORT
        resources.head.resourceId === RESOURCE_ID
        resources.head.createdAt.getMillis === NOW
        resources.head.updatedAt.getMillis === NOW
      }
    }

    "return empty set for missing id" in new daoSetup {
      Get("/smrt-link/registry-service/resources?resourceId=foo") ~> addCredentials(READ_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue
        val resources = responseAs[Set[RegistryResource]]
        resources.size === 0
      }
    }
    
    "create a new resource" in new daoSetup {
      val newResourceId = "new_resource_id"
      val create = RegistryResourceCreate(FAKE_HOST, FAKE_PORT, newResourceId)
      var uuid: UUID = null
      Post("/smrt-link/registry-service/resources", create) ~> addCredentials(WRITE_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue
        val resource = responseAs[RegistryResource]
        uuid = resource.uuid
      }
      Get("/smrt-link/registry-service/resources/" + uuid.toString) ~> addCredentials(READ_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue
        val resource = responseAs[RegistryResource]
        resource.host === FAKE_HOST
        resource.port === FAKE_PORT
        resource.resourceId === newResourceId
        resource.createdAt.getMillis === NOW
        resource.updatedAt.getMillis === NOW
      }
    }
    
    "update a resource" in new daoSetup {
      val newHost = "new.server.com"
      val newPort = 4321
      val newNow = NOW + 1L
      val update = RegistryResourceUpdate(Some(newHost), Some(newPort))
      var uuid: UUID = null
      Get("/smrt-link/registry-service/resources?resourceId=" + RESOURCE_ID) ~> addCredentials(READ_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue
        val resource = responseAs[Set[RegistryResource]].head
        uuid = resource.uuid
      }
      TestProviders.clock().asInstanceOf[FakeClock].reset(newNow)
      Post("/smrt-link/registry-service/resources/" + uuid.toString + "/update", update) ~> addCredentials(WRITE_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue
        val resource = responseAs[RegistryResource]
        resource.host === newHost
        resource.port === newPort
        resource.resourceId === RESOURCE_ID
        resource.createdAt.getMillis === NOW
        resource.updatedAt.getMillis === newNow
      }
      Get("/smrt-link/registry-service/resources/" + uuid.toString) ~> addCredentials(READ_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue
        val resource = responseAs[RegistryResource]
        resource.host === newHost
        resource.port === newPort
        resource.resourceId === RESOURCE_ID
        resource.createdAt.getMillis === NOW
        resource.updatedAt.getMillis === newNow
      }
    }

    "delete a resource" in new daoSetup {
      var uuid: UUID = null
      Get("/smrt-link/registry-service/resources?resourceId=" + RESOURCE_ID) ~> addCredentials(READ_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue
        val resource = responseAs[Set[RegistryResource]].head
        uuid = resource.uuid
      }
      Delete("/smrt-link/registry-service/resources/" + uuid.toString) ~> addCredentials(WRITE_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue
      }
      Get("/smrt-link/registry-service/resources/" + uuid.toString) ~> addCredentials(READ_CREDENTIALS) ~> routes ~> check {
        status.intValue === 404
      }
    }

    "get from proxy" in new daoSetup {
      var uuid: UUID = null
      Get("/smrt-link/registry-service/resources?resourceId=" + RESOURCE_ID) ~> addCredentials(READ_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue
        val resource = responseAs[Set[RegistryResource]].head
        uuid = resource.uuid
      }
      val testPath = "/path/foo"
      val expectedResponse = "expected get response"
      val expectedVia = "test 1.2.3"

      val mockRequest = mock[HttpRequest]
      mockHttp.apply(any[String]) returns mockRequest
      mockRequest.params(any[Map[String, String]]) returns mockRequest
      mockRequest.headers(any[Map[String, String]]) returns mockRequest
      mockRequest.method(any[String]) returns mockRequest
      mockRequest.asBytes returns
        HttpResponse[Array[Byte]](expectedResponse.getBytes(HttpConstants.utf8), 200, Map("Via" -> expectedVia))

      val paramName = "param"
      val paramVal = "value"
      Get("/smrt-link/registry-service/resources/" + uuid.toString + "/proxy" + testPath + "?" + paramName + "=" + paramVal) ~> addCredentials(READ_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue

        val resp = responseAs[String]
        resp === expectedResponse
        header("Via").get.toString === "Via: " + expectedVia

        there was one(mockHttp).apply(new URL("http", FAKE_HOST, FAKE_PORT, testPath).toString)
        there was one(mockRequest).method("GET")
        there was one(mockRequest).asBytes
        there was one(mockRequest).params(Map(paramName -> paramVal))
        there was one(mockRequest).headers(Map("Authorization" -> ("Bearer " + READ_USER_LOGIN)))

        there was no(mockRequest).postData(any[Array[Byte]])
      }
    }

    "post to proxy" in new daoSetup {
      var uuid: UUID = null
      Get("/smrt-link/registry-service/resources?resourceId=" + RESOURCE_ID) ~> addCredentials(READ_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue
        val resource = responseAs[Set[RegistryResource]].head
        uuid = resource.uuid
      }
      val testPath = "/path/foo"
      val expectedResponse = "expected get response"
      val expectedVia = "test 1.2.3"

      val mockRequest = mock[HttpRequest]
      mockHttp.apply(any[String]) returns mockRequest
      mockRequest.postData(any[Array[Byte]]) returns mockRequest
      mockRequest.headers(any[Map[String, String]]) returns mockRequest
      mockRequest.method(any[String]) returns mockRequest
      mockRequest.asBytes returns
        HttpResponse[Array[Byte]](expectedResponse.getBytes(HttpConstants.utf8), 200, Map("Via" -> expectedVia))

      val postData = "post data"
      Post("/smrt-link/registry-service/resources/" + uuid.toString + "/proxy" + testPath, postData) ~> addCredentials(READ_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue

        val resp = responseAs[String]
        resp === expectedResponse
        header("Via").get.toString === "Via: " + expectedVia

        there was one(mockHttp).apply(new URL("http", FAKE_HOST, FAKE_PORT, testPath).toString)
        there was one(mockRequest).method("POST")
        there was one(mockRequest).asBytes
        there was one(mockRequest).postData(postData.getBytes(HttpConstants.utf8))
        there was one(mockRequest).headers(Map("Authorization" -> ("Bearer " + READ_USER_LOGIN)))

        there was no(mockRequest).params(any[Map[String, String]])
      }
    }
  }
}
