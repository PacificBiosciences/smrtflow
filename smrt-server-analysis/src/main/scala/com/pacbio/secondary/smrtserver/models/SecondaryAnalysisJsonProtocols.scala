package com.pacbio.secondary.smrtserver.models

import fommil.sjs.FamilyFormats

import com.pacbio.secondary.smrtlink.models.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtserver.models.SecondaryModels._
import com.pacbio.secondary.analysis.jobs.SecondaryJobProtocols
import com.pacbio.secondary.analysis.reports.ReportJsonProtocol


trait SecondaryAnalysisJsonProtocols extends SmrtLinkJsonProtocols with ReportJsonProtocol with FamilyFormats {

  // We bring the required imports from SecondaryJobJsonProtocols like this, as opposed to using it as a mixin, because
  // of namespace conflicts.
  implicit val pipelineTemplateFormat = SecondaryJobProtocols.PipelineTemplateFormat
  implicit val pipelineTemplateViewRule = SecondaryJobProtocols.pipelineTemplateViewRule
  implicit val importDataStoreOptionsFormat = SecondaryJobProtocols.importDataStoreOptionsFormat
  implicit val importConvertFastaOptionsFormat = SecondaryJobProtocols.importConvertFastaOptionsFormat
  implicit val movieMetadataToHdfSubreadOptionsFormat = SecondaryJobProtocols.movieMetadataToHdfSubreadOptionsFormat

  // Jobs
  implicit val jobEventRecordFormat = jsonFormat2(JobEventRecord)

  implicit val reportAttributeViewRuleFormat = jsonFormat2(ReportAttributeViewRule)
  implicit val reportViewRuleFormat = jsonFormat3(ReportViewRule)
  implicit val exportOptions = jsonFormat3(DataSetExportServiceOptions)

  // this is here to break a tie between otherwise-ambiguous implicits;
  // see the spray-json-shapeless documentation
  implicit val llFormat = LogLevelFormat
}

object SecondaryAnalysisJsonProtocols extends SecondaryAnalysisJsonProtocols
