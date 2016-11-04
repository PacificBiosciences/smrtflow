package com.pacbio.secondary.smrtserver.models

import fommil.sjs.FamilyFormats

import com.pacbio.secondary.smrtlink.models.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtserver.models.SecondaryModels._
import com.pacbio.secondary.analysis.jobs.SecondaryJobProtocols
import com.pacbio.secondary.analysis.reports.ReportJsonProtocol

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

  // Jobs
  implicit val jobEventRecordFormat = jsonFormat2(JobEventRecord)

  implicit val exportOptions = jsonFormat3(DataSetExportServiceOptions)

  // this is here to break a tie between otherwise-ambiguous implicits;
  // see the spray-json-shapeless documentation
  implicit val llFormat = LogLevelFormat
}

object SecondaryAnalysisJsonProtocols extends SecondaryAnalysisJsonProtocols
