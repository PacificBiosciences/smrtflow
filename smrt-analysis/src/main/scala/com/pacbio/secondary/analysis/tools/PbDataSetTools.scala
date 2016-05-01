package com.pacbio.secondary.analysis.tools

import java.io.File
import java.nio.file.{Path, Paths}

import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes.DataSetMetaType
import scopt.OptionParser

import scala.io.Source
import scala.util.Try

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

trait Runners {
  def runValidate(): Try [String] = {
    ???
  }


}


/**
  * Created by mkocher on 12/21/15.
  */
trait PbDataSetTools {

  val VERSION = "0.1.1"
  val toolId = "pbscala.tools.dataset_merger"

  def showDefaults(c: CustomConfig): Unit = {
    println(s"Defaults $c")
  }

  // Fix the logLevel to be an enum. This should aim to mirror pbcommand
  case class CustomConfig(mode: Modes.Mode = Modes.UNKNOWN,
                          path: String,
                          x: Int,
                          y: Int,
                          z: String,
                          debug: Boolean = false,
                          logLevel: Boolean = false,
                          logFile: Option[File],
                          mergeDataSetOutput: File = null,
                          command: CustomConfig => Unit = showDefaults)

  lazy val defaults = CustomConfig(null, "", -1, -1, "", debug = false, logLevel = false, logFile = None)

  lazy val parser = new OptionParser[CustomConfig]("validate-datasets") {
    head("PacBio XML DataSet Utils.", VERSION)

    // Common options that mirror pbcommand's interface

    opt[Boolean]("log-level") action { (logLevel, c) =>
      c.copy(logLevel = logLevel)
    } text "Log level (Default INFO)"
    opt[File]("log-file") action { (logFile, c) =>
      c.copy(logFile = Option(logFile))
    } text "Log output Defaults to console"
    opt[Boolean]("debug") action { (v, c) =>
      c.copy(logLevel = true)
    } text "Alias for setting --log-level:DEBUG"
    opt[Boolean]("quick") action { (v, c) =>
      c.copy(logLevel = false)
    } text "Alias for setting --log-level:CRITICAL to suppress output"

    // Info Subparser
    cmd(Modes.INFO.name) action { (_, c) =>
      c.copy(command = (c) => println("without " + c.path), mode = Modes.INFO)
    } children(
      arg[String]("path") action { (s, c) =>
        c.copy(path = s)
      } text "path (string) to DataSet files",
      arg[Int]("y") required() action { (s, c) =>
        c.copy(y = s)
      } text "Y (Int) is a value that does this and that"
      ) text "Info description for "

    // Validate Subparser
    cmd(Modes.VALIDATE.name) action { (_, c) =>
      c.copy(command = (c) => println("with " + c), mode = Modes.VALIDATE)
    } children(
      opt[Int]("vx") action { (id, c) =>
        c.copy(x = id)
      } text "X (Int) is a value with stuff doc.",

      arg[String]("string") required() action { (s, c) =>
        c.copy(z = s)
      } text "Z (string) is a string doc."
      ) text "Validate Description for validating a PacBio XML DataSet"

    // Merge DataSet Subparser
    cmd(Modes.MERGE.name) action { (_, c) =>
      c.copy(command = (c) => println(s"Running merge with $c"), mode = Modes.MERGE)
    } children (
      opt[File]("output-dataset") required() action { (i, c) =>
        c.copy(mergeDataSetOutput = i)
      } text "Output dataset Path"
      ) text "Merge Detailed Description. Merge two or more XML Datasets "

    // Import DataSet
    cmd("import") action { (_, c) =>
      c.copy(command = (c) => println(s"Running import with $c"), mode = Modes.IMPORT)
    } children(
      opt[Int]("port") required() action { (i, c) =>
        c.copy(x = i)
      } text "Port to connect to",
      opt[String]("host") required() action { (i, c) =>
        c.copy(z = i)
      } text "Host to connect to"
      )
  }
}

object PbDataSetTools extends PbDataSetTools

object PbDataSetToolsApp extends App {

  case class ValidOpts(path: Path, strict: Boolean = false)

  case class InfoOpts(path: Path, debug: Boolean = false)

  /**
    * Load the dataset metatype from Path
    * @param path
    * @return
    */
  def loadMetaTypeFrom(path: Path): Option[DataSetMetaType] =
    DataSetMetaTypes.toDataSetType((scala.xml.XML.loadFile(path.toFile) \ "@MetaType").text)

  def runValidator(opts: ValidOpts): Unit = {

    loadMetaTypeFrom(opts.path) match {
      case Some(metaType) =>
        println(s"Metatype $metaType path:${opts.path}")
      case _ => println("")
    }

    println(s"Running validator with opts $opts")
  }

  def runInfo(opts: InfoOpts): Unit = {
    println(s"Running info with opts: $opts")
  }


  override def main(args: Array[String]): Unit = {
    // add to build.sbt
    //  "ds-tools" -> "com.pacbio.secondary.analysis.tools.PbDataSetToolsApp"
    val xs = PbDataSetTools.parser.parse(args.toSeq, PbDataSetTools.defaults) map { config =>
      val cx = config.command(config)
      println(s"command ${config.command}")
      println(s"Processed Config $config")
      config.mode match {
        case Modes.VALIDATE => runValidator(ValidOpts(Paths.get(config.path), strict = false))
        case Modes.INFO => runInfo(InfoOpts(Paths.get(config.path), debug = false))
        case x => throw new Exception(s"Unsupported command type '$x'")
      }
      cx
    }

    //    xs match {
    //      case Some(opt) =>
    //        println(s"running with opts $opt")
    //      case _ =>
    //        println("Unable to parse args")
    //    }

    println("Exiting ds-tools")
  }
}
