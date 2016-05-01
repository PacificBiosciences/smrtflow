package com.pacbio.secondary.analysis.pipelines

import com.pacbio.common.models.pipelines.viewrules.PipelineTemplateViewRule

/**
 * Load all Avro pipeline template rules
 * Created by mkocher on 9/19/15.
 */
trait PipelineTemplateViewRulesLoader extends AvroLoader[PipelineTemplateViewRule]

object PipelineTemplateViewRulesLoader extends PipelineTemplateViewRulesLoader
