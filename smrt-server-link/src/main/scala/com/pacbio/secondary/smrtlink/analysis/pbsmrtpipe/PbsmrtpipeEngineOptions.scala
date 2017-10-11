package com.pacbio.secondary.smrtlink.analysis.pbsmrtpipe

import java.nio.file.{Paths, Path}
import scala.language.postfixOps

import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._

import scala.util.Try

/**
  * Default pbsmrtpipe Options
  *
  * These are the default pbsmrtpipe options that are converted
  * These must be kept insync. Use `pbsmrtpipe show-workflow-options`
  * Created by mkocher on 9/26/15.
  */
case class PbsmrtpipeEngineOptions(debugMode: Boolean = false,
                                   maxNproc: Int = 16,
                                   tmpDir: Path = Paths.get("/tmp"),
                                   chunkedMode: Boolean = false,
                                   maxTotalNproc: Int = 10000,
                                   maxNChunks: Int = 128,
                                   distributedMode: Boolean = true,
                                   exitOnFailure: Boolean = false,
                                   clusterManagerTemplateDir: Option[String] =
                                     Some("pbsmrtpipe.cluster_templates.sge"),
                                   maxNWorkers: Int = 100) {

  //See comments below
  def toPipelineOptions: Seq[PipelineBaseOption] = {

    Seq(
      PipelineBooleanOption(
        PbsmrtpipeConstants.DEBUG_MODE.id,
        "pbsmrtpipe DEBUG Mode",
        debugMode,
        "Enable pbsmrtpipe DEBUG Mode to pbsmrtpipe.log and master.log"),
      PipelineIntOption(PbsmrtpipeConstants.MAX_NPROC.id,
                        "Max Number of Processors per Task",
                        maxNproc,
                        "Max Number of Processors"),
      PipelineStrOption(PbsmrtpipeConstants.PB_TMP_DIR.id,
                        "Tmp directory",
                        tmpDir.toAbsolutePath.toString,
                        "Root location to temp directory"),
      PipelineBooleanOption(
        PbsmrtpipeConstants.CHUNKED_MODE.id,
        "Enable chunk mode",
        chunkedMode,
        "Enable chunk mode to create chunk.json from registered chunkable tasks"),
      PipelineIntOption(
        PbsmrtpipeConstants.MAX_TOTAL_NPROC.id,
        "Max Total Number of processors to use per pipeline execution",
        maxTotalNproc,
        "Total Max Number of slots/proc to use"
      ),
      PipelineIntOption(PbsmrtpipeConstants.MAX_NCHUNKS.id,
                        "Max Number of Chunks per Task",
                        maxNChunks,
                        "Max Number of Chunks per task"),
      PipelineBooleanOption(
        PbsmrtpipeConstants.DISTRIBUTED_MODE.id,
        "Enable Distributed mode",
        distributedMode,
        s"Enable Distributed mode to submit jobs to the cluster resources. ${PbsmrtpipeConstants.CLUSTER_MANAGER_TMPL_DIR.toString} must be set"
      ),
      PipelineBooleanOption(PbsmrtpipeConstants.EXIT_ON_FAILURE.id,
                            "Enable Exit on Failure",
                            exitOnFailure,
                            "Exit on the first failed task."),
      PipelineStrOption(
        PbsmrtpipeConstants.CLUSTER_MANAGER_TMPL_DIR.id,
        "Cluster Template Directory",
        clusterManagerTemplateDir getOrElse "",
        "Directory of Cluster templates (must contain start.tmpl and stop.tmpl) or python module path (e.g, pbsmrtpipe.my_module.sge"
      ),
      PipelineIntOption(
        PbsmrtpipeConstants.MAX_NWORKERS.id,
        "Max Number of Workers",
        maxNWorkers,
        "Max Number of concurrently running tasks. (Note:  max_nproc will restrict the number of workers if max_nworkers * max_nproc > max_total_nproc)"
      )
    )
  }

  def getPipelineOptionById(sx: String): Option[PipelineBaseOption] = {
    (toPipelineOptions.map(x => (x.id, x)) toMap).get(sx)
  }

  def summary(): String = {

    val opts = toPipelineOptions

    val pad = 4
    val maxName = opts.map(_.id.length).max

    toPipelineOptions
      .map(p => f"${p.id}%-38s ${p.value.toString}%-34s ${p.name}")
      .reduce(_ + "\n" + _)
  }

}

object PbsmrtpipeEngineOptions {

  /*
  Keep the option handling to working and have parity with the TaskOption model.

  The Seq[PipelineBaseOption] should eventually be removed.
   */
  def apply(opts: Seq[ServiceTaskOptionBase]): PbsmrtpipeEngineOptions = {
    val mx = opts.map(x => (x.id, x)) toMap

    def getBy[T](sx: String, defaultValue: T): T =
      Try {
        mx.get(sx).map(x => x.value.asInstanceOf[T]) getOrElse defaultValue
      } getOrElse defaultValue

    def getTmp(sx: String): Path = {
      mx.get(sx) match {
        case Some(x: ServiceTaskStrOption) => Paths.get(x.value)
        case _ => defaults.tmpDir
      }
    }

    def getClusterTmpl(sx: String): Option[String] = {
      mx.get(sx) match {
        case Some(x: ServiceTaskStrOption) => Option(x.value)
        case _ => None
      }
    }

    PbsmrtpipeEngineOptions(
      debugMode = getBy(PbsmrtpipeConstants.DEBUG_MODE.id, defaults.debugMode),
      getBy(PbsmrtpipeConstants.MAX_NPROC.id, defaults.maxNproc),
      getTmp(PbsmrtpipeConstants.PB_TMP_DIR.id),
      getBy(PbsmrtpipeConstants.CHUNKED_MODE.id, defaults.chunkedMode),
      getBy(PbsmrtpipeConstants.MAX_TOTAL_NPROC.id, defaults.maxTotalNproc),
      getBy(PbsmrtpipeConstants.MAX_NCHUNKS.id, defaults.maxNChunks),
      getBy(PbsmrtpipeConstants.DISTRIBUTED_MODE.id, defaults.distributedMode),
      getBy(PbsmrtpipeConstants.EXIT_ON_FAILURE.id, defaults.exitOnFailure),
      getClusterTmpl(PbsmrtpipeConstants.CLUSTER_MANAGER_TMPL_DIR.id),
      getBy(PbsmrtpipeConstants.MAX_NWORKERS.id, defaults.maxNWorkers)
    )
  }

  def defaults: PbsmrtpipeEngineOptions = {
    PbsmrtpipeEngineOptions(debugMode = false)
  }

  val defaultWorkflowOptions: Seq[PipelineBaseOption] = {
    PbsmrtpipeEngineOptions.defaults.toPipelineOptions
  }
}
