package com.pacbio.secondary.smrtlink.analysis.externaltools

import java.io.FileWriter

import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import java.nio.file.Path
import org.joda.time.{DateTime => JodaDateTime}

import scala.sys.process.ProcessLogger

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
    val getOut = (sx: String) => outputJson.getParent.resolve(sx)

    val stdout = getOut("stdout")
    val stderr = getOut("stderr")

    val fout = new FileWriter(stdout.toAbsolutePath.toString, true)
    val ferr = new FileWriter(stderr.toAbsolutePath.toString, true)

    val errorMsg = new StringBuilder

    val processLogger: ProcessLogger = ProcessLogger(
      (o: String) => {
        fout.write(o + "\n")
      },
      (e: String) => {
        ferr.write(e + "\n")
        errorMsg.append(e + "\n")
      }
    )

    val startedAt = JodaDateTime.now()
    runUnixCmd(cmd, stdout, stderr, processLogger = Some(processLogger)) match {
      case (0, _) => None
      case (n, msg) =>
        val completedAt = JodaDateTime.now()
        val runTime = computeTimeDelta(completedAt, startedAt)
        Some(ExternalCmdFailure(cmd, runTime, msg))
    }
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
