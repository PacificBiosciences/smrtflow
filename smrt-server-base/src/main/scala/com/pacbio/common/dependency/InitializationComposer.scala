package com.pacbio.common.dependency

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

trait RequiresInitialization {
  def init(): Future[Any]
}
trait InitializationComposer {
  val inits: mutable.Buffer[Singleton[RequiresInitialization]] = new mutable.ArrayBuffer

  def requireInitialization[T <: RequiresInitialization](i: Singleton[T]): Singleton[T] = {
    inits += i
    i
  }

  def init(): Future[Seq[Any]] = Future.sequence(inits.toSeq.map(_().init()))
}
