package com.pacbio.secondary.smrtlink.services

import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.ResourceNotFoundError
import com.pacbio.secondary.smrtlink.loaders.PipelineTemplateViewRulesResourceLoader
import com.pacbio.secondary.smrtlink.models.{JsonAble, PacBioComponentManifest}
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

/**
  *
  * Created by mkocher on 9/24/15.
  */
class PipelineTemplateViewRulesService(ptvs: Seq[JsonAble])
    extends SmrtLinkBaseRouteMicroService {

  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  val PTVR_PREFIX = "pipeline-template-view-rules"

  val manifest = PacBioComponentManifest(
    toServiceId("secondary.pipeline_template_view_rules"),
    "Pipeline Template View Rules Service Service",
    "0.1.0",
    "Analysis PiplineTemplate View RulesService"
  )

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
              ptvrs
                .find(_.id == sx)
                .getOrElse(throw new ResourceNotFoundError(
                  s"Unable to find Pipeline Template View Rule $sx"))
            }
          }
        }
    }
}

trait PipelineTemplateViewRulesServiceProvider { this: ServiceComposer =>

  lazy val pipelineTemplateViewRules =
    PipelineTemplateViewRulesResourceLoader.loadResources

  val pipelineTemplateViewRulesService
    : Singleton[PipelineTemplateViewRulesService] =
    Singleton(
      () => new PipelineTemplateViewRulesService(pipelineTemplateViewRules))

  addService(pipelineTemplateViewRulesService)
}
