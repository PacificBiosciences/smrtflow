import java.net.URL
import java.util.UUID

import com.pacbio.secondary.smrtlink.auth._
import com.pacbio.common.models._
import com.pacbio.secondary.smrtlink.services.{
  PacBioServiceErrors,
  RegistryService,
  RegistryServiceProvider,
  ServiceComposer
}
import com.pacbio.secondary.smrtlink.time.{FakeClock, FakeClockProvider}
import com.pacbio.secondary.smrtlink.actors._
import com.pacbio.secondary.smrtlink.models.{
  RegistryResource,
  RegistryResourceCreate,
  RegistryResourceUpdate,
  UserRecord
}
import org.specs2.mock._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.Specs2RouteTest
import akka.stream.ActorMaterializer
import com.pacbio.secondary.smrtlink.dependency.{
  ConfigProvider,
  DefaultConfigProvider
}

class RegistryServiceSpec
    extends Specification
    with Directives
    with Specs2RouteTest
    with Mockito
    with PacBioServiceErrors {
  sequential

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._
  import JwtUtils._

  val FAKE_HOST = "fake.server.com"
  val FAKE_PORT = 1234
  val RESOURCE_ID = "resource_id"

  val NOW = 1000000000L

  val jwtUtil = new JwtUtilsImpl

  val READ_USER_LOGIN = UserRecord("reader", Some("carbon/reader@domain.com"))
  val ADMIN_USER_LOGIN = UserRecord("admin", Some("carbon/admin@domain.com"))
  val READ_CREDENTIALS =
    RawHeader(JWT_HEADER, jwtUtil.userRecordToJwt(READ_USER_LOGIN))
  val ADMIN_CREDENTIALS =
    RawHeader(JWT_HEADER, jwtUtil.userRecordToJwt(ADMIN_USER_LOGIN))

  trait daoSetup extends Scope {

    object TestProviders
        extends ServiceComposer
        with DefaultConfigProvider
        with ActorSystemProvider
        with InMemoryRegistryDaoProvider
        with JwtUtilsImplProvider
        with RegistryServiceProvider
        with FakeClockProvider {}

    implicit val customExceptionHandler = pacbioExceptionHandler
    implicit val customRejectionHandler = pacBioRejectionHandler

    //val routes = new RegistryService(TestProviders.registryDao(), actorSystem, materializer).prefixedRoutes
    val routes = TestProviders.routes()

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

//    "get from proxy" in new daoSetup {
//      var uuid: UUID = null
//      Get("/smrt-link/registry-service/resources?resourceId=" + RESOURCE_ID) ~> addHeader(
//        READ_CREDENTIALS) ~> routes ~> check {
//        // println(s"Response $responseEntity")
//        status.isSuccess must beTrue
//        val resource = responseAs[Set[RegistryResource]].head
//        uuid = resource.uuid
//      }
//      val testPath = "/path/foo"
//      val expectedResponse = "expected get response"
//      val expectedVia = "test 1.2.3"
//
//      val mockRequest = mock[HttpRequest]
//      mockHttp.apply(any[String]) returns mockRequest
//      mockRequest.params(any[Map[String, String]]) returns mockRequest
//      mockRequest.headers(any[Map[String, String]]) returns mockRequest
//      mockRequest.method(any[String]) returns mockRequest
//      mockRequest.asBytes returns
//        HttpResponse[Array[Byte]](
//          expectedResponse.getBytes(HttpConstants.utf8),
//          200,
//          Map("Via" -> IndexedSeq(expectedVia)))
//
//      val paramName = "param"
//      val paramVal = "value"
//      val registryUrl = s"/smrt-link/registry-service/resources/${uuid.toString}/proxy$testPath" + "?" + paramName + "=" + paramVal
//      Get(registryUrl) ~> addHeader(READ_CREDENTIALS) ~> routes ~> check {
//        //println(s"Response $responseEntity")
//        status.isSuccess must beTrue
//
//        val resp = responseAs[String]
//        resp === expectedResponse
//        header("Via").get.toString === "Via: " + expectedVia
//
//        there was one(mockHttp).apply(
//          new URL("http", FAKE_HOST, FAKE_PORT, testPath).toString)
//        there was one(mockRequest).method("GET")
//        there was one(mockRequest).asBytes
//        there was one(mockRequest).params(Map(paramName -> paramVal))
//        there was one(mockRequest).headers(
//          Map(JWT_HEADER -> jwtUtil.userRecordToJwt(READ_USER_LOGIN)))
//
//        there was no(mockRequest).postData(any[Array[Byte]])
//      }
//    }

//    "post to proxy" in new daoSetup {
//      var uuid: UUID = null
//      Get("/smrt-link/registry-service/resources?resourceId=" + RESOURCE_ID) ~> addHeader(
//        READ_CREDENTIALS) ~> routes ~> check {
//        status.isSuccess must beTrue
//        val resource = responseAs[Set[RegistryResource]].head
//        uuid = resource.uuid
//      }
//      val testPath = "/path/foo"
//      val expectedResponse = "expected get response"
//      val expectedVia = "test 1.2.3"
//
//      val mockRequest = mock[HttpRequest]
//      mockHttp.apply(any[String]) returns mockRequest
//      mockRequest.postData(any[Array[Byte]]) returns mockRequest
//      mockRequest.headers(any[Map[String, String]]) returns mockRequest
//      mockRequest.method(any[String]) returns mockRequest
//      mockRequest.asBytes returns
//        HttpResponse[Array[Byte]](
//          expectedResponse.getBytes(HttpConstants.utf8),
//          200,
//          Map("Via" -> IndexedSeq(expectedVia)))
//
//      val postData = "post data"
//      Post(
//        "/smrt-link/registry-service/resources/" + uuid.toString + "/proxy" + testPath,
//        postData) ~> addHeader(READ_CREDENTIALS) ~> routes ~> check {
//        status.isSuccess must beTrue
//
//        val resp = responseAs[String]
//        resp === expectedResponse
//        header("Via").get.toString === "Via: " + expectedVia
//
//        there was one(mockHttp).apply(
//          new URL("http", FAKE_HOST, FAKE_PORT, testPath).toString)
//        there was one(mockRequest).method("POST")
//        there was one(mockRequest).asBytes
//        there was one(mockRequest).postData(
//          postData.getBytes(HttpConstants.utf8))
//        there was one(mockRequest).headers(
//          Map(JWT_HEADER -> jwtUtil.userRecordToJwt(READ_USER_LOGIN)))
//
//        there was no(mockRequest).params(any[Map[String, String]])
//      }
//    }
  }
}
