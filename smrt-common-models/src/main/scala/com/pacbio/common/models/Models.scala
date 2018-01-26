package com.pacbio.common.models

import java.util.UUID

import akka.http.scaladsl.server.{Directives, PathMatcher1}

import scala.language.implicitConversions
import scala.util.Try

/**
  * Created by mkocher on 8/9/16.
  */
object CommonModels {
  sealed trait IdAble {
    def toIdString: String
    def map[T](fInt: Int => _ <: T, fUUID: UUID => _ <: T): T
  }

  case class IntIdAble(n: Int) extends IdAble {
    override def toIdString = n.toString
    override def map[T](fInt: Int => _ <: T, fUUID: UUID => _ <: T): T =
      fInt(n)
  }

  case class UUIDIdAble(n: UUID) extends IdAble {
    override def toIdString = n.toString
    override def map[T](fInt: Int => _ <: T, fUUID: UUID => _ <: T): T =
      fUUID(n)
  }
  object IdAble {
    def fromString(ix: String): Try[IdAble] = {
      Try(IntIdAble(ix.toInt))
        .recoverWith { case _ => Try(UUIDIdAble(UUID.fromString(ix))) }
    }
  }

}

object CommonModelImplicits {
  import CommonModels._

  implicit def toUUIDIdAble(n: UUID): UUIDIdAble = UUIDIdAble(n)
  implicit def toIntIdAble(n: Int): IntIdAble = IntIdAble(n)
}

object CommonModelSpraySupport extends Directives {
  import CommonModels._

  // The order of JavaUUID and IntNumber are important here, as IntNumber will capture a UUID
  val IdAbleMatcher: PathMatcher1[IdAble] = (JavaUUID | IntNumber).flatMap {
    p =>
      val idAble: Option[IdAble] = p match {
        case id: Int => Some(IntIdAble(id))
        case uuid: UUID => Some(UUIDIdAble(uuid))
        case _ => None
      }
      idAble
  }
}
