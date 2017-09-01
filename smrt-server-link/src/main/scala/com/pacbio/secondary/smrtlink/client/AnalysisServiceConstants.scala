package com.pacbio.secondary.smrtlink.client

import com.pacbio.secondary.smrtlink.JobServiceConstants

/**
  * Created by mkocher on 3/1/17.
  */

trait ServiceEndpointConstants extends JobServiceConstants {
  val ROOT_JM = s"/$ROOT_SERVICE_PREFIX/$JOB_MANAGER_PREFIX"
  val ROOT_JOBS = s"$ROOT_JM/$JOB_ROOT_PREFIX"
  val ROOT_DS = s"/$ROOT_SERVICE_PREFIX/datasets"
  val ROOT_DATASTORE = s"/$ROOT_SERVICE_PREFIX/$DATASTORE_FILES_PREFIX"
  val ROOT_PROJECTS = s"/$ROOT_SERVICE_PREFIX/projects"
  val ROOT_SERVICE_MANIFESTS = "/services/manifests" // keeping with the naming convention
  val ROOT_EULA = "/smrt-base/eula"
  val ROOT_PT = s"/$ROOT_SERVICE_PREFIX/resolved-pipeline-templates"
  val ROOT_PTRULES = s"/$ROOT_SERVICE_PREFIX/pipeline-template-view-rules"
  val ROOT_REPORT_RULES = s"/$ROOT_SERVICE_PREFIX/report-view-rules"
  val ROOT_DS_RULES = s"/$ROOT_SERVICE_PREFIX/pipeline-datastore-view-rules"
  // Not sure where this should go
  val TERMINATE_JOB = "terminate"

  // Base smrt-link routes. Everything should migrate to use this prefix (eventually)
  val ROOT_RUNS = s"/$ROOT_SL_PREFIX/runs"
  val ROOT_PB_DATA_BUNDLE = s"/$ROOT_SL_PREFIX/bundles"
  val ROOT_ALARMS = s"/$ROOT_SL_PREFIX/alarms"

}
