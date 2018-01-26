package com.pacbio.secondary.smrtlink.analysis.reports

import java.nio.file.{Path, Paths}
import java.util.UUID

import collection.JavaConverters._
import org.joda.time.{DateTime => JodaDateTime}
import org.apache.commons.io.FileUtils

import scala.util.{Try, Success, Failure}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json._

import com.pacificbiosciences.pacbiodatasets.{
  DataSetMetadataType,
  SubreadSet,
  DataSetType
}
import com.pacbio.common.models.Constants
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.smrtlink.analysis.externaltools.{
  CallPbReport,
  PbReport,
  PbReports
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs.SecondaryJobJsonProtocol
import com.pacbio.secondary.smrtlink.analysis.jobs.JobResultsWriter
import com.pacbio.secondary.smrtlink.analysis.reports.ReportModels._

object DataSetReports
    extends ReportJsonProtocol
    with SecondaryJobJsonProtocol {
  val simple = "simple_dataset_report"
  val reportPrefix = "dataset-reports"

  /**
    *
    * @param inPath    Path to DataSet
    * @param dst       DataSet type
    * @param rptParent Root Report Directory
    */
  case class DataSetReportOptions(inPath: Path,
                                  dst: DataSetMetaTypes.DataSetMetaType,
                                  rptParent: Path,
                                  log: JobResultsWriter,
                                  jobTypeId: JobTypeIds.JobType)

  // all of the current reports will only work if at least one sts.xml file
  // is present as an ExternalResource of a SubreadSet BAM file
  def hasStatsXml(inPath: Path,
                  dst: DataSetMetaTypes.DataSetMetaType): Boolean = dst match {
    case DataSetMetaTypes.Subread => {
      val ds = DataSetLoader.loadSubreadSet(inPath)
      val extRes = ds.getExternalResources
      if (extRes == null) false
      else {
        (extRes.getExternalResource.asScala
          .filter(_ != null)
          .map { x =>
            val extRes2 = x.getExternalResources
            if (extRes2 == null) false
            else {
              extRes2.getExternalResource.asScala
                .filter(_ != null)
                .map { x2 =>
                  x2.getMetaType == FileTypes.STS_XML.fileTypeId
                }
                .exists(_ == true)
            }
          })
          .toList
          .exists(_ == true)
      }
    }
    case _ => false
  }

  private def toDataStoreFile(path: Path, taskId: String): DataStoreFile = {
    val now = JodaDateTime.now()

    val (reportId, uuid) = Try { ReportUtils.loadReport(path) }
      .map(r => (r.id, r.uuid))
      .getOrElse(("unknown", UUID.randomUUID())) // XXX this is not ideal

    //FIXME(mpkocher)(2016-4-21) Need to store the report type id in the db
    DataStoreFile(
      uuid,
      taskId,
      FileTypes.REPORT.fileTypeId.toString,
      path.toFile.length(),
      now,
      now,
      path.toAbsolutePath.toString,
      isChunked = false,
      s"PacBio Report $reportId",
      s"PacBio DataSet Report for $reportId"
    )
  }

  private def runSubreadSetReports(
      opts: DataSetReportOptions): Seq[DataStoreFile] = {
    val dsFile = opts.rptParent.resolve("datastore.json")
    val rpt = PbReports.SubreadReports
    opts.log.writeLine(s"running report ${rpt.reportModule}")
    rpt.run(opts.inPath, dsFile) match {
      case Left(failure) => {
        opts.log.writeLine(
          s"Failed to generate report SubreadSet. Using simple report for $dsFile")
        opts.log.writeLine(failure.msg)
        generateSimpleReports(opts)
      }
      case Right(result) => {
        val ds = FileUtils
          .readFileToString(result.outputJson.toFile, "UTF-8")
          .parseJson
          .convertTo[PacBioDataStore]
        ds.files
      }
    }
  }

  private def generateSimpleReports(
      opts: DataSetReportOptions): Seq[DataStoreFile] =
    List(simpleReport(opts))

  private def generateSubreadSetReports(
      opts: DataSetReportOptions): Seq[DataStoreFile] = {
    if (PbReports.SubreadReports.canProcess(
          opts.dst,
          hasStatsXml(opts.inPath, opts.dst))) {
      runSubreadSetReports(opts)
    } else {
      val msg =
        s"Can't process detailed Reports for SubreadSet. Defaulting to simple report for ${opts.inPath}"
      opts.log.writeLine(msg)
      generateSimpleReports(opts)
    }
  }

  private def generateReports(opts: DataSetReportOptions): Seq[DataStoreFile] = {
    opts.dst match {
      case DataSetMetaTypes.Subread =>
        generateSubreadSetReports(opts)
      case _ =>
        generateSimpleReports(opts)
    }
  }

  /**
    * Run DataSet Reports for any dataset type.
    *
    * @param inPath    Path to DataSet XML
    * @param dst       DataSet type for inPath
    * @param jobPath   Root job directory
    * @param jobTypeId JobType (this will/can be used in the dataset file as the source id)
    * @param log       Logger
    * @return
    */
  def runAll(inPath: Path,
             dst: DataSetMetaTypes.DataSetMetaType,
             jobPath: Path,
             jobTypeId: JobTypeIds.JobType,
             log: JobResultsWriter): Seq[DataStoreFile] = {

    val rptParent = jobPath.resolve(reportPrefix)
    rptParent.toFile.mkdir()

    val opts = DataSetReportOptions(inPath, dst, rptParent, log, jobTypeId)
    if (PbReports.isAvailable()) {
      generateReports(opts)
    } else {
      generateSimpleReports(opts)
    }
  }

  private def simpleReport(opts: DataSetReportOptions): DataStoreFile = {

    val inPath = opts.inPath
    val dst = opts.dst

    def attribs(md: DataSetMetadataType) =
      List(
        ReportLongAttribute("total_length", "Total Length", md.getTotalLength),
        ReportLongAttribute("num_records", "Num Records", md.getNumRecords)
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
        attribs(
          DataSetLoader.loadConsensusAlignmentSet(inPath).getDataSetMetadata)
      case DataSetMetaTypes.Contig =>
        attribs(DataSetLoader.loadContigSet(inPath).getDataSetMetadata)
      case DataSetMetaTypes.Barcode =>
        attribs(DataSetLoader.loadBarcodeSet(inPath).getDataSetMetadata)
      case DataSetMetaTypes.GmapReference =>
        attribs(DataSetLoader.loadGmapReferenceSet(inPath).getDataSetMetadata)
    }

    val rpt = Report(simple,
                     "Import DataSet Report",
                     Constants.SMRTFLOW_VERSION,
                     reportAttrs,
                     Nil,
                     Nil,
                     UUID.randomUUID())

    val reportPath = opts.rptParent.resolve(simple + ".json")
    ReportUtils.writeReport(rpt, reportPath)
    toDataStoreFile(reportPath, s"pbscala::dataset_report")
  }
}
