package com.pacbio.secondary.analysis.pipelines

import com.pacbio.common.models.pipelines.PipelineTemplate

/**
 * Loads Avro Pipeline templates from resources
 * Created by mkocher on 9/18/15.
 */
trait PipelineTemplateAvroLoader extends AvroLoader[PipelineTemplate]

object PipelineTemplateAvroLoader extends PipelineTemplateAvroLoader
