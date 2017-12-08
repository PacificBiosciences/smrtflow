package com.pacbio.secondary.smrtlink.services

import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.models.PacBioComponentManifest
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.ResourceNotFoundError
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.PipelineTemplate
import com.pacbio.secondary.smrtlink.analysis.pipelines.PipelineTemplateDao
import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.loaders.PipelineTemplateResourceLoader
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

/**
  *
  * Service from the PipelineTemplates and PipelineTemplate View Rules
  *
  * Created by mkocher on 9/21/15.
  */
class ResolvedPipelineTemplateService(dao: PipelineTemplateDao)
    extends SmrtLinkBaseRouteMicroService {

  import SmrtLinkJsonProtocols._

  val manifest = PacBioComponentManifest(
    toServiceId("resolved_pipeline_templates"),
    "Pipeline Template Service",
    "0.1.0",
    "Resolved Pipeline JSON Templates Service for pbsmrtpipe pipelines")

  val PIPELINE_TEMPLATE_PREFIX = "resolved-pipeline-templates"

  val routes =
    pathPrefix(PIPELINE_TEMPLATE_PREFIX) {
      path("status") {
        complete {
          s"status-OK Loaded ${dao.getPipelineTemplates.length}"
        }
      } ~
        pathEndOrSingleSlash {
          complete {
            dao.getPipelineTemplates
          }
        } ~
        path(Segment) { sx =>
          get {
            complete {
              ok {
                dao
                  .getPipelineTemplateById(sx)
                  .getOrElse(throw new ResourceNotFoundError(
                    s"Unable to find pipeline $sx"))
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
              throw new ResourceNotFoundError(
                s"Unable to find pipeline prest template $tx pipeline $sx")
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
  this: PipelineTemplateProvider with ServiceComposer =>

  val resolvedPipelineTemplateService
    : Singleton[ResolvedPipelineTemplateService] =
    Singleton(
      () =>
        new ResolvedPipelineTemplateService(
          new PipelineTemplateDao(pipelineTemplates())))

  addService(resolvedPipelineTemplateService)
}
