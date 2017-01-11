package com.pacbio.secondary.analysis.pbsmrtpipe

import java.io.{FileWriter, BufferedWriter, PrintWriter}
import java.net.URI
import java.nio.file.Path

import scala.xml.{Elem, Node}
import scala.io.Source

import com.typesafe.scalalogging.LazyLogging
import spray.json._

import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.tools.CommandLineUtils

/**
 *
 * Created by mkocher on 9/26/15.
 */
object IOUtils extends LazyLogging{

  val pbsmrtpipeExe = CommandLineUtils.which("pbsmrtpipe") getOrElse {
    logger.warn(s"Unable to find pbsmrtpipe exe")
    "pbsmrtpipe"
  }

  /**
   * Parses and converts the pipeline engine level "options"
   *
   * @param rnode
   * @return
   */
  private def parseEngineOptions(rnode: scala.xml.Elem): Seq[PipelineBaseOption] = {

    /**
     * Cast String to bool. Default to false if can't parse value.
     *
     * (False|false) and {True|true) are supported.
     *
     * @param sx
     * @return
     */
    def castInt(sx: String): Boolean = {
      sx.replace("\n", "").replace(" ", "").toLowerCase match {
        case "true" => true
        case "false" => false
        case _ => false
      }
    }

    // Convert the Raw values and Cast to correct type
    def toV(optionId: String, optionValue: String): Option[PipelineBaseOption] = {
      PbsmrtpipeEngineOptions().getPipelineOptionById(optionId).map {
        case o: PipelineBooleanOption => o.copy(value = castInt(optionValue))
        case o: PipelineIntOption => o.copy(value = optionValue.toInt)
        case o: PipelineDoubleOption => o.copy(value = optionValue.toDouble)
        case o: PipelineStrOption => o.copy(value = optionValue)
      }
    }

    (rnode \ "options" \ "option")
      .map(x => ((x \ "@id").text, (x \ "value").text))
      .flatMap(x => toV(x._1, x._2))
  }

  /**
   * Parse the pbsmrtpipe Preset XML
   *
   * @param path
   * @return
   */
  def parsePresetXml(path: Path): (Seq[PipelineBaseOption], Seq[PipelineBaseOption]) = {
    val x = scala.xml.XML.loadFile(path.toFile)
    val engineOptions = parseEngineOptions(x)
    // FIXME The Pipeline Level Options are not supported in the preset.xml yet
    val defaultTaskOptions = Seq[PipelineBaseOption]()
    (engineOptions, defaultTaskOptions)
  }

  // FIXME this is super hacky - pbcommand/pbsmrtpipe currently expect the
  // preset options to be supplied as dictionaries rather than serialized
  // models.  We should consolidate on something similar to the service task
  // options in smrt-server-link.
  def writePresetJson(
      path: Path,
      pipelineId: String,
      workflowOptions: Seq[PipelineBaseOption],
      taskOptions: Seq[PipelineBaseOption]): Path = {
    logger.info(s"Engine opts: $workflowOptions")
    logger.info(s"Task   opts: $taskOptions")
    val jsPresets = JsObject(
      "pipelineId" -> JsString(pipelineId),
      "presetId" -> JsString("smrtlink-job"),
      "name" -> JsString("smrtlink-job-settings"),
      "description" -> JsString("Pipeline settings from SMRT Link"),
      "options" -> JsObject(workflowOptions.map(o =>
        o match {
          case PipelineStrOption(id,_,value,_) => (id, JsString(value))
          case PipelineIntOption(id,_,value,_) => (id, JsNumber(value))
          case PipelineDoubleOption(id,_,value,_) => (id, JsNumber(value))
          case PipelineBooleanOption(id,_,value,_) => (id, JsBoolean(value))
          case PipelineChoiceStrOption(id,_,value,_,_) => (id, JsString(value))
          case PipelineChoiceIntOption(id,_,value,_,_) => (id, JsNumber(value))
          case PipelineChoiceDoubleOption(id,_,value,_,_) => (id, JsNumber(value))
        }).toMap),
      "taskOptions" -> JsObject(taskOptions.map(o =>
        o match {
          case PipelineStrOption(id,_,value,_) => (id, JsString(value))
          case PipelineIntOption(id,_,value,_) => (id, JsNumber(value))
          case PipelineDoubleOption(id,_,value,_) => (id, JsNumber(value))
          case PipelineBooleanOption(id,_,value,_) => (id, JsBoolean(value))
          case PipelineChoiceStrOption(id,_,value,_,_) => (id, JsString(value))
          case PipelineChoiceIntOption(id,_,value,_,_) => (id, JsNumber(value))
          case PipelineChoiceDoubleOption(id,_,value,_,_) => (id, JsNumber(value))
        }).toMap))
    val pw = new PrintWriter(path.toFile)
    pw.write(jsPresets.prettyPrint)
    pw.close
    path.toAbsolutePath
  }

