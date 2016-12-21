package com.pacbio.secondary.analysis.pbsmrtpipe

import java.io.{FileWriter, BufferedWriter}
import java.net.URI
import java.nio.file.Path

import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.tools.CommandLineUtils
import com.typesafe.scalalogging.LazyLogging

import scala.xml.{Elem, Node}

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

  /**
   * Convert a List of Pipeline Engine Options and Task Options to pbsmrtpipe XML node
   *
   * @param options
   * @param taskOptions
   * @return
   */
  def toPresetXml(options: Seq[PipelineBaseOption], taskOptions: Seq[PipelineBaseOption]): Node = {

    def toOptionXml(o: PipelineBaseOption) = {
      <option id={o.id}>
        <value>{o match {
          case opt: PipelineStrOption => if (o.value == "") "\"\"" else o.value
          case _ => o.value
        }}</value>
      </option>
    }

    val root =
      <preset-template-xml>
        <options>
          {options.map(x => toOptionXml(x))}
        </options>
        <task-options>
          {taskOptions.map(x => toOptionXml(x))}
        </task-options>
      </preset-template-xml>
    root
  }

  /**
   * Write the
   *
   * @param path
   * @param node
   * @return
   */
  def writePresetXml(path: Path, node: Node): Path = {
    val p = new scala.xml.PrettyPrinter(80, 4)
    scala.xml.XML.save(path.toAbsolutePath.toString, node, "UTF-8", true, null)
    //val p = new scala.xml.PrettyPrinter(80, 4)
    //p.format(node)
    logger.debug(s"Writing preset to ${path.toAbsolutePath.toString}")
    path.toAbsolutePath
  }

  def writeOptionsToPresetXML(options: Seq[PipelineBaseOption], taskOptions: Seq[PipelineBaseOption], path: Path) = {
    logger.info(s"Engine opts: $options")
    logger.info(s"Task   opts: $taskOptions")
    writePresetXml(path, toPresetXml(options, taskOptions))
  }

  /**
   * Convert the require value to pbsmrtpipe commandline exe
   * and writes the preset.xml
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

    val presetXmlPath = outputDir.resolve("preset.xml")
    writePresetXml(presetXmlPath, toPresetXml(workflowOptions, taskOptions))

    val serviceStr = serviceUri match {
      case Some(x) => s"--service-uri ${x.toString}"
      case _ => ""
    }

    s"$pbsmrtpipeExe pipeline-id $pipelineId $e --preset-xml=${presetXmlPath.toString} --output-dir=${outputDir.toAbsolutePath.toString} $serviceStr "
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
  def writeJobShellWrapper(path: Path, cmd: String, env: Option[Path]): Path = {
    val bw = new BufferedWriter(new FileWriter(path.toFile))

    val envStr = env.map(sx => s"source $sx").getOrElse("# no custom ENV defined")

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
