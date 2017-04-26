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
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration


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

// These should all be deleted
trait ToolResult {
  val toolId: String
  val runTimeSec: Int
}
// These should all be deleted
case class ToolSuccess(toolId: String, runTimeSec: Int) extends ToolResult
case class ToolFailure(toolId: String, runTimeSec: Int, message: String) extends ToolResult

// Delete this when the AmClient and PbService have migrated to the new Subparser interface
trait CommandLineToolVersion {
  def showToolVersion(toolId: String, version: String): Unit =
    println(s"Tool $toolId tool version $version (smrtflow ${Constants.SMRTFLOW_VERSION})")
}

trait CommandLineToolBase[T <: LoggerConfig] extends LazyLogging with timeUtils{
  /**
    * Tool Id. this should have the form smrtflow.tools.{my_tool}
    */
  val toolId: String
  /**
    * Version of the Tool. Should use semver form
    */
  val VERSION: String
  /**
    * Description of the Tool
    */
  val DESCRIPTION: String

  val parser: scopt.OptionParser[T]
  val defaults: T

  def showToolVersion(toolId: String, version: String): Unit =
    println(s"Tool $toolId tool version $version (smrtflow ${Constants.SMRTFLOW_VERSION})")

  def showVersion: Unit = showToolVersion(toolId, VERSION)

  /**
    * This is the fundamental Interface that must implemented.
    *
    * The tool should return a terse summary string to indicate
    * that the tool was successful.
    *
    * @param opt
    * @return
    */
  def runTool(opt: T): Try[String]

  /**
    * This is the new model to improve clarity and remove the duplication in Either[ToolSuccess|Failure] layer.
    *
    * 1. Validation parsed options from scopt (after the options are parsed, the log is setup. See comments above)
    * 2. Log startup information
    * 3. Run Main Tool func -> Try[String] where the string is the message of success (no current way to pass a specific exit code)
    * 4. Catch NonFatal errors, log errors
    * 5. Print Shutdown message
    * 6. return exit code
    *
    * @param args
    * @return
    */
  def runnerWithArgs(args: Array[String]): Int = {
    val startedAt = JodaDateTime.now()

    val tx = for {
      parsedOpts <- parseAndValidateOptions(parser, defaults, args)
      _ <- Try { logParsedOptions(parsedOpts)}
      result <- runTool(parsedOpts)
      summary <- Try { successfulSummary(result, computeTimeDeltaFromNow(startedAt))}
    } yield summary

    tryToInt(tx, startedAt)
  }

  /**
    * The is the fundamental method that should be called from App
    * @param args
    * @return
    */
  def runnerWithArgsAndExit(args: Array[String]) =
    sys.exit(runnerWithArgs(args))


  def successfulSummary(sx: String, runTimeSec: Long): String = {
    val msg = s"Successfully completed running $toolId $VERSION (smrtflow ${Constants.SMRTFLOW_VERSION}) in $runTimeSec sec."
    println(sx)
    println(msg)
    logger.info(sx)
    logger.info(msg)
    s"$sx\nmsg"
  }

  def errorSummary(ex: Throwable, runTimeSec: Long): Int = {
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

  def parseAndValidateOptions(parser: OptionParser[T], defaults: T, args: Array[String]): Try[T] = {

    val prettyArgs = args.toSeq.reduceLeftOption(_ + " " + _).getOrElse("")

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

  def logParsedOptions(c: T): T = {
    logger.debug(s"Parsed options into Config $c")
    c
  }

  def tryToInt(tx: Try[String], startedAt: JodaDateTime): Int = {
    tx match {
      case Success(_) => 0
      case Failure(ex) => errorSummary(ex, computeTimeDeltaFromNow(startedAt))
    }
  }

}


trait CommandLineToolRunner[T <: LoggerConfig] extends LazyLogging with timeUtils with CommandLineToolBase[T]{


  /**
    * Util to block and run tool
    * @param fx func to run
    * @param timeOut timeout for the blocking call
    * @return
    */
  def runAndBlock(fx: => Future[String], timeOut: Duration): Try[String] =
    Try { Await.result(fx, timeOut) }



  // It's too much effort to update all of the tools. Adding this hack to create
  // a provide an intermediate model. This should be abstract once runner(args) and and run(config) has been
  // removed from the interface and all the tools have migrated to runTool interface
  def runTool(opts: T): Try[String] = Failure(throw new Exception(s"'runTool' Not Supported in $toolId"))

  /**
    * Deprecated method.
    *
    * @deprecated
    * @param config
    * @return
    */
  def run(config: T): Either[ToolFailure, ToolSuccess]

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

trait HasMode {
  val mode: String
}

// This must be bolted on to any SubParser based Options
trait HasModeAndLoggingConfig extends HasMode with LoggerConfig {}


trait SubParserModeRunner[T] {
  /**
    * Name of the subparser option
    */
  val name: String

  /**
    * Custom Validation of the "Total" options
    * This will often only validate a subset
    * of the total options
    *
    * @param opt
    * @return
    */
  def validateOpt(opt: T): Try[T]

  /**
    * Run the Subparser Tool and return a
    * terse message of results to indicate that
    * the tool was successful.
    *
    * @param opt
    * @return
    */
  def run(opt: T): Try[String]
}


trait CommandLineSubParserToolRunner[T <: HasModeAndLoggingConfig] extends LazyLogging with timeUtils with CommandLineToolBase[T]{

  // This should really be a set
  val subModes: Seq[SubParserModeRunner[T]]

  // This must be Lazy or def to avoid the NPE from delayed init
  private lazy val subModesMap = subModes.map(m => m.name -> m).toMap

  private def failIfInvalidSubParserMode(modeName: String): Try[SubParserModeRunner[T]] = {
    val errorMessage = s"Invalid subparser mode '$modeName'. Valid modes: ${subModesMap.keys.toSeq.reduce(_ + "," + _)}"
    subModesMap.get(modeName) match {
      case Some(m) => Success(m)
      case _ => Failure(throw new Exception(errorMessage))
    }
  }

  def runTool(opt: T): Try[String] = {
    for {
      subMode <- failIfInvalidSubParserMode(opt.mode)
      validOpts <- subMode.validateOpt(opt)
      result <- subMode.run(validOpts)
    } yield result
  }

}