package com.pacbio.simulator.scenarios

import java.util.UUID

import akka.actor.ActorSystem
import com.typesafe.config.Config

import com.pacbio.common.models._
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.{
  DataSetMetadataUtils,
  DataSetMetaTypes
}
import com.pacbio.secondary.smrtlink.analysis.datasets.io.DataSetLoader
import com.pacbio.secondary.smrtlink.analysis.externaltools.PacBioTestData
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  JobModels,
  OptionTypes
}
import com.pacbio.secondary.smrtlink.client.SmrtLinkServiceClient
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.pacbio.simulator.steps._

object SampleNamesScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(
      implicit system: ActorSystem): Scenario = {
    require(config.isDefined,
            "Path to config file must be specified for SampleNamesScenario")
    require(PacBioTestData.isAvailable,
            "PacBioTestData must be configured for SampleNamesScenario")
    val c: Config = config.get

    new SampleNamesScenario(getHost(c), getPort(c))
  }
}

class SampleNamesScenario(host: String, port: Int)
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

  override val name = "SampleNamesScenario"
  override val requirements = Seq.empty[String]
  override val smrtLinkClient = new SmrtLinkServiceClient(host, port)

  private val EXIT_SUCCESS: Var[Int] = Var(0)
  private val EXIT_FAILURE: Var[Int] = Var(1)
  private val PIPELINE_ID = "pbsmrtpipe.pipelines.dev_verify_sample_names"
  private val BIO_SAMPLE_NAME = "Hobbit"
  private val WELL_SAMPLE_NAME = "H. florensis DNA"
  private val FT_SUBREADS: Var[DataSetMetaTypes.DataSetMetaType] = Var(
    DataSetMetaTypes.Subread)

  private val testdata = PacBioTestData()

  private def toI(name: String) = s"pbsmrtpipe.task_options.$name"

  private def toPbsmrtpipeOpts(dsName: String,
                               dsId: Var[UUID],
                               bioName: String = BIO_SAMPLE_NAME,
                               wellName: String = WELL_SAMPLE_NAME) = {
    dsId.mapWith { id =>
      val ep = BoundServiceEntryPoint("eid_subread",
                                      FileTypes.DS_SUBREADS.fileTypeId,
                                      id)
      val taskOpts = Seq(ServiceTaskStrOption(toI("bio_sample_name"),
                                              bioName,
                                              STR.optionTypeId),
                         ServiceTaskStrOption(toI("well_sample_name"),
                                              wellName,
                                              STR.optionTypeId))
      PbSmrtPipeServiceOptions(s"Test sample name propagation with $dsName",
                               PIPELINE_ID,
                               Seq(ep),
                               taskOpts,
                               Seq.empty[ServiceTaskOptionBase])
    }
  }

  private def updateSubreadSet(uuid: Var[UUID]) =
    UpdateSubreadSetDetails(uuid,
                            Var(Some(true)),
                            Var(Some(BIO_SAMPLE_NAME)),
                            Var(Some(WELL_SAMPLE_NAME)))

  private def failIfWrongWellSampleName(subreads: Var[SubreadServiceDataSet],
                                        name: String) =
    fail(s"Expected wellSampleName=$name") IF subreads.mapWith(
      _.wellSampleName) !=? name

  private def failIfWrongBioSampleName(subreads: Var[SubreadServiceDataSet],
                                       name: String) =
    fail(s"Expected bioSampleName=$name") IF subreads.mapWith(_.bioSampleName) !=? name

  private def getSubreadsFile(files: Seq[DataStoreServiceFile]) =
    files.filter(_.fileTypeId == FileTypes.DS_SUBREADS.fileTypeId).head

  private val jobId: Var[UUID] = Var()
  private val jobStatus: Var[Int] = Var()
  private val job: Var[EngineJob] = Var()
  private val msg: Var[String] = Var()
  private val subreads: Var[SubreadServiceDataSet] = Var()
  private val subreadSets: Var[Seq[SubreadServiceDataSet]] = Var()
  private val dataStore: Var[Seq[DataStoreServiceFile]] = Var()

  private val singleSampleSteps =
    Seq("subreads-sequel", "subreads-biosample-1", "subreads-biosample-2")
      .flatMap { dsId =>
        val path = testdata.getTempDataSet(dsId)
        val uuid = getDataSetMiniMeta(path).uuid
        Seq(
          jobStatus := GetStatus,
          fail("Can't get SMRT server status") IF jobStatus !=? EXIT_SUCCESS,
          jobId := ImportDataSet(Var(path), FT_SUBREADS),
          WaitForSuccessfulJob(jobId),
          updateSubreadSet(Var(uuid)),
          subreads := GetSubreadSet(Var(uuid)),
          failIfWrongWellSampleName(subreads, WELL_SAMPLE_NAME),
          failIfWrongBioSampleName(subreads, BIO_SAMPLE_NAME),
          // test propagation of sampe names to pbsmrtpipe
          jobId := RunAnalysisPipeline(toPbsmrtpipeOpts(dsId, Var(uuid))),
          WaitForSuccessfulJob(jobId)
        )
      }

  private val sampleDsIds = Seq(1, 2).map(i => s"subreads-biosample-$i")
  private val sampleDsTmp = sampleDsIds.map(id => testdata.getTempDataSet(id))
  private val sampleDs = sampleDsTmp.map(p => DataSetLoader.loadSubreadSet(p))
  private val sampleBioNames = sampleDs.map(ds => getBioSampleNames(ds).head)
  private val sampleWellNames = sampleDs.map(ds => getWellSampleNames(ds).head)
  private val combinedWellName = sampleWellNames.mkString(";")
  private val combinedBioName = sampleBioNames.mkString(";")
  private val sampleDsUuids =
    sampleDs.map(ds => UUID.fromString(ds.getUniqueId))
  private val mergedDataSetSteps =
    sampleDsTmp.zipWithIndex.flatMap {
      case (path, idx) =>
        // import and merge two related samples
        Seq(
          jobId := ImportDataSet(Var(path), FT_SUBREADS),
          WaitForSuccessfulJob(jobId),
          subreadSets := GetSubreadSets,
          subreads := GetSubreadSet(subreadSets.mapWith(_.last.uuid)),
          failIfWrongBioSampleName(subreads, sampleBioNames(idx)),
          failIfWrongWellSampleName(subreads, sampleWellNames(idx))
        )
    } ++ Seq(
      subreadSets := GetSubreadSets,
      subreads := GetSubreadSet(subreadSets.mapWith(_.last.uuid)),
      // merging the two inputs should result in name = '[multiple]', which we
      // can't edit
      jobId := MergeDataSets(
        FT_SUBREADS,
        subreadSets.mapWith(_.takeRight(2).map(ss => ss.id)),
        Var("merge-bio-samples")),
      WaitForSuccessfulJob(jobId),
      // we will use the existing subreadSets again below, so we get the new
      // SubreadSet from the job datastore
      dataStore := GetJobDataStore(jobId),
      subreads := GetSubreadSet(
        dataStore.mapWith(files => getSubreadsFile(files).uuid)),
      failIfWrongWellSampleName(subreads, MULTIPLE_SAMPLES_NAME),
      failIfWrongBioSampleName(subreads, MULTIPLE_SAMPLES_NAME),
      updateSubreadSet(subreads.mapWith(_.uuid)) SHOULD_RAISE classOf[
        Exception],
      // the individual sample names should still be propagated to pbsmrtpipe
      jobId := RunAnalysisPipeline(
        toPbsmrtpipeOpts("merged-bio-samples",
                         subreads.mapWith(_.uuid),
                         combinedBioName,
                         combinedWellName)),
      WaitForSuccessfulJob(jobId),
      // Now set both inputs to have the same sample names, and merge again
      updateSubreadSet(Var(sampleDsUuids(0))),
      updateSubreadSet(Var(sampleDsUuids(1))),
      jobId := MergeDataSets(
        FT_SUBREADS,
        subreadSets.mapWith(_.takeRight(2).map(ss => ss.id)),
        Var("merge-bio-samples-renamed")),
      WaitForSuccessfulJob(jobId),
      // the new merged dataset should have the single names, which we can edit
      dataStore := GetJobDataStore(jobId),
      subreads := GetSubreadSet(
        dataStore.mapWith(files => getSubreadsFile(files).uuid)),
      failIfWrongWellSampleName(subreads, WELL_SAMPLE_NAME),
      failIfWrongBioSampleName(subreads, BIO_SAMPLE_NAME),
      updateSubreadSet(subreads.mapWith(_.uuid)),
      jobId := RunAnalysisPipeline(
        toPbsmrtpipeOpts("merged-bio-samples-renamed",
                         subreads.mapWith(_.uuid))),
      WaitForSuccessfulJob(jobId)
    )

  override val steps = singleSampleSteps ++ mergedDataSetSteps
}
