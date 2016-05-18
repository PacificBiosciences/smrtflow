package com.pacbio.logging

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

  /**
    * Common logger config options for SMRT server Apps.
    *
    * See Readme.md for details about the shared parameters and examples of use.
    */
  def add(parser: OptionParser[LoggerConfig]): Unit = {

    parser.opt[Unit]("debug") action { (x, c) =>
      c.configure(c.logbackFile, c.logFile, true, c.logLevel)
    } text "If true, log output will be displayed to the console. Default is false."

    parser.opt[String]("loglevel") action { (x, c) =>
      c.configure(c.logbackFile, c.logFile, c.debug, x)
    } text "Level for logging: \"ERROR\", \"WARN\", \"DEBUG\", or \"INFO\". Default is \"ERROR\""

    parser.opt[String]("logfile") action { (x, c) =>
      c.configure(c.logbackFile, x, c.debug, c.logLevel)
    } text "File for log output. Default is \".\""

    parser.opt[String]("logback") action { (x, c) =>
      c.configure(x, c.logFile, c.debug, c.logLevel)
    } text "Override all logger config with the given logback.xml file."
  }

  /**
    * Helper method for cases where an App doesn't otherwise use scopt parsing
    *
    * @param args Command line arguments
    */
  def parse(args: Seq[String]): Unit = {
    val parser = new OptionParser[LoggerConfig]("Logger Default") {
      LoggerOptions.add(this)
    }
    parser.parse(args, new LoggerConfig(){})
  }
}
