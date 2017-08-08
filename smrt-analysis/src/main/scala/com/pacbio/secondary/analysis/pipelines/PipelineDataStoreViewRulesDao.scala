package com.pacbio.secondary.analysis.pipelines

import com.pacbio.secondary.analysis.jobs.JobModels.PipelineDataStoreViewRules


class PipelineDataStoreViewRulesDao(resources: Seq[PipelineDataStoreViewRules]) {

  val _resources: Map[String, Seq[PipelineDataStoreViewRules]] =
    resources.map(r => (r.pipelineId, r))
      .groupBy(_._1)
      .mapValues(_.map(_._2))

  def getById(x: String, version: Option[String] = None):
      Option[PipelineDataStoreViewRules] = {
    val allRules = _resources.get(x).map(_.map(r => (r.smrtlinkVersion, r)).toMap)
    allRules.flatMap(vrs => version match {
      case Some(v) => vrs.values.toSeq.filter(r => v.startsWith(r.smrtlinkVersion)).headOption
      case None => Some(vrs.values.toSeq.sortBy(_.smrtlinkVersion).last)
    })
  }
  def getResources: Seq[PipelineDataStoreViewRules] =
    _resources.values.toSeq.map(vrs => vrs.sortBy(_.smrtlinkVersion).last).sortBy(_.pipelineId)

}
