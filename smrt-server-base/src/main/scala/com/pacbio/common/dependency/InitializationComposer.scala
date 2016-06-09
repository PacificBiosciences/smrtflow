package com.pacbio.common.dependency

import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable

trait RequiresInitialization {
  def init(): Any
}

trait InitializationComposer extends LazyLogging {
  private val inits: mutable.Buffer[Singleton[RequiresInitialization]] = new mutable.ArrayBuffer

  final def requireInitialization[T <: RequiresInitialization](i: Singleton[T]): Singleton[T] = {
    inits += i
    i
  }

  final def init(): Seq[Any] = inits.map{ i =>
    val ri = i()
    logger.info(s"---------- Initializing: ${ri.getClass.getSimpleName} ----------")
    val res = i().init()
    logger.info(s"---------- Result: ${res.toString} ----------")
    res
  }
}
