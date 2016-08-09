package com.pacbio.common.models

import java.util.UUID

import scala.language.implicitConversions

/**
  * Created by mkocher on 8/9/16.
  */
object Models {

  // "Old" model
  case class MixedIdType(id: Either[Int, UUID]) {
    def map[T](fInt: Int => _ <: T, fUUID: UUID => _ <: T): T = id match {
      case Left(i) => fInt(i)
      case Right(i) => fUUID(i)
    }
  }

  sealed trait IdAble {
    def toIdString: String
  }

  case class IntIdAble(n: Int) extends IdAble {
    def toIdString = n.toString
  }

  case class UUIDIdAble(n: UUID) extends IdAble {
    def toIdString = n.toString
  }

}

object ModelImplicits {
  import Models._

  implicit def toUUIDIdAble(n: UUID): UUIDIdAble = UUIDIdAble(n)
  implicit def toIntIdAble(n: Int):IntIdAble = IntIdAble(n)
}
