package com.pacbio.secondary.analysis.tools

import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.datasets.validators.ValidateReferenceSet

import collection.JavaConversions._
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes.HdfSubread
import com.pacbio.secondary.analysis.datasets.io.{DataSetLoader, DataSetValidator}
import com.pacificbiosciences.pacbiodatasets._
import org.joda.time.{DateTime => JodaDateTime}
import java.nio.file.{Path, Paths}

import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import scopt.OptionParser

import scala.util.Try


case class ValidatorConfig(dsPath: String) extends LoggerConfig

object ValidateDataSetRunner extends CommandLineToolRunner[ValidatorConfig] {

  val toolId = "pbscala.tools.validate_datasets"
  val VERSION = "0.1.2"
  val defaults = ValidatorConfig("")

  val parser = new OptionParser[ValidatorConfig]("validate-datasets") {
    head("Validate DataSet XMLs By type and Display DataSet Summary", VERSION)

    arg[String]("dataset-xml") required() action { (x, c) =>
      c.copy(dsPath = x)
    } text "Path to DataSet XML"

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show Options and exit"

    // add the shared `--debug` and logging options
    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
  }

  /**
   * Peek into the XML file to extract the MetaType
   *
   * @param path
   * @return
   */
  private def peekIntoXML(path: String): Option[DataSetMetaTypes.DataSetMetaType] = {
    Try {
      val root = scala.xml.XML.loadFile(path)
      val dsMetaType = (root \ "@MetaType").text
      DataSetMetaTypes.toDataSetType(dsMetaType)
    } getOrElse None
  }

  def run(config: ValidatorConfig): Either[ToolFailure, ToolSuccess] = {
    val startedAt = JodaDateTime.now()

    val p = Paths.get(config.dsPath).toAbsolutePath

    val supportedDataSetTypes = DataSetMetaTypes.ALL

    val dsType = peekIntoXML(config.dsPath)
    val dsTypeError = dsType.getOrElse("UNKNOWN")

    val errorMesssage = s"Unsupported dataset type id '$dsTypeError' Supported types $supportedDataSetTypes"
    // For external resources that we defined relative to the XML file
    val rootDir = p.getParent

    def toResult[T <: DataSetType](px: Path, result: Either[String, T]) = result match {
      case Right(x) => Right(ToolSuccess(s"Successfully validated ${px.toAbsolutePath}", computeTimeDeltaFromNow(startedAt)))
      case Left(ex) => Left(ToolFailure(toolId, computeTimeDeltaFromNow(startedAt), ex))
    }

    def vx[T <: DataSetType](loaderF: (Path => T), validatorF:((T, Path) => Either[String, T])): (Path => Either[ToolFailure, ToolSuccess]) = { px =>
      logger.info(s"Loading dataset from ${p}")
      val ds = loaderF(px)
      logger.info("\n" + DataSetValidator.summarize(ds))
      toResult[T](px, validatorF(loaderF(px), rootDir))
    }

    val validateReferenceSet = vx[ReferenceSet](DataSetLoader.loadAndResolveReferenceSet, DataSetValidator.validate)
    val validateSubreadSet = vx[SubreadSet](DataSetLoader.loadAndResolveSubreadSet, DataSetValidator.validate)
    val validateHdfSubreadSet = vx[HdfSubreadSet](DataSetLoader.loadAndResolveHdfSubreadSet, DataSetValidator.validate)
    val validateAlignmentSet = vx[AlignmentSet](DataSetLoader.loadAlignmentSet, DataSetValidator.validate)
    val validateCCSReadSet = vx[ConsensusReadSet](DataSetLoader.loadConsensusReadSet, DataSetValidator.validate)
    val validateBarcodeSet = vx[BarcodeSet](DataSetLoader.loadBarcodeSet, DataSetValidator.validate)
    val validateConsensusAlignmentSet = vx[ConsensusAlignmentSet](DataSetLoader.loadConsensusAlignmentSet, DataSetValidator.validate)
    val validateContigSet = vx[ContigSet](DataSetLoader.loadContigSet, DataSetValidator.validate)
    val validateGmapReferenceSet = vx[GmapReferenceSet](DataSetLoader.loadAndResolveGmapReferenceSet, DataSetValidator.validate)

    dsType match {
      case Some(metatype) =>
        logger.info(s"Validating $metatype dataset ${p.toAbsolutePath}")
        metatype match {
          case DataSetMetaTypes.Reference => validateReferenceSet(p)
          case DataSetMetaTypes.Subread => validateSubreadSet(p)
          case DataSetMetaTypes.HdfSubread => validateHdfSubreadSet(p)
          case DataSetMetaTypes.Alignment => validateAlignmentSet(p)
          case DataSetMetaTypes.CCS => validateContigSet(p)
          case DataSetMetaTypes.AlignmentCCS => validateConsensusAlignmentSet(p)
          case DataSetMetaTypes.Barcode => validateBarcodeSet(p)
          case DataSetMetaTypes.Contig => validateContigSet(p)
          case DataSetMetaTypes.GmapReference => validateGmapReferenceSet(p)
        }
      case _ => Left(ToolFailure(toolId, computeTimeDeltaFromNow(startedAt), errorMesssage))
    }
  }
}

object ValidateDataSetApp extends App {

  import ValidateDataSetRunner._

  runner(args)
}
