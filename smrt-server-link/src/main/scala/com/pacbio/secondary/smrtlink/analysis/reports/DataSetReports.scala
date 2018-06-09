package com.pacbio.secondary.smrtlink.analysis.reports

import java.nio.file.{Path, Paths}
import java.util.UUID

import collection.JavaConverters._
import org.joda.time.{DateTime => JodaDateTime}
import org.apache.commons.io.FileUtils

import scala.util.{Failure, Success, Try}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json._
import com.pacificbiosciences.pacbiodatasets.{
  DataSetMetadataType,
  DataSetType,
  SubreadSet
}
import com.pacbio.common.models.Constants
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.{
  DataSetMetaTypes,
  DataSetMetadataUtils
}
import com.pacbio.secondary.smrtlink.analysis.datasets.io.{
  DataSetJsonUtils,
  DataSetLoader
}
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
      DataSetMetadataUtils
        .getAllExternalResources(ds.getExternalResources)
        .exists(_.getMetaType == FileTypes.STS_XML.fileTypeId)
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
          s"Failed to generate report SubreadSet. Skipping Report Generation")
        opts.log.writeLine(failure.msg)
        Seq.empty[DataStoreFile]
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

  private def generateSubreadSetReports(
      opts: DataSetReportOptions): Seq[DataStoreFile] = {
    if (PbReports.SubreadReports.canProcess(
          opts.dst,
          hasStatsXml(opts.inPath, opts.dst))) {
      runSubreadSetReports(opts)
    } else {
      val msg =
        s"Can't process detailed Reports for SubreadSet. Skipping Report Generation"
      opts.log.writeLine(msg)
      Seq.empty[DataStoreFile]
    }
  }

  private def generateReports(opts: DataSetReportOptions): Seq[DataStoreFile] = {
    opts.dst match {
      case DataSetMetaTypes.Subread =>
        generateSubreadSetReports(opts)
      case _ =>
        Seq.empty[DataStoreFile]
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
             log: JobResultsWriter,
             forceSimpleReports: Boolean = false): Seq[DataStoreFile] = {

    val rptParent = jobPath.resolve(reportPrefix)
    rptParent.toFile.mkdir()

    val opts = DataSetReportOptions(inPath, dst, rptParent, log, jobTypeId)
    if (PbReports.isAvailable()) {
      generateReports(opts)
    } else {
      Seq.empty[DataStoreFile]
    }
  }

  /**
    * This is a pretty heavy handed approach that won't fail during report generation.
    */
  def runAllIgnoreErrors(
      inPath: Path,
      dst: DataSetMetaTypes.DataSetMetaType,
      jobPath: Path,
      jobTypeId: JobTypeIds.JobType,
      resultsWriter: JobResultsWriter,
      forceSimpleReports: Boolean = false): Seq[DataStoreFile] = {

    val reports = Try {
      DataSetReports.runAll(inPath, dst, jobPath, jobTypeId, resultsWriter)
    }

    reports match {
      case Success(rpts) => rpts
      case Failure(ex) =>
        val errorMsg =
          s"Error ${ex.getMessage}\n ${ex.getStackTrace.mkString("\n")}"
        // logger.error(errorMsg)
        resultsWriter.writeLineError(errorMsg)
        // Might want to consider adding a report attribute that has this warning message
        Nil
    }
  }
}
