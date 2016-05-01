package com.pacbio.common.logging

import com.pacbio.common.actors.{LogDaoProvider, LogDao}
import com.pacbio.common.dependency.{SetBindings, SetBinding, Singleton}
import com.pacbio.common.models._
import org.slf4j.{LoggerFactory => LogbackFactory}

import scala.util.Try

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
    NOTICE -> (m => logbackLogger.info(m)), // Logback has no NOTICE level
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
  def notice(msg: String): Unit = log(msg, NOTICE)
  def warn(msg: String): Unit = log(msg, WARN)
  def error(msg: String): Unit = log(msg, ERROR)
  def critical(msg: String): Unit = log(msg, CRITICAL)
  def fatal(msg: String): Unit = log(msg, FATAL)
}

/**
 * Implementation of Logger that sends akka messages to the Log Service Actor.
 *
 * @param logId The id of the log resource that this logger will send messages to
 * @param sourceId The id of the source of the log messages
 * @param logDao The DAO for the logs
 * @param logback If true, messages will also be logged via Typesafe Logback
 */
class LoggerImpl(override val logId: String,
                 override val sourceId: String,
                 logDao: LogDao,
                 logback: Boolean) extends Logger {

  import LogLevel._

  override def log(msg: String, level: LogLevel): Unit = {
    logDao.createLogMessage(logId, LogMessageRecord(msg, level, sourceId))
    if (logback) logbackLog(msg, level)
  }
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

/**
 * Concrete LoggerFactory that provides a LoggerImpl.
 *
 * @param logDao The DAO for the logs
 * @param logback If true, messages will also be logged via Typesafe Logback
 * @param logResources Set of records for initializing log resources
 */
class LoggerFactoryImpl(logDao: LogDao,
                        logback: Boolean,
                        logResources: Set[LogResourceRecord]) extends LoggerFactory {

  logResources.foreach(r => Try(logDao.createLogResource(r)))

  override def getLogger(logId: String, sourceId: String): Logger = {
    new LoggerImpl(logId, sourceId, logDao, logback)
  }
}

/**
 * Provides a LoggerFactoryImpl. LazyLogging is false by default. Concrete providers must mixin a
 * LogServiceActorRefProvider and SetBindings.
 */
trait LoggerFactoryImplProvider extends LoggerFactoryProvider {
  this: LogDaoProvider with SetBindings =>

  val logback: Singleton[Boolean] = Singleton(false)

  override val loggerFactory: Singleton[LoggerFactory] =
    Singleton(() => new LoggerFactoryImpl(logDao(), logback(), logResources()))
}