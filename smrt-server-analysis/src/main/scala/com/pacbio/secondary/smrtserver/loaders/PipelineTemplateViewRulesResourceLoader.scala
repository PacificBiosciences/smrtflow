package com.pacbio.secondary.smrtserver.loaders

import com.pacbio.secondary.analysis.jobs.JobModels.PipelineTemplateViewRule
import spray.json._

trait PipelineTemplateViewRulesResourceLoader extends JsonResourceLoader[PipelineTemplateViewRule] {
  var RPT_PREFIX = "pipeline-template-view-rules"
  def stringTo(xs: String): PipelineTemplateViewRule = {
    val jx = xs.parseJson
    jx.convertTo[PipelineTemplateViewRule]
  }
}

object PipelineTemplateViewRulesResourceLoader extends PipelineTemplateViewRulesResourceLoader
