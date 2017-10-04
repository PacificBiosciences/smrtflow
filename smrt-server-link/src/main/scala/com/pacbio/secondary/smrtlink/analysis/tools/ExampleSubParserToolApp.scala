package com.pacbio.secondary.smrtlink.analysis.tools

import com.pacbio.common.logging.{LoggerConfig, LoggerOptions}
import com.typesafe.scalalogging.LazyLogging
import scopt.OptionParser

import scala.util.{Success, Try}

// Example of writing a PacBio scala commandline util that leverages subparsers

case class ExampleSubParserToolOptions(a: Int,
                                       b: Int,
                                       mode: String = "UNKNOWN")
    extends HasModeAndLoggingConfig

class Alpha extends SubParserModeRunner[ExampleSubParserToolOptions] {
  override def validateOpt(
      opt: ExampleSubParserToolOptions): Try[ExampleSubParserToolOptions] =
    Try { opt }

  override val name = "alpha"

  override def run(opt: ExampleSubParserToolOptions): Try[String] =
    Success(s"Completed running alpha with $opt")
}

class Beta extends SubParserModeRunner[ExampleSubParserToolOptions] {
  override def validateOpt(
      opt: ExampleSubParserToolOptions): Try[ExampleSubParserToolOptions] =
    Try { opt }

  override val name = "beta"

  override def run(opt: ExampleSubParserToolOptions): Try[String] =
    Success(s"Completed running beta with $opt")
}

object ExampleSubParserToolRunner
    extends CommandLineSubParserToolRunner[ExampleSubParserToolOptions] {

  val toolId = "smrtflow.tools.example_subparser"
  val VERSION = "1.0.0"
  val DESCRIPTION = "Example of a PacBio Subparser Tool"
  val defaults = ExampleSubParserToolOptions(1, 2, "UNKNOWN_MODE")

  // Custom Subparser Tool Runners
  lazy val alpha = new Alpha
  lazy val beta = new Beta

  val subModes = Seq(alpha, beta)

  val parser: OptionParser[ExampleSubParserToolOptions] =
    new OptionParser[ExampleSubParserToolOptions]("example-subparser") {
      head(DESCRIPTION, VERSION)

      // Each Subparser has to be manually added. I don't really see a way
      // to make this type safe
      cmd(alpha.name)
        .action { (_, c) =>
          c.copy(mode = alpha.name)
        }
        .children(
          opt[Int]("a-value")
            .required()
            .action { (p, c) =>
              c.copy(a = p)
            })
        .text("Run Alpha Subparser")

      cmd(beta.name)
        .action { (_, c) =>
          c.copy(mode = beta.name)
        }
        .children(
          opt[Int]("b-value")
            .required()
            .action { (p, c) =>
              c.copy(b = p)
            })
        .text("Run beta subparser")

      // Add Help and Version
      opt[Unit]('h', "help") action { (x, c) =>
        showUsage
        sys.exit(0)
      } text "Show options and exit"

      opt[Unit]("version") action { (x, c) =>
        showVersion
        sys.exit(0)
      } text "Show tool version and exit"

      LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
    }

}

object ExampleSubParserToolApp extends App with LazyLogging {
  import ExampleSubParserToolRunner._
  runnerWithArgsAndExit(args)
}
