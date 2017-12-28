package com.pacbio.secondary.smrtlink.analysis.tools

import java.io.{BufferedWriter, File, FileWriter}
import java.nio.file.{Path, Paths}
import java.util

import scopt.OptionParser

import scala.collection.JavaConverters._
import com.pacbio.common.models.contracts.ResolvedToolContract
import com.pacbio.common.models.contracts._
import com.pacbio.common.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.contracts.ContractLoaders
import com.typesafe.scalalogging.LazyLogging

/**
  * Example Commandline Tool that leverages the TC/RTC interface
  *
  * The commandline interface is a little awkward because tool
  *
  * Generates a Fasta File
  *
  * Created by mkocher on 6/24/16.
  */
object ExampleToolsConstants {

  sealed trait Mode { val name: String }
  case object RUN extends Mode { val name = "run" }
  case object RUN_RTC extends Mode { val name = "run-rtc" }
  case object EMIT_TC extends Mode { val name = "emit-tc" }

  final val TOOL_ID = "smrtflow.tasks.example_tool"
  final val TOOL_NAME = "Example Tool"
  final val VERSION = "0.2.0"
  final val DESCRIPTION =
    """
      |Example Tool that generates a fasta file from a txt file
    """.stripMargin

  final val NUM_RECORDS_OPT_ID = "smrtflow.task_options.num_records"
  final val NUM_RECORDS_DEFAULT = 100

  // The input file is a sentinel/dummy file
  case class ExampleToolOptions(inputTxtFile: Option[Path] = Some(
                                  Paths.get("input.txt")),
                                outputFastaFile: Path,
                                outputToolContract: Path,
                                numRecords: Int = NUM_RECORDS_DEFAULT,
                                mode: Mode = RUN,
                                command: ExampleToolOptions => Unit = println,
                                rtc: Path = null)

  final val DEFAULTS = ExampleToolOptions(
    Some(Paths.get("input.txt")),
    null,
    Paths.get(s"${TOOL_ID}_tool_contract.json"),
    NUM_RECORDS_DEFAULT)

}

/**
  * Define Tool Contract Here using Avro classes
  *
  * The avro generated classes have a toString method that will emit the JSON representation.
  *
  */
trait ExampleToolEmitToolContract {

  import ExampleToolsConstants._

  def inputFileTypes: List[ToolInputFile] =
    List(
      new ToolInputFile("txt",
                        FileTypes.TXT.fileTypeId,
                        "Txt File",
                        "Example Input Txt file description"))

  def outputFileTypes: Seq[ToolOutputFile] =
    Seq(
      new ToolOutputFile("fasta",
                         FileTypes.FASTA.fileTypeId,
                         "Fasta",
                         "file",
                         "Random Fasta File Output"))

  val pbInt: PacBioOptionType = PacBioOptionType.integer

  def taskOptionNumRecords =
    PacBioOption
      .newBuilder()
      .setName("NumRecords")
      .setDescription("Number of Fasta Record to generate")
      .setOptionId(NUM_RECORDS_OPT_ID)
      .setDefault$(50)
      .setType(PacBioOptionType.integer)
      .build()

  def taskOptions: PacBioOptions =
    PacBioOptions
      .newBuilder()
      .setPbOption(taskOptionNumRecords)
      .build()

  def toolContractTask: ToolContractTask = {

    // Translation of types to get the java API to work
    val inputFiles: java.util.List[ToolInputFile] =
      new util.ArrayList[ToolInputFile](inputFileTypes.asJavaCollection)
    val outputFiles: java.util.List[ToolOutputFile] =
      new util.ArrayList[ToolOutputFile](outputFileTypes.asJavaCollection)
    val resources: java.util.List[CharSequence] =
      new util.ArrayList[CharSequence]()
    val opts: java.util.List[PacBioOptions] =
      new util.ArrayList[PacBioOptions](util.Arrays.asList(taskOptions))

    ToolContractTask
      .newBuilder()
      .setIsDistributed(false)
      .setNproc(1)
      .setToolContractId(TOOL_ID)
      .setName(TOOL_NAME)
      .setDescription(DESCRIPTION)
      .setInputTypes(inputFiles)
      .setOutputTypes(outputFiles)
      .setTaskType("pbsmrtpipe.task_types.standard")
      .setResourceTypes(resources)
      .setSchemaOptions(opts)
      .build()
  }

  def toolContract: ToolContract = {
    ToolContract
      .newBuilder()
      .setDriver(new ToolDriver("smrtflow-example-tool run-rtc ", "avro"))
      .setToolContract(toolContractTask)
      .setVersion(VERSION)
      .setToolContractId(TOOL_ID) // This is duplicated for unclear reasons
      .build()
  }
}

