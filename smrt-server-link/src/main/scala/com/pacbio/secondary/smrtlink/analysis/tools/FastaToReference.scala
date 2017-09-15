package com.pacbio.secondary.smrtlink.analysis.tools

import java.nio.file.Paths

import scala.util.{Failure, Success, Try}

import org.joda.time.{DateTime => JodaDateTime}
import scopt.OptionParser

import com.pacbio.common.models.{Constants => CommonConstants}
import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.smrtlink.analysis.converters.FastaToReferenceConverter

case class FastaToReferenceConfig(fastaFile: String,
                                  outputDir: String,
                                  name: String,
                                  organism: String,
                                  ploidy: String,
                                  skipNgmlr: Boolean = false)
    extends LoggerConfig

object FastaToReference extends CommandLineToolRunner[FastaToReferenceConfig] {

  val toolId = "pbscala.tools.fasta_to_reference"
  // Reuse this version for the commandline tool. This verison will be written to the description
  // in the dataset XML
  val VERSION = "0.6.0"
  val defaults = FastaToReferenceConfig("", "", "", "", "")
  val DESCRIPTION =
    s"""
      |Tool to convert a fasta file to a PacBio ReferenceSet DataSet XML
      |that contains the required index files:
      |- samtools index (fai)
      |- sawriter index (fasta.sa)
      |- ngmlr indices (.ngm)
      |
      |Requires exes 'sawriter' (can be installed from blasr tools) and
      |'ngmlr' (unless --skip-ngmlr is used)
      |
      |DataSet spec version: ${CommonConstants.DATASET_VERSION}
    """.stripMargin

  val parser = new OptionParser[FastaToReferenceConfig]("fasta-to-reference") {
    head("Convert a Fasta file to a PacBio ReferenceSet DataSet XML ", VERSION)
    note(DESCRIPTION)

    arg[String]("fasta-file") required () action { (x, c) =>
      c.copy(fastaFile = x)
    } text "Path to Fasta file"

    arg[String]("output-dir") action { (x, c) =>
      c.copy(outputDir = x)
    } text "Path to output Pacbio Reference Dataset XML"

    arg[String]("name") action { (x, c) =>
      c.copy(name = x)
    } text "ReferenceSet Name"

    opt[String]("organism") action { (x, c) =>
      c.copy(organism = x)
    } text "Organism Name"

    opt[String]("ploidy") action { (x, c) =>
      c.copy(ploidy = x)
    } text "ploidy "

    opt[Unit]("skip-ngmlr") action { (x, c) =>
      c.copy(skipNgmlr = true)
    } text "Skip generating NGMLR indices (for structural variants analysis)"

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show Options and exit"

    opt[Unit]("version") action { (x, c) =>
      showVersion
      sys.exit(0)
    } text "Show tool version and exit"

    opt[Unit]("debug") action { (_, c) =>
      c.asInstanceOf[LoggerConfig]
        .configure(c.logbackFile, c.logFile, true, c.logLevel)
        .asInstanceOf[FastaToReferenceConfig]
    } text "Display debugging log output"

    // add the shared `--debug` and logging options
    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
  }

  def run(c: FastaToReferenceConfig): Either[ToolFailure, ToolSuccess] = {

    val startedAt = JodaDateTime.now()
    val fastaPath = Paths.get(c.fastaFile)
    val outputDir = Paths.get(c.outputDir)
    val name = Option(c.name)
    val ploidy = Option(c.ploidy)
    val organism = Option(c.organism)

    FastaToReferenceConverter(c.name,
                              organism,
                              ploidy,
                              fastaPath,
                              outputDir,
                              skipNgmlr = c.skipNgmlr) match {
      case Right(rio) =>
        logger.info(
          s"Successfully converted Fasta to dataset ${rio.dataset.getName} ${rio.dataset.getUniqueId}")
        logger.info(
          s"TotalLength:${rio.dataset.getDataSetMetadata.getTotalLength} nrecords:${rio.dataset.getDataSetMetadata.getNumRecords}")
        Right(ToolSuccess(toolId, computeTimeDeltaFromNow(startedAt)))
      case Left(ex) =>
        Left(ToolFailure(toolId, computeTimeDeltaFromNow(startedAt), ex.msg))
    }
  }
}

object FastaToReferenceApp extends App {

  import FastaToReference._

  runner(args)
}
