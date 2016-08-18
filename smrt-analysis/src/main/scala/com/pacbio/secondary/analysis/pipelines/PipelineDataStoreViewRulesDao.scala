package com.pacbio.secondary.analysis.pipelines

import com.pacbio.secondary.analysis.jobs.JobModels.PipelineDataStoreViewRules


class PipelineDataStoreViewRulesDao(resources: Seq[PipelineDataStoreViewRules]) {

  val _resources = resources.map(x => (x.pipelineId, x)).toMap

  def getById(x: String): Option[PipelineDataStoreViewRules] = _resources.get(x)
  def getResources = _resources.values.toSeq

}
