package com.pacbio.database

import com.typesafe.scalalogging.LazyLogging

/**
 * Created by jfalkner on 6/10/16.
 */
class LoggingListener extends DatabaseListener with LazyLogging {

  val Prefix = "[PacBio:Database] "


  // life cycle of the nested Future. useful for debugging nested db.run use
  override def create(code: String, stacktrace: Throwable): Unit =
    logger.error(s"$Prefix RDMS created DBIOAction $code")
  override def start(code: String, stacktrace: Throwable, qc: Int): Unit =
    logger.error(s"$Prefix RDMS started DBIOAction $code, queryCount = $qc")
  override def end(code: String, stacktrace: Throwable, qc: Int): Unit =
    logger.error(s"$Prefix RDMS finished DBIOAction $code, queryCount = $qc")

  override def timeout(
      code: String,
      stacktrace: Throwable,
      t: Throwable): Unit =
    logger.error(s"$Prefix RDMS timeout for $code", t)

  override def error(
      code: String,
      stacktrace: Throwable,
      t: Throwable): Unit =
    logger.error(s"$Prefix RDMS error for $code", t)

  override def allDone(
      start: Long,
      end: Long,
      code: String,
      stacktrace: Throwable): Unit =
    logger.debug(s"$Prefix total timing for $code was ${end - start}", stacktrace)


  override def success(
      code: String,
      stacktrace: Throwable,
      result: Any): Unit =
    logger.debug(s"$Prefix RDMS completed $code got $result")

  override def dbDone(
      start: Long,
      end: Long,
      code: String,
      stacktrace: Throwable): Unit =
    logger.debug(s"$Prefix RDMS executed $code in ${end - start} ms")
}
