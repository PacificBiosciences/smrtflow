package com.pacbio.secondary.lims

import java.util.UUID

import spray.json._
import DefaultJsonProtocol._

import com.pacbio.common.models.UUIDJsonProtocol


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

    val path: String,
    val pa_version: String,
    val ics_version: String,
    val well: String,
    val context: String,
    val created_at: String,
    val inst_name: String,
    val instid: Int)

object LimsJsonProtocol {

  val uuidRegex = "/^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$/".r

  implicit object UUIDJsonFormat extends JsonFormat[UUID] {
    def write(obj: UUID): JsValue = JsString(obj.toString)

    def read(json: JsValue): UUID = json match {
      case JsString(x) => UUID.fromString(x)
      case _ => deserializationError("Expected UUID as JsString")
    }
  }

  implicit val limsSubreadSetFormat = jsonFormat11(LimsSubreadSet)

  object LimsTypes {
    final val limsSubreadSet = "lims_subreadset"
    val all = Seq(limsSubreadSet)
  }

}