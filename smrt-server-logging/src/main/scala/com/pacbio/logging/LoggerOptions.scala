package com.pacbio.logging

import java.io.File
import java.nio.file.Files

import scopt.OptionParser


/**
 * Command Line Logging options
 *
 * This is intended for use with the CLI parsers so that debugging and logging related flags are shared across apps.
 *
 * See Readme.md for details about the shared parameters and examples of use.
 */
object LoggerOptions {

  var configured = false

  val VALID_LOG_LEVELS = Set("ERROR", "WARN", "INFO", "DEBUG")

  private def validateExists(file: File): Either[String, Unit] = {
    if (Files.exists(file.toPath)) Right(Unit)
    else Left(s"Unable to find $file")
  }
  private def validateLogLevel(level: String): Either[String, Unit] = {
    if (VALID_LOG_LEVELS contains level.toUpperCase) Right(Unit)
    else Left(s"Invalid log level '${level.toUpperCase}'. Valid log levels: ${VALID_LOG_LEVELS.reduce(_ + "," + _)}")
  }

  /**
   * Common logger config options for SMRT server Apps.
   *
   * See Readme.md for details about the shared parameters and examples of use.
   */
  def add(parser: OptionParser[LoggerConfig]): Unit = {

    parser.opt[Unit]("log2stdout") action { (x, c) =>
      c.configure(c.logbackFile, c.logFile, true, c.logLevel)
    } text "If true, log output will be displayed to the console. Default is false."

    parser.opt[String]("log-level").action { (x, c) =>
      c.configure(c.logbackFile, c.logFile, c.debug, x)
    }.validate(validateLogLevel)
        .text(s"Level for logging: ${VALID_LOG_LEVELS.reduce(_ + "," + _)}")

    for ((n, l) <- List(("debug", "DEBUG"), ("quiet", "ERROR"), ("verbose", "INFO")))
      parser.opt[Unit](n) action { (x, c) =>
        c.configure(c.logbackFile, c.logFile, c.debug, l)
      } text s"Same as --log-level $l"

    parser.opt[String]("log-file") action { (x, c) =>
      c.configure(c.logbackFile, x, c.debug, c.logLevel)
    } text "File for log output."

    parser.opt[File]("logback").action { (x, c) =>
      c.configure(x.toPath.toAbsolutePath.toString, c.logFile, c.debug, c.logLevel)
    }.text("Override all logger config with the given logback.xml file.")
  }
  /**
   * Helper method for cases where an App doesn't otherwise use scopt parsing
   *
   * @param args Command line arguments
   */
  def parse(args: Seq[String], lc:LoggerConfig = new LoggerConfig(){}): Unit = {
    val parser = new OptionParser[LoggerConfig]("./app_with_logging") {
      // Don't complain about args such as -jar used via command-line server execution
      override def errorOnUnknownArgument = false
      override def showUsageOnError = false
      note("This is an app that supports PacBio logging flags. ")

      opt[Unit]('h', "help") action { (x, c) =>
        showUsage
        sys.exit(0)
      } text "Show Options and exit"

      LoggerOptions.add(this)
    }
    parser.parse(args, lc)
  }

  def parseAddDebug(args: Seq[String]): Unit = {
    val requireOne = Set("--log-file", "--log2stdout", "-h")
    val v = if (args.filter(requireOne).isEmpty) args :+ "--log2stdout" else args
    parse(v)
  }
}
