package com.pacbio.secondary.smrtlink.models

import EngineConfig
import com.pacbio.secondary.smrtlink.analysis.pbsmrtpipe.{CommandTemplate, PbsmrtpipeEngineOptions}
import spray.json._


object SecondaryModels {

  // POST creation of a job event
  case class JobEventRecord(
      state: String,
      message: String)

  case class ReportViewRule(id: String, rules: JsObject)

  case class DataSetExportServiceOptions(datasetType: String, ids: Seq[Int],
                                         outputPath: String)
  case class DataSetDeleteServiceOptions(datasetType: String, ids: Seq[Int],
                                         removeFiles: Boolean = true)

  case class TsJobBundleJobServiceOptions(jobId: Int, user: String, comment: String)

  case class TsSystemStatusServiceOptions(user: String, comment: String)

  case class DbBackUpServiceJobOptions(user: String, comment: String)


}
