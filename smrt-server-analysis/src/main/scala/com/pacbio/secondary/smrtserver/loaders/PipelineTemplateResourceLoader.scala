package com.pacbio.secondary.smrtserver.loaders

import com.pacbio.secondary.analysis.jobs.JobModels.PipelineTemplate
import spray.json._

trait PipelineTemplateResourceLoader extends JsonResourceLoader[PipelineTemplate] {
  var RPT_PREFIX = "resolved-pipeline-templates"
  override def loadMessage(pt: PipelineTemplate) = s"Loaded PipelineTemplate ${pt.id}"
  def stringTo(xs: String): PipelineTemplate = {
    val jx = xs.parseJson
    jx.convertTo[PipelineTemplate]
  }
}
object PipelineTemplateResourceLoader extends PipelineTemplateResourceLoader

