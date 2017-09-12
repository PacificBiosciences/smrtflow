import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.{SecondaryJobJsonProtocol, SecondaryJobProtocols}
import com.pacbio.secondary.smrtlink.services.{PipelineTemplateProvider, ResolvedPipelineTemplateServiceProvider, ServiceComposer}
import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.httpx.SprayJsonSupport._

import scala.concurrent.duration.FiniteDuration


class PipelineTemplateSpec extends Specification
    with Specs2RouteTest
    with SecondaryJobJsonProtocol
    with JobServiceConstants {

  sequential

  //import SecondaryJobProtocols._

  implicit val routeTestTimeout = RouteTestTimeout(FiniteDuration(5, "sec"))

  object TestProviders extends
  ServiceComposer with
  ResolvedPipelineTemplateServiceProvider with
  PipelineTemplateProvider

  val totalRoutes = TestProviders.resolvedPipelineTemplateService().prefixedRoutes

  val workflowPrefix = "resolved-pipeline-templates"
  val mockPipelineId = "pbsmrtpipe.pipelines.dev_diagnostic"
  val mockPresetPipelineId = "dev_01-preset_01"

  // Theses are all broken because of the serialization issues with PT
  // The PT options/taskOptions are translated into JsonSchema compatible form
  "Service lists" should {
    "Get workflow Templates" in {
      Get(s"/$ROOT_SA_PREFIX/$workflowPrefix") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val pipelineTemplates = responseAs[List[PipelineTemplate]]
        pipelineTemplates.length must beGreaterThan(0)
        status.isSuccess must beTrue
      }
    }
    "Get Workflow template by id" in {
      Get(s"/$ROOT_SA_PREFIX/$workflowPrefix/$mockPipelineId") ~> totalRoutes ~> check {
        status.isSuccess must beTrue
        val rpt = responseAs[PipelineTemplate]
        rpt.id must beEqualTo(mockPipelineId)
        rpt.taskOptions.size must beEqualTo(8)
      }
    }
    //    "Get Error from bad Workflow template by id" in {
    //      Get(s"/$baseSecondaryPrefix/$workflowPrefix/rs_resequencings") ~> totalRoutes ~> check {
    //        //val status = responseAs[BaseServiceError]
    //        //status.httpCode must beEqualTo(404)
    //        status.isSuccess must beFalse
    //      }
    //    }
    "Get Workflow template preset by workflow template id" in {
      Get(s"/$ROOT_SA_PREFIX/$workflowPrefix/$mockPipelineId/presets") ~> totalRoutes ~> check {
        //val templates = responseAs[List[PipelineTemplatePreset]]
        status.isSuccess must beTrue
      }
    }
    "Get Workflow template preset by workflow template preset id" in {
      Get(s"/$ROOT_SA_PREFIX/$workflowPrefix/$mockPipelineId/presets/$mockPresetPipelineId") ~> totalRoutes ~> check {
        //val templates = responseAs[PipelineTemplatePreset]
        status.isSuccess must beFalse
      }
    }
  }
}
