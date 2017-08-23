package com.pacbio.common.models

import java.net.{URI, URL}
import java.nio.file.{Path, Paths}
import java.util.UUID

import org.joda.time.{DateTime => JodaDateTime}
import spray.json._
import fommil.sjs.FamilyFormats

trait UUIDJsonProtocol extends DefaultJsonProtocol with FamilyFormats {
  implicit object UUIDFormat extends JsonFormat[UUID] {
    def write(obj: UUID): JsValue = JsString(obj.toString)

    def read(json: JsValue): UUID = json match {
      case JsString(x) => UUID.fromString(x)
      case _ => deserializationError("Expected UUID as JsString")
    }
  }
}

trait IdAbleJsonProtocol extends DefaultJsonProtocol with FamilyFormats{
  import CommonModels._

  implicit object IdAbleFormat extends JsonFormat[IdAble] {
    def write(i: IdAble): JsValue =
      i match {
        case IntIdAble(n) => JsNumber(n)
        case UUIDIdAble(n) => JsString(n.toString)
      }
    def read(json: JsValue): IdAble = {
      json match {
        case JsString(n) => UUIDIdAble(UUID.fromString(n))
        case JsNumber(n) => IntIdAble(n.toInt)
        case _ => deserializationError("Expected IdAble Int or UUID format")
      }
    }
  }
}

// These are borrowed from Base SMRT Server
trait JodaDateTimeProtocol extends DefaultJsonProtocol with FamilyFormats{

  implicit object JodaDateTimeFormat extends JsonFormat[JodaDateTime] {
    def write(obj: JodaDateTime): JsValue = JsString(obj.toString)

    def read(json: JsValue): JodaDateTime = json match {
      case JsString(x) => JodaDateTime.parse(x)
      case _ => deserializationError("Expected DateTime as JsString")
    }
  }

}

trait PathProtocols extends DefaultJsonProtocol with FamilyFormats {
  implicit object PathFormat extends RootJsonFormat[Path] {
    def write(p: Path): JsValue = JsString(p.toString)
    def read(v: JsValue): Path = v match {
      case JsString(s) => Paths.get(s)
      case _ => deserializationError("Expected Path as JsString")
    }
  }
}

trait UrlProtocol extends DefaultJsonProtocol with FamilyFormats{
  implicit object UrlFormat extends RootJsonFormat[URL] {
    def write(u: URL): JsValue = JsString(u.toString)
    def read(v: JsValue): URL = v match {
      case JsString(sx) => new URL(sx) // Should this default to file:// if not provided?
      case _ => deserializationError("Expected URL as JsString")
    }
  }
}

trait URIJsonProtocol extends DefaultJsonProtocol {

  implicit object URIJsonProtocolFormat extends RootJsonFormat[URI] {
    def write(x: URI) = JsString(x.toString)
    def read(value: JsValue) = {
      value match {
        case JsString(x) => new URI(x)
        case _ => deserializationError("Expected URI")
      }
    }
  }

}

object CommonJsonProtocols extends UUIDJsonProtocol with IdAbleJsonProtocol with JodaDateTimeProtocol with PathProtocols with UrlProtocol with URIJsonProtocol

object IdAbleJsonProtocol extends IdAbleJsonProtocol