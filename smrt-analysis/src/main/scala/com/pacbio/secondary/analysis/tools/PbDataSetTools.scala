package com.pacbio.secondary.analysis.tools

import java.io.File
import java.nio.file.{Path, Paths}

import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes.DataSetMetaType
import scopt.OptionParser


object Modes {
  sealed trait Mode {
    val name: String
  }
  // Summary of dataset
  case object INFO extends Mode {val name = "info"}
  // Validate Dataset
  case object VALIDATE extends Mode {val name = "validate"}
  // Merge DataSet
  case object MERGE extends Mode {val name = "merge"}
  // Import Dataset
  case object IMPORT extends Mode {val name = "import"}

  case object UNKNOWN extends Mode { val name = "unknown"}
}


/**
 * Created by mkocher on 12/21/15.
 */
trait PbDataSetTools {

  val VERSION = "0.2.0"
  val toolId = "pbscala.tools.pbdataset"
  val DESCRIPTION =
    """
      |PacBio DataSet Tools
      |
    """.stripMargin

  def showDefaults(c: CustomConfig): Unit = {
    println(s"Defaults $c")
  }

  // Fix the logLevel to be an enum. This should aim to mirror pbcommand
  case class CustomConfig(
      mode: Modes.Mode = Modes.UNKNOWN,
      path: File,
      debug: Boolean = false,
      logLevel: Boolean = false,
      logFile: Option[File],
      mergeDataSetOutput: File = null,
      command: CustomConfig => Unit = showDefaults,
      host: String = "localhost",
      port: Int = 8070)

  // There must be a better way to do this
  lazy val defaults = CustomConfig(Modes.UNKNOWN, new File("dataset.xml"), debug = false, logLevel = false, logFile = None)

  lazy val parser = new OptionParser[CustomConfig]("pb-datasets") {
    head(DESCRIPTION, VERSION)

    // Common options that mirror pbcommand's setup_log interface

    //FIXME(mpkocher)(2016-5-3) This doesn't do anything. Need to have a programmatic model to configure the logger
    opt[Boolean]("log-level") action { (logLevel, c) =>
      c.copy(logLevel = logLevel)
    } text "Log level (Default INFO)"
    opt[File]("log-file") action { (logFile, c) =>
      c.copy(logFile = Option(logFile))
    } text "Log output Defaults to console"
    opt[Boolean]("debug") action { (v, c) =>
      c.copy(logLevel = true)
    } text "Alias for setting --log-level:DEBUG"
    opt[Boolean]("quiet") action { (v, c) =>
      c.copy(logLevel = false)
    } text "Alias for setting --log-level:CRITICAL to suppress output"

    /**
     * INFO Print a summary of the dataset
     */
    cmd(Modes.INFO.name) action { (_, c) =>
      c.copy(command = (c) => println("without " + c.path), mode = Modes.INFO)
    } children(
      arg[File]("path") action { (s, c) =>
        c.copy(path = s)
      } text "path (string) to DataSet file",
      opt[Unit]('h', "help") action { (x, c) =>
        showUsage
        sys.exit(0)
      } text "Show Options and exit"
      ) text "PacBio DataSet summary"

    /**
     * VALIDATE
     * Validate a PacBio XML dataset
     *
     * TODO(mpkocher)(2016-5-3) This should validate against the XSD, not use the java classes
     */
    cmd(Modes.VALIDATE.name) action { (_, c) =>
      c.copy(command = (c) => println("with " + c), mode = Modes.VALIDATE)
    } children(
      opt[File]("path") required() action { (i, c) =>
        c.copy(path = i)
      } text "Fofn (file of fofn name) DataSet file names (dataSet metatype must be the same)",
      opt[Unit]('h', "help") action { (x, c) =>
        showUsage
        sys.exit(0)
      } text "Show Options and exit"
      ) text "Validate Description for validating a PacBio XML DataSet"

    /**
     * MERGE
     *
     * Merge a FOFN (file of file names) of datasets and write an output DataSet
     *
     */
    cmd(Modes.MERGE.name) action { (_, c) =>
      c.copy(command = (c) => println(s"Running merge with $c"), mode = Modes.MERGE)
    } children (
      opt[File]("fofn") required() action { (i, c) =>
        c.copy(mergeDataSetOutput = i)
      } text "Fofn (file of fofn name) DataSet file names (dataSet metatype must be the same)",
      opt[File]("output-dataset") required() action { (i, c) =>
        c.copy(mergeDataSetOutput = i)
      } text "Output PacBio DataSet Path",
      opt[Unit]('h', "help") action { (x, c) =>
        showUsage
        sys.exit(0)
      } text "Show Options and exit"
      ) text "Merge Detailed Description. Merge XML Datasets from a FOFN (file of file names)"

    /**
     * IMPORT
     *
     * Import a Dataset that is local to the exe. Must be run from a
     * server where the system is running
     *
     * I think this is useful to duplicate with pbservice.
     * Why would host ever not be localhost?
     *
     */
    cmd(Modes.IMPORT.name) action { (_, c) =>
      c.copy(command = (c) => println(s"Running import with $c"), mode = Modes.IMPORT)
    } children(
      arg[File]("path") action { (s, c) =>
        c.copy(path = s)
      } text "path to DataSet file",
      opt[Int]("port") required() action { (i, c) =>
        c.copy(port = i)
      } text s"Port to connect to (Default ${defaults.port})",
      opt[String]("host") required() action { (i, c) =>
        c.copy(host = i)
      } text s"Host to connect to (Default ${defaults.host}",
      opt[Unit]('h', "help") action { (x, c) =>
        showUsage
        sys.exit(0)
      } text "Show Options and exit"
      ) text "Import DataSet into SMRT Link server"
  }
}

