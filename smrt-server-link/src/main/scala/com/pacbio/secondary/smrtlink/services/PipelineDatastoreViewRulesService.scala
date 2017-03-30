package com.pacbio.secondary.smrtlink.services

import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.common.services.PacBioServiceErrors.ResourceNotFoundError
import com.pacbio.common.services.ServiceComposer
import com.pacbio.secondary.analysis.jobs.JobModels.PipelineDataStoreViewRules
import com.pacbio.secondary.analysis.pipelines.PipelineDataStoreViewRulesDao
import com.pacbio.secondary.smrtlink.loaders.PipelineDataStoreViewRulesResourceLoader
import com.pacbio.secondary.smrtlink.models.SecondaryAnalysisJsonProtocols

import spray.httpx.SprayJsonSupport._
import spray.json._
import DefaultJsonProtocol._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._

/**
  * Created by mkocher on 8/18/16.
  */
class PipelineDataStoreViewRulesService(dao: PipelineDataStoreViewRulesDao) extends JobsBaseMicroService{

  import SecondaryAnalysisJsonProtocols._

  def failIfNone[T](x: Option[T], message: String): Future[T] =
    x.map(p => Future {p}).getOrElse(Future.failed(new ResourceNotFoundError(message)))

  val PVR_PREFIX = "pipeline-datastore-view-rules"

  val manifest = PacBioComponentManifest(toServiceId("secondary.pipeline_datastore_view_rules"),
    "Pipeline Datastore View Rules Service Service",
    "0.1.0",
    "Pipeline Datastore View Rules Service")

  val routes =
    pathPrefix(PVR_PREFIX) {
      pathEndOrSingleSlash {
        get {
          complete {
            ok {
              dao.getResources
            }
          }
        }
      } ~
      path(Segment) { pipelineId =>
        get {
          complete {
            ok {
              failIfNone[PipelineDataStoreViewRules](dao.getById(pipelineId), s"Unable to find view rules for pipeline id $pipelineId")
            }
          }
        }
      }
    }

}

trait PipelineDataStoreViewRulesServiceProvider {
  this: ServiceComposer with PipelineDataStoreViewRulesServiceProvider =>

  val pipelineDataStoreRulesService: Singleton[PipelineDataStoreViewRulesService] =
    Singleton(() => new PipelineDataStoreViewRulesService(new PipelineDataStoreViewRulesDao(PipelineDataStoreViewRulesResourceLoader.resources)))

  addService(pipelineDataStoreRulesService)
}
