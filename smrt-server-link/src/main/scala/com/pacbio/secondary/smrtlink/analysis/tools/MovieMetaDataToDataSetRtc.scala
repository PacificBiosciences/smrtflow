package com.pacbio.secondary.smrtlink.analysis.tools

import java.nio.file.Paths

import com.pacbio.common.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.smrtlink.analysis.contracts.ContractLoaders
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetWriter

import collection.JavaConversions._
import collection.JavaConverters._
import com.pacbio.secondary.smrtlink.analysis.converters.MovieMetadataConverter._
import org.joda.time.{DateTime => JodaDateTime}
import scopt.OptionParser

case class MovieMetaDataToDataSetRtcConfig(rtcAvroPath: String)
    extends LoggerConfig

object MovieMetaDataToDataSetRtcTool
    extends CommandLineToolRunner[MovieMetaDataToDataSetRtcConfig] {

  val toolId = "pbscala.tasks.rs_movie_to_ds_rtc"
  val VERSION = "0.2.0"
  val DESCRIPTION =
    "Convert a MovieMetadata To HdfSubread Dataset XML using Resolved Tool Contract"
  val defaults = MovieMetaDataToDataSetRtcConfig("")
  defaults.debug = true // keeping old debug default. most others are false

  val parser = new OptionParser[MovieMetaDataToDataSetRtcConfig](
    "movie-metadata-to-dataset-rtc") {
    head(DESCRIPTION, VERSION)
    note(
      "Tool to convert a RS movie.metadata.xml to a HdfSubreadSet Dataset XML using Resolved Tool Contract")

    arg[String]("resolved-tool-contract") required () action { (x, c) =>
      c.copy(rtcAvroPath = x)
    } text "Path to Resolved Tool Contract"

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

  def run(
      c: MovieMetaDataToDataSetRtcConfig): Either[ToolFailure, ToolSuccess] = {
    val startedAt = JodaDateTime.now()

    val rtcPath = Paths.get(c.rtcAvroPath)

    val rtc = ContractLoaders.loadResolvedToolContract(rtcPath)

    val inputFiles = rtc.getResolvedToolContract.getInputFiles.asScala.toList
    val movieMetaDataXMLPath = inputFiles.head.toString
    // Output Path
    val dsPath =
      rtc.getResolvedToolContract.getOutputFiles.asScala.toList.head.toString

    val x = convertMovieOrFofnToHdfSubread(movieMetaDataXMLPath)

    x match {
      case Right(ds) =>
        DataSetWriter.writeHdfSubreadSet(ds, Paths.get(dsPath))
        println(s"Successfully converted $movieMetaDataXMLPath")
        println(s"Writing HdfSubreadSet Dataset XML to $dsPath")
        Right(ToolSuccess(toolId, computeTimeDeltaFromNow(startedAt)))
      case Left(e) =>
        System.err.println(s"Failed to convert $movieMetaDataXMLPath")
        Left(ToolFailure(toolId, computeTimeDeltaFromNow(startedAt), e.msg))
    }
  }
}

object MovieMetaDataToDataSetRtcApp extends App {

  import MovieMetaDataToDataSetRtcTool._

  runner(args)
}
