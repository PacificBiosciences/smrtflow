package com.pacbio.secondary.smrtlink.analysis.pipelines

import java.nio.file.{Paths, Path}
import com.typesafe.scalalogging.LazyLogging

import collection.JavaConversions._
import collection.JavaConverters._

import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.PipelineTemplateJsonProtocol
import org.apache.commons.io.FileUtils

import scala.util.Random
import spray.json._

/**
  * Temporary layer to handle Pbsmrtpipe and mock Pipelines pipelines
  *
  * Created by mkocher on 5/6/15.
  */
trait PipelineUtils extends LazyLogging {

  def getPresetTaskOptions(
      p: PipelineTemplate,
      taskOptions: Seq[ServiceTaskOptionBase]): Seq[ServiceTaskOptionBase] = {
    val presetOptsMap = taskOptions.map(x => (x.id, x)).toMap
    p.taskOptions.map { opt =>
      presetOptsMap.get(opt.id).map(pOpt => (pOpt, opt)) match {
        case Some((presetOpt: ServiceTaskStrOption, _)) =>
          // Need to do ugly casting here. The 'raw' options defined in XML are treated as PipelineStringOption
          // and cast to the type consistent with what is defined in the Pipeline
          opt match {
            case t: PipelineBooleanOption =>
              t.copy(value = presetOpt.value.toBoolean)
            case t: PipelineIntOption => t.copy(value = presetOpt.value.toInt)
            case t: PipelineDoubleOption =>
              t.copy(value = presetOpt.value.toDouble)
            case t: PipelineStrOption => t.copy(value = presetOpt.value)
            case t: PipelineChoiceStrOption => t.applyValue(presetOpt.value)
            case t: PipelineChoiceIntOption =>
              t.applyValue(presetOpt.value.toInt)
            case t: PipelineChoiceDoubleOption =>
              t.applyValue(presetOpt.value.toDouble)
          }
        // If the non-raw XML values are provided, just default to the correct values
        case Some(
            (presetOpt: ServiceTaskBooleanOption,
             opt: PipelineBooleanOption)) =>
          opt.copy(value = presetOpt.value)
        case Some((presetOpt: ServiceTaskIntOption, opt: PipelineIntOption)) =>
          opt.copy(value = presetOpt.value)
        case Some(
            (presetOpt: ServiceTaskIntOption, opt: PipelineChoiceIntOption)) =>
          opt.applyValue(presetOpt.value)
        case Some(
            (presetOpt: ServiceTaskDoubleOption, opt: PipelineDoubleOption)) =>
          opt.copy(value = presetOpt.value)
        case Some(
            (presetOpt: ServiceTaskDoubleOption,
             opt: PipelineChoiceDoubleOption)) =>
          opt.applyValue(presetOpt.value)
        case _ => opt
      }
    } map (_.asServiceOption)
  }

  /**
    * Will override any presets in the pipeline and translate any "raw" task options to the correct taskOption
    * The pbsmrtpipe preset XML only has the K-V pairs as Str-Str.
    *
    * Any existing presets in the pipeline will be overwritten by provided presets
    *
    * @param p
    * @param presets
    * @return
    */
  def updatePipelinePreset(
      p: PipelineTemplate,
      presets: Seq[PipelineTemplatePreset]): PipelineTemplate = {

    // All Pipeline Options, a preset might contain options that are not in the definition of the pipeline, these
    // options (and or presets) will be filtered out.

    // Filter all presets that don't reference the fundamental pipeline of interest
    val processedPresets = presets.filter(_.pipelineId == p.id).map { preset =>
      val presetTaskOptions = getPresetTaskOptions(p, preset.taskOptions)
      preset.copy(taskOptions = presetTaskOptions)
    }

    val mergedPresets =
      (p.presets ++ processedPresets).map(t => (t.presetId, t)).toMap
    p.copy(presets = mergedPresets.values.toList)
  }

  def updatePipelinePresets(ps: Seq[PipelineTemplate],
                            presets: Seq[PipelineTemplatePreset]) =
    ps.map(updatePipelinePreset(_, presets))

}

object PipelineUtils extends PipelineUtils

trait Loader[T] {

  // Filter files to load by extension load (e.g., Seq("json")
  val extFilter: Seq[String]

  def loadFrom(path: Path): T

  /**
    * Load All the files matching the extension Filter
    * @param path Path to Root directory with resource files
    * @return
    */
  def loadFromDir(path: Path): Seq[T] = {
    val files = FileUtils.listFiles(path.toFile, extFilter.toArray, false)
    files.map(x => loadFrom(Paths.get(x.getAbsolutePath))).toList
  }
}

trait JsonPipelineTemplatesLoader
    extends Loader[PipelineTemplate]
    with PipelineTemplateJsonProtocol {

  val extFilter = Seq("json")

  def loadFrom(path: Path): PipelineTemplate = {
    val sx = scala.io.Source.fromFile(path.toFile).mkString
    val jx = sx.parseJson
    jx.convertTo[PipelineTemplate]
  }
}

object JsonPipelineTemplatesLoader extends JsonPipelineTemplatesLoader
