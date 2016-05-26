package com.pacbio.secondary.smrtserver.services

import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.common.services.ServiceComposer
import com.pacbio.common.services.PacBioServiceErrors.ResourceNotFoundError
import com.pacbio.secondary.analysis.jobs.JobModels.PipelineTemplate
import com.pacbio.secondary.analysis.pipelines.PipelineTemplateDao
import com.pacbio.secondary.smrtlink.services.JobsBaseMicroService
import com.pacbio.secondary.smrtserver.loaders.PipelineTemplateResourceLoader
import com.pacbio.secondary.smrtserver.models.SecondaryAnalysisJsonProtocols
import spray.httpx.SprayJsonSupport._


/**
 *
 * Service from the PipelineTemplates and PipelineTemplate View Rules
 *
 * Created by mkocher on 9/21/15.
 */
class ResolvedPipelineTemplateService(dao: PipelineTemplateDao) extends JobsBaseMicroService {

  import SecondaryAnalysisJsonProtocols._

  val manifest = PacBioComponentManifest(toServiceId("resolved_pipeline_templates"),
    "New Pipeline Template Service",
    "0.1.0",
    "Resolved Pipeline Templates Service")

  val PIPELINE_TEMPLATE_PREFIX = "resolved-pipeline-templates"

  val routes =
    pathPrefix(PIPELINE_TEMPLATE_PREFIX) {
      path("status") {
        complete {
          s"status-OK Loaded ${dao.getPipelineTemplates.length}"
        }
      } ~
      pathEnd {
        complete {
          dao.getPipelineTemplates
        }
      } ~
      path(Segment) { sx =>
        get {
          complete {
            ok {
              dao.getPipelineTemplateById(sx)
                .getOrElse(throw new ResourceNotFoundError(s"Unable to find pipeline $sx"))
            }
          }
        }
      } ~
      path(Segment / "presets") { zx =>
        get {
          complete {
            List[PipelineTemplate]()
          }
        }
      } ~
      path(Segment / "presets" / Segment) { (sx, tx) =>
        get {
          complete {
            throw new ResourceNotFoundError(s"Unable to find pipeline prest template $tx pipeline $sx")
          }
        }
      }
    }
}

trait PipelineTemplateProvider {
  val pipelineTemplates: Singleton[Seq[PipelineTemplate]] =
    Singleton(() => PipelineTemplateResourceLoader.loadResources)
}

trait ResolvedPipelineTemplateServiceProvider {
  this: PipelineTemplateProvider
      with ServiceComposer =>

  val resolvedPipelineTemplateService: Singleton[ResolvedPipelineTemplateService] =
    Singleton(() => new ResolvedPipelineTemplateService(new PipelineTemplateDao(pipelineTemplates())))

  addService(resolvedPipelineTemplateService)
}
