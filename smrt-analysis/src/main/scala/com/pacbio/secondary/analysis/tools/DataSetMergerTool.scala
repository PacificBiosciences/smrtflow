package com.pacbio.secondary.analysis.tools

import java.io.File
import java.nio.file.{Path, Paths}

import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.datasets.io.{DataSetLoader, DataSetMerger, DataSetWriter}
import org.joda.time.{DateTime => JodaDateTime}
import scopt.OptionParser

import scala.util.{Failure, Success, Try}

case class DataSetMergerOptions(
    datasetType: String,
    paths: Seq[File],
    outputPath: String) extends LoggerConfig

case class UnSupportDataSetTypeException(message: String) extends Exception

/**
 *
 * Created by mkocher on 5/15/15.
 */
object DataSetMergerTool extends CommandLineToolRunner[DataSetMergerOptions]{

  val VERSION = "0.2.0"
  val DESCRIPTION = "Merge DataSet XMLs By type"
  val toolId = "pbscala.tools.dataset_merger"

  val defaults = DataSetMergerOptions("", Seq[File](), "")

  val parser = new OptionParser[DataSetMergerOptions]("validate-datasets") {
    head(DESCRIPTION, VERSION)

    arg[String]("dataset-type") required() action { (x, c) =>
      c.copy(datasetType = x)
    } text "DataSet Type"

    arg[Seq[File]]("dataset-xmls") required() action { (x, c) =>
      c.copy(paths = x)
    } text "Path to DataSet XML ',' separated values"

    opt[String]('o', "output-xml") required() action {(x, c) =>
      c.copy(outputPath = x)
    }
    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show Options and exit"

    opt[Unit]("version") action { (x, c) =>
      showVersion
      sys.exit(0)
    } text "Show tool version and exit"

    // add the shared `--debug` and logging options
    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
  }

  def run(config: DataSetMergerOptions): Either[ToolFailure, ToolSuccess] = {
    val startedAt = JodaDateTime.now()
    val supportedDataSetTypes = DataSetMetaTypes.ALL.map(x => DataSetMetaTypes.typeToIdString(x))

    logger.info(s"Loaded config $config")
    logger.info(s"resolved '${config.datasetType}' to type ${DataSetMetaTypes.toDataSetType(config.datasetType)}. Supported Types $supportedDataSetTypes")

    val name = s"DataSet Merged ${config.datasetType}"
    val outputPath = Paths.get(config.outputPath)

    val inputPaths = config.paths.map(px => px.toPath)

    val results = for {
      outputPath <- Try { Paths.get(config.outputPath)}

      ds <- DataSetMetaTypes.toDataSetType(config.datasetType) match {
        case Some(DataSetMetaTypes.Subread) => Try { DataSetMerger.mergeSubreadSetPathsTo(inputPaths, name, outputPath) }
        case Some(DataSetMetaTypes.HdfSubread) => Try { DataSetMerger.mergeHdfSubreadSetPathsTo(inputPaths, name, outputPath) }
        case Some(DataSetMetaTypes.Alignment) => Try { DataSetMerger.mergeAlignmentSetPathsTo(inputPaths, name, outputPath) }
        case _ => Try { throw UnSupportDataSetTypeException(s"Unsupported dataset type '${config.datasetType}'. Supported types $supportedDataSetTypes") }
      }
    } yield ds

    results match {
      case Success(x) =>
        logger.info(s"wrote merged dataset ${config.datasetType} to ${outputPath.toAbsolutePath.toString}")
        Right(ToolSuccess(toolId, computeTimeDeltaFromNow(startedAt)))
      case Failure(ex) =>
        logger.error(s"Error ${ex.printStackTrace()}")
        Left(ToolFailure(toolId, computeTimeDeltaFromNow(startedAt), s"Failed to merge dataset. ${ex.getMessage}"))
    }
  }
}

object DataSetMergerApp extends App{
  import DataSetMergerTool._
  runner(args)
}
