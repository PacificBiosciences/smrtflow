package com.pacbio.secondary.smrtlink.models

import com.pacbio.secondary.analysis.jobs.SecondaryJobProtocols
import com.pacbio.secondary.analysis.reports.ReportJsonProtocol
import com.pacbio.secondary.smrtlink.models.SecondaryModels._
import fommil.sjs.FamilyFormats
import spray.json._


trait ReportViewRuleProtocol extends DefaultJsonProtocol {
  implicit object reportViewRuleFormat extends RootJsonFormat[ReportViewRule] {
    def write(r: ReportViewRule) = r.rules
    def read(value: JsValue) = {
      val rules = value.asJsObject
      rules.getFields("id") match {
        case Seq(JsString(id)) => ReportViewRule(id, rules)
        case x => deserializationError(s"Expected ReportViewRule, got $x")
      }
    }
  }
}

trait SecondaryAnalysisJsonProtocols extends SmrtLinkJsonProtocols with ReportJsonProtocol with ReportViewRuleProtocol with FamilyFormats {

  // We bring the required imports from SecondaryJobJsonProtocols like this, as opposed to using it as a mixin, because
  // of namespace conflicts.
  implicit val pipelineTemplateFormat = SecondaryJobProtocols.PipelineTemplateFormat
  implicit val pipelineTemplateViewRule = SecondaryJobProtocols.pipelineTemplateViewRule
  implicit val importDataStoreOptionsFormat = SecondaryJobProtocols.importDataStoreOptionsFormat
  implicit val importConvertFastaOptionsFormat = SecondaryJobProtocols.importConvertFastaOptionsFormat
  implicit val movieMetadataToHdfSubreadOptionsFormat = SecondaryJobProtocols.movieMetadataToHdfSubreadOptionsFormat

  // Jobs
  implicit val jobEventRecordFormat = jsonFormat2(JobEventRecord)

  implicit val exportOptions = jsonFormat3(DataSetExportServiceOptions)
  implicit val deleteDataSetsOptions = jsonFormat3(DataSetDeleteServiceOptions)

  // this is here to break a tie between otherwise-ambiguous implicits;
  // see the spray-json-shapeless documentation
  implicit val llFormat = LogLevelFormat
}

object SecondaryAnalysisJsonProtocols extends SecondaryAnalysisJsonProtocols
