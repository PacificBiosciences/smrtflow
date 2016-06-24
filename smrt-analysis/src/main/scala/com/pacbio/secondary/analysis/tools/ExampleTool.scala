package com.pacbio.secondary.analysis.tools

import java.io.{BufferedWriter, File, FileWriter}
import java.nio.file.{Path, Paths}

import scopt.OptionParser

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import com.pacbio.common.models.contracts.ResolvedToolContract
import com.pacbio.common.models.contracts._
import com.pacbio.logging.{LoggerConfig, LoggerOptions}
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.contracts.ContractLoaders
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

  sealed trait Mode { val name: String}
  case object RUN extends Mode { val name = "run"}
  case object RUN_RTC extends Mode {val name = "run-rtc"}
  case object EMIT_TC extends Mode {val name = "emit-tc"}

  final val TOOL_ID = "smrtflow.tasks.example_tool"
  final val VERSION = "0.1.0"
  final val DESCRIPTION =
    """
      |Example Tool that generates a fasta file from a txt file
    """.stripMargin

  final val NUM_RECORDS_OPT_ID = "smrtflow.task_options.num_records"
  final val NUM_RECORDS_DEFAULT = 100

  case class ExampleToolOptions(inputTxtFile: Option[Path] = Some(Paths.get("input.txt")),
                                outputFastaFile: Path,
                                numRecords: Int = NUM_RECORDS_DEFAULT,
                                mode: Mode = RUN,
                                command: ExampleToolOptions => Unit = println,
                                rtc: Path = null)

  final val DEFAULTS = ExampleToolOptions(Some(Paths.get("input.txt")), null, NUM_RECORDS_DEFAULT)

}


/**
  * Define Tool Contract Here using Avro classes
  *
  * The avro generated classes have a toString method that will emit the JSON representation.
  *
  */
trait ExampleToolEmitToolContract {
  def inputFileTypes: Seq[ToolInputFile] =
    Seq(new ToolInputFile("txt", FileTypes.TXT.fileTypeId, "Txt File", "Example Input Txt file description"))


  def outputFileTypes: Seq[ToolOutputFile] =
    Seq(new ToolOutputFile("fasta", FileTypes.FASTA.fileTypeId, "Fasta", "file", "Random Fasta File Output"))

  // This is clearly not correct
  //def taskOptions: Seq[PacBioOptions] =
  //  Seq(PacBioOptions.newBuilder().setPbOption()

  def toolContractTask: ToolContractTask = {
    ToolContractTask.newBuilder()
        .setIsDistributed(false)
        .setNproc(1)
        .setToolContractId(ExampleToolsConstants.TOOL_ID)
        .setInputTypes(inputFileTypes)
        .setOutputTypes(outputFileTypes)
            .setTaskType("task-type")
            .setResourceTypes(Seq.empty[String])
            .setSchemaOptions(Seq.empty[PacBioOptions])
        .build()
  }

  def toolContract: ToolContract = {
    ToolContract.newBuilder()
        .setDriver(new ToolDriver("example-tool run-rtc "))
        .setToolContract(toolContractTask)
        .build()
  }
}


object ExampleTool extends LazyLogging with ExampleToolEmitToolContract{

  import ExampleToolsConstants._

  def showDefaults(c: ExampleToolOptions): Unit = println(s"Defaults $c")

  // This is the Main function. This should be imported from library code
  def run(outputFastaFile: Path, numRecords: Int): Int = {
    val bw = new BufferedWriter(new FileWriter(outputFastaFile.toFile))
    (0 until numRecords).foreach { x =>
      bw.write(s">record_$x\nACGT\n")
    }
    bw.close()
    0
  }

  def runRtc(rtc: ResolvedToolContract): Int = {
    logger.info(s"Loaded RTC with ToolContractId ${rtc.getResolvedToolContract.getToolContractId}")

    // Not using this because
    val inputTxt = Paths.get(rtc.getResolvedToolContract.getInputFiles.head.toString)
    //
    val outputFasta = Paths.get(rtc.getResolvedToolContract.getInputFiles.head.toString)

    // Make this type safe?
    val numRecordsOpt = rtc.getResolvedToolContract.getOptions.get(NUM_RECORDS_OPT_ID)

    run(outputFasta, 100)
  }

  // Utils from Parser+ Config
  def runRtcFrom(c: ExampleToolOptions): Int = {
    val rtc = ContractLoaders.loadResolvedToolContract(c.rtc)
    runRtc(rtc)
  }

  def runFrom(c: ExampleToolOptions): Unit = {
    println(s"Running from RUN $c")
    run(c.outputFastaFile, c.numRecords)
    0
  }

  def runEmitTc(c: ExampleToolOptions) = {
    // toString automatically converts it to JSON
    println(toolContract.toString)
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
    } children(
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


    //LoggerOptions.add(this.asInstanceOf[OptionParser[LoggerConfig]])

    cmd(RUN_RTC.name) action { (_, c) =>
      c.copy(command = (c) => runRtcFrom(c), mode = RUN_RTC)
    } children(
        arg[File]("resolved-tool-contract") action { (s, c) =>
          c.copy(rtc = s.toPath)
        } text "Path to Resolved Tool Contract"
        )

    cmd(EMIT_TC.name) action { (_, c) =>
      c.copy(command = (c) => runEmitTc(c), mode = EMIT_TC)
    } text "Emit Tool Contract to Stdout"

  }
}

trait ExampleToolRunner extends ExampleToolParser{

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


object ExampleToolApp extends App with ExampleToolRunner{
  runner(args)
}
