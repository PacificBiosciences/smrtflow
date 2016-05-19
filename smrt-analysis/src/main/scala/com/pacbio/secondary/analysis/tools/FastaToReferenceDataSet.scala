package com.pacbio.secondary.analysis.tools

import java.nio.file.Paths

import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.analysis.converters.FastaConverter._
import com.pacbio.secondary.analysis.externaltools.ExternalToolsUtils
import com.pacbio.secondary.analysis.legacy.ReferenceEntry
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}
import scopt.OptionParser

import scala.util.{Failure, Success, Try}


case class FastaToReferenceDataSetConfig(fastaFile: String,
                                         outputDir: String,
                                         name: String,
                                         organism: String,
                                         ploidy: String,
                                         onlyValidate: Boolean = false) extends LoggerConfig

/**
  * Convert a Fasta file to a ReferenceDataSet
  *
  * Calls samtools to create the fai
  * Calls sawriter to create suffix array
  * Optionally calls gmap to the create the gmapdb
  *
  * This should be depreciated in favor of FastaToReference
  * (which will generate the SMRT View index) files.
  * The old RS-era code to generate teh SMRT view index files should be
  * replaced with a pure scala implementation that is more efficient.
  *
  */
object FastaToReferenceDataSet extends CommandLineToolRunner[FastaToReferenceDataSetConfig] {

  import ExternalToolsUtils._
  val toolId = "pbscala.tools.fasta_to_dataset"
  val VERSION = "0.1.1"
  val defaults = FastaToReferenceDataSetConfig("", "", "", "", "")

  val parser = new OptionParser[FastaToReferenceDataSetConfig]("fasta-to-dataset") {
    head("Fasta file to Reference Dataset XML ", VERSION)
    note("Tool to convert a fasta to a Dataset XML (requires 'samtools' and 'sawriter' commandline exes in path)")

    arg[String]("fasta-file") required() action { (x, c) =>
      c.copy(fastaFile = x)
    } text "Path to Fasta file"

    arg[String]("output-dir") action { (x, c) =>
      c.copy(outputDir = x)
    } text "Path to output Pacbio Reference Dataset XML"

    opt[String]("name") action { (x, c) =>
      c.copy(name = x)
    } text "ReferenceSet Name"

    opt[String]("organism") action { (x, c) =>
      c.copy(organism = x)
    } text "Organism Name"

    opt[String]("ploidy") action { (x, c) =>
      c.copy(ploidy = x)
    } text "ploidy "

    opt[Unit]("only-validate") action {(x, c) =>
      c.copy(onlyValidate = true)
    }  text "Only validate the fasta with PacBio spec. Don't write ReferenceSet"

    // add the shared `--debug` and logging options
    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
  }

  def run(c: FastaToReferenceDataSetConfig): Either[ToolFailure, ToolSuccess] = {
    val startedAt = JodaDateTime.now()
    val fastaPath = Paths.get(c.fastaFile)
    val outputDir = Paths.get(c.outputDir)
    val name = Option(c.name)
    val ploidy = Option(c.ploidy)
    val organism = Option(c.organism)

    val rio = Try {
        if (c.onlyValidate) {
            fastaToReferenceSet(fastaPath)
        } else {
            createReferenceFromFasta(fastaPath, outputDir, name, organism, ploidy)
        }
    }

    rio match {
      case Success(x) =>
        println(rio)
        Right(ToolSuccess(toolId, computeTimeDeltaFromNow(startedAt)))
      case Failure(ex) => Left(ToolFailure(toolId, computeTimeDeltaFromNow(startedAt), ex.getMessage))
    }
  }
}

object FastaToReferenceDataSetApp extends App {

  import FastaToReferenceDataSet._

  runner(args)
}