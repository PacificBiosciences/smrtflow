package com.pacbio.simulator.scenarios

import java.util.UUID

import scala.concurrent.Future

import akka.actor.ActorSystem
import com.typesafe.config.Config

import com.pacbio.common.models._
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.{
  DataSetMetadataUtils,
  DataSetMetaTypes,
  DataSetFilterProperty
}
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.smrtlink.analysis.externaltools.PacBioTestData
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  JobModels,
  OptionTypes
}
import com.pacbio.secondary.smrtlink.client.SmrtLinkServiceClient
import com.pacbio.secondary.smrtlink.jobtypes.PbsmrtpipeJobOptions
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.pacbio.simulator.steps._

object CopyDataSetScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(
      implicit system: ActorSystem): Scenario = {
    require(config.isDefined,
            "Path to config file must be specified for CopyDataSetScenario")
    require(PacBioTestData.isAvailable,
            "PacBioTestData must be configured for CopyDataSetScenario")
    val c: Config = config.get

    val smrtLinkClient = new SmrtLinkServiceClient(getHost(c), getPort(c))
    val testdata = PacBioTestData()
    new CopyDataSetScenario(smrtLinkClient, testdata)
  }
}

class CopyDataSetScenario(client: SmrtLinkServiceClient,
                          testdata: PacBioTestData)
    extends Scenario
    with VarSteps
    with ConditionalSteps
    with IOSteps
    with SmrtLinkSteps
    with DataSetMetadataUtils {

  import JobModels._
  import OptionTypes._
  import CommonModels._
  import CommonModelImplicits._

  override val name = "CopyDataSetScenario"
  override val requirements = Seq("SL-49")
  override val smrtLinkClient = client

  private val PIPELINE_ID = "pbsmrtpipe.pipelines.dev_verify_dataset_filters"
  private val FT_SUBREADS: Var[DataSetMetaTypes.DataSetMetaType] = Var(
    DataSetMetaTypes.Subread)
  private val TEST_FILE_ID = "subreads-xml"
  private val DS_NAME_1 = "DataSet 1"
  private val DS_NAME_2 = "DataSet 2"
  private val TOTAL_LENGTH_EXPECTED_1 = 62340
  private val NUM_RECORDS_EXPECTED_1 = 117
  private val TOTAL_LENGTH_EXPECTED_2 = 17601
  private val NUM_RECORDS_EXPECTED_2 = 13

  private val subreadsXml = testdata.getTempDataSet(TEST_FILE_ID)

  private def toI(name: String) = s"pbsmrtpipe.task_options.$name"

  private def toPbsmrtpipeOpts(dsName: String,
                               dsId: UUID,
                               numRecords: Int,
                               totalLength: Int) = {
    val ep = BoundServiceEntryPoint("eid_subread",
                                    FileTypes.DS_SUBREADS.fileTypeId,
                                    dsId)
    val taskOpts = Seq(
      ServiceTaskIntOption(toI("num_records"), numRecords, INT.optionTypeId),
      ServiceTaskIntOption(toI("total_length"), totalLength, INT.optionTypeId))
    PbsmrtpipeJobOptions(
      Some(s"Test dataset copying/filtering with $dsName"),
      Some("scenario-runner CopyDataSetScenario"),
      PIPELINE_ID,
      Seq(ep),
      taskOpts,
      Seq.empty[ServiceTaskOptionBase]
    )
  }

  private val filters1 = Seq(
    Seq(DataSetFilterProperty("length", ">=", "1000")))

  private def getSubreadsId(files: Seq[DataStoreServiceFile]) =
    files.filter(_.fileTypeId == FileTypes.DS_SUBREADS.fileTypeId).head.uuid

  private def failIf(condition: Boolean, msg: String) = {
    if (!condition) {
      Future.successful("Condition false")
    } else {
      Future.failed(new RuntimeException(msg))
    }
  }

  private def runCopySubreadsAndVerify(jobId: UUID,
                                       name: String,
                                       numRecords: Int,
                                       totalLength: Int) = {
    for {
      copyJob <- Future.fromTry(client.pollForSuccessfulJob(jobId))
      dataStore <- client.getJobDataStore(copyJob.id)
      dsId <- Future.successful(getSubreadsId(dataStore))
      subreads <- client.getSubreadSet(dsId)
      _ <- failIf(subreads.numRecords != numRecords, "Wrong numRecords")
      _ <- failIf(subreads.totalLength != totalLength, "Wrong totalLength")
      _ <- failIf(subreads.name != name, "Wrong dataset name")
      analysisJob <- client.runAnalysisPipeline(
        toPbsmrtpipeOpts(name, dsId, numRecords, totalLength))
      analysisJob <- Future.fromTry(
        client.pollForSuccessfulJob(analysisJob.id))
    } yield subreads
  }

  private def runTests = {
    for {
      _ <- andLog(s"Starting to run CopyDataSetScenario")
      status <- client.getStatus
      _ <- andLog(
        s"Successfully connected to SMRT Link Server: ${client.RootUri}")
      dsMeta <- Future.successful(getDataSetMiniMeta(subreadsXml))
      importJob <- client.importDataSet(subreadsXml, dsMeta.metatype)
      importJob <- Future.fromTry(client.pollForSuccessfulJob(importJob.id))
      _ <- andLog(s"Successfully imported dataset ${dsMeta.uuid}")
      subreads <- client.getSubreadSet(dsMeta.uuid)
      _ <- failIf(subreads.numRecords != NUM_RECORDS_EXPECTED_1,
                  "Wrong numRecords")
      _ <- failIf(subreads.totalLength != TOTAL_LENGTH_EXPECTED_1,
                  "Wrong totalLength")
      // copy with no filters
      copyJob <- client.copyDataSet(dsMeta.uuid, Seq(), Some(DS_NAME_1))
      subreads <- runCopySubreadsAndVerify(copyJob.uuid,
                                           DS_NAME_1,
                                           NUM_RECORDS_EXPECTED_1,
                                           TOTAL_LENGTH_EXPECTED_1)
      _ <- failIf(subreads.uuid == dsMeta.uuid, "UUID should have changed")
      // with filters
      copyJob <- client.copyDataSet(dsMeta.uuid, filters1, Some(DS_NAME_2))
      subreads <- runCopySubreadsAndVerify(copyJob.uuid,
                                           DS_NAME_2,
                                           NUM_RECORDS_EXPECTED_2,
                                           TOTAL_LENGTH_EXPECTED_2)
      _ <- failIf(subreads.uuid == dsMeta.uuid, "UUID should have changed")
    } yield subreads
  }

  case object RunCopyDataSetsTestsStep extends VarStep[SubreadServiceDataSet] {
    override val name: String = "RunCopyDataSetsTestsStep"
    override def runWith = runTests
  }

  override val steps = Seq(RunCopyDataSetsTestsStep)
}
