package com.pacbio.logging

import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.{Level, LoggerContext, PatternLayout}
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.rolling.{FixedWindowRollingPolicy, RollingFileAppender, SizeBasedTriggeringPolicy}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConversions._

/**
 * Mixin for adding shared logger params to SMRT App classes
 *
 * See Readme.md for details about the shared parameters and examples of use.
 */
trait LoggerConfig {

  // params for logger configuration
  var logLevel = "INFO"
  var logFile = "default_smrt.log"
  var logbackFile: String = null
  var debug = false

  // arbitrary formatting for console and log files
  val consolePattern = "%X{akkaTimestamp} %-5level[%thread] %logger{0} - %msg%n"
  val filePattern = "%date{yyyy-MM-dd} %X{akkaTimestamp} %-5level[%thread] %logger{1} - %msg%n"
  val fileRollingPosfix = ".%i.gz"

  /**
   * Lazy updates the logger config.
   *
   * @param logbackFile
   * @param logFile
   * @param debug
   * @param logLevel
   * @return
   */
  def configure(
      logbackFile: String,
      logFile: String,
      debug: Boolean,
      logLevel: String): LoggerConfig = {

    // logback.xml trumps all other config
    if (logbackFile != this.logbackFile)
      setLogback(logbackFile)
    else {
      // order matters here so that debug can trump file and level is correctly set
      if (logFile != this.logFile) {
        setFile(logFile)
        setLevel(this.logLevel)
      }
      if (debug != this.debug) {
        setDebug(debug)
        setLevel(this.logLevel)
      }
      if (logLevel != this.logLevel) setLevel(logLevel)
    }
    // ignore the default configurator
    LoggerOptions.configured = true
    this
  }

  /**
   * Parses a logback.xml file and uses that as the SLFJ4 config.
   *
   * See http://logback.qos.ch/manual/configuration.html
   *
   * @param path File path of the logback.xml file
   * @return How many params were consumed
   */
  def setLogback(path: String) {
    this.logbackFile = path
    val lc = LoggerFactory.getILoggerFactory().asInstanceOf[LoggerContext]
    val configurator = new JoranConfigurator()
    configurator.setContext(lc)
    // clear any old config
    lc.reset()
    // load the new logback.xml
    configurator.doConfigure(path)
  }

  /**
   * Sets the logging level for *all* registered loggers.
   *
   * @param level @see ch.qos.logback.classic.Level
   * @return How many params were consumed
   */
  def setLevel(level: String) {
    this.logLevel = level
    val lc = LoggerFactory.getILoggerFactory().asInstanceOf[LoggerContext]
    val l = Level.toLevel(level)
    for (logger <- lc.getLoggerList) logger.setLevel(l)
  }

  /**
   * Removes all handlers and directs logs to a file.
   *
   * @param file File path to save logging output
   * @return How many params were consumed
   */
  def setFile(file: String) {
    this.logFile = file
    val lc = LoggerFactory.getILoggerFactory().asInstanceOf[LoggerContext]

    // configure the rolling file appender
    val appender = new RollingFileAppender[ILoggingEvent]()
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
    }
    lc.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender)
  }

  /**
   * Sets all loggers to display information on System.out.
   *
   * Useful for debugging the code or piping output.
   *
   * @return How many params were consumed
   */
  def setDebug(debug: Boolean) {
    this.debug = debug
    if (!debug) return
    val lc = LoggerFactory.getILoggerFactory().asInstanceOf[LoggerContext]
    // build up a SLFJ4 console logger
    val appender = new ConsoleAppender[ILoggingEvent]()
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
    }
    lc.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender)
  }
}
