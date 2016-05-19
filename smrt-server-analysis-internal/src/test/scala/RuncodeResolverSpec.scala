import java.util.UUID

import com.pacbio.common.models.{PacBioJsonProtocol, ServiceStatus}
import com.pacbio.secondaryinternal.models.{RuncodeResource, InternalSubreadSet}
import com.pacbio.secondaryinternal.{
  SecondaryInternalAnalysisApi, InternalAnalysisJsonProcotols,
  InternalServiceName}
import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.http._
import spray.http.StatusCodes._
import spray.httpx.SprayJsonSupport._

//class RuncodeResolverSpec extends
//    Specification with
//    Specs2RouteTest with
//    InternalServiceName {
//
//  import InternalAnalysisJsonProcotols._
//
//  object Api extends SecondaryInternalAnalysisApi {
//  }
//
//  val routes = Api.routes
//
//  sequential ^ stopOnFail
//
//  val mockSubreadSet = InternalSubreadSet("2470303-0008", 2470303, "/path/to/subreadset.xml",
//    new UUID(0xda7a5e7da7a5e7daL, 0x7a5e7da7a5e70008L))
//
//  "Runcode resolver" should {
//    "accept an InternalSubreadSet post" in {
//      Post(
//        s"/$baseServiceName/resolvers/subreads",
//        mockSubreadSet
//      ) ~> routes ~> check {
//        val portalResource = responseAs[RuncodeResource]
//        status.isSuccess must beTrue
//      }
//    }
//    "resolve by runcode" in {
//      Get(s"/$baseServiceName/resolvers/runcode/${mockSubreadSet.runcode}") ~> routes ~> check {
//        val portalResource = responseAs[RuncodeResource]
//        status.isSuccess must beTrue
//        portalResource.runcode must beEqualTo(mockSubreadSet.runcode)
//      }
//    }
//    "resolve by expId" in {
//      Get(s"/$baseServiceName/resolvers/experiment/${mockSubreadSet.expId}") ~> routes ~> check {
//        val portalResource = responseAs[List[RuncodeResource]]
//        status.isSuccess must beTrue
//        portalResource.length must beEqualTo(1)
//        portalResource.head.runcode must beEqualTo(mockSubreadSet.runcode)
//      }
//    }
//    "list subreads" in {
//      Get(s"/$baseServiceName/resolvers/subreads") ~> routes ~> check {
//        val portalResource = responseAs[List[RuncodeResource]]
//        status.isSuccess must beTrue
//        portalResource.length must beEqualTo(1)
//        portalResource.head.runcode must beEqualTo(mockSubreadSet.runcode)
//      }
//    }
//    "Malformed Runcode should raise 404" in {
//      Get(s"/$baseServiceName/resolvers/runcode/2470303-0008b") ~> routes ~> check {
//        handled must beFalse
//      }
//    }
//  }
//}
