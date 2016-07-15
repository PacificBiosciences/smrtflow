package com.pacbio.secondary.smrtserver.loaders

import com.pacbio.secondary.analysis.jobs.JobModels.PipelineTemplateViewRule
import com.pacbio.secondary.analysis.pbsmrtpipe.PbsmrtpipeConstants
import spray.json._

trait PipelineTemplateViewRulesResourceLoader extends JsonAndEnvResourceLoader[PipelineTemplateViewRule] {
  val ROOT_DIR_PREFIX = "pipeline-template-view-rules"

  val ENV_VAR = PbsmrtpipeConstants.ENV_PB_RULES_PIPELINE_VIEW_DIR

  def loadFromString(xs: String): PipelineTemplateViewRule = {
    val jx = xs.parseJson
    jx.convertTo[PipelineTemplateViewRule]
  }
}

object PipelineTemplateViewRulesResourceLoader extends PipelineTemplateViewRulesResourceLoader
