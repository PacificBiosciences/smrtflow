package com.pacbio.secondary.smrtlink.analysis.reports

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

  case class ReportLongAttribute(id: String, name: String, value: Long)
      extends ReportAttribute

  case class ReportStrAttribute(id: String, name: String, value: String)
      extends ReportAttribute

  case class ReportDoubleAttribute(id: String, name: String, value: Double)
      extends ReportAttribute

  case class ReportBooleanAttribute(id: String, name: String, value: Boolean)
      extends ReportAttribute

  // This is a bit goofy, but literal "NA" values should be mapped to this data model
  case class ReportNullAttribute(id: String,
                                 name: String,
                                 value: Option[String] = None)
      extends ReportAttribute

  trait ReportPlotType {
    val id: String
  }
  object ReportPlotTypes {
    object IMAGE extends ReportPlotType { val id = "image" }
    object PLOTLY extends ReportPlotType { val id = "plotly" }
  }

  trait ReportPlotBase {
    val id: String
    val image: String
    val caption: Option[String]
    val thumbnail: Option[String]
    def plotType: ReportPlotType
  }

  case class ReportImagePlot(id: String,
                             image: String,
                             caption: Option[String] = None,
                             thumbnail: Option[String])
      extends ReportPlotBase {
    override def plotType = ReportPlotTypes.IMAGE
  }

  case class ReportPlotlyPlot(id: String,
                              image: String,
                              caption: Option[String] = None,
                              thumbnail: Option[String],
                              plotlyVersion: Option[String] = None)
      extends ReportPlotBase {
    override def plotType: ReportPlotType = ReportPlotTypes.PLOTLY
  }

  case class ReportTable(id: String,
                         title: Option[String] = None,
                         columns: Seq[ReportTableColumn])

  case class ReportTableColumn(id: String,
                               header: Option[String] = None,
                               values: Seq[Any])

  case class ReportPlotGroup(id: String,
                             title: Option[String] = None,
                             legend: Option[String] = None,
                             plots: List[ReportPlotBase]) {}

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
  case class Report(id: String,
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
      getAttributeValue(attrId).map(_.asInstanceOf[Long])
    }

    def getAttributeDoubleValue(attrId: String): Option[Double] = {
      getAttributeValue(attrId).map(_.asInstanceOf[Double])
    }

    private def getPlotsByType(plotType: ReportPlotType,
                               plotGroupId: String,
                               plotId: String): Option[ReportPlotBase] = {
      // Adding filtering by type to make sure the casting in
      // callers to specific Report Plot types.
      plotGroups
        .map { pg =>
          val plots = pg.plots.filter(p => p.plotType == plotType)
          pg.copy(plots = plots)
        }
        .find(_.id == plotGroupId)
        .flatMap(_.plots.find(_.id == plotId))
    }

    def getPlot(plotGroupId: String, plotId: String): Option[ReportImagePlot] =
      getPlotsByType(ReportPlotTypes.IMAGE, plotGroupId, plotId)
        .map(_.asInstanceOf[ReportImagePlot])

    def getPlotlyPlot(plotGroupId: String,
                      plotId: String): Option[ReportPlotlyPlot] = {
      getPlotsByType(ReportPlotTypes.PLOTLY, plotGroupId, plotId)
        .map(_.asInstanceOf[ReportPlotlyPlot])
    }

    /**
      * This return all of the column values for a given column id and table id.
      *
      * @param tableId  Report Table Id
      * @param columnId Report Column Id
      * @return
      */
    def getTableValueFromColumn(tableId: String, columnId: String): Seq[Any] = {
      tables
        .find(_.id == tableId)
        .flatMap(_.columns.find(_.id == columnId))
        .map(_.values)
        .getOrElse(Nil)
    }

    /**
      * Returns the first value in the report table column for given table id and column id.
      *
      * @param tableId  Report Table Id
      * @param columnId Report column Id
      * @return
      */
    def getFirstValueFromTableColumn(tableId: String,
                                     columnId: String): Option[Any] =
      getTableValueFromColumn(tableId, columnId).headOption

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
  implicit val reportNullAttributeFormat = jsonFormat3(ReportNullAttribute)

  implicit object reportAttributeFormat extends JsonFormat[ReportAttribute] {
    def write(ra: ReportAttribute) = ra match {
      case rla: ReportLongAttribute => rla.toJson
      case rsa: ReportStrAttribute => rsa.toJson
      case rda: ReportDoubleAttribute => rda.toJson
      case rba: ReportBooleanAttribute => rba.toJson
      case rna: ReportNullAttribute => rna.toJson
    }

    private def defaultReportAttrName(attributeId: String): String =
      s"Attribute Name $attributeId"

    def read(jsonAttr: JsValue): ReportAttribute = {
      val jsonObj = jsonAttr.asJsObject

      def getName(id: String) = jsonObj.getFields("name") match {
        case Seq(JsString(name)) => name
        case Seq(JsNull) => defaultReportAttrName(id)
      }

      jsonAttr.asJsObject.getFields("id", "value") match {
        case Seq(JsString(id), JsNumber(value)) => {
          if (value.isValidInt)
            ReportLongAttribute(id, getName(id), value.toLong)
          else ReportDoubleAttribute(id, getName(id), value.toDouble)
        }
        case Seq(JsString(id), JsBoolean(value)) =>
          ReportBooleanAttribute(id, getName(id), value)
        case Seq(JsString(id), JsString(value)) =>
          ReportStrAttribute(id, getName(id), value.toString)
        case Seq(JsString(id), JsNull) =>
          ReportNullAttribute(id, getName(id))
      }
    }
  }

  private def getOptionalKey(jsObject: JsObject, key: String): Option[String] = {
    jsObject.getFields(key) match {
      case Seq(JsString(v)) => Some(v)
      case _ => None
    }
  }

  implicit object reportPlotImagePlotFormat
      extends JsonFormat[ReportPlotBase] {
    override def write(obj: ReportPlotBase): JsObject = {
      JsObject(
        "id" -> JsString(obj.id),
        "image" -> JsString(obj.image),
        "plotType" -> JsString(obj.plotType.id),
        "caption" -> obj.caption.map(s => JsString(s)).getOrElse(JsNull),
        "thumbnail" -> obj.thumbnail.map(s => JsString(s)).getOrElse(JsNull)
      )
    }

    override def read(json: JsValue): ReportPlotBase = {
      val jsObject = json.asJsObject

      val plotType = getOptionalKey(jsObject, "type") match {
        case Some(ReportPlotTypes.IMAGE.id) => ReportPlotTypes.IMAGE
        case Some(ReportPlotTypes.PLOTLY.id) => ReportPlotTypes.PLOTLY
        case Some(sx) => deserializationError(s"Expected Plot Type, got $sx")
        case _ => ReportPlotTypes.IMAGE
      }

      val (id, image): Tuple2[String, String] =
        jsObject.getFields("id", "image") match {
          case Seq(JsString(plotId), JsString(plotImage)) =>
            (plotId, plotImage)
          case _ => deserializationError("Expected 'id' and 'image' fields")
        }

      val caption = getOptionalKey(jsObject, "caption")
      val thumbnail = getOptionalKey(jsObject, "thumbnail")

      plotType match {
        case ReportPlotTypes.IMAGE =>
          ReportImagePlot(id, image, caption, thumbnail)
        case ReportPlotTypes.PLOTLY =>
          val plotlyVersion = getOptionalKey(jsObject, "plotlyVersion")
          ReportPlotlyPlot(id, image, caption, thumbnail, plotlyVersion)
      }
    }
  }

  //implicit val reportPlotGroupFormat = jsonFormat5(ReportPlot)
  implicit val reportPlotGroupsFormat = jsonFormat4(ReportPlotGroup)

  implicit object reportColumnFormat extends JsonFormat[ReportTableColumn] {
    def write(c: ReportTableColumn): JsObject = {
      JsObject(
        "id" -> JsString(c.id),
        "header" -> c.header.toJson,
        "values" -> JsArray(
          c.values
            .map((v) =>
              v match {
                case x: Double => JsNumber(x)
                case f: Float => JsNumber(f)
                case i: Int => JsNumber(i)
                case l: Long => JsNumber(l)
                case s: String => JsString(s)
                case b: Boolean => JsBoolean(b)
                case _ => JsNull
            })
            .toList: _* // expand sequence
        )
      )
    }

    def read(jsColumn: JsValue): ReportTableColumn = {
      val jsObj = jsColumn.asJsObject

      val header = getOptionalKey(jsObj, "header")

      jsObj.getFields("id", "values") match {
        case Seq(JsString(id), JsArray(jsValues)) => {
          val values = (for (value <- jsValues)
            yield
              value match {
                case JsNumber(x) =>
                  // FIXME. This could be an Int
                  x.doubleValue
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
    override def write(r: Report): JsObject = {
      JsObject(
        "id" -> JsString(r.id),
        "title" -> JsString(r.title),
        "version" -> JsString(r.version),
        "attributes" -> r.attributes.toJson,
        "plotGroups" -> r.plotGroups.toJson,
        "tables" -> r.tables.toJson,
        "uuid" -> r.uuid.toJson,
        "dataset_uuids" -> r.datasetUuids.toJson
      )
    }

    override def read(value: JsValue) = {
      val jsObj = value.asJsObject

      def getOptionalKeyFrom(key: String): Option[String] =
        getOptionalKey(jsObj, key)

      val version: String = Seq("version", "_version")
        .flatMap(getOptionalKeyFrom)
        .headOption
        .getOrElse(REPORT_SCHEMA_VERSION)

      // If the UUID is not preset in the report JSON,
      // a random value will be generated
      val uuid: UUID = getOptionalKeyFrom("uuid")
        .map(UUID.fromString)
        .getOrElse(UUID.randomUUID())

      jsObj.getFields("id", "attributes", "plotGroups", "tables") match {
        case Seq(JsString(id),
                 JsArray(jsAttr),
                 JsArray(jsPlotGroups),
                 JsArray(jsTables)) => {

          val datasetUuids = jsObj.getFields("dataset_uuids") match {
            case Seq(JsArray(uuids)) => uuids.map(_.convertTo[UUID]).toSet
            case _ => Set.empty[UUID]
          }

          val title = getOptionalKeyFrom("title").getOrElse(s"Report $id")

          val attributes = jsAttr.map(_.convertTo[ReportAttribute]).toList
          val plotGroups =
            jsPlotGroups.map(_.convertTo[ReportPlotGroup]).toList
          val tables = jsTables.map(_.convertTo[ReportTable]).toList
          Report(id,
                 title,
                 version,
                 attributes,
                 plotGroups,
                 tables,
                 uuid,
                 datasetUuids)
        }
        case x => deserializationError(s"Expected Report, got $x")
      }
    }
  }

}

object ReportUtils extends ReportJsonProtocol {

  import ReportModels._

  private def toReportTable: ReportTable = {
    // This is very awkward. Each Column should be a single consistent type, e.g., Option[T], not Option[Any]
    // These test values are perhaps not very useful or representative of reports generated in the current system.
    // If this really is the case, then the reports should be tightened up and adhere to a schema.
    val column = ReportTableColumn("col1",
                                   Some("Column 1"),
                                   Seq[Any](10, null, 5.931, "asdf"))
    ReportTable("report_table", Some("report title"), Seq(column))
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
    val xs = Seq(
      ("host", "Host ", 1234),
      ("task_id", "pbscala.pipelines.mock_dev", 1234),
      ("run_time", "Run Time", 1234),
      ("exit_code", "Exit code", 0),
      ("error_msg", "Error Message", 1234),
      ("warning_msg", "Warning Message", -1)
    )
    val attrs = xs.map(x => toRa(toI(x._1), x._2, x._3)).toList
    val plots = List.empty[ReportPlotGroup]
    val tables = List.empty[ReportTable]
    Report("workflow_task",
           title,
           REPORT_SCHEMA_VERSION,
           attrs,
           plots,
           tables,
           UUID.randomUUID())
  }

  /**
    * Generate an example Report with a Few Attributes and PlotReports
    *
    * @param baseId pbreport style report id
    * @return Report
    */
  def mockReport(baseId: String, title: String): Report = {

    def toI(n: String) = s"$baseId.$n"
    def toP(n: Int, id: String) =
      ReportImagePlot(toI(id), s"$id.png", Some(s"Caption $id"), None)
    def toPg(id: String, plots: List[ReportImagePlot]) =
      ReportPlotGroup(toI(id), Some(s"title $id"), Some(s"legend $id"), plots)

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
      ReportBooleanAttribute("was_successful", "Job Succeeded", true)
    )

    Report(baseId,
           title,
           REPORT_SCHEMA_VERSION,
           attrs.toList,
           List(plotGroup),
           List(toReportTable),
           UUID.randomUUID)
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
    FileUtils
      .readFileToString(path.toFile, "UTF-8")
      .parseJson
      .convertTo[Report]

}
