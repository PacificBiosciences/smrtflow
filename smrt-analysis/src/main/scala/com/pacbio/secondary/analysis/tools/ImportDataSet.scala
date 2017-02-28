package com.pacbio.secondary.analysis.tools

import java.nio.file.Paths

import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import org.joda.time.DateTime
import scopt.OptionParser

// Is this even used?
case class ImportDataSetConfig(
    datasetMetaType: String,
    path: String,
    host: String = "http://localhost",
    port: Int = 8070) extends LoggerConfig
/**
 * Import DataSet into the SMRTLink Common or SMRTLink Analysis Services
 *
 * Created by mkocher on 9/19/15.
 */
object ImportDataSet extends CommandLineToolRunner[ImportDataSetConfig]{

  val toolId = "pbscala.tools.import_dataset"
  val VERSION = "0.1.0"
  val DESCRIPTION = "Import PacBio DataSet "
  val defaults = ImportDataSetConfig("", "", "http://localhost", 8070)

  val parser = new OptionParser[ImportDataSetConfig]("import-dataset") {
    head(DESCRIPTION, VERSION)
    note("Tool to import a PacBio DataSet into SMRTLink Common or Analysis Services")

    arg[String]("ds-type") required() action { (x, c) =>
      c.copy(datasetMetaType = x)
    } text "DataSet MetaData type (e.g., PacBio.DataSet.ReferenceSet"

    arg[String]("dataset-xml") action { (x, c) =>
      c.copy(path = x)
    } text "Path to Dataset XML"

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

  def run(c: ImportDataSetConfig): Either[ToolFailure, ToolSuccess] = {
    val startedAt = DateTime.now()

    val dsPath = Paths.get(c.path)
    val dsMetaType = DataSetMetaTypes.toDataSetType(c.datasetMetaType)

    Right(ToolSuccess(toolId, computeTimeDeltaFromNow(startedAt)))
  }

}

object ImportDataSetApp extends App {
  import ImportDataSet._
  runner(args)
}
