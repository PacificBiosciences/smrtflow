package com.pacbio.secondary.smrtserver.loaders

import com.pacbio.secondary.analysis.jobs.JobModels.PipelineTemplate
import com.pacbio.secondary.analysis.pbsmrtpipe.PbsmrtpipeConstants
import spray.json._

trait PipelineTemplateResourceLoader extends JsonAndEnvResourceLoader[PipelineTemplate]{

  val ENV_VAR = PbsmrtpipeConstants.ENV_PIPELINE_TEMPLATE_DIR

  val ROOT_DIR_PREFIX = "resolved-pipeline-templates"

  override def loadMessage(pt: PipelineTemplate) = s"Loaded PipelineTemplate ${pt.id}"

  def loadFromString(xs: String): PipelineTemplate = {
    val jx = xs.parseJson
    jx.convertTo[PipelineTemplate]
  }

}
object PipelineTemplateResourceLoader extends PipelineTemplateResourceLoader

