package com.pacbio.secondary.analysis.externaltools

import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes

import java.nio.file.Path

/**
 * External Call To pbreports
 */

case class PbReport(outputJson: Path, taskId: String)

trait CallPbReport extends Python {
  val reportModule: String
  val reportTaskId: String

  def apply(stsXml: Path, outputJson: Path): Option[ExternalCmdFailure] = {
    val cmd = Seq(
      EXE,
      "-m", s"pbreports.report.${reportModule}",
      stsXml.toAbsolutePath.toString,
      outputJson.toAbsolutePath.toString
    )
    runSimpleCmd(cmd)
  }

  def run(stsXml: Path, outputJson: Path): Either[ExternalCmdFailure, PbReport] = {
    apply(stsXml, outputJson) match {
      case Some(e) => Left(e)
      case _ => Right(PbReport(outputJson, reportTaskId))
    }
  }

  def canProcess(dst: DataSetMetaTypes.DataSetMetaType, hasStatsXml: Boolean = false): Boolean
}

object PbReports {
  def isAvailable(): Boolean = {
    Python.hasModule("pbreports")
  }

  object FilterStatsXml extends CallPbReport {
    val reportModule = "filter_stats_xml"
    val reportTaskId = "pbreports.tasks.filter_stats_report_xml"
    def canProcess(dst: DataSetMetaTypes.DataSetMetaType, hasStatsXml: Boolean) = {
      (dst == DataSetMetaTypes.Subread) && (hasStatsXml)
    }
  }

  object LoadingXml extends CallPbReport {
    val reportModule = "loading_xml"
    val reportTaskId = "pbreports.tasks.loading_report_xml"
    def canProcess(dst: DataSetMetaTypes.DataSetMetaType, hasStatsXml: Boolean) = {
      (dst == DataSetMetaTypes.Subread) && (hasStatsXml)
    }
  }

  object AdapterXml extends CallPbReport {
    val reportModule = "adapter_xml"
    val reportTaskId = "pbreports.tasks.adapter_report_xml"
    def canProcess(dst: DataSetMetaTypes.DataSetMetaType, hasStatsXml: Boolean) = {
      (dst == DataSetMetaTypes.Subread) && (hasStatsXml)
    }
  }

  val ALL = List(FilterStatsXml, LoadingXml, AdapterXml)

}
