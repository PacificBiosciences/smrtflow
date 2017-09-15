package com.pacbio.secondary.smrtlink.analysis.pipelines

import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{
  PipelineTemplatePreset,
  PipelineTemplate
}

/**
  *
  * Created by mkocher on 6/24/15.
  */
trait PipelineTemplateDaoComponent {

  def getPipelineTemplates: Seq[PipelineTemplate]

  def getPipelineTemplateById(templateId: String): Option[PipelineTemplate]

  // This should probably be deleted. The primary key is (pipeline-id, preset-id)
  def getPresetsFromPipelineTemplateId(
      pipelineTemplateId: String): Option[Seq[PipelineTemplatePreset]]

  def getPipelinePresetBy(pipelineTemplateId: String,
                          presetId: String): Option[PipelineTemplatePreset]

}

class PipelineTemplateDao(pipelineTemplates: Seq[PipelineTemplate])
    extends PipelineTemplateDaoComponent {

  val _pipelines = pipelineTemplates.map(x => x.id -> x).toMap

  def getPipelineTemplates = _pipelines.values.toSeq

  def getPipelineTemplateById(
      pipelineTemplateId: String): Option[PipelineTemplate] =
    _pipelines.get(pipelineTemplateId)

  def getPipelinePresetBy(
      pipelineId: String,
      pipelinePresetId: String): Option[PipelineTemplatePreset] =
    getPipelineTemplateById(pipelineId).flatMap(x =>
      x.presets.find(_.presetId == pipelinePresetId))

  def getPresetsFromPipelineTemplateId(pipelineId: String) =
    _pipelines.get(pipelineId).map(_.presets)

}
