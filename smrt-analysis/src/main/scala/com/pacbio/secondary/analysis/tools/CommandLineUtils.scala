package com.pacbio.secondary.analysis.tools

import java.io.{PrintWriter, StringWriter}
import java.nio.file.{Files, Path, Paths}
import java.text.ParseException

import com.pacbio.logging.LoggerConfig
import com.pacbio.common.models.Constants
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}
import scopt.OptionParser

import scala.util.{Failure, Success, Try}
import collection.JavaConversions._
import collection.JavaConverters._


object CommandLineUtils extends LazyLogging {

  /**
   * Find the exe in the current path
   *
   * @param exe A Commandline executable
   * @return
   */
  def which(exe: String): Option[Path] = {
    System.getenv("PATH").
      split(":").
      map(x => Paths.get(x).resolve(exe)).
      filter(p => Files.exists(p)) match {
      case Array(x) =>
        Option(x)
      case _ => None
    }
  }
}

trait timeUtils {


  /**
   * Compute the time difference in seconds
   *
   * @param tf Final time
   * @param ti Initial time
   * @return
   */
  def computeTimeDelta(tf: JodaDateTime, ti: JodaDateTime): Int = {
    // return delta time in seconds
    // Do this to scope the imports
    import com.github.nscala_time.time.Implicits._
    val dt = (ti to tf).toInterval
    (dt.millis / 1000).toInt
  }

  def computeTimeDeltaFromNow(t: JodaDateTime): Int = {
    computeTimeDelta(JodaDateTime.now, t)
  }

}

trait ToolResult {
  val toolId: String
  val runTimeSec: Int
}

case class ToolSuccess(toolId: String, runTimeSec: Int) extends ToolResult

case class ToolFailure(toolId: String, runTimeSec: Int, message: String) extends ToolResult

trait CommandLineToolVersion {

  def showToolVersion(toolId: String, version: String): Unit =
    println(s"Tool $toolId tool version $version (smrtflow ${Constants.SMRTFLOW_VERSION})")
}


trait CommandLineToolRunner[T <: LoggerConfig] extends LazyLogging with timeUtils with CommandLineToolVersion{

  val toolId: String
  val VERSION: String
  val parser: scopt.OptionParser[T]
  val defaults: T

  def showVersion: Unit = showToolVersion(toolId, VERSION)

  def run(config: T): Either[ToolFailure, ToolSuccess]


  private def parseAndValidateOptions(parser: OptionParser[T], defaults: T, args: Array[String]): Try[T] = {

    val prettyArgs = args.toSeq.reduce(_ + " " + _)

    // Note, due to global variables, this is where the log gets setup. This should be revisited to use a better pattern.
    val parsedOpts = parser.parse(args, defaults)

    logger.info(s"Starting to run tool $toolId with smrtflow ${Constants.SMRTFLOW_VERSION}")

    parsedOpts match {
      case Some(validateOpts) =>
        logger.info(s"Successfully Parsed options $validateOpts")
        Success(validateOpts)
      case _ => Failure(throw new ParseException(s"Failed to parse options '$prettyArgs'", 0))
    }
  }

  private def logParsedOptions(c: T): T = {
    logger.debug(s"Parsed options into Config $c")
    c
  }

  private def successfulSummary(sx: String, runTimeSec: Long): String = {
    val msg = s"Successfully completed running $toolId $VERSION (smrtflow ${Constants.SMRTFLOW_VERSION}) in $runTimeSec sec."
    println(sx)
    println(msg)
    logger.info(sx)
    logger.info(msg)
    s"$sx\nmsg"
  }

  private def errorSummary(ex: Throwable, runTimeSec: Long): Int = {
    // this could pattern match on Throwable and return a specific exit code
    val exitCode = 1

    val msg = s"Failed (exit code $exitCode) running $toolId $VERSION (smrtflow ${Constants.SMRTFLOW_VERSION}) in $runTimeSec sec. ${ex.getMessage}"

    System.err.println(msg)

    val sw = new StringWriter
    ex.printStackTrace(new PrintWriter(sw))

    // This is typically useless to a user. Putting the stacktrace in the logger
    //System.err.println(sw.toString)
    logger.error(sw.toString)
    logger.error(msg)

    exitCode
  }

  /**
    * This is the new model to improve clarity and remove the duplication in Either[ToolSuccess|Failure] layer.
    *
    * 1. Validation parsed options from scopt (after the options are parsed, the log is setup. See comments above)
    * 2. Log startup information
    * 3. Run Main func -> Try[String] where the string is the message of success (no current way to pass a specific exit code)
    * 4. Catch NonFatal errors, log errors
    * 5. Print Shutdown message
    * 6. System.exit with exit code
    *
    * @param f
    * @param args
    * @return
    */
  private def runnerWithTry(f: (T => Try[String]), args: Array[String]): Int = {
    val startedAt = JodaDateTime.now()

    val tx = for {
      validOptions <- parseAndValidateOptions(parser, defaults, args)
      _ <- Try { logParsedOptions(validOptions)}
      result <- f(validOptions)
      summary <- Try { successfulSummary(result, computeTimeDeltaFromNow(startedAt))}
    } yield summary


    // map to exit code
    tx match {
      case Success(_) => 0
      case Failure(ex) => errorSummary(ex, computeTimeDeltaFromNow(startedAt))
    }
  }

  /**
    * Core function that should be called from within the subclass of App
    *
    * @param f    ToolRunner func. Returns a summary message of successful run
    * @param args raw commandline args.
    */
  def runnerWithTryAndExit(f: (T => Try[String]), args: Array[String]): Unit = {
    sys.exit(runnerWithTry(f, args))
  }

  /**
    * This was a very clumsy design and needs to go away.
    *
    * @deprecated
    * @param args raw commandline args
    */
  def runner(args: Array[String]): Unit = {
    val startedAt = JodaDateTime.now()

    parser.parse(args, defaults) match {

      case Some(c) =>
        // TODO Setup logging if c.debug
        logger.info(s"Starting to run tool $toolId with smrtflow ${Constants.SMRTFLOW_VERSION}")
        logger.debug(s"Config $c")

        val result = Try {
          run(c)
        }
        // This needs to be cleaned up.
        result match {
          case Success(r) =>
            r match {
              case Right(x) =>
                println(s"Successfully completed (exit code 0) running $toolId v$VERSION in ${x.runTimeSec} sec.")
                sys.exit(0)
              case Left(e) =>
                val msg = s"Failed (exit code 1) running $toolId v$VERSION in ${e.runTimeSec} sec. ${e.message}"
                //logger.error(msg)
                System.err.println(msg)
                sys.exit(1)
            }
          case Failure(e) =>
            val rcode = 1
            val runTimeSec = computeTimeDeltaFromNow(startedAt)
            val msg = s"Failed running $toolId v$VERSION in $runTimeSec sec. Exiting with exit code $rcode"
            logger.error(msg)
            val sw = new StringWriter
            e.printStackTrace(new PrintWriter(sw))
            System.err.println(sw.toString)
            System.err.println(msg)
            sys.exit(rcode)
        }
      case _ =>
        val rcode = 2
        val runTimeSec = computeTimeDeltaFromNow(startedAt)
        val errorMessage = s"Failed to parse options for tool $toolId"
        logger.error(errorMessage)
        System.err.println(errorMessage)
        Left(ToolFailure(s"Tool $toolId failed (exit $rcode)", runTimeSec, errorMessage))
        sys.exit(rcode)
    }
  }

}
