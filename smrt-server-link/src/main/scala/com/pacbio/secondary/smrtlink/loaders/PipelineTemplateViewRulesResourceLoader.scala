package com.pacbio.secondary.smrtlink.loaders

import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.PipelineTemplateViewRule
import com.pacbio.secondary.smrtlink.analysis.pbsmrtpipe.PbsmrtpipeConstants
import spray.json._

trait PipelineTemplateViewRulesResourceLoader
    extends BundleResourceLoader[PipelineTemplateViewRule] {
  val ROOT_DIR_PREFIX = "pipeline-template-view-rules"
  val BUNDLE_ENV_VAR = PbsmrtpipeConstants.ENV_BUNDLE_DIR
  val ENV_VAR = PbsmrtpipeConstants.ENV_PB_RULES_PIPELINE_VIEW_DIR

  def loadFromString(xs: String): PipelineTemplateViewRule = {
    val jx = xs.parseJson
    jx.convertTo[PipelineTemplateViewRule]
  }
}

object PipelineTemplateViewRulesResourceLoader extends PipelineTemplateViewRulesResourceLoader
