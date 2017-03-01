package com.pacbio.secondary.smrtlink.client

/**
  * Created by mkocher on 3/1/17.
  */
trait AnalysisServiceConstants extends ServiceEndpointConstants {
    val ROOT_PT = s"/$ROOT_SERVICE_PREFIX/resolved-pipeline-templates"
    val ROOT_PTRULES = s"/$ROOT_SERVICE_PREFIX/pipeline-template-view-rules"
    val ROOT_REPORT_RULES = s"/$ROOT_SERVICE_PREFIX/report-view-rules"
    val ROOT_DS_RULES = s"/$ROOT_SERVICE_PREFIX/pipeline-datastore-view-rules"
    // Not sure where this should go
    val TERMINATE_JOB = "terminate"
}
