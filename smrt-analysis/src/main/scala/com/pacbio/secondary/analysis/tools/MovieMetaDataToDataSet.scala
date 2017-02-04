package com.pacbio.secondary.analysis.tools

import java.nio.file.Paths

import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.analysis.converters.MovieMetadataConverter._
import com.pacbio.secondary.analysis.datasets.io.DataSetWriter
import org.joda.time.{DateTime => JodaDateTime}
import scopt.OptionParser

case class MovieMetaDataToDataSetConfig(
    movieMetadataXMLPath: String,
    datasetXMLPath: String) extends LoggerConfig

object MovieMetaDataToDataSetTool extends CommandLineToolRunner[MovieMetaDataToDataSetConfig] {

  val toolId = "pbscala.tools.rs_movie_to_ds"
  val VERSION = "0.3.0"
  val defaults = MovieMetaDataToDataSetConfig("", "")
  defaults.debug = true // keeping old debug default. most others are false

  val parser = new OptionParser[MovieMetaDataToDataSetConfig]("movie-metadata-to-dataset") {
    head("MovieMetadata To Hdf5 Subread Dataset XML ", VERSION)
    note("Tool to convert a RS movie.metadata.xml to a Hdf5 Dataset XML")

    arg[String]("movie-metadata-xml") required() action { (x, c) =>
      c.copy(movieMetadataXMLPath = x)
    } text "Path to Pacbio RS Movie metadata.xml (or a fofn of RS Movie Metadata.xml files) "

    arg[String]("subread-dataset-xml") action { (x, c) =>
      c.copy(datasetXMLPath = x)
    } text "Path to output Subread Dataset XML"

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

  def run(c: MovieMetaDataToDataSetConfig): Either[ToolFailure, ToolSuccess] = {
    val startedAt = JodaDateTime.now()

    val dsPath = Paths.get(c.datasetXMLPath)

    val movieMetaDataXMLPath = c.movieMetadataXMLPath
    val x = convertMovieOrFofnToHdfSubread(movieMetaDataXMLPath)

    x match {
      case Right(ds) =>
        DataSetWriter.writeHdfSubreadSet(ds, dsPath)
        println(s"Successfully converted $movieMetaDataXMLPath")
        println(s"Writing HdfSubreadSet Dataset XML to $dsPath")
        Right(ToolSuccess(toolId, computeTimeDeltaFromNow(startedAt)))
      case Left(e) =>
        System.err.println(s"Failed to convert $movieMetaDataXMLPath")
        Left(ToolFailure(toolId, computeTimeDeltaFromNow(startedAt), e.msg))
    }
  }
}

object MovieMetaDataToDataSetApp extends App {

  import MovieMetaDataToDataSetTool._

  runner(args)
}