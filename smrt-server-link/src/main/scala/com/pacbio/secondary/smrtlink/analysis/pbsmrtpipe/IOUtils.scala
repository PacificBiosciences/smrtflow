package com.pacbio.secondary.smrtlink.analysis.pbsmrtpipe

import java.io.{FileWriter, BufferedWriter, PrintWriter}
import java.net.URI
import java.nio.file.Path

import scala.xml.{Elem, Node}
import scala.io.Source

import com.typesafe.scalalogging.LazyLogging
import spray.json._

import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.SecondaryJobJsonProtocol
import com.pacbio.secondary.smrtlink.analysis.tools.CommandLineUtils
import com.pacbio.secondary.smrtlink.analysis.pipelines.PipelineTemplatePresetLoader

/**
 *
 * Created by mkocher on 9/26/15.
 */
object IOUtils
    extends SecondaryJobJsonProtocol
    with PipelineTemplatePresetLoader
    with LazyLogging{

  val pbsmrtpipeExe = CommandLineUtils.which("pbsmrtpipe") getOrElse {
    logger.warn(s"Unable to find pbsmrtpipe exe")
    "pbsmrtpipe"
  }

  def writePresetJson(
      path: Path,
      pipelineId: String,
      workflowOptions: Seq[ServiceTaskOptionBase],
      taskOptions: Seq[ServiceTaskOptionBase]): Path = {
    val pw = new PrintWriter(path.toFile)
    val jsPresets = PipelineTemplatePreset("smrtlink-job-settings", pipelineId, workflowOptions, taskOptions).toJson
    pw.write(jsPresets.prettyPrint)
    pw.close
    path.toAbsolutePath
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
      taskOptions: Seq[ServiceTaskOptionBase],
      workflowOptions: Seq[ServiceTaskOptionBase],
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
