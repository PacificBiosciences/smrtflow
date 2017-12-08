import java.net.URL
import java.util.UUID

import akka.testkit.TestActorRef
import com.pacbio.secondary.smrtlink.auth._
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.common.models._
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors
import com.pacbio.secondary.smrtlink.time.{FakeClock, FakeClockProvider}
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.models.{
  RegistryResource,
  RegistryResourceCreate,
  RegistryResourceUpdate,
  UserRecord
}
import com.pacbio.secondary.smrtlink.services.{
  RegistryService,
  ServiceComposer
}
import org.specs2.mock._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import spray.http.HttpHeaders.RawHeader
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.routing.Directives
import spray.testkit.Specs2RouteTest

import scalaj.http.{BaseHttp, HttpConstants, HttpRequest, HttpResponse}

class RegistryServiceSpec
    extends Specification
    with Directives
    with Specs2RouteTest
    with Mockito
    with PacBioServiceErrors {
  sequential

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._
  import Authenticator._

  val FAKE_HOST = "fake.server.com"
  val FAKE_PORT = 1234
  val RESOURCE_ID = "resource_id"

  val NOW = 1000000000L

  val READ_USER_LOGIN = "reader"
  val ADMIN_USER_LOGIN = "admin"
  val READ_CREDENTIALS = RawHeader(JWT_HEADER, READ_USER_LOGIN)
  val ADMIN_CREDENTIALS = RawHeader(JWT_HEADER, ADMIN_USER_LOGIN)

  trait daoSetup extends Scope {

    val mockHttp = mock[BaseHttp]

    object TestProviders
        extends ServiceComposer
        with RegistryServiceActorProvider
        with InMemoryRegistryDaoProvider
        with AuthenticatorImplProvider
        with JwtUtilsProvider
        with FakeClockProvider {

      // Provide a fake JwtUtils that uses the login as the JWT, and validates every JWT except for invalidJwt.
      override final val jwtUtils: Singleton[JwtUtils] = Singleton(() =>
        new JwtUtils {
          override def parse(jwt: String): Option[UserRecord] =
            Some(UserRecord(jwt))
      })

      // Mock http connections
      override val registryProxyHttp = Singleton(mockHttp)
    }

    val actorRef =
      TestActorRef[RegistryServiceActor](TestProviders.registryServiceActor())
    val authenticator = TestProviders.authenticator()

    val routes = new RegistryService(actorRef, authenticator).prefixedRoutes

    TestProviders.clock().asInstanceOf[FakeClock].reset(NOW)
    TestProviders.registryDao().asInstanceOf[InMemoryRegistryDao].clear()
    TestProviders
      .registryDao()
      .createResource(
        RegistryResourceCreate(FAKE_HOST, FAKE_PORT, RESOURCE_ID))
  }

  "Registry Service" should {
    "return a list of all resources" in new daoSetup {
      Get("/smrt-link/registry-service/resources") ~> addHeader(
        READ_CREDENTIALS) ~> routes ~> check {
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
      Get("/smrt-link/registry-service/resources?resourceId=" + RESOURCE_ID) ~> addHeader(
        READ_CREDENTIALS) ~> routes ~> check {
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
      Get("/smrt-link/registry-service/resources?resourceId=foo") ~> addHeader(
        READ_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue
        val resources = responseAs[Set[RegistryResource]]
        resources.size === 0
      }
    }

    "create a new resource" in new daoSetup {
      val newResourceId = "new_resource_id"
      val create = RegistryResourceCreate(FAKE_HOST, FAKE_PORT, newResourceId)
      var uuid: UUID = null
      Post("/smrt-link/registry-service/resources", create) ~> addHeader(
        ADMIN_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue
        val resource = responseAs[RegistryResource]
        uuid = resource.uuid
      }
      Get("/smrt-link/registry-service/resources/" + uuid.toString) ~> addHeader(
        READ_CREDENTIALS) ~> routes ~> check {
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
      Get("/smrt-link/registry-service/resources?resourceId=" + RESOURCE_ID) ~> addHeader(
        READ_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue
        val resource = responseAs[Set[RegistryResource]].head
        uuid = resource.uuid
      }
      TestProviders.clock().asInstanceOf[FakeClock].reset(newNow)
      Post(
        "/smrt-link/registry-service/resources/" + uuid.toString + "/update",
        update) ~> addHeader(ADMIN_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue
        val resource = responseAs[RegistryResource]
        resource.host === newHost
        resource.port === newPort
        resource.resourceId === RESOURCE_ID
        resource.createdAt.getMillis === NOW
        resource.updatedAt.getMillis === newNow
      }
      Get("/smrt-link/registry-service/resources/" + uuid.toString) ~> addHeader(
        READ_CREDENTIALS) ~> routes ~> check {
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
      Get("/smrt-link/registry-service/resources?resourceId=" + RESOURCE_ID) ~> addHeader(
        READ_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue
        val resource = responseAs[Set[RegistryResource]].head
        uuid = resource.uuid
      }
      Delete("/smrt-link/registry-service/resources/" + uuid.toString) ~> addHeader(
        ADMIN_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue
      }
      Get("/smrt-link/registry-service/resources/" + uuid.toString) ~> addHeader(
        READ_CREDENTIALS) ~> routes ~> check {
        status.intValue === 404
      }
    }

    "get from proxy" in new daoSetup {
      var uuid: UUID = null
      Get("/smrt-link/registry-service/resources?resourceId=" + RESOURCE_ID) ~> addHeader(
        READ_CREDENTIALS) ~> routes ~> check {
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
        HttpResponse[Array[Byte]](
          expectedResponse.getBytes(HttpConstants.utf8),
          200,
          Map("Via" -> IndexedSeq(expectedVia)))

      val paramName = "param"
      val paramVal = "value"
      Get(
        "/smrt-link/registry-service/resources/" + uuid.toString + "/proxy" + testPath + "?" + paramName + "=" + paramVal) ~> addHeader(
        READ_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue

        val resp = responseAs[String]
        resp === expectedResponse
        header("Via").get.toString === "Via: " + expectedVia

        there was one(mockHttp).apply(
          new URL("http", FAKE_HOST, FAKE_PORT, testPath).toString)
        there was one(mockRequest).method("GET")
        there was one(mockRequest).asBytes
        there was one(mockRequest).params(Map(paramName -> paramVal))
        there was one(mockRequest).headers(Map(JWT_HEADER -> READ_USER_LOGIN))

        there was no(mockRequest).postData(any[Array[Byte]])
      }
    }

    "post to proxy" in new daoSetup {
      var uuid: UUID = null
      Get("/smrt-link/registry-service/resources?resourceId=" + RESOURCE_ID) ~> addHeader(
        READ_CREDENTIALS) ~> routes ~> check {
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
        HttpResponse[Array[Byte]](
          expectedResponse.getBytes(HttpConstants.utf8),
          200,
          Map("Via" -> IndexedSeq(expectedVia)))

      val postData = "post data"
      Post(
        "/smrt-link/registry-service/resources/" + uuid.toString + "/proxy" + testPath,
        postData) ~> addHeader(READ_CREDENTIALS) ~> routes ~> check {
        status.isSuccess must beTrue

        val resp = responseAs[String]
        resp === expectedResponse
        header("Via").get.toString === "Via: " + expectedVia

        there was one(mockHttp).apply(
          new URL("http", FAKE_HOST, FAKE_PORT, testPath).toString)
        there was one(mockRequest).method("POST")
        there was one(mockRequest).asBytes
        there was one(mockRequest).postData(
          postData.getBytes(HttpConstants.utf8))
        there was one(mockRequest).headers(Map(JWT_HEADER -> READ_USER_LOGIN))

        there was no(mockRequest).params(any[Map[String, String]])
      }
    }
  }
}
