package com.pacbio.secondary.smrtlink.logging

import com.pacbio.secondary.smrtlink.dependency.{SetBindings, SetBinding, Singleton}
import com.pacbio.secondary.smrtlink.models._
import org.slf4j.{LoggerFactory => LogbackFactory}

/**
 * Trait designed to simplify interaction with the Log Service.
 */
trait Logger extends ContextualLogging {
  import LogLevel._

  val logId: String
  val sourceId: String

  val logbackLogger = LogbackFactory.getLogger(logId)
  final val levelToLogbackLevel: Map[LogLevel, String => Unit] = Map(
    TRACE -> (m => logbackLogger.trace(m)),
    DEBUG -> (m => logbackLogger.debug(m)),
    INFO -> (m => logbackLogger.info(m)),
    WARN -> (m => logbackLogger.warn(m)),
    ERROR -> (m => logbackLogger.error(m)),
    CRITICAL -> (m => logbackLogger.error(m)), // Logback has no CRITICAL level
    FATAL -> (m => logbackLogger.error(m)) // Logback has no FATAL level
  )

  final def logbackLog(msg: String, level: LogLevel): Unit = {
    // TODO(smcclellan): Logging context should be added at the service level and actor level
    for(_ <- logContext("Logger.logId" -> logId, "Logger.sourceId" -> sourceId)) {
      levelToLogbackLevel(level)(s"[Source: $sourceId] $msg")
    }
  }

  def log(msg: String, level: LogLevel): Unit

  def trace(msg: String): Unit = log(msg, TRACE)
  def debug(msg: String): Unit = log(msg, DEBUG)
  def info(msg: String): Unit = log(msg, INFO)
  def warn(msg: String): Unit = log(msg, WARN)
  def error(msg: String): Unit = log(msg, ERROR)
  def critical(msg: String): Unit = log(msg, CRITICAL)
  def fatal(msg: String): Unit = log(msg, FATAL)
}

/**
 * Trait for producing Loggers
 */
trait LoggerFactory {
  /**
   * Creates a Logger that will use the given sourceId and send messages to the log resource with the given logId.
   * Automatically initializes the log resource, if necessary.
   */
  def getLogger(logId: String, sourceId: String): Logger
}

/**
 * SetBinding for initial log resources. Providers may contribute a log resource to be initiialized by binding a
 * LogResourceRecord to this.
 */
object LogResources extends SetBinding[LogResourceRecord]

/**
 * Abstract provider that provides a Singleton LoggerFactory
 */
trait LoggerFactoryProvider {
  this: SetBindings =>

  /**
   * Provides a singleton LoggerFactory.
   */
  val loggerFactory: Singleton[LoggerFactory]

  /**
   * Defines a set of log resources.
   */
  val logResources: Singleton[Set[LogResourceRecord]] = Singleton(() => set(LogResources))
}

