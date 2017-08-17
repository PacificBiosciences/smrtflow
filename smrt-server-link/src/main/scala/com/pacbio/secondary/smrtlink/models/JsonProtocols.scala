package com.pacbio.secondary.smrtlink.models

import com.pacbio.common.models._
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.time.PacBioDateTimeFormat
import fommil.sjs.FamilyFormats
import org.joda.time.{DateTime => JodaDateTime}
import spray.json._

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
}

trait LogLevelProtocol extends DefaultJsonProtocol with FamilyFormats {

  implicit object LogLevelFormat extends JsonFormat[LogLevel.LogLevel] {
    def write(obj: LogLevel.LogLevel): JsValue = JsString(obj.toString)

    def read(json: JsValue): LogLevel.LogLevel = json match {
      case JsString(x) => LogLevel.logLevelByName(x.toLowerCase)
      case _ => deserializationError("Expected LogLevel type as JsString")
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
with DurationProtocol
with DirectoryResourceProtocol
{
  implicit val pbThrowableResponseFormat = jsonFormat3(ThrowableResponse)
  implicit val pbComponentFormat = jsonFormat5(PacBioComponentManifest)
  implicit val pbServiceConfigFormat = jsonFormat2(ServerConfig)
  implicit val pbServiceComponentFormat = jsonFormat3(ServiceComponent)
  implicit val pbServiceStatusFormat = jsonFormat6(ServiceStatus)
  implicit val pbAlarmFormat = jsonFormat3(Alarm)
  implicit val pbAlarmStatusFormat = jsonFormat5(AlarmStatus)
  implicit val pbLogResourceRecordFormat = jsonFormat3(LogResourceRecord)
  implicit val pbLogResourceFormat = jsonFormat4(LogResource)
  implicit val pbLogMessageRecordFormat = jsonFormat3(LogMessageRecord)
  implicit val pbLogMessageFormat = jsonFormat5(LogMessage)
  implicit val pbUserRecordFormat = jsonFormat5(UserRecord)
  implicit val pbFileResourceFormat = jsonFormat5(FileResource)
  implicit val pbDiskSpaceResourceFormat = jsonFormat3(DiskSpaceResource)
  implicit val subSystemResourceFormat = jsonFormat8(SubsystemResource)
  implicit val subSystemResourceRecordFormat = jsonFormat5(SubsystemResourceRecord)
  implicit val subSystemConfigFormat = jsonFormat3(SubsystemConfig)
  implicit val pbMessageResponseFormat = jsonFormat1(MessageResponse)
}


object PacBioJsonProtocol extends BaseJsonProtocol {

}
