package com.pacbio.secondary.analysis.externaltools

import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes

import java.nio.file.Path

/**
  * External Call To pbreports
  */
trait CallPbReport extends Python {
  val reportModule: String

  def apply(stsXml: Path, outputJson: Path): Option[ExternalCmdFailure] = {
    val cmd = Seq(
      EXE,
      "-m", s"pbreports.report.${reportModule}",
      stsXml.toAbsolutePath.toString,
      outputJson.toAbsolutePath.toString
    )
    runSimpleCmd(cmd)
  }

  def run(stsXml: Path, outputJson: Path): Either[ExternalCmdFailure, Path] = {
    apply(stsXml, outputJson) match {
      case Some(e) => Left(e)
      case _ => Right(outputJson)
    }
  }

  def canProcess(dst: DataSetMetaTypes.DataSetMetaType): Boolean
}

object PbReports {
  def isAvailable(): Boolean = {
    Python.hasModule("pbreports")
  }

  object FilterStatsXml extends CallPbReport {
    val reportModule = "filter_stats_xml"
    def canProcess(dst: DataSetMetaTypes.DataSetMetaType) = {
      dst == DataSetMetaTypes.Subread
    }
  }

  object LoadingXml extends CallPbReport {
    val reportModule = "loading_xml"
    def canProcess(dst: DataSetMetaTypes.DataSetMetaType) = {
      dst == DataSetMetaTypes.Subread
    }
  }

  object AdapterXml extends CallPbReport {
    val reportModule = "adapter_xml"
    def canProcess(dst: DataSetMetaTypes.DataSetMetaType) = {
      dst == DataSetMetaTypes.Subread
    }
  }

  val ALL = List(FilterStatsXml, LoadingXml, AdapterXml)

}
