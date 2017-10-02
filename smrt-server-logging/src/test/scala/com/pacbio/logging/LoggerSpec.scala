package com.pacbio.logging

import java.util
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.{Appender, Context}
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.FilterReply
import ch.qos.logback.core.status.Status
import com.typesafe.scalalogging.LazyLogging
import org.slf4j.{Logger, LoggerFactory}
import org.specs2.mutable.Specification

import scala.concurrent.{Await, Future}

/**
  * Tests showing that the logging CLI flags work as exepected
  *
  * A start to covering the expected use cases with a test. This spec or related ones could improve
  * the testing to verify that expected param combinations, logging to files and using logback files
  * works.
  */
class LoggerSpec extends Specification with LazyLogging {

  // sequential since the logger doesn't guarantee ordering
  sequential

  val queue = new LinkedBlockingQueue[(String, String)]()

  // helper to sync the async logging event
  def log(level: String, message: String): Unit = {
    queue.put((level, message))
  }

  def vals(ts: Int = 10000): (String, String) = {
    queue.poll(ts, TimeUnit.MILLISECONDS)
  }

  // custom handler for checking log level and string
  val lc = LoggerFactory.getILoggerFactory().asInstanceOf[LoggerContext]
  lc.getLogger(Logger.ROOT_LOGGER_NAME)
    .addAppender(
      // custom appender for testing
      new Appender[ILoggingEvent] {
        override def getName: String = "LoggerSpec"

        override def setName(name: String): Unit = Unit

        override def doAppend(e: ILoggingEvent): Unit =
          log(e.getLevel.toString, e.getMessage)

        override def getCopyOfAttachedFiltersList
          : util.List[Filter[ILoggingEvent]] = null

        override def getFilterChainDecision(
            event: ILoggingEvent): FilterReply = null

        override def addFilter(newFilter: Filter[ILoggingEvent]): Unit = Unit

        override def clearAllFilters(): Unit = {}

        override def addInfo(msg: String): Unit = log("INFO", msg)

        override def addInfo(msg: String, ex: Throwable): Unit =
          log("INFO", msg)

        override def addWarn(msg: String): Unit = log("WARN", msg)

        override def addWarn(msg: String, ex: Throwable): Unit =
          log("WARN", msg)

        override def addError(msg: String): Unit = log("ERROR", msg)

        override def addError(msg: String, ex: Throwable): Unit =
          log("ERROR", msg)

        override def addStatus(status: Status): Unit = Unit

        override def getContext: Context = null

        override def setContext(context: Context): Unit = Unit

        override def stop(): Unit = Unit

        override def isStarted: Boolean = true

        override def start(): Unit = Unit
      }
    )
  // need a ref to the config for testing
  val c = new LoggerConfig() {}

  "Configured logging" should {
    "Have working DEBUG logging" in {
      logger.debug("Test DEBUG")
      vals() mustEqual ("DEBUG", "Test DEBUG")
    }
    "Have working INFO logging" in {
      logger.info("Test INFO")
      vals() mustEqual ("INFO", "Test INFO")
    }
    "Have working WARN logging" in {
      logger.warn("Test WARN")
      vals() mustEqual ("WARN", "Test WARN")
    }
    "Have working ERROR logging" in {
      logger.error("Test ERROR")
      vals() mustEqual ("ERROR", "Test ERROR")
    }
    "Respect --quiet command-line param" in {
      LoggerOptions.parse("--quiet" :: Nil, c)
      logger.debug("Ignore DEBUG")
      vals(ts = 100) mustEqual null
      logger.error("Show ERROR")
      vals() mustEqual ("ERROR", "Show ERROR")
    }
    "Respect --verbose command-line param" in {
      LoggerOptions.parse("--verbose" :: Nil, c)
      logger.info("Don't ignore INFO")
      vals() mustEqual ("INFO", "Don't ignore INFO")
      logger.error("Show ERROR")
      vals() mustEqual ("ERROR", "Show ERROR")
    }
    "Respect --debug command-line param" in {
      LoggerOptions.parse("--debug" :: Nil, c)
      logger.debug("Don't ignore DEBUG")
      vals() mustEqual ("DEBUG", "Don't ignore DEBUG")
      logger.error("Show ERROR")
      vals() mustEqual ("ERROR", "Show ERROR")
    }
  }
}
