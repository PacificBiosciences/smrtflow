package com.pacbio.secondary.analysis.tools

import java.nio.file.{Paths,Files}

import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.analysis.converters.GmapReferenceConverter
import com.pacbio.secondary.analysis.externaltools.ExternalToolsUtils

import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}
import scopt.OptionParser

import scala.util.{Failure, Success, Try}


case class FastaToGmapReferenceSetConfig(
    fastaFile: String,
    outputDir: String,
    name: String,
    organism: String,
    ploidy: String,
    inPlace: Boolean = false) extends LoggerConfig

object FastaToGmapReferenceSet extends CommandLineToolRunner[FastaToGmapReferenceSetConfig] {

  import ExternalToolsUtils._
  val toolId = "pbscala.tools.fasta_to_gmap_reference"
  val VERSION = "0.1.1"
  val defaults = FastaToGmapReferenceSetConfig("", "", "", "", "")

  val parser = new OptionParser[FastaToGmapReferenceSetConfig]("fasta-to-gmap-reference") {
    head("Fasta file to GMAP Reference Dataset XML ", VERSION)
    note("Tool to generate GMAP database and convert a fasta to a Dataset XML (requires 'gmap_build' commandline exe in path)")

    arg[String]("fasta-file") required() action { (x, c) =>
      c.copy(fastaFile = x)
    } text "Path to Fasta file"

    arg[String]("output-dir") action { (x, c) =>
      c.copy(outputDir = x)
    } text "Path to write GMAP database and dataset XML"

    arg[String]("name") action { (x, c) =>
      c.copy(name = x)
    } text "GmapReferenceSet Name"

    opt[String]("organism") action { (x, c) =>
      c.copy(organism = x)
    } text "Organism Name"

    opt[String]("ploidy") action { (x, c) =>
      c.copy(ploidy = x)
    } text "ploidy "

    opt[Unit]("in-place") action { (x, c) =>
      c.copy(inPlace = true)
    } text "Don't copy input FASTA file to output location"

    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])

    opt[Unit]('h', "help") action { (x, c) =>
      showUsage
      sys.exit(0)
    } text "Show options and exit"
  }

  def run(c: FastaToGmapReferenceSetConfig): Either[ToolFailure, ToolSuccess] = {
    val startedAt = JodaDateTime.now()
    val fastaPath = Paths.get(c.fastaFile)
    val outputDir = Paths.get(c.outputDir)
    val ploidy = Option(c.ploidy)
    val organism = Option(c.organism)

    Try {
      if (! Files.exists(outputDir)) throw new Exception(s"The output directory '${outputDir.toString}' does not exist; please create it or specify an already existing path.")
      GmapReferenceConverter(c.name, fastaPath, outputDir, organism, ploidy, c.inPlace)
    } match {
      case Success(x) => x match {
        case Right(ofn) =>
          println(s"Wrote dataset to ${ofn}")
          Right(ToolSuccess(toolId, computeTimeDeltaFromNow(startedAt)))
        case Left(ex) => Left(ToolFailure(toolId, computeTimeDeltaFromNow(startedAt), ex.getMessage))
      }
      case Failure(ex) => Left(ToolFailure(toolId, computeTimeDeltaFromNow(startedAt), ex.getMessage))
    }
  }
}

object FastaToGmapReferenceSetApp extends App {

  import FastaToGmapReferenceSet._

  runner(args)
}
