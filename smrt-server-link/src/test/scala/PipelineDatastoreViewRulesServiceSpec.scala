import scala.concurrent.duration.FiniteDuration
import org.specs2.mutable.Specification
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.testkit.{RouteTestTimeout, Specs2RouteTest}
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  SecondaryJobJsonProtocol,
  SecondaryJobProtocols
}
import com.pacbio.secondary.smrtlink.services.{
  PipelineDataStoreViewRulesServiceProvider,
  ServiceComposer
}
import com.pacbio.secondary.smrtlink.analysis.pbsmrtpipe.PbsmrtpipeConstants

class PipelineDatastoreViewRulesServiceSpec
    extends Specification
    with Specs2RouteTest
    with SecondaryJobJsonProtocol
    with JobServiceConstants {

  sequential

  args(
    skipAll = scala.util.Properties
      .envOrNone(PbsmrtpipeConstants.ENV_BUNDLE_DIR)
      .isEmpty)

  val CURRENT_VERSION = "5.1"

  implicit val routeTestTimeout = RouteTestTimeout(FiniteDuration(5, "sec"))

  object TestProviders
      extends ServiceComposer
      with PipelineDataStoreViewRulesServiceProvider

  val totalRoutes =
    TestProviders.pipelineDataStoreRulesService().prefixedRoutes

  val rulesPrefix = "pipeline-datastore-view-rules"
  val pipelineId = "pbsmrtpipe.pipelines.dev_01"

  "Service lists" should {
    "Get all current pipeline view rules" in {
      Get(s"/$ROOT_SA_PREFIX/$rulesPrefix") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val viewRules = responseAs[List[PipelineDataStoreViewRules]]
        viewRules must not be empty
        viewRules
          .map(r => (r.pipelineId, r.smrtlinkVersion))
          .toMap
          .get(pipelineId) must beEqualTo(Some(CURRENT_VERSION))
      }
    }
    "Retrieve unversioned view rules" in {
      Get(s"/$ROOT_SA_PREFIX/$rulesPrefix/$pipelineId") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val viewRule = responseAs[PipelineDataStoreViewRules]
        viewRule.pipelineId must beEqualTo(pipelineId)
        viewRule.smrtlinkVersion must beEqualTo(CURRENT_VERSION)
      }
    }
    "Retrieve specific versions" in {
      Seq("4.0", "5.0", "5.1").map { v =>
        Get(s"/$ROOT_SA_PREFIX/$rulesPrefix/$pipelineId?version=$v") ~> totalRoutes ~> check {
          status.isSuccess must beTrue
          val viewRule = responseAs[PipelineDataStoreViewRules]
          viewRule.pipelineId must beEqualTo(pipelineId)
          viewRule.smrtlinkVersion must beEqualTo(v)
        }
      }
      Get(
        s"/$ROOT_SA_PREFIX/$rulesPrefix/$pipelineId?version=5.0.0.SNAPSHOT1234") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val viewRule = responseAs[PipelineDataStoreViewRules]
        viewRule.pipelineId must beEqualTo(pipelineId)
        viewRule.smrtlinkVersion must beEqualTo("5.0")
      }
      Get(s"/$ROOT_SA_PREFIX/$rulesPrefix/$pipelineId?version=0.9") ~> totalRoutes ~> check {
        status.isSuccess must beFalse
      }
    }
  }
}
