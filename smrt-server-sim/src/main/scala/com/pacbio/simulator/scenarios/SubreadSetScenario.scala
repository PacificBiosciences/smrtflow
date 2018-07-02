// combines various dependency-heavy tests of SubreadSet-related functionality.
// more general dataset functions are covered by DataSetScenario.
package com.pacbio.simulator.scenarios

import java.nio.file.Path
import java.util.UUID

import scala.concurrent.Future

import akka.actor.ActorSystem
import com.typesafe.config.Config

import com.pacbio.common.models._
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.externaltools.PacBioTestResources
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels
import com.pacbio.secondary.smrtlink.analysis.reports.ReportModels.Report
import com.pacbio.secondary.smrtlink.jobtypes.PbsmrtpipeJobOptions
import com.pacbio.secondary.smrtlink.client.SmrtLinkServiceClient
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.pacbio.simulator.steps._

object SubreadSetScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(
      implicit system: ActorSystem): Scenario = {

    val c = verifyRequiredConfig(config)
    val testResources = verifyConfiguredWithTestResources(c)
    val smrtLinkClient = new SmrtLinkServiceClient(getHost(c), getPort(c))
    new SubreadSetScenario(smrtLinkClient, testResources)
  }
}

class SubreadSetScenario(client: SmrtLinkServiceClient,
                         testResources: PacBioTestResources)
    extends Scenario
    with VarSteps
    with ConditionalSteps
    with IOSteps
    with SmrtLinkSteps {

  override val name = "SubreadSetScenario"
  override val requirements = Seq("SL-2951")
  override val smrtLinkClient = client

  import JobModels._
  import CommonModels._
  import CommonModelImplicits._

  // Task IDs in datastore
  val RAW_DATA_ID = "pbreports.tasks.filter_stats_report_xml"
  val ADAPTER_ID = "pbreports.tasks.adapter_report_xml"
  val LOADING_ID = "pbreports.tasks.loading_report_xml"
  // various Report model identifiers
  val RAW_DATA_REPORT = "raw_data_report"
  val RPT_NBASES = s"${RAW_DATA_REPORT}.nbases"
  val RPT_READLENGTH = s"${RAW_DATA_REPORT}.read_length"
  val RPT_INSERT = s"${RAW_DATA_REPORT}.insert_length"
  val RPT_PLOT_GROUP = s"${RAW_DATA_REPORT}.insert_length_plot_group"
  val RPT_PLOT = RPT_PLOT_GROUP + ".insert_length_plot_0"
  val LOADING_REPORT = "loading_xml_report"
  val RPT_TABLE = s"${LOADING_REPORT}.loading_xml_table"
  val RPT_PRODZMWS = s"${LOADING_REPORT}.loading_xml_table.productive_zmws"
  val RPT_PROD = s"${LOADING_REPORT}.loading_xml_table.productivity"
  val RPT_PROD0 = s"${RPT_PROD}_0_n"
  val ADAPTER_REPORT = "adapter_xml_report"

  private def toF[T](result: T) = Future.successful(result)

  protected def getTmp(ix: String, setNewUuid: Boolean = false): Path =
    testResources
      .findById(ix)
      .get
      .getTempDataSetFile(tmpDirBase = "dataset contents",
                          setNewUuid = setNewUuid)
      .path

  /**
    * Import a dataset and return the resulting dataset metadata.  Assumes that
    * the dataset has not already been imported.
    */
  protected def runImportToDataSet(xmlPath: Path): Future[DataSetMetaDataSet] =
    for {
      status <- client.getStatus
      _ <- andLog(
        s"Successfully connected to SMRT Link Server: ${client.RootUri}")
      dsMeta <- toF(getDataSetMiniMeta(xmlPath))
      importJob <- client.importDataSet(xmlPath, dsMeta.metatype)
      importJob <- Future.fromTry(client.pollForSuccessfulJob(importJob.id))
      datastore <- client.getJobDataStore(importJob.id)
      dsFile <- toF(
        datastore.filter(_.fileTypeId == dsMeta.metatype.toString).head)
      serviceDataSet <- client.getDataSet(dsFile.uuid)
      _ <- failIf(dsMeta.uuid != serviceDataSet.uuid, "UUIDs do not match")
      _ <- andLog(s"Successfully imported dataset ${dsMeta.uuid}")
    } yield serviceDataSet

  protected def runTmpImportToDataSet(ix: String): Future[DataSetMetaDataSet] =
    runImportToDataSet(getTmp(ix, setNewUuid = true))

  protected def getReport(jobId: Int,
                          reports: Seq[DataStoreReportFile],
                          taskId: String,
                          reportId: String) =
    for {
      uuid <- toF(getReportId(reports, taskId))
      report <- client.getJobReport(jobId, uuid)
      _ <- failIf(report.id != reportId, "Wrong report ID")
      _ <- failIf(report.uuid != uuid, "Wrong report UUID")
    } yield report

  private def getColValue(report: Report, tableId: String, colId: String) =
    report.getFirstValueFromTableColumn(tableId, colId)

  private def getReportId(reports: Seq[DataStoreReportFile], taskId: String) =
    reports.filter(_.reportTypeId == taskId).head.dataStoreFile.uuid

  private def testReportValue(report: Report,
                              key: String,
                              value: Long): Future[Int] =
    report
      .getAttributeLongValue(key)
      .map { reportValue =>
        failIf(reportValue != value, s"$key: $reportValue != $value")
          .map(_ => 1)
      }
      .getOrElse(toFail(s"Attribute $key not found"))

  private def testReportValues(report: Report,
                               values: Map[String, Long]): Future[Int] =
    Future
      .sequence {
        values
          .filterKeys(_.split('.')(0) == report.id)
          .map {
            case (key: String, value: Long) =>
              testReportValue(report, key, value)
          }
      }
      .map(_.sum)

  protected def testSubreadImportReports(
      dsMeta: DataSetMetaDataSet,
      expectedValues: Option[Map[String, Long]] = None) = {
    for {
      jobReports <- client.getJobReports(dsMeta.jobId)
      reports <- client.getSubreadSetReports(dsMeta.uuid)
      _ <- failIf(jobReports.size != reports.size,
                  "Expected identical reports from job endpoint")
      rpt <- getReport(dsMeta.jobId, reports, RAW_DATA_ID, RAW_DATA_REPORT)
      nTested <- expectedValues
        .map(v => testReportValues(rpt, v))
        .getOrElse(toF("skipped"))
      _ <- andLog(s"Tested $nTested report values")
      nBytes <- client.getDataStoreFileResource(
        rpt.uuid,
        rpt.getPlot(RPT_PLOT_GROUP, RPT_PLOT).get.image)
      _ <- failIf(nBytes == 0, "Image has no content")
      rpt <- getReport(dsMeta.jobId, reports, LOADING_ID, LOADING_REPORT)
      _ <- failIf(getColValue(rpt, RPT_TABLE, RPT_PRODZMWS).isEmpty,
                  s"Can't retrieve $RPT_PRODZMWS")
      _ <- failIf(getColValue(rpt, RPT_TABLE, RPT_PROD0).isEmpty,
                  s"Can't retrieve productivity")
      rpt <- getReport(dsMeta.jobId, reports, ADAPTER_ID, ADAPTER_REPORT)
    } yield s"All SubreadSet reports verified"
  }

  private def toPbsmrtpipeOpts(dsId: UUID) = Future.successful {
    val ep = BoundServiceEntryPoint("eid_subread",
                                    FileTypes.DS_SUBREADS.fileTypeId,
                                    dsId)
    PbsmrtpipeJobOptions(
      Some(s"Test dataset reports filtering"),
      Some("scenario-runner SubreadSetScenario"),
      "pbsmrtpipe.pipelines.dev_dataset_reports_filter",
      Seq(ep),
      Seq.empty[ServiceTaskOptionBase],
      Seq.empty[ServiceTaskOptionBase],
      None,
      Some(true)
    )
  }

  protected def testPbsmrtpipeReports(dsMeta: DataSetMetaDataSet) =
    for {
      opts <- toPbsmrtpipeOpts(dsMeta.uuid)
      dsInputReports <- client.getSubreadSetReports(dsMeta.uuid)
      job <- client.runAnalysisPipeline(opts)
      job <- Future.fromTry(client.pollForSuccessfulJob(job.id))
      datastore <- client.getJobDataStore(job.id)
      dsFile <- toF(
        datastore
          .filter(_.fileTypeId == FileTypes.DS_SUBREADS.fileTypeId)
          .head)
      serviceDataSet <- client.getDataSet(dsFile.uuid)
      dsReports <- client.getSubreadSetReports(serviceDataSet.uuid)
      _ <- failIf(dsReports.size != 1, // only one references the ds uuid
                  "Expected a single report from dataset endpoint")
      jobReports <- client.getJobReports(job.id)
      _ <- failIf(jobReports.size != 3, // two from task, one from pbsmrtpipe
                  "Expected two reports from job endpoint")
      dsInputReports2 <- client.getSubreadSetReports(dsMeta.uuid)
      _ <- failIf(
        dsInputReports2.size != dsInputReports.size,
        "Number of reports for input dataset should not have changed")
      rpt <- client.getJobReport(job.id, dsReports(0).dataStoreFile.uuid)
      _ <- failIf(!(rpt.datasetUuids.contains(serviceDataSet.uuid)),
                  "Expected report object to reference dataset UUID")
    } yield s"Filtering by dataset_uuids verified"

  case object RunImportReportsStep extends VarStep[DataSetMetaDataSet] {
    private val TEST_VALUES = Map[String, Long](RPT_NBASES -> 19825,
                                                RPT_READLENGTH -> 9913,
                                                RPT_INSERT -> 1168)
    override val name: String = "RunImportReportsStep"
    override def runWith =
      for {
        dsMeta <- runTmpImportToDataSet("subreads-sequel")
        msg <- testSubreadImportReports(dsMeta, Some(TEST_VALUES))
      } yield dsMeta
  }

  case class RunPbsmrtpipeReportsStep(dsMeta: Var[DataSetMetaDataSet])
      extends VarStep[String] {
    override val name: String = "RunPbsmrtpipeReportsStep"
    override def runWith = testPbsmrtpipeReports(dsMeta.get)
  }

  private val subreadsMeta: Var[DataSetMetaDataSet] = Var()

  override val steps = Seq(
    subreadsMeta := RunImportReportsStep,
    RunPbsmrtpipeReportsStep(subreadsMeta)
  )
}
