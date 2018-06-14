package com.pacbio.simulator.scenarios

import java.util.UUID

import akka.actor.ActorSystem

import scala.collection._
import com.typesafe.config.Config
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.externaltools.PacBioTestResources
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.io.PacBioDataBundleIOUtils
import com.pacbio.secondary.smrtlink.client.{
  ClientUtils,
  SmrtLinkServiceClient
}
import com.pacbio.secondary.smrtlink.jobtypes.PbsmrtpipeJobOptions
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.pacbio.simulator.steps._

object ChemistryBundleScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(
      implicit system: ActorSystem): Scenario = {

    val c = verifyRequiredConfig(config)
    val testResources = verifyConfiguredWithTestResources(c)
    val smrtLinkClient = new SmrtLinkServiceClient(getHost(c), getPort(c))
    new ChemistryBundleScenario(smrtLinkClient, testResources)
  }
}

class ChemistryBundleScenario(client: SmrtLinkServiceClient,
                              testResources: PacBioTestResources)
    extends Scenario
    with VarSteps
    with ConditionalSteps
    with IOSteps
    with SmrtLinkSteps
    with ClientUtils
    with PacBioDataBundleIOUtils {

  import com.pacbio.common.models.CommonModelImplicits._

  override val name = "ChemistryBundleScenario"
  override val requirements = Seq("SEQ-306", "SL-458", "SL-998")

  override val smrtLinkClient = client

  val EXIT_SUCCESS: Var[Int] = Var(0)
  val EXIT_FAILURE: Var[Int] = Var(1)

  val subreads = Var(
    testResources.findById("subreads-sequel").get.getTempDataSetFile().path)
  val subreadsUuid = Var(getDataSetMiniMeta(subreads.get).uuid)
  val ftSubreads: Var[DataSetMetaTypes.DataSetMetaType] = Var(
    DataSetMetaTypes.Subread)
  val bundle: Var[PacBioDataBundle] = Var()
  val jobId: Var[UUID] = Var()
  val jobStatus: Var[Int] = Var()

  def getPipelineOpts(version: String): PbsmrtpipeJobOptions = {
    println(s"Chemistry bundle version is $version")
    PbsmrtpipeJobOptions(
      Some("chemistry-bundle-test"),
      Some("scenario-runner ChemistryBundleScenario"),
      "pbsmrtpipe.pipelines.dev_verify_chemistry",
      Seq(
        BoundServiceEntryPoint("eid_subread",
                               FileTypes.DS_SUBREADS.fileTypeId,
                               subreadsUuid.get)),
      Seq(
        ServiceTaskStrOption("pbsmrtpipe.task_options.chemistry_version",
                             version)),
      Seq[ServiceTaskOptionBase]()
    )
  }

  val setupSteps = Seq(
    jobStatus := GetStatus,
    fail("Can't get SMRT server status") IF jobStatus !=? EXIT_SUCCESS,
    jobId := ImportDataSet(subreads, ftSubreads),
    jobStatus := WaitForJob(jobId),
    fail("Import job failed") IF jobStatus !=? EXIT_SUCCESS
  )
  val pbsmrtpipeSteps = Seq(
    bundle := GetBundle(Var("chemistry-pb")),
    jobId := RunAnalysisPipeline(
      bundle.mapWith(b => getPipelineOpts(b.version))),
    jobStatus := WaitForJob(jobId),
    fail("pbsmrtpipe job failed") IF jobStatus !=? EXIT_SUCCESS
  )
  override val steps = setupSteps ++ pbsmrtpipeSteps
}
