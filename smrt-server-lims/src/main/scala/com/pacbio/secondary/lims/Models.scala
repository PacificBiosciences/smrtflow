package com.pacbio.secondary.lims

import java.util.UUID

import spray.json._
import DefaultJsonProtocol._


/**
 * Superset of a SubreadDataSet file that represents lims.yml
 *
 * This is a practical superset of the subreaddataset XML file data. It includes various information that gets
 * calculated and included in the lims.yml files MJ makes. Long-term, this abstraction needs to be rethought and likely
 * recast to a more formal abstraction. As-is, this data duplicates other
 */
case class LimsSubreadSet(
    val uuid: UUID,
    val expid: Int,
    val runcode: String,
    val json: JsValue)

object LimsJsonProtocol {

  val uuidRegex = "/^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$/".r

  implicit object UUIDJsonFormat extends JsonFormat[UUID] {
    def write(x: UUID) = JsString(x.toString)
    def read(value: JsValue) = value match {
      case JsString(x) => UUID.fromString(x)
    }
  }

  implicit val limsSubreadSetFormat = jsonFormat4(LimsSubreadSet)

  implicit object AnyJsonFormat extends JsonFormat[Any] {
    def write(x: Any) = x match {
      case n: Int => JsNumber(n)
      case s: String => JsString(s)
      case m: Map[String, _] => mapFormat[String, Any].write(m)
      case b: Boolean if b == true => JsTrue
      case b: Boolean if b == false => JsFalse
    }

    def read(value: JsValue) = value match {
      case JsNumber(n) => n.intValue()
      case JsString(s) => s
      case o: JsObject => mapFormat[String, Any].read(value)
      case JsTrue => true
      case JsFalse => false
    }
  }

  object LimsTypes {
    final val limsSubreadSet = "lims_subreadset"
    val all = Seq(limsSubreadSet)
  }

}