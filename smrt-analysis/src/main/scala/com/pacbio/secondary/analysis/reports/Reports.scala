package com.pacbio.secondary.analysis.reports

import com.pacbio.common.models.UUIDJsonProtocol

import java.util.UUID
import java.nio.file.Path
import scala.io.Source

import org.apache.commons.io.FileUtils

import spray.json._
import DefaultJsonProtocol._


object ReportModels {

  // This is the version of the report data model schema
  final val REPORT_SCHEMA_VERSION = "1.0.0"

  sealed trait ReportAttribute {
    val id: String
    val name: String
    val value: Any
  }

  case class ReportLongAttribute(id: String, name: String, value: Long) extends ReportAttribute

  case class ReportStrAttribute(id: String, name: String, value: String) extends ReportAttribute

  case class ReportDoubleAttribute(id: String, name: String, value: Double) extends ReportAttribute

  case class ReportBooleanAttribute(id: String, name: String, value: Boolean) extends ReportAttribute

  case class ReportPlot(id: String, image: String, caption: Option[String] = None)

  case class ReportTable(id: String, title: Option[String] = None, columns: Seq[ReportTableColumn])

  case class ReportTableColumn(id: String, header: Option[String] = None, values: Seq[Any])

  case class ReportPlotGroup(
      id: String,
      title: Option[String] = None,
      legend: Option[String] = None,
      plots: List[ReportPlot])

  // Tools outside of pbreports are generating reports, so tools should use a report id scheme
  // of {tool}_{report_base_id} such as smrtflow_examplereport as the base id

  /**
    * PacBio Report data model
    *
    * @param id           Report Id use smrtflow_{my_report} format. [a-z0-9_] must begin with a-z
    * @param title        Display name of the report
    * @param version      Report Schema version
    * @param attributes   Report Attributes
    * @param plotGroups   Report PlotGroups
    * @param tables       Report Tables
    * @param uuid         Report UUID If `uuid` is not present in the JSON, a random value will be generated
    * @param datasetUuids Dataset UUIDs used to generate the reports
    */
  case class Report(
      id: String,
      title: String,
      version: String = REPORT_SCHEMA_VERSION,
      attributes: List[ReportAttribute],
      plotGroups: List[ReportPlotGroup],
      tables: List[ReportTable],
      uuid: UUID,
      datasetUuids: Set[UUID] = Set.empty[UUID]) {

    def getAttributeValue(attrId: String): Option[Any] =
      attributes.map(a => (a.id, a.value)).toMap.get(attrId)
    def getAttributeLongValue(attrId: String): Option[Long] = {
      getAttributeValue(attrId) match {
        case Some(x) => Some(x.asInstanceOf[Long])
        case _ => None
      }
    }
    def getAttributeDoubleValue(attrId: String): Option[Double] = {
      getAttributeValue(attrId) match {
        case Some(x) => Some(x.asInstanceOf[Double])
        case _ => None
      }
    }

    def getPlot(plotGroupId: String, plotId: String): Option[ReportPlot] = {
      for (plotGroup <- plotGroups) {
        if (plotGroup.id == plotGroupId) {
          return plotGroup.plots.map(p => (p.id, p)).toMap.get(plotId)
        }
      }
      None
    }
  }

  /**
    * Report and corresponding Path to the Json file
    *
    * @param report PacBio Report
    * @param path JSON file
    */
  case class ReportIO(report: Report, path: Path)

}


