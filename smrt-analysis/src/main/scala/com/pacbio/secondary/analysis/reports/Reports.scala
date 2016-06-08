package com.pacbio.secondary.analysis.reports

import com.pacbio.common.models.UUIDJsonProtocol

import java.io.{FileWriter, BufferedWriter, File}
import java.nio.file.{Path, Files}

import org.apache.commons.io.FileUtils
import spray.json._
import java.util.UUID
import DefaultJsonProtocol._


/**
 * Need to figure out how to handle different Types
 */
object ReportModels {

  val REPORT_MODEL_VERSION = "0.3.0"
  val DEFAULT_VERSION = "UNKNOWN"

  abstract class ReportAttribute

  case class ReportLongAttribute(id: String, name: String, value: Long) extends ReportAttribute

  case class ReportStrAttribute(id: String, name: String, value: String) extends ReportAttribute

  case class ReportDoubleAttribute(id: String, name: String, value: Double) extends ReportAttribute

  case class ReportPlot(id: String, image: String, caption: Option[String] = None)

  case class ReportTable(id: String, title: Option[String] = None, columns: Seq[ReportTableColumn])

  case class ReportTableColumn(id: String, header: Option[String] = None, values: Seq[Any])

  case class ReportPlotGroup(
      id: String,
      title: Option[String] = None,
      legend: Option[String] = None,
      plots: List[ReportPlot])

  case class Report(
      id: String,
      version: String = DEFAULT_VERSION,
      attributes: List[ReportAttribute],
      plotGroups: List[ReportPlotGroup],
      tables: List[ReportTable],
      uuid: Option[UUID] = None,
      datasetUuids: List[UUID] = List[UUID]())

}


trait ReportJsonProtocol extends DefaultJsonProtocol with UUIDJsonProtocol {

  import ReportModels._

  implicit val reportLongAttributeFormat = jsonFormat3(ReportLongAttribute)
  implicit val reportStrAttributeFormat = jsonFormat3(ReportStrAttribute)
  implicit val reportDoubleAttributeFormat = jsonFormat3(ReportDoubleAttribute)
  implicit object reportAttributeFormat extends JsonFormat[ReportAttribute] {
    def write(ra: ReportAttribute) = ra match {
      case rla: ReportLongAttribute => rla.toJson
      case rsa: ReportStrAttribute => rsa.toJson
      case rda: ReportDoubleAttribute => rda.toJson
    }

    def read(jsonAttr: JsValue): ReportAttribute = {
      jsonAttr.asJsObject.getFields("id", "name", "value") match {
        case Seq(JsString(id), JsString(name), JsNumber(value)) => {
          if (value.isValidInt) ReportLongAttribute(id, name, value.toLong)
          else ReportDoubleAttribute(id, name, value.toDouble)
        }
        case Seq(JsString(id), JsString(name), JsString(value)) =>
          ReportStrAttribute(id, name, value.toString)
      }
    }
  }

  implicit val reportPlotGroupFormat = jsonFormat3(ReportPlot)
  implicit val reportPlotGroupsFormat = jsonFormat4(ReportPlotGroup)
  //implicit val reportColumnFormat = jsonFormat3(ReportTableColumn)
  implicit object reportColumnFormat extends JsonFormat[ReportTableColumn] {
    def write(c: ReportTableColumn): JsObject = {
      JsObject(
        "id" -> JsString(c.id),
        "header" -> c.header.toJson,
        "values" -> JsArray(c.values.map((v) => v match {
              case x: Double => JsNumber(x)
              case i: Int => JsNumber(i)
              case s: String => JsString(s)
              case _ => JsNull
          }).toList
        )
      )
    }

    def read(jsColumn: JsValue):  ReportTableColumn = {
      val jsObj = jsColumn.asJsObject
      jsObj.getFields("id", "values") match {
        case Seq(JsString(id), JsArray(jsValues)) => {
          val header = jsObj.getFields("header") match {
            case Seq(JsString(h)) => Some(h)
            case _ => None
          }
          val values = (for (value <- jsValues) yield value match {
            case JsNumber(x) => x.doubleValue
            case JsString(s) => s
            case _ => null
          }).toList
          ReportTableColumn(id, header, values)
        }
        case x => deserializationError(s"Expected Column, got ${x}")
      }
    }
  }
  implicit val reportTableFormat = jsonFormat3(ReportTable)
  //implicit val reportFormat = jsonFormat5(Report)
  // FIXME(nechols)(2016-06-06) this is basically a giant hack to allow the
  // 'version' field to be optional, but we should probably fix this on the
  // pbcommand side as well
  implicit object reportFormat extends RootJsonFormat[Report] {
    def write(r: Report): JsObject = {
      JsObject(
        "id" -> JsString(r.id),
        "version" -> JsString(r.version),
        "attributes" -> r.attributes.toJson,
        "plotGroups" -> r.plotGroups.toJson,
        "tables" -> r.tables.toJson,
        "uuid" -> r.uuid.toJson,
        "dataset_uuids" -> r.datasetUuids.toJson)
    }
    def read(value: JsValue) = {
      val jsObj = value.asJsObject
      jsObj.getFields("id", "attributes", "plotGroups", "tables") match {
        case Seq(JsString(id), JsArray(jsAttr), JsArray(jsPlotGroups), JsArray(jsTables)) => {
          val version = jsObj.getFields("version") match {
            case Seq(JsString(v)) => v
            // fallback to support pbcommand model
            case _ => jsObj.getFields("_version") match {
              case Seq(JsString(v)) => v
              case _ => DEFAULT_VERSION
            }
          }
          val uuid = jsObj.getFields("uuid") match {
            case Seq(JsString(u)) => Some(UUID.fromString(u))
            case _ => None
          }
          val datasetUuids = jsObj.getFields("dataset_uuids") match {
            case Seq(JsArray(uuids)) => uuids.map(_.convertTo[UUID]).toList
            case _ => List[UUID]()
          }
          val attributes = jsAttr.map(_.convertTo[ReportAttribute]).toList
          val plotGroups = jsPlotGroups.map(_.convertTo[ReportPlotGroup]).toList
          val tables = jsTables.map(_.convertTo[ReportTable]).toList
          Report(id, version, attributes, plotGroups, tables, uuid,
                 datasetUuids)
        }
        case x => deserializationError(s"Expected Report, got ${x}")
      }
    }
  }

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
    ReportTable("report_table", Some("report title"), Seq[ReportTableColumn](
      ReportTableColumn("col1", Some("Column 1"), Seq[Any](null, 10, 5.931, "asdf"))))
  }

  /**
   * Generate a pbreport-eseque task/jobOptions report of the execution
   */
  def toMockTaskReport(reportId: String): Report = {
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
    def toP(n: Int, id: String) = ReportPlot(toI(id), s"$id.png", Some(s"Caption $id"))
    def toPg(id: String, plots: List[ReportPlot]) = ReportPlotGroup(toI(id), Some(s"title $id"), Some(s"legend $id"), plots)

    val rid = s"pbsmrtpipe.reports.$baseId"
    val rversion = "0.2.1"
    val nattrs = 10

    val nplots = 3
    val plots = (1 until nplots).map(x => toP(x, toI(s"plot_$x")))
    val plotGroup = toPg(toI("plotgroups"), plots.toList)
    val attrs = (1 until nattrs).map(n => toA(n, s"attr$n"))
    Report(rid, rversion, attrs.toList, List(plotGroup), List(toReportTable),
           Some(UUID.randomUUID), List[UUID](UUID.randomUUID))
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
