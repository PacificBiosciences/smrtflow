package com.pacbio.common.models

import com.pacbio.common.time.PacBioDateTimeFormat
import com.pacbio.secondary.analysis.engine.CommonMessages.MessageResponse
import org.joda.time.{DateTime => JodaDateTime}
import spray.json._
import fommil.sjs.FamilyFormats

import scala.concurrent.duration.Duration

trait JodaDateTimeProtocol extends DefaultJsonProtocol with FamilyFormats {
  import PacBioDateTimeFormat.DATE_TIME_FORMAT

  implicit object JodaDateTimeFormat extends JsonFormat[JodaDateTime] {
    def write(obj: JodaDateTime): JsValue = JsString(obj.toString(DATE_TIME_FORMAT))


    def read(json: JsValue): JodaDateTime = json match {
      case JsString(x) => JodaDateTime.parse(x, DATE_TIME_FORMAT)
      case _ => deserializationError("Expected DateTime as JsString")
    }
  }
}

trait AlarmProtocols extends DefaultJsonProtocol with FamilyFormats {

  implicit object AlarmSeverityFormat extends JsonFormat[AlarmSeverity.AlarmSeverity] {
    def write(obj: AlarmSeverity.AlarmSeverity): JsValue = JsString(obj.toString)

    def read(json: JsValue): AlarmSeverity.AlarmSeverity = json match {
      case JsString(x) => AlarmSeverity.alarmSeverityByName(x)
      case _ => deserializationError("Expected AlarmSeverity type as JsString")
    }
  }

  implicit object MetricTypeFormat extends JsonFormat[MetricType.MetricType] {
    def write(obj: MetricType.MetricType): JsValue = JsString(obj.toString)

    def read(json: JsValue): MetricType.MetricType = json match {
      case JsString(x) =>
        MetricType.ALL
          .find(_.toString == x.toUpperCase)
          .getOrElse(deserializationError(s"Could not find MetricType named $x"))
      case _ => deserializationError("Expected AlarmSeverity type as JsString")
    }
  }
}

trait LogLevelProtocol extends DefaultJsonProtocol with FamilyFormats {

  implicit object LogLevelFormat extends JsonFormat[LogLevel.LogLevel] {
    def write(obj: LogLevel.LogLevel): JsValue = JsString(obj.toString)

    def read(json: JsValue): LogLevel.LogLevel = json match {
      case JsString(x) => LogLevel.logLevelByName(x)
      case _ => deserializationError("Expected LogLevel type as JsString")
    }
  }
}

trait CleanupFrequencyProtocol extends DefaultJsonProtocol with FamilyFormats {

  implicit object CleanupFrequencyProtocol extends JsonFormat[CleanupFrequency.CleanupFrequency] {
    def write(obj: CleanupFrequency.CleanupFrequency): JsValue = JsString(obj.toString)

    def read(json: JsValue): CleanupFrequency.CleanupFrequency = json match {
      case JsString(x) => CleanupFrequency.cleanupFrequencyByName(x)
      case _ => deserializationError("Expected CleanupFrequency type as JsString")
    }
  }
}

trait CleanupSizeProtocol extends DefaultJsonProtocol with FamilyFormats {

  implicit object CleanupSizeProtocol extends JsonFormat[CleanupSize] {
    def write(obj: CleanupSize): JsValue = JsString(obj.toString)

    def read(json: JsValue): CleanupSize = json match {
      case JsString(x) =>
        try { CleanupSize(x) }
        catch {
          case e: RuntimeException => deserializationError(e.getMessage, e)
        }
      case _ => deserializationError("Expected CleanupSizeUnit type as JsString")
    }
  }
}

trait DurationProtocol extends DefaultJsonProtocol with FamilyFormats {

  implicit object DurationProtocol extends JsonFormat[Duration] {
    def write(obj: Duration): JsValue = JsString(obj.toString)

    def read(json: JsValue): Duration = json match {
      case JsString(x) => Duration(x)
      case _ => deserializationError("Expected Duration type as JsString")
    }
  }
}

// Requires custom JSON serialization because of recursive structure
trait DirectoryResourceProtocol extends DefaultJsonProtocol {
  this: BaseJsonProtocol =>

  implicit object DirectoryResourceFormat extends RootJsonFormat[DirectoryResource] {
    def write(obj: DirectoryResource) = JsObject(
        "fullPath" -> JsString(obj.fullPath),
        "subDirectories" -> JsArray(obj.subDirectories.map(this.write):_*),
        "files" -> JsArray(obj.files.map(pbFileResourceFormat.write):_*)
    )

    def read(value: JsValue): DirectoryResource = {
      value.asJsObject.getFields("fullPath", "subDirectories", "files", "lazyLoaded") match {
        case Seq(JsString(fullPath), JsArray(subDirectories), JsArray(files)) =>
          DirectoryResource(fullPath, subDirectories.toSeq.map(this.read), files.toSeq.map(pbFileResourceFormat.read))
        case _ => deserializationError("Expected DirectoryResource fields: fullPath, subDirectories, files")
      }
    }
  }
}

trait BaseJsonProtocol extends DefaultJsonProtocol
with FamilyFormats
with UUIDJsonProtocol
with JodaDateTimeProtocol
with AlarmProtocols
with LogLevelProtocol
with CleanupFrequencyProtocol
with CleanupSizeProtocol
with DurationProtocol
with DirectoryResourceProtocol
{
  implicit val pbThrowableResponseFormat = jsonFormat3(ThrowableResponse)
  implicit val pbComponentFormat = jsonFormat5(PacBioComponentManifest)
  implicit val pbServiceConfigFormat = jsonFormat2(ServerConfig)
  implicit val pbServiceComponentFormat = jsonFormat3(ServiceComponent)
  implicit val pbServiceStatusFormat = jsonFormat6(ServiceStatus)
  implicit val pbAlarmMetricCreateMessageFormat = jsonFormat7(AlarmMetricCreateMessage)
  implicit val pbAlarmMetricFormat = jsonFormat11(AlarmMetric)
  implicit val pbAlarmMetricUpdateMessageFormat = jsonFormat3(AlarmMetricUpdateMessage)
  implicit val pbAlarmMetricUpdateFormat = jsonFormat5(AlarmMetricUpdate)
  implicit val pbLogResourceRecordFormat = jsonFormat3(LogResourceRecord)
  implicit val pbLogResourceFormat = jsonFormat4(LogResource)
  implicit val pbLogMessageRecordFormat = jsonFormat3(LogMessageRecord)
  implicit val pbLogMessageFormat = jsonFormat5(LogMessage)
  implicit val pbUserRecordFormat = jsonFormat4(UserRecord)
  implicit val pbConfigEntryFormat = jsonFormat2(ConfigEntry)
  implicit val pbConfigResponseFormat = jsonFormat2(ConfigResponse)
  implicit val pbApiCleanupJobCreateFormat = jsonFormat7(ApiCleanupJobCreate)
  implicit val pbCleanupJobResponseFormat = jsonFormat8(CleanupJobResponse)
  implicit val pbFileResourceFormat = jsonFormat5(FileResource)
  implicit val pbDiskSpaceResourceFormat = jsonFormat5(DiskSpaceResource)
  implicit val subSystemResourceFormat = jsonFormat8(SubsystemResource)
  implicit val subSystemResourceRecordFormat = jsonFormat5(SubsystemResourceRecord)
  implicit val subSystemConfigFormat = jsonFormat3(SubsystemConfig)
  implicit val pbMessageResponseFormat = jsonFormat1(MessageResponse)
}


object PacBioJsonProtocol extends BaseJsonProtocol {

}
