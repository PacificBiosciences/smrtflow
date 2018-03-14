package com.pacbio.secondary.smrtlink.analysis.externaltools

import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes

import java.nio.file.Path

/**
  * External Call To pbreports
  */
case class PbReport(outputJson: Path, taskId: String)

trait CallPbReport extends Python {
  val reportModule: String
  val reportTaskId: String

  /**
    * Note, We completely ignore any errors here.
    *
    * If the report can't be generated for ANY reason we
    * default to generating a "Simple" report.
    *
    * This is indeed perhaps not a great idea.
    *
    * @param stsXml Path to STS XML file
    * @param outputJson Output Report JSON file
    * @return
    */
  def apply(stsXml: Path, outputJson: Path): Option[ExternalCmdFailure] = {
    val cmd = Seq(
      EXE,
      "-m",
      s"pbreports.report.$reportModule",
      stsXml.toAbsolutePath.toString,
      outputJson.toAbsolutePath.toString
    )
    runCheckCall(cmd)
  }

  def run(stsXml: Path,
          outputJson: Path): Either[ExternalCmdFailure, PbReport] = {
    apply(stsXml, outputJson) match {
      case Some(e) => Left(e)
      case _ => Right(PbReport(outputJson, reportTaskId))
    }
  }

  def canProcess(dst: DataSetMetaTypes.DataSetMetaType,
                 hasStatsXml: Boolean = false): Boolean
}

object PbReports {
  def isAvailable(): Boolean = {
    Python.hasModule("pbreports")
  }

  trait SubreadStatsReport extends CallPbReport {
    override def canProcess(dst: DataSetMetaTypes.DataSetMetaType,
                            hasStatsXml: Boolean): Boolean = {
      (dst == DataSetMetaTypes.Subread) && hasStatsXml
    }
  }

  object FilterStatsXml extends SubreadStatsReport {
    val reportModule = "filter_stats_xml"
    val reportTaskId = "pbreports.tasks.filter_stats_report_xml"
  }

  object LoadingXml extends SubreadStatsReport {
    val reportModule = "loading_xml"
    val reportTaskId = "pbreports.tasks.loading_report_xml"
  }

  object AdapterXml extends SubreadStatsReport {
    val reportModule = "adapter_xml"
    val reportTaskId = "pbreports.tasks.adapter_report_xml"
  }

  object ControlRpt extends SubreadStatsReport {
    val reportModule = "control"
    val reportTaskId = "pbreports.tasks.control_report"
  }

  val ALL = List(FilterStatsXml, LoadingXml, AdapterXml, ControlRpt)

  object SubreadReports extends SubreadStatsReport {
    val reportModule = "subreads_reports"
    val reportTaskId = "pbreports.tasks.subreads_reports"
  }
}
