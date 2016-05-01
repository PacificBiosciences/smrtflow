package com.pacbio.secondary.analysis.tools

import java.nio.file.Paths

import com.pacbio.secondary.analysis.converters.{FastaToReferenceConverter, ReferenceInfoConverter}
import com.pacbio.secondary.analysis.converters.FastaToReferenceConverter._
import org.joda.time.{DateTime => JodaDateTime}
import scopt.OptionParser

import scala.util.{Failure, Success, Try}


case class FastaToReferenceConfig(fastaFile: String,
                                  outputDir: String,
                                  name: String,
                                  organism: String,
                                  ploidy: String,
                                  debug: Boolean = false) extends ToolConfig


object FastaToReference extends CommandLineToolRunner[FastaToReferenceConfig] {

  val toolId = "pbscala.tools.fasta_to_reference"
  // Reuse this version for the commandline tool. This verison will be written to the description
  // in the dataset XML
  val VERSION = ReferenceInfoConverter.REF_INFO_TO_DS_VERSION
  val defaults = FastaToReferenceConfig("", "", "", "", "", debug = false)
  val DESCRIPTION =
    """
      |Tool to convert a fasta file to a SA3 ReferenceSet DataSet XML
      |that contains the required index files.
      |- samtools index (fai)
      |- sawriter index (fasta.sa)
      |- SMRT View indexes (fasta.config.index and fasta.index)
      |
      |Requires exes 'samtools' and 'sawriter' (can be installed from blasr tools)
      |
    """.stripMargin

  val parser = new OptionParser[FastaToReferenceConfig]("fasta-to-reference") {
    head("Convert a Fasta file to a SA 3.x ReferenceSet DataSet XML ", VERSION)
    note(DESCRIPTION)

    arg[String]("fasta-file") required() action { (x, c) =>
      c.copy(fastaFile = x)
    } text "Path to Fasta file"

    arg[String]("output-dir") action { (x, c) =>
      c.copy(outputDir = x)
    } text "Path to output Pacbio Reference Dataset XML"

    // This is required because of how the legacy ReferenceInfoXML works
    arg[String]("name") action { (x, c) =>
      c.copy(name = x)
    } text "ReferenceSet Name"

    opt[String]("organism") action { (x, c) =>
      c.copy(organism = x)
    } text "Organism Name"

    opt[String]("ploidy") action { (x, c) =>
      c.copy(ploidy = x)
    } text "ploidy "

    opt[Unit]('d', "debug") action { (x, c) =>
      c.copy(debug = true)
    } text "Emit logging to stdout."

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show Options and exit"

  }

  def run(c: FastaToReferenceConfig): Either[ToolFailure, ToolSuccess] = {

    val startedAt = JodaDateTime.now()
    val fastaPath = Paths.get(c.fastaFile)
    val outputDir = Paths.get(c.outputDir)
    val name = Option(c.name)
    val ploidy = Option(c.ploidy)
    val organism = Option(c.organism)

    FastaToReferenceConverter(c.name, organism, ploidy, fastaPath, outputDir) match {
      case Right(rio) =>
        logger.info(s"Successfully converted Fasta to dataset ${rio.dataset.getName} ${rio.dataset.getUniqueId}")
        logger.info(s"TotalLength:${rio.dataset.getDataSetMetadata.getTotalLength} nrecords:${rio.dataset.getDataSetMetadata.getNumRecords}")
        Right(ToolSuccess(toolId, computeTimeDeltaFromNow(startedAt)))
      case Left(ex) => Left(ToolFailure(toolId, computeTimeDeltaFromNow(startedAt), ex.msg))
    }
  }
}

object FastaToReferenceApp extends App {

  import FastaToReference._

  runner(args)
}