/**
  * Report Serialization Utils
  */
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
      case rba: ReportBooleanAttribute => rba.toJson
    }

    def read(jsonAttr: JsValue): ReportAttribute = {
      jsonAttr.asJsObject.getFields("id", "name", "value") match {
        case Seq(JsString(id), JsString(name), JsNumber(value)) => {
          if (value.isValidInt) ReportLongAttribute(id, name, value.toLong)
          else ReportDoubleAttribute(id, name, value.toDouble)
        }
        case Seq(JsString(id), JsString(name), JsBoolean(value)) =>
          ReportBooleanAttribute(id, name, value)
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
              case f: Float => JsNumber(f)
              case i: Int => JsNumber(i)
              case l: Long => JsNumber(l)
              case s: String => JsString(s)
              case b: Boolean => JsBoolean(b)
              case _ => JsNull
          }).toList:_* // expand sequence
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
            case JsBoolean(b) => b
            case _ => null
          }).toList
          ReportTableColumn(id, header, values)
        }
        case x => deserializationError(s"Expected Column, got $x")
      }
    }
  }
  implicit val reportTableFormat = jsonFormat3(ReportTable)

  /**
    * This is manually rolled value to allow different schema versions
    * However, the code as of 7/6/2016 should generate reports that are
    * compliant with the 1.0.0 schema spec
    *
    **/
  implicit object reportFormat extends RootJsonFormat[Report] {
    def write(r: Report): JsObject = {
      JsObject(
        "id" -> JsString(r.id),
        "title" -> JsString(r.title),
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
              case _ => REPORT_SCHEMA_VERSION
            }
          }

          // If the UUID is not preset in the report JSON,
          // a random value will be generated
          val uuid = jsObj.getFields("uuid") match {
            case Seq(JsString(u)) => UUID.fromString(u)
            case _ => UUID.randomUUID()
          }
          val datasetUuids = jsObj.getFields("dataset_uuids") match {
            case Seq(JsArray(uuids)) => uuids.map(_.convertTo[UUID]).toSet
            case _ => Set.empty[UUID]
          }

          // Generate a title from the Report Id
          val title = jsObj.getFields("title") match {
            case Seq(JsString(t)) => t
            case _ => s"Report $id"
          }

          val attributes = jsAttr.map(_.convertTo[ReportAttribute]).toList
          val plotGroups = jsPlotGroups.map(_.convertTo[ReportPlotGroup]).toList
          val tables = jsTables.map(_.convertTo[ReportTable]).toList
          Report(id, title, version, attributes, plotGroups, tables, uuid, datasetUuids)
        }
        case x => deserializationError(s"Expected Report, got $x")
      }
    }
  }

}

object ReportUtils extends ReportJsonProtocol {

  import ReportModels._

  private def toReportTable: ReportTable = {

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
    * Generates a pbsmrtpipe-eseque Task Report of host, runtime, etc...
    * This needs to be refactored into a concrete model and pbsmrtpipe should emit this
    * same model.
    *
    * @param reportId PacBio Report Id
    * @param title    Display name of Report
    * @return
    */
  def toMockTaskReport(reportId: String, title: String): Report = {
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
    Report("workflow_task", title, REPORT_SCHEMA_VERSION, attrs, plots, tables, UUID.randomUUID())
  }

  /**
    * Generate an example Report with a Few Attributes and PlotReports
    *
    * @param baseId pbreport style report id
    * @return Report
    */
  def mockReport(baseId: String, title: String): Report = {

    def toI(n: String) = s"$baseId.$n"
    def toP(n: Int, id: String) = ReportPlot(toI(id), s"$id.png", Some(s"Caption $id"))
    def toPg(id: String, plots: List[ReportPlot]) = ReportPlotGroup(toI(id), Some(s"title $id"), Some(s"legend $id"), plots)

    // Now that reports are generated outside of pbreports, the id format needs to be expanded.
    // the format should evolve to have the base id of {tool}.reports.{base-id}
    //val rid = s"pbsmrtpipe.reports.$baseId"

    val nplots = 3

    val plots = (1 until nplots).map(x => toP(x, toI(s"plot_$x")))
    val plotGroup = toPg(toI("plotgroups"), plots.toList)

    val attrs = Seq(
      ReportLongAttribute("nfiles", "Number of Files ", 7),
      ReportDoubleAttribute("run_time", "Run Time", 123.4),
      ReportStrAttribute("job_name", "Job Name", "Report test"),
      ReportBooleanAttribute("was_successful", "Job Succeeded", true))

    Report(baseId, title, REPORT_SCHEMA_VERSION, attrs.toList, List(plotGroup), List(toReportTable), UUID.randomUUID)
  }

  /**
    * Write a Report to a JSON file
    *
    * @param report PacBio Report
    * @param path   output path to JSON report
    * @return
    */
  def writeReport(report: Report, path: Path): Report = {
    FileUtils.writeStringToFile(path.toFile, report.toJson.prettyPrint)
    report
  }

  /**
    * Load a Report from Path
    *
    * @param path Path to Report JSON file
    * @return
    */
  def loadReport(path: Path): Report =
    Source.fromFile(path.toFile).getLines.mkString.parseJson.convertTo[Report]


}