object PbDataSetTools extends PbDataSetTools

object PbDataSetToolsApp extends App {

  case class ValidOpts(path: Path, strict: Boolean = false)

  case class InfoOpts(path: Path, debug: Boolean = false)

  /**
   * Load the dataset metatype from Path
   *
   * @param path
   * @return
   */
  def loadMetaTypeFrom(path: Path): Option[DataSetMetaType] =
    DataSetMetaTypes.toDataSetType((scala.xml.XML.loadFile(path.toFile) \ "@MetaType").text)

  def runValidator(opts: ValidOpts): Int = {
    println("Running validator")
    loadMetaTypeFrom(opts.path) match {
      case Some(metaType) =>
        println(s"Metatype $metaType path:${opts.path}")
        0
      case _ => println("")
        0
    }
  }

  def runInfo(opts: InfoOpts): Int = {
    println(s"Running info with opts: $opts")
    0
  }

  def runMergeDataSet(fofn: Path, outputDataSet: Path, debug: Boolean = false): Int = {
    println("Running merge dataset")
    0
  }

  def runImportDataSet(path: Path, host: String, port: Int, debug: Boolean = false): Int = {
    println("Running dataset importing")
    0
  }

  def runner(args: Array[String]): Unit = {

    val xs = PbDataSetTools.parser.parse(args.toSeq, PbDataSetTools.defaults) map { config =>

      //val cx = config.command(config)
      println(s"command ${config.command}")
      println(s"Processed Config $config")

      val exitCode: Int = config.mode match {
        case Modes.VALIDATE => runValidator(ValidOpts(config.path.toPath, strict = false))
        case Modes.INFO => runInfo(InfoOpts(config.path.toPath, debug = false))
        case Modes.MERGE => runMergeDataSet(config.path.toPath, config.mergeDataSetOutput.toPath, debug = false)
        case Modes.IMPORT => runImportDataSet(config.path.toPath, config.host, config.port, debug = false)
        case unknown => throw new Exception(s"Unsupported command type '$unknown'")
      }
      exitCode
    }

    val x: Int = xs match {
      case Some(exitCode) => exitCode
      case _ => println("Unable to parse args") ; 1
    }

    System.exit(x)
  }

  // This is avoid the compile warning
  runner(args)
}
