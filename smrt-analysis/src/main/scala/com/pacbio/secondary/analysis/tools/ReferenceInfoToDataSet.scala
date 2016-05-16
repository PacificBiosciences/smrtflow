package com.pacbio.secondary.analysis.tools

import java.nio.file.Paths

import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.analysis.converters.ReferenceInfoConverter._
import com.pacbio.secondary.analysis.legacy.{ReferenceEntry, ReferenceInfoUtils}
import org.joda.time.{DateTime => JodaDateTime}
import scopt.OptionParser


case class ReferenceConverterConfig(referenceInfoXMLPath: String,
                                    datasetXMLPath: String) extends LoggerConfig

object ReferenceInfoToDataSetTool extends CommandLineToolRunner[ReferenceConverterConfig] {

  val toolId = "pbscala.tools.reference_info_to_ds"
  val VERSION = "0.3.0"
  val defaults = ReferenceConverterConfig("", "")

  val parser = new OptionParser[ReferenceConverterConfig]("reference-to-dataset") {
    head("Reference To Reference Dataset XML ", VERSION)
    note("Tool to convert a reference.info.xml to a Dataset XML")

    arg[String]("reference-info-xml") required() action { (x, c) =>
      c.copy(referenceInfoXMLPath = x)
    } text "Path to Reference INFO XML"

    arg[String]("reference-dataset-xml") action { (x, c) =>
      c.copy(datasetXMLPath = x)
    } text "Path to output Reference Dataset XML"

    // add the shared `--debug` and logging options
    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
  }

  /**
   * This needs to be re-written to load as a ReferenceInfo and do a direct
   * conversion to ReferenceSet
   * @param c Config parameters
   * @return
   */
  def run(c: ReferenceConverterConfig): Either[ToolFailure, ToolSuccess] = {
    val startedAt = JodaDateTime.now()
    val referenceInfoXMLFile = Paths.get(c.referenceInfoXMLPath)
    // sanity check to load from schema
    val referenceInfo = ReferenceInfoUtils.loadFrom(referenceInfoXMLFile)
    println(referenceInfo)
    val dsPath = Paths.get(c.referenceInfoXMLPath)
    val r = ReferenceEntry.loadFrom(referenceInfoXMLFile.toFile)
    val ds = converter(r)

    val outputPath = Paths.get(c.datasetXMLPath)

    println(s"Writing Reference dataset xml to ${outputPath.toAbsolutePath}")

    writeReferenceDataset(ds, outputPath)
    println(ds)
    Right(ToolSuccess(toolId, computeTimeDeltaFromNow(startedAt)))
  }
}

object ReferenceInfoToDataSetApp extends App {

  import ReferenceInfoToDataSetTool._

  runner(args)
}
