package com.pacbio.secondary.smrtserver.services

import akka.util.Timeout
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.common.services.ServiceComposer
import com.pacbio.common.services.PacBioServiceErrors.ResourceNotFoundError
import com.pacbio.secondary.analysis.jobs.JobModels.PipelineTemplateViewRule
import com.pacbio.secondary.smrtlink.services.JobsBaseMicroService
import com.pacbio.secondary.smrtserver.loaders.PipelineTemplateViewRulesResourceLoader
import com.pacbio.secondary.smrtserver.models.SecondaryAnalysisJsonProtocols
import spray.httpx.SprayJsonSupport._

import scala.concurrent.duration._


/**
 *
 * Created by mkocher on 9/24/15.
 */
class PipelineTemplateViewRulesService(ptvs: Seq[PipelineTemplateViewRule]) extends JobsBaseMicroService {

  import SecondaryAnalysisJsonProtocols._

  implicit val timeout = Timeout(4.seconds)
  val PTVR_PREFIX = "pipeline-template-view-rules"

  val manifest = PacBioComponentManifest(toServiceId("secondary.pipeline_template_view_rules"),
    "Pipeline Template View Rules Service Service",
    "0.1.0",
    "Analysis PiplineTemplate View RulesService", None)

  val ptvrs = PipelineTemplateViewRulesResourceLoader.loadResources

  val routes =
    pathPrefix(PTVR_PREFIX) {
      path("status") {
        get {
          complete {
            s"Hello. Loaded Pipeline template views ${ptvrs.length}"
          }
        }
      } ~
      pathEnd {
        get {
          complete {
            ptvrs
          }
        }
      } ~
      path(Segment) { sx =>
        get {
          complete {
            ok {
              ptvrs.find(_.id == sx)
                .getOrElse(throw new ResourceNotFoundError(s"Unable to find Pipeline Template View Rule $sx"))
            }
          }
        }
      }
    }
}

trait PipelineTemplateViewRulesServiceProvider {
  this: ServiceComposer =>

  lazy val pipelineTemplateViewRules = PipelineTemplateViewRulesResourceLoader.loadResources

  val pipelineTemplateViewRulesService: Singleton[PipelineTemplateViewRulesService] =
    Singleton(() => new PipelineTemplateViewRulesService(pipelineTemplateViewRules))

  addService(pipelineTemplateViewRulesService)
}
