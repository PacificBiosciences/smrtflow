package com.pacbio.simulator.util

import java.util.UUID
import scala.xml.{Attribute, Elem, MetaData, Null, Text}
import scala.language.implicitConversions

/**
  * Created by amaster on 3/13/17.
  */
object XmlManipulator{

  implicit def addGoodCopyToAttribute(attr: Attribute) = new {
    def goodcopy(key: String = attr.key, value: Any = attr.value): Attribute =
      Attribute(attr.pre, key, Text(value.toString), attr.next)
  }

  implicit def iterableToMetaData(items: Iterable[MetaData]): MetaData = {
    items match {
      case Nil => Null
      case head :: tail => head.copy(next=iterableToMetaData(tail))
    }
  }
}

trait XmlAttributeManipulator{
  import XmlManipulator._

  def updateSubreadSetUuid(uuid : UUID, elem : scala.xml.Elem)  : Elem={

    val ee = elem.copy(attributes=
      for (attr <- elem.attributes) yield attr match {
        case attr@Attribute("UniqueId", _, _) =>
          attr.goodcopy(value=uuid.toString)
        case other => other
      })

    ee
  }
}

