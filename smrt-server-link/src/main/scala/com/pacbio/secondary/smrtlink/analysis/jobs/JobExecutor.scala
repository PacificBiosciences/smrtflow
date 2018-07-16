package com.pacbio.secondary.smrtlink.analysis.jobs

import java.io.FileWriter

import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}

trait JobResultsWriter {

  /**
    * Write a (info) message
    * @param msg
    */
  def write(msg: String): Unit

  /**
    * Write a (info) with an added new line
    * @param msg
    */
  def writeLine(msg: String) = write(msg + "\n")

  /**
    * Write an Error Message
    * @param msg
    */
  def writeError(msg: String): Unit

  /**
    * Write an Error message with a new line added
    * @param msg
    */
  def writeLineError(msg: String) = writeError(msg + "\n")
}

/**
  * Don't Write any outputs
  */
class NullJobResultsWriter extends JobResultsWriter {
  def write(msg: String) = {}
  def writeError(msg: String) = {}
}

/**
  * Write Stdout to console, stderr to Stderr
  */
class PrinterJobResultsWriter extends JobResultsWriter {
  def write(msg: String) = println(msg)

  def writeError(msg: String) = System.err.println(msg)
}

/**
  * Write stdout and stderr to Log
  */
class LogJobResultsWriter extends JobResultsWriter with LazyLogging {
  def write(msg: String): Unit = logger.info(msg)
  def writeError(msg: String): Unit = logger.error(msg)
}

/**
  * Write to output streams and err to file AND stderr with a prefixed Timestamp
  *
  * These are used as stdout and stderr (not necessarily full log messages)
  *
  * @param stdout
  * @param stderr
  */
class FileJobResultsWriter(stdout: FileWriter, stderr: FileWriter)
    extends JobResultsWriter {

  // This is a temporary hacky logging-ish model.
  private def toTimeStampMessage(msg: String, level: String = "INFO"): String =
    s"[$level] [${JodaDateTime.now()}] $msg"

  override def write(msg: String) = {
    stdout.append(toTimeStampMessage(msg))
    stdout.flush()
  }

  override def writeError(msg: String) = {
    val logMsg = toTimeStampMessage(msg, level = "ERROR")
    stderr.append(msg)
    stderr.flush()
  }
}
