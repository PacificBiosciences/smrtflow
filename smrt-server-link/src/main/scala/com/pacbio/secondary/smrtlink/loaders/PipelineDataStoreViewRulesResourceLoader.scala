package com.pacbio.secondary.smrtlink.loaders

import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.PipelineDataStoreViewRules
import com.pacbio.secondary.smrtlink.analysis.pbsmrtpipe.PbsmrtpipeConstants
import spray.json._

trait PipelineDataStoreViewRulesResourceLoader
    extends BundleResourceLoader[PipelineDataStoreViewRules] {

  val ENV_VAR = PbsmrtpipeConstants.ENV_PB_RULES_DATASTORE_VIEW_DIR
  val BUNDLE_ENV_VAR = PbsmrtpipeConstants.ENV_BUNDLE_DIR
  val ROOT_DIR_PREFIX = "pipeline-datastore-view-rules"

  override def loadMessage(x: PipelineDataStoreViewRules) =
    s"Loaded PipelineDataStoreView for pipeline id ${x.pipelineId}"

  override def loadFromString(sx: String): PipelineDataStoreViewRules =
    sx.parseJson.convertTo[PipelineDataStoreViewRules]

}

object PipelineDataStoreViewRulesResourceLoader
    extends PipelineDataStoreViewRulesResourceLoader