object ExampleTool extends LazyLogging with ExampleToolEmitToolContract {

  import ExampleToolsConstants._

  def showDefaults(c: ExampleToolOptions): Unit = println(s"Defaults $c")

  // This is the Main function. This should be imported from library code
  def run(outputFastaFile: Path, numRecords: Int): Int = {
    println(s"Running $TOOL_ID v$VERSION")
    println(s"Writing $numRecords Fasta records to $outputFastaFile")

    val bw = new BufferedWriter(new FileWriter(outputFastaFile.toFile))
    (0 until numRecords).foreach { x =>
      bw.write(s">record_$x\nACGT\n")
    }
    bw.close()
    logger.info(s"wrote $numRecords to $outputFastaFile")
    0
  }

  def runRtc(rtc: ResolvedToolContract): Int = {
    logger.info(
      s"Loaded RTC with ToolContractId ${rtc.getResolvedToolContract.getToolContractId}")

    // Not using this because
    val inputTxt =
      Paths.get(
        rtc.getResolvedToolContract.getInputFiles.asScala.head.toString)

    val outputFasta =
      Paths.get(
        rtc.getResolvedToolContract.getOutputFiles.asScala.head.toString)

    // Is there a better way to do this in a type safe manner?
    // In local tests, this will just cast to 0 which is wrong.
    // FIXME(mpkocher)(2016-7-16) Fix this casting issue
    val numRecords = rtc.getResolvedToolContract.getOptions
      .get(NUM_RECORDS_OPT_ID)
      .asInstanceOf[Int]

    run(outputFasta, numRecords)
  }

  // Utils from Parser+ Config
  def runRtcFrom(c: ExampleToolOptions): Int = {
    println(s"Running RTC with $c")
    logger.info(s"Loading resolved tool contract Avro file from ${c.rtc}")
    val rtc = ContractLoaders.loadResolvedToolContract(c.rtc)
    logger.info(
      s"Resolved tool contract Id ${rtc.getResolvedToolContract.getToolContractId}")
    runRtc(rtc)
  }

  def runFrom(c: ExampleToolOptions): Unit = {
    println(s"Running from RUN $c")
    run(c.outputFastaFile, c.numRecords)
  }

  def runEmitTc(c: ExampleToolOptions) = {
    val bw = new BufferedWriter(new FileWriter(c.outputToolContract.toFile))
    bw.write(toolContract.toString)
    bw.close()
    logger.info(s"wrote tool contract to ${c.outputToolContract}")
    0
  }

}

trait ExampleToolParser {

  import ExampleTool._
  import ExampleToolsConstants._

  val parser = new OptionParser[ExampleToolOptions]("example-tool") {
    head("Example Tool")
    note(DESCRIPTION)

    cmd(RUN.name) action { (_, c) =>
      c.copy(command = (c) => runFrom(c), mode = RUN)
    } children (
      arg[File]("output-fasta") action { (s, c) =>
        c.copy(outputFastaFile = s.toPath)
      } text "Path to output Fasta File",
      opt[File]('i', "input-txt") action { (s, c) =>
        c.copy(inputTxtFile = Some(s.toPath))
      } text "Optional Path to input.txt file",
      opt[Int]('n', "num-records") action { (s, c) =>
        c.copy(numRecords = s)
      } text "Number of Records to write"
    )

    cmd(RUN_RTC.name) action { (_, c) =>
      c.copy(command = (c) => runRtcFrom(c), mode = RUN_RTC)
    } children (
      arg[File]("resolved-tool-contract") action { (s, c) =>
        c.copy(rtc = s.toPath)
      } text "Path to Resolved Tool Contract"
    )

    cmd(EMIT_TC.name) action { (_, c) =>
      c.copy(command = (c) => runEmitTc(c), mode = EMIT_TC)
    } children (
      opt[File]('o', "output-tc") action { (s, c) =>
        c.copy(outputToolContract = s.toPath)
      } text "Output path to Tool Contract"
    )

    // Not sure this works
    LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])
  }
}

trait ExampleToolRunner extends ExampleToolParser {

  import ExampleTool._
  import ExampleToolsConstants._

  def runner(args: Array[String]): Unit = {

    val result = parser.parse(args, DEFAULTS) map { config =>
      config.mode match {
        case RUN => runFrom(config); 0
        case RUN_RTC => runRtcFrom(config); 0
        case EMIT_TC => runEmitTc(config); 0
      }
    }

    val exitCode = result.getOrElse(1)
    System.exit(exitCode)
  }
}

object ExampleToolApp extends App with ExampleToolRunner {
  runner(args)
}
