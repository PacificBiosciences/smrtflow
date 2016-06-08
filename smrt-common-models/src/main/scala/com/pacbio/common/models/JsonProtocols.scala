package com.pacbio.common.models

import java.util.UUID
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