  // FIXME even hackier!
  def parsePresetJson(path: Path): (Seq[PipelineBaseOption], Seq[PipelineBaseOption]) = {
    val jsonSrc = Source.fromFile(path.toFile).getLines.mkString
    val jsonAst = jsonSrc.parseJson
    def toV(optionId: String, jsValue: JsValue): Option[PipelineBaseOption] = {
      PbsmrtpipeEngineOptions().getPipelineOptionById(optionId).map {
        case o: PipelineBooleanOption => o.copy(value = jsValue.asInstanceOf[JsBoolean].value)
        case o: PipelineIntOption => o.copy(value = jsValue.asInstanceOf[JsNumber].value.toInt)
        case o: PipelineDoubleOption => o.copy(value = jsValue.asInstanceOf[JsNumber].value.toDouble)
        case o: PipelineStrOption => o.copy(value = jsValue.asInstanceOf[JsString].value)
      }
    }
    val engineOptions: Seq[PipelineBaseOption] = jsonAst.asJsObject.getFields("options") match {
      case Seq(JsObject(options)) =>
        (for {
          (optionId, jsValue) <- options
          opt <- toV(optionId, jsValue)
        } yield opt).toList
      case x => throw new Exception(s"Can't process $x")
    }
    // FIXME The Pipeline Level Options are not supported in the preset.json yet
    val defaultTaskOptions = Seq[PipelineBaseOption]()
    (engineOptions, defaultTaskOptions)
  }

  /**
   * Convert the require value to pbsmrtpipe commandline exe
   * and writes the preset.json
   *
   */
  def toCmd(
      entryPoints: Seq[BoundEntryPoint],
      pipelineId: String,
      outputDir: Path,
      taskOptions: Seq[PipelineBaseOption],
      workflowOptions: Seq[PipelineBaseOption],
      serviceUri: Option[URI]) = {

    val e = entryPoints.map(x => s"-e '${x.entryId}:${x.path.toString}'").fold("")((a, b) => s"$a $b")

    val presetJsonPath = outputDir.resolve("preset.json")
    writePresetJson(presetJsonPath, pipelineId, workflowOptions, taskOptions)

    val serviceStr = serviceUri match {
      case Some(x) => s"--service-uri ${x.toString}"
      case _ => ""
    }

    s"$pbsmrtpipeExe pipeline-id $pipelineId $e --preset-json=${presetJsonPath.toString} --output-dir=${outputDir.toAbsolutePath.toString} $serviceStr "
  }


  def toShell(cmd: String, envShell: Option[Path]): String = {
    val e = envShell match {
      case Some(x) => s"source ${x.toAbsolutePath}"
      case _ => "# no custom ENV provided"
    }
    Seq("#!bin/bash", e, cmd).fold("")((a, b) => a + b + "\n")
  }

  /**
   * Write the jobOptions Wrapper shell script
   *
   * @param path Path to write jobOptions.sh wrapper script to
   * @return
   */
  def writeJobShellWrapper(path: Path, cmd: String, env: Option[String]): Path = {
    val bw = new BufferedWriter(new FileWriter(path.toFile))

    val envStr = env match {
      case Some(e) if !e.isEmpty => s"source $e"
      case _ => "# no custom ENV defined"
    }

    bw.write(envStr + "\n")
    bw.write(cmd + "\n")
    bw.close()
    path.toAbsolutePath
  }

  /**
   * Writes a mock bound entry point
   *
   * @param path
   * @return
   */
  def writeMockBoundEntryPoints(path: Path): Path = {
    val bw = new BufferedWriter(new FileWriter(path.toFile))
    val s = (0 until 10).map(x => s"record_$x").foldLeft("")((a, b) => a + "\n" + b)
    bw.write(s)
    bw.close()
    path
  }

  
}
