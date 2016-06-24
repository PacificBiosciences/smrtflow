
package com.pacbio.secondary.smrtserver.testkit

import java.nio.file.Path

import scala.collection.immutable.Seq

import spray.json._

import com.pacbio.secondary.smrtserver.models._
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.reports.ReportModels
import com.pacbio.secondary.smrtlink.client._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.common.client._
import com.pacbio.common.models._


object TestkitModels {

  case class EntryPointPath(entryId: String, path: Path)

  abstract class ReportAttributeRule {
    val attrId: String
    val value: Any
    val op: String
  }

  case class ReportAttributeLongRule(attrId: String, value: Long, op: String) extends ReportAttributeRule

  case class ReportAttributeDoubleRule(attrId: String, value: Double, op: String) extends ReportAttributeRule

  case class ReportAttributeStringRule(attrId: String, value: String, op: String = "eq") extends ReportAttributeRule

  case class ReportTestRules(reportId: String, rules: Seq[ReportAttributeRule])

  case class TestkitConfig(
    jobName: String,
    jobType: String,
    description: String,
    pipelineId: Option[String],
    workflowXml: Option[String],
    presetXml: Option[String],
    entryPoints: Seq[EntryPointPath],
    reportTests: Seq[ReportTestRules])

}

trait TestkitJsonProtocol extends SmrtLinkJsonProtocols with SecondaryAnalysisJsonProtocols {

  import TestkitModels._

  implicit val entryPointPathFormat = jsonFormat2(EntryPointPath)
  implicit val reportLongRuleFormat = jsonFormat3(ReportAttributeLongRule)
  implicit val reportDoubleRuleFormat = jsonFormat3(ReportAttributeDoubleRule)
  implicit val reportStringRuleFormat = jsonFormat3(ReportAttributeStringRule)

  implicit object reportAttributeRuleFormat extends JsonFormat[ReportAttributeRule] {
    def write(rar: ReportAttributeRule) = rar match {
      case rlr: ReportAttributeLongRule => rlr.toJson
      case rdr: ReportAttributeDoubleRule => rdr.toJson
      case rsr: ReportAttributeStringRule => rsr.toJson
    }

    def read(jsRule: JsValue): ReportAttributeRule = {
      jsRule.asJsObject.getFields("attrId", "value", "op") match {
        case Seq(JsString(id), JsNumber(value), JsString(op)) => {
          if (value.isValidInt) ReportAttributeLongRule(id, value.toLong, op)
          else ReportAttributeDoubleRule(id, value.toDouble, op)
        }
        case _ => jsRule.asJsObject.getFields("attrId", "value") match {
          case Seq(JsString(id), JsString(value)) => ReportAttributeStringRule(id, value)
          case x => deserializationError(s"Expected attribute rule, got ${x}")
        }
      }
    }
  }

  implicit val reportRulesFormat = jsonFormat2(ReportTestRules)
  implicit val testkitConfigFormat = jsonFormat8(TestkitConfig)
}

//object TestkitJsonProtocol extends TestkitJsonProtocol
