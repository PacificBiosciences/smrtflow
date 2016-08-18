package com.pacbio.secondary.smrtserver.services

import com.pacbio.common.dependency.Singleton
import com.pacbio.common.models.PacBioComponentManifest
import com.pacbio.common.services.ServiceComposer
import com.pacbio.secondary.analysis.pipelines.PipelineDataStoreViewRulesDao
import com.pacbio.secondary.smrtlink.services.JobsBaseMicroService
import com.pacbio.secondary.smrtserver.loaders.PipelineDataStoreViewRulesResourceLoader
import com.pacbio.secondary.smrtserver.models.SecondaryAnalysisJsonProtocols
import spray.httpx.SprayJsonSupport._

/**
  * Created by mkocher on 8/18/16.
  */
class PipelineDataStoreViewRulesService(dao: PipelineDataStoreViewRulesDao) extends JobsBaseMicroService{

  import SecondaryAnalysisJsonProtocols._

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
              dao.getById(pipelineId)
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
