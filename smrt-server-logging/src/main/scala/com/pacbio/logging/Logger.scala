/**
  * Reusable lazy logging and logging configuration for all SMRT CLI tools.
  *
  * This code serves two purposes.
  *
  * 1. A drop in SLFJ4 LazyLogger replacement for scala-logging.
  * 2. LogConfig for reusable CLI params across tools and fallback to logback.xml if desired
  *
  * The codebase used to have several logback.xml files and related logging.properties files that would specify similar
  * log configuration along with a file path for where to save logs for deployed code. This replaced all that with
  * conventions that match the old use cases and a `--logfile <path>` arg to set file paths and `--loglevel <level>` arg
  * to control verbosity.
  *
  * There are at least two minor other perks as well. This code runs faster than logback.xml parsing and it removes the
  * dependency on `com.typesafe.scalalogging` for the PacBio code.
  */
package com.pacbio.logging

import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, PatternLayout}
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.rolling.{FixedWindowRollingPolicy, RollingFileAppender, SizeBasedTriggeringPolicy}
import ch.qos.logback.classic.LoggerContext
import org.slf4j.LoggerFactory

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer

/**
  * Defines `logger` as a lazy value initialized with an underlying `org.slf4j.Logger`
  * named according to the class into which this trait is mixed.
  */
trait LazyLogging {

  protected lazy val logger: org.slf4j.Logger = LoggerFactory.getLogger(getClass.getName)

}

/**
  * Singleton that encapsulates logging conventions and enables them to be reused by all CLI Apps.
  *
  * See Readme.md for details about the shared parameters and examples of use.
  */
object LogConfig {

  val consolePattern = "%X{akkaTimestamp} %-5level[%thread] %logger{0} - %msg%n"
  val filePattern = "%date{yyyy-MM-dd} %X{akkaTimestamp} %-5level[%thread] %logger{1} - %msg%n"
  val fileRollingPosfix = ".%i.gz"

  // all the expected loggers
  val loggerNames = "akka" :: "Slf4jLogger" :: "scala.slick" :: "spray" :: "log1-Slf4jLogge" :: Nil

  def trim(args: Seq[String]): Seq[String] = {
    // register all known loggers in case the code makes them later
    loggerNames.foreach(LoggerFactory.getLogger)
    // buffer all non-debug params for the App's normal config parsing
    var toreturn = ListBuffer[String]()
    var i = 0
    // helper method to update the log configs based on param pairs
    def increment(arg: String, nextArg: String) : Int  = args(i) match {
      case "--logfile" => setFile(nextArg)
      case "--debug" => setDebug()
      case "--loglevel" => setLevel(nextArg)
      case "--logback" => setLogback(nextArg)
      case _ => {
        toreturn += arg
        1
      }
    }
    // walk through the params and handle debugging config
    while (i < args.length) {
      i += increment(args(i), if (i + 1 < args.length) args(i + 1) else null)
    }
    return toreturn
  }

  /**
    * Parses a logback.xml file and uses that as the SLFJ4 config.
    *
    * See http://logback.qos.ch/manual/configuration.html
    * @param path File path of the logback.xml file
    * @return How many params were consumed
    */
  def setLogback(path: String): Int = {
    val lc = LoggerFactory.getILoggerFactory().asInstanceOf[LoggerContext]
    val configurator = new JoranConfigurator()
    configurator.setContext(lc)
    // clear any old config
    lc.reset()
    // load the new logback.xml
    configurator.doConfigure(path)
    2
  }

  /**
    * Sets the logging level for *all* registered loggers.
    * @param level @see ch.qos.logback.classic.Level
    * @return How many params were consumed
    */
  def setLevel(level: String): Int = {
    val lc = LoggerFactory.getILoggerFactory().asInstanceOf[LoggerContext]
    val l = Level.toLevel(level)
    for (logger <- lc.getLoggerList) { logger.setLevel(l) }
    2
  }

  /**
    * Removes all handlers and directs logs to a file.
    * @param file File path to save logging output
    * @return How many params were consumed
    */
  def setFile(file: String): Int = {
    val lc = LoggerFactory.getILoggerFactory().asInstanceOf[LoggerContext]

    // configure the rolling file appender
    val appender = new RollingFileAppender[ILoggingEvent]();
    appender.setContext(lc)
    appender.setName("FILE")
    val patternEncoder = new PatternLayoutEncoder()
    patternEncoder.setPattern(filePattern)
    appender.setEncoder(patternEncoder)
    val patternLayout = new PatternLayout()
    patternLayout.setPattern(filePattern)
    patternLayout.setContext(lc)
    patternLayout.start()
    appender.setLayout(patternLayout)
    val rollingPolicy = new FixedWindowRollingPolicy()
    rollingPolicy.setParent(appender)
    rollingPolicy.setMinIndex(1)
    rollingPolicy.setMaxIndex(9)
    rollingPolicy.setFileNamePattern(file + fileRollingPosfix)
    appender.setRollingPolicy(rollingPolicy)
    val triggeringPolicy = new SizeBasedTriggeringPolicy[ILoggingEvent]()
    triggeringPolicy.setMaxFileSize("100MB")
    triggeringPolicy.start()
    appender.setTriggeringPolicy(triggeringPolicy)
    appender.setFile(file)
    appender.start()
    // set all loggers to direct output to the specified file
    for (logger <- lc.getLoggerList) {
      // remove any old appenders
      logger.detachAndStopAllAppenders()
      logger.addAppender(appender)
    }
    2
  }

  /**
    * Sets all loggers to display information on System.out.
    *
    * Useful for debugging the code or piping output.
    * @return How many params were consumed
    */
  def setDebug(): Int = {
    val lc = LoggerFactory.getILoggerFactory().asInstanceOf[LoggerContext]
    // build up a SLFJ4 console logger
    val appender = new ConsoleAppender[ILoggingEvent]();
    appender.setContext(lc)
    appender.setName("STDOUT")
    val patternEncoder = new PatternLayoutEncoder()
    patternEncoder.setPattern(filePattern)
    appender.setEncoder(patternEncoder)
    val patternLayout = new PatternLayout()
    patternLayout.setPattern(filePattern)
    patternLayout.setContext(lc)
    patternLayout.start()
    appender.setLayout(patternLayout)
    appender.start()
    // set all loggers to direct output to console
    for (logger <- lc.getLoggerList) {
      // remove any old appenders
      logger.detachAndStopAllAppenders()
      // dump to console
      logger.addAppender(appender)
    }
    1
  }
}