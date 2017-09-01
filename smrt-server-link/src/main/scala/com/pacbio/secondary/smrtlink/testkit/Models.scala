
package com.pacbio.secondary.smrtlink.testkit

import java.nio.file.{Path, Paths}

import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.models._
import spray.json._

import scala.collection.immutable.Seq


object TestkitModels {

  case class EntryPointPath(entryId: String, path: Path)

  abstract class ReportAttributeRule {
    val attrId: String
    val value: Any
    val op: String
  }

  case class ReportAttributeLongRule(attrId: String, value: Long, op: String) extends ReportAttributeRule

  case class ReportAttributeDoubleRule(attrId: String, value: Double, op: String) extends ReportAttributeRule

  case class ReportAttributeBooleanRule(attrId: String, value: Boolean, op: String) extends ReportAttributeRule

  case class ReportAttributeStringRule(attrId: String, value: String, op: String = "eq") extends ReportAttributeRule

  case class ReportTestRules(reportId: String, rules: Seq[ReportAttributeRule])

  case class TestkitConfig(
    testId: String,
    jobType: String,
    description: String,
    pipelineId: Option[String],
    workflowXml: Option[String],
    presetXml: Option[String],
    presetJson: Option[String],
    entryPoints: Seq[EntryPointPath],
    reportTests: Seq[ReportTestRules])

}

trait TestkitJsonProtocol extends SmrtLinkJsonProtocols {

  import TestkitModels._

  implicit val entryPointPathFormat = jsonFormat2(EntryPointPath)
  implicit val reportLongRuleFormat = jsonFormat3(ReportAttributeLongRule)
  implicit val reportDoubleRuleFormat = jsonFormat3(ReportAttributeDoubleRule)
  implicit val reportBooleanRuleFormat = jsonFormat3(ReportAttributeBooleanRule)
  implicit val reportStringRuleFormat = jsonFormat3(ReportAttributeStringRule)

  implicit object reportAttributeRuleFormat extends JsonFormat[ReportAttributeRule] {
    def write(rar: ReportAttributeRule) = rar match {
      case rlr: ReportAttributeLongRule => rlr.toJson
      case rdr: ReportAttributeDoubleRule => rdr.toJson
      case rbr: ReportAttributeBooleanRule => rbr.toJson
      case rsr: ReportAttributeStringRule => rsr.toJson
    }

    def read(jsRule: JsValue): ReportAttributeRule = {
      jsRule.asJsObject.getFields("attrId", "value", "op") match {
        case Seq(JsString(id), JsNumber(value), JsString(op)) => {
          if (value.isValidInt) ReportAttributeLongRule(id, value.toLong, op)
          else ReportAttributeDoubleRule(id, value.toDouble, op)
        }
        case Seq(JsString(id), JsBoolean(value), JsString(op)) =>
          ReportAttributeBooleanRule(id, value, op)
        case _ => jsRule.asJsObject.getFields("attrId", "value") match {
          case Seq(JsString(id), JsString(value)) => ReportAttributeStringRule(id, value)
          case x => deserializationError(s"Expected attribute rule, got ${x}")
        }
      }
    }
  }

  /*
   * We could just use jsonFormat2 for ReportTestRules, but this code allows
   * us to use a shorthand for specifying expected attribute values, e.g.:
   *
   *   "rules": {
   *     "alpha": 1234,
   *     "beta__ge": 0.5678
   *   }
   *
   * instead of the more literal format (which can still be used, but not at
   * the same time).
   */
  implicit object reportRulesFormat extends JsonFormat[ReportTestRules] {
    def write(rtr: ReportTestRules) = JsObject(
      "reportId" -> JsString(rtr.reportId),
      "rules" -> rtr.rules.toJson)

    def read(jsValue: JsValue): ReportTestRules = {
      jsValue.asJsObject.getFields("reportId", "rules") match {
        case Seq(JsString(rptId), JsArray(rules)) =>
          ReportTestRules(rptId, rules.map(_.convertTo[ReportAttributeRule]))
        case Seq(JsString(rptId), JsObject(rules)) => {
          ReportTestRules(rptId, (for ((k,v) <- rules) yield {
            val keyFields = k.split("__")
            val id = keyFields(0)
            val op = if (keyFields.size == 2) keyFields(1) else "eq"
            v match {
              case JsNumber(nv) => {
                if (nv.isValidInt) ReportAttributeLongRule(id, nv.toLong, op)
                else ReportAttributeDoubleRule(id, nv.toDouble, op)
              }
              case JsBoolean(bv) => ReportAttributeBooleanRule(id, bv, op)
              case JsString(sv) => ReportAttributeStringRule(id, sv, op)
              case x => deserializationError(s"Expected report attribute rule, got ${x}")
            }
          }).toList)
        }
        case x => deserializationError(s"Expected report test rule, got ${x}")
      }
    }
  }

  implicit val testkitConfigFormat = jsonFormat9(TestkitConfig)
}

object MockConfig extends TestkitJsonProtocol {

  import TestkitModels._

  def makeCfg: TestkitConfig = {
    val entryPoints = Seq(
        EntryPointPath("eid_subread", Paths.get("/path/to/subreadset.xml")),
        EntryPointPath("eid_ref_dataset", Paths.get("/path/to/referenceset.xml")))
      val reportTests = Seq(ReportTestRules(
        "example_report",
        Seq(
          ReportAttributeLongRule("mapped_reads_n", 100, "gt"),
          ReportAttributeDoubleRule("concordance", 0.85, "ge"),
          ReportAttributeStringRule("instrument", "54006"))))
      TestkitConfig(
        "test_job",
        "pbsmrtpipe",
        "example test config",
        Some("pbsmrtpipe.pipelines.sa3_sat"),
        None,
        Some("preset.xml"),
        None,
        entryPoints,
        reportTests)
  }

  def showCfg: Unit = println(makeCfg.toJson.prettyPrint)
}
