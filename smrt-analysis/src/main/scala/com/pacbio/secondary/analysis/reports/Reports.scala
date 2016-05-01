package com.pacbio.secondary.analysis.reports

import java.io.{FileWriter, BufferedWriter, File}
import java.nio.file.{Path, Files}

import org.apache.commons.io.FileUtils
import spray.json._
import DefaultJsonProtocol._


/**
 * Need to figure out how to handle different Types
 */
object ReportModels {

  val REPORT_MODEL_VERSION = "0.3.0"

  abstract class ReportAttribute

  case class ReportLongAttribute(id: String, name: String, value: Long) extends ReportAttribute

  case class ReportStrAttribute(id: String, name: String, value: String) extends ReportAttribute

  case class ReportPlot(id: String, image: String, caption: String)

  case class ReportTable(id: String, title: String, columns: JsArray)

  case class ReportPlotGroup(id: String,
                             title: String,
                             legend: String,
                             plots: List[ReportPlot])

  case class Report(id: String,
                    version: String,
                    attributes: List[ReportAttribute],
                    plotGroups: List[ReportPlotGroup],
                    tables: List[ReportTable])

}


trait ReportJsonProtocol extends DefaultJsonProtocol {

  import ReportModels._

  implicit val reportLongAttributeFormat = jsonFormat3(ReportLongAttribute)
  implicit val reportStrAttributeFormat = jsonFormat3(ReportStrAttribute)
  implicit object reportAttributeFormat extends JsonFormat[ReportAttribute] {
    def write(ra: ReportAttribute) = ra match {
      case rla: ReportLongAttribute => rla.toJson
      case rsa: ReportStrAttribute => rsa.toJson
    }

    def read(jsonAttr: JsValue): ReportAttribute = {
      jsonAttr.asJsObject.getFields("id", "name", "value") match {
        case Seq(JsString(id), JsString(name), JsNumber(value)) =>
            ReportLongAttribute(id, name, value.toLong)
        case Seq(JsString(id), JsString(name), JsString(value)) =>
            ReportStrAttribute(id, name, value.toString)
      }
    }
  }

  implicit val reportPlotGroupFormat = jsonFormat3(ReportPlot)
  implicit val reportPlotGroupsFormat = jsonFormat4(ReportPlotGroup)
  implicit val reportTableFormat = jsonFormat3(ReportTable)
  implicit val reportFormat = jsonFormat5(Report)

}

object MockReportUtils extends ReportJsonProtocol {

  import ReportModels._

  def toReportTable: ReportTable = {

    def toC(id: String, nvalues: Int) = JsObject(Map(
      "id" -> JsString(id),
      "header" -> JsString(s"header $id"),
      "values" -> JsArray((0 until nvalues).map(x => JsNumber(x * 2)).toVector)
    ))

    val nvalues = 10
    val ncolumns = 5
    val cs = (1 until ncolumns).map(x => toC(s"column_$x", nvalues))
    ReportTable("report_table", "report title", JsArray(cs.toVector))
  }

  /*
  Generate a pbreport-eseque task/jobOptions report of the execution
   */
  def toMockTaskReport(reportId: String) = {
    def toI(x: String) = s"$reportId.$x"
    def toRa(i: String, n: String, v: Int) = ReportLongAttribute(i, n, v)
    val xs = Seq(("host", "Host ", 1234),
      ("task_id", "pbscala.pipelines.mock_dev", 1234),
      ("run_time", "Run Time", 1234), ("exit_code", "Exit code", 0),
      ("error_msg", "Error Message", 1234),
      ("warning_msg", "Warning Message", -1))
    val attrs = xs.map(x => toRa(toI(x._1), x._2, x._3)).toList
    val plots = List[ReportPlotGroup]()
    val tables = List[ReportTable]()
    Report("workflow_task", REPORT_MODEL_VERSION, attrs, plots, tables)
  }

  /**
   * Generate an example Report with a Few Attributes and PlotReports
   *
   * @param baseId pbreport style report id
   * @return Report
   */
  def mockReport(baseId: String): Report = {

    def toI(n: String) = s"$baseId.$n"
    def toA(n: Int, id: String) = ReportLongAttribute(toI(id), s"name $id", n)
    def toP(n: Int, id: String) = ReportPlot(toI(id), s"$id.png", s"Caption $id")
    def toPg(id: String, plots: List[ReportPlot]) = ReportPlotGroup(toI(id), s"title $id", s"legend $id", plots)

    val rid = s"pbsmrtpipe.reports.$baseId"
    val rversion = "0.2.1"
    val nattrs = 10

    val nplots = 3
    val plots = (1 until nplots).map(x => toP(x, toI(s"plot_$x")))
    val plotGroup = toPg(toI("plotgroups"), plots.toList)
    val attrs = (1 until nattrs).map(n => toA(n, s"attr$n"))
    Report(rid, rversion, attrs.toList, List(plotGroup), List(toReportTable))
  }

  def writeReport(report: Report, path: Path): Report = {
    FileUtils.writeStringToFile(path.toFile, report.toJson.toString)
    report
  }

  def example: Boolean = {
    val r = mockReport("filter_stats")
    val s = r.toJson
    println(r)
    println("Report JSON")
    println(s.prettyPrint)
    true
  }

}
