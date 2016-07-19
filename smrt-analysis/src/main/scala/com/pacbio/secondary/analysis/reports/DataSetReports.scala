package com.pacbio.secondary.analysis.reports

import java.nio.file.{Path, Paths}
import java.util.UUID

import collection.JavaConversions._
import org.joda.time.{DateTime => JodaDateTime}

import scala.util.Try
import com.pacificbiosciences.pacbiodatasets.{DataSetMetadataType, SubreadSet, DataSetType}
import com.pacbio.common.models.Constants
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.analysis.externaltools.{CallPbReport, PbReport, PbReports}
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobs.JobResultWriter
import com.pacbio.secondary.analysis.reports.ReportModels._


object DataSetReports extends ReportJsonProtocol {
  val simple = "simple_dataset_report"
  val reportPrefix = "dataset-reports"

  def toDataStoreFile(path: Path, taskId: String): DataStoreFile = {
    val now = JodaDateTime.now()

    val (reportId, uuid) = Try { ReportUtils.loadReport(path) }
        .map(r => (r.id, r.uuid))
        .getOrElse(("unknown", UUID.randomUUID())) // XXX this is not ideal

    //FIXME(mpkocher)(2016-4-21) Need to store the report type id in the db
    DataStoreFile(uuid, taskId,
      FileTypes.REPORT.fileTypeId.toString,
      path.toFile.length(),
      now,
      now,
      path.toAbsolutePath.toString,
      isChunked = false,
      s"PacBio Report $reportId",
      s"PacBio DataSet Report for $reportId")
  }

  def run(
      srcPath: Path,
      rpt: CallPbReport,
      parentDir: Path,
      log: JobResultWriter): Option[DataStoreFile] = {

    val reportDir = parentDir.resolve(rpt.reportModule)
    reportDir.toFile.mkdir()
    val reportFile = reportDir.resolve(s"${rpt.reportModule}.json")

    log.writeLineStdout(s"running report ${rpt.reportModule}")

    rpt.run(srcPath, reportFile) match {
      case Left(failure) => {
        log.writeLineStdout("failed to generate report:")
        log.writeLineStdout(failure.msg)
        None
      }
      case Right(report) => Some(toDataStoreFile(report.outputJson, report.taskId))
    }
  }

  def runAll(
      inPath: Path,
      dst: DataSetMetaTypes.DataSetMetaType,
      jobPath: Path,
      jobTypeId: JobTypeId,
      log: JobResultWriter): Seq[DataStoreFile] = {

    val rptParent = jobPath.resolve(reportPrefix)
    rptParent.toFile.mkdir()

    // all of the current reports will only work if at least one sts.xml file
    // is present as an ExternalResource of a SubreadSet BAM file
    val hasStatsXml: Boolean = dst match {
      case DataSetMetaTypes.Subread => {
        val ds = DataSetLoader.loadSubreadSet(inPath)
        val extRes = ds.getExternalResources
        if (extRes == null) false
        else {
          (extRes.getExternalResource.filter(_ != null).map { x =>
            val extRes2 = x.getExternalResources
            if (extRes2 == null) false else {
              extRes2.getExternalResource.filter(_ != null).map { x2 =>
                x2.getMetaType == FileTypes.STS_XML.fileTypeId
              }.exists(_ == true)
            }
          }).toList.exists(_ == true)
        }
      }
      case _ => false
    }

    val reportFiles = if (PbReports.isAvailable()) {
      PbReports.ALL
          .filter(_.canProcess(dst, hasStatsXml))
          .flatMap(run(inPath, _, rptParent, log))
    } else {
      log.writeLineStdout("pbreports is unavailable")
      List()
    }

    if (reportFiles.nonEmpty) {
      reportFiles
    } else {
      List(simpleReport(inPath, dst, rptParent, jobTypeId))
    }
  }

  def simpleReport(
      inPath: Path,
      dst: DataSetMetaTypes.DataSetMetaType,
      jobPath: Path,
      jobTypeId: JobTypeId): DataStoreFile = {


    def attribs(md: DataSetMetadataType) =
      List(
        ReportLongAttribute(
          "total_length", "Total Length", md.getTotalLength),
        ReportLongAttribute(
          "num_records", "Num Records", md.getNumRecords)
      )

    // This doesn't work. See comments below
    //def toSimpleAttributes[T <: DataSetType](ds: T): Seq[ReportLongAttribute] = attribs(ds.getDataSetMetadata())

    // The base DataSetType doesn't have a base metadatatype, therefore this
    // has to be explicitly encoded here.
    val reportAttrs: List[ReportAttribute] = dst match {
      case DataSetMetaTypes.Subread =>
        attribs(DataSetLoader.loadSubreadSet(inPath).getDataSetMetadata)
      case DataSetMetaTypes.HdfSubread =>
        attribs(DataSetLoader.loadHdfSubreadSet(inPath).getDataSetMetadata)
      case DataSetMetaTypes.Reference =>
        attribs(DataSetLoader.loadReferenceSet(inPath).getDataSetMetadata)
      case DataSetMetaTypes.Alignment =>
        attribs(DataSetLoader.loadAlignmentSet(inPath).getDataSetMetadata)
      case DataSetMetaTypes.CCS =>
        attribs(DataSetLoader.loadConsensusReadSet(inPath).getDataSetMetadata)
      case DataSetMetaTypes.AlignmentCCS =>
        attribs(DataSetLoader.loadConsensusAlignmentSet(inPath).getDataSetMetadata)
      case DataSetMetaTypes.Contig =>
        attribs(DataSetLoader.loadContigSet(inPath).getDataSetMetadata)
      case DataSetMetaTypes.Barcode =>
        attribs(DataSetLoader.loadBarcodeSet(inPath).getDataSetMetadata)
      case DataSetMetaTypes.GmapReference =>
        attribs(DataSetLoader.loadGmapReferenceSet(inPath).getDataSetMetadata)
    }

    val rpt = Report(
      simple, "Import DataSet Report", Constants.SMRTFLOW_VERSION, reportAttrs, Nil, Nil, UUID.randomUUID())

    val reportPath = jobPath.resolve(simple + ".json")
    ReportUtils.writeReport(rpt, reportPath)
    toDataStoreFile(reportPath, s"pbscala::${jobTypeId.id}")
  }
}
