package com.pacbio.secondary.analysis.tools

import java.io.{PrintWriter, StringWriter}
import java.nio.file.{Files, Path, Paths}

import com.pacbio.logging.LoggerConfig
import com.pacbio.secondary.analysis.constants.GlobalConstants
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}

import scala.util.{Failure, Success, Try}

/**
  * General Utils for commandline
  *
  * 1. ReferenceInfo -> ReferenceDataSet
  * 2. RS MovieMetaData -> BamSubreadDataSet
  * 3. Fasta -> ReferenceDataSet
  */
object CommandLineUtils extends LazyLogging {

  /**
    * Find the exe in the current path
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
    * @param tf Final time
    * @param ti Initial time
    * @return
    */
  def computeTimeDelta(tf: JodaDateTime, ti: JodaDateTime): Int = {
    // return delta time in seconds
    // Do this to scope the imports
    import com.github.nscala_time.time.Implicits._
    val dt = (ti to JodaDateTime.now).toInterval
    dt.millis.toInt / 1000
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

trait CommandLineToolRunner[T <: LoggerConfig] extends LazyLogging with timeUtils {

  val toolId: String
  val VERSION: String
  val parser: scopt.OptionParser[T]
  val defaults: T

  def run(config: T): Either[ToolFailure, ToolSuccess]

  def runner(args: Array[String]): Unit = {
    val startedAt = JodaDateTime.now()

    parser.parse(args, defaults) match {

      case Some(c) =>
        // TODO Setup logging if c.debug
        logger.info(s"Starting to run tool $toolId with pbscala ${GlobalConstants.PB_SCALA_VERSION}")
        logger.debug(s"Config $c")

        val result = Try {
          run(c)
        }
        // This needs to be cleaned up.
        result match {
          case Success(r) =>
            r match {
              case Right(x) =>
                println(s"Successfully completed running $toolId v$VERSION in ${x.runTimeSec} sec.")
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
        val rcode = -1
        val runTimeSec = computeTimeDeltaFromNow(startedAt)
        val errorMessage = s"Failed to parse options for tool $toolId"
        logger.error(errorMessage)
        System.err.println(errorMessage)
        Left(ToolFailure(s"Tool $toolId failed (exit $rcode)", runTimeSec, errorMessage))
        sys.exit(rcode)
    }
  }

}
