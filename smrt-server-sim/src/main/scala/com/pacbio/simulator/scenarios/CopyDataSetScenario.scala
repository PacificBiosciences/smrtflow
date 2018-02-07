package com.pacbio.simulator.scenarios

import java.util.UUID

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

    new CopyDataSetScenario(getHost(c), getPort(c))
  }
}

class CopyDataSetScenario(host: String, port: Int)
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
  override val requirements = Seq.empty[String]
  override val smrtLinkClient = new SmrtLinkServiceClient(host, port)

  private val EXIT_SUCCESS: Var[Int] = Var(0)
  private val EXIT_FAILURE: Var[Int] = Var(1)
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

  private val testdata = PacBioTestData()
  private val subreadsXml = testdata.getTempDataSet(TEST_FILE_ID)
  private val subreadsId = getDataSetMiniMeta(subreadsXml).uuid

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

  private val jobId: Var[UUID] = Var()
  private val jobStatus: Var[Int] = Var()
  private val job: Var[EngineJob] = Var()
  private val msg: Var[String] = Var()
  private val subreads: Var[SubreadServiceDataSet] = Var()
  private val subreadSets: Var[Seq[SubreadServiceDataSet]] = Var()
  private val dataStore: Var[Seq[DataStoreServiceFile]] = Var()

  private def failIfWrongName(name: String) =
    fail("Wrong datset name") IF subreads.mapWith(_.name) !=? name
  private def failIfWrongNumRecords(numRecords: Int) =
    fail("Wrong number of records") IF subreads.mapWith(_.numRecords) !=? numRecords
  private def failIfWrongTotalLength(totalLength: Int) =
    fail("Wrong total length") IF subreads.mapWith(_.totalLength) !=? totalLength
  private def toOpts(name: String, numRecords: Int, totalLength: Int) =
    subreads.mapWith(s =>
      toPbsmrtpipeOpts(name, s.uuid, numRecords, totalLength))

  private val subreadSteps = Seq(
    jobStatus := GetStatus,
    fail("Can't get SMRT server status") IF jobStatus !=? EXIT_SUCCESS,
    jobId := ImportDataSet(Var(subreadsXml), FT_SUBREADS),
    WaitForSuccessfulJob(jobId),
    subreads := GetSubreadSet(Var(subreadsId)),
    failIfWrongNumRecords(NUM_RECORDS_EXPECTED_1),
    failIfWrongTotalLength(TOTAL_LENGTH_EXPECTED_1),
    // no filters
    jobId := CopyDataSetJob(subreadsId, Seq(), Some(DS_NAME_1)),
    WaitForSuccessfulJob(jobId),
    dataStore := GetJobDataStore(jobId),
    fail("Expected new UUID") IF dataStore
      .mapWith(getSubreadsId) ==? subreadsId,
    subreads := GetSubreadSet(dataStore.mapWith(getSubreadsId)),
    failIfWrongName(DS_NAME_1),
    jobId := RunAnalysisPipeline(
      toOpts(DS_NAME_1, NUM_RECORDS_EXPECTED_1, TOTAL_LENGTH_EXPECTED_1)),
    WaitForSuccessfulJob(jobId),
    // with filters
    jobId := CopyDataSetJob(subreadsId, filters1, Some(DS_NAME_2)),
    WaitForSuccessfulJob(jobId),
    dataStore := GetJobDataStore(jobId),
    fail("Expected new UUID") IF dataStore
      .mapWith(getSubreadsId) ==? subreadsId,
    subreads := GetSubreadSet(dataStore.mapWith(getSubreadsId)),
    failIfWrongName(DS_NAME_2),
    /*
    failIfWrongNumRecords(subreads, N_RECORDS_EXPECTED_2),
    failIfWrongTotalLength(subreads, TOTAL_LENGTH_EXPECTED_2)
     */
    jobId := RunAnalysisPipeline(
      toOpts(DS_NAME_2, NUM_RECORDS_EXPECTED_2, TOTAL_LENGTH_EXPECTED_2)),
    WaitForSuccessfulJob(jobId)
  )

  override val steps = subreadSteps
}
