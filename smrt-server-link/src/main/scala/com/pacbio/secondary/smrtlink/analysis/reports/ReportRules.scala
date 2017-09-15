package com.pacbio.secondary.smrtlink.analysis.reports

import java.io.File
import java.nio.file.{Path, Files}

import spray.json._
import DefaultJsonProtocol._

object ReportRulesModels {
  // this also has value-format
  // id is the report id
  case class AttributeRule(id: String, name: String)
  case class AttributeRules(defaultView: String,
                            order: Boolean,
                            rules: List[AttributeRule])
  // Id of the report
  case class TableRule(id: String, hidden: Boolean)
  case class TableRules(id: String, rules: List[TableRule])
  case class ColumnRule(id: String, valueFormat: String)

  case class PlotRule(id: String, hidden: Boolean)
  case class PlotGroupRule(id: String, rules: List[PlotRule])

  case class ReportView(inputs: List[String],
                        attributeRules: List[AttributeRule],
                        plotGroupRules: List[PlotGroupRule])

  def parseXML(f: File): Boolean = {
    val root = scala.xml.XML.loadFile(f)

    // List of paths
    val inputs = (root \ "inputs").map(x => x.text)
    val order = (root \ "order" \ "block").map(x => x.text)
    val defaultView = (root \ "attributesRules" \ "@defaultView").text

    // Attribute Rules
    val attrRules = (root \ "attributesRules" \ "attributeRules").map(x =>
      (x \ "@id", x \ "@name"))

    // Table Rules
    val tRules =
      (root \ "tablesRules" \ "tableRules").map(x => (x \ "@id", x \ "@name"))
    val cRules = (root \ "tablesRules" \ "tableRules" \ "columnRules").map(x =>
      (x \ "@id", x \ "valueFormat" \ "@type"))

    // Plot Groups Rules
    val pgRules = (root \ "plotGroupsRules" \ "plotGroupRules").map(x =>
      (x \ "@id", x \ "@hidden"))
    true
  }

}
