package com.pacbio.secondary.smrtserver.tools

import scala.reflect.ClassTag

import spray.json._

class EnumJsonFormat[E <: Enumeration: ClassTag](enum: E)
    extends JsonFormat[E#Value] {

  val EnumerationClass = classOf[E#Value]

  def read(json: JsValue): E#Value = json match {
    case JsString(value) if isValid(json) =>
      enum.withName(value)
    case value =>
      deserializationError(s"Can't convert $value to $EnumerationClass")
  }

  def write(i: E#Value): JsValue = {
    JsString(i.toString)
  }

  private[this] def isValid(json: JsValue) = json match {
    case JsString(value) if enum.values.exists(_.toString == value) => true
    case _ => false
  }
}
