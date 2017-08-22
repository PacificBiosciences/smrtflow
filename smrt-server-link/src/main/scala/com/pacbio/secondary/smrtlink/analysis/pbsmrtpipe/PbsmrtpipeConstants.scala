package com.pacbio.secondary.smrtlink.analysis.pbsmrtpipe

import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels.{PipelineStrOption, PipelineIntOption, PipelineBaseOption}

/**
 * This is ported from pbsmrtpipe. This should really be a case class of
 * the Pipeline Engine Options (i.e., the workflow level options)
 */
object PbsmrtpipeConstants {

  // pbsmrtpipe ENV vars
  final val ENV_BUNDLE_DIR = "SMRT_PIPELINE_BUNDLE_DIR"
  final val ENV_TOOL_CONTACT_DIR = "PB_TOOL_CONTRACT_DIR"
  final val ENV_PIPELINE_TEMPLATE_DIR = "PB_PIPELINE_TEMPLATE_DIR"

  // This aren't really pbsmrtpipe specific related, but keeping them here in a central location
  final val ENV_PB_RULES_REPORT_VIEW_DIR = "PB_RULES_REPORT_VIEW_DIR"
  final val ENV_PB_RULES_PIPELINE_VIEW_DIR = "PB_RULES_PIPELINE_VIEW_DIR"

  final val ENV_PB_RULES_DATASTORE_VIEW_DIR = "PB_RULES_DATASTORE_VIEW_DIR"

  // base pbsmrtpipe options
  sealed trait PbsmrtpipeEngineOption {
    def toI(n: String) = s"pbsmrtpipe.options.$n"

    def id: String
  }

  case object DEBUG_MODE extends PbsmrtpipeEngineOption {
    def id = toI("debug_mode")
  }

  case object PB_TMP_DIR extends PbsmrtpipeEngineOption {
    def id = toI("tmp_dir")
  }

  case object MAX_NPROC extends PbsmrtpipeEngineOption {
    def id = toI("max_nproc")
  }

  case object CHUNKED_MODE extends PbsmrtpipeEngineOption {
    def id = toI("chunk_mode")
  }

  case object MAX_TOTAL_NPROC extends PbsmrtpipeEngineOption {
    def id = toI("max_total_nproc")
  }

  case object MAX_NCHUNKS extends PbsmrtpipeEngineOption {
    def id = toI("max_nchunks")
  }

  case object DISTRIBUTED_MODE extends PbsmrtpipeEngineOption {
    def id = toI("distributed_mode")
  }

  case object EXIT_ON_FAILURE extends PbsmrtpipeEngineOption {
    def id = toI("exit_on_failure")
  }

  case object CLUSTER_MANAGER_TMPL_DIR extends PbsmrtpipeEngineOption {
    def id = toI("cluster_manager")
  }

  case object MAX_NWORKERS extends PbsmrtpipeEngineOption {
    def id = toI("max_nworkers")
  }
}

