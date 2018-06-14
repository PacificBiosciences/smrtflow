// TODO this should eventually replace stress.py.  need to figure out a way
// to rewrite dataset UUIDs on the fly first.

package com.pacbio.simulator.scenarios

import java.util.UUID

import scala.collection._
import akka.actor.ActorSystem
import com.pacbio.common.models.CommonModelImplicits
import com.typesafe.config.Config

import scala.concurrent.duration._
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.externaltools.PacBioTestResources
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.client.{
  ClientUtils,
  SmrtLinkServiceClient
}
import com.pacbio.secondary.smrtlink.jobtypes.PbsmrtpipeJobOptions
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.pacbio.simulator.steps._

object StressTestScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(
      implicit system: ActorSystem): Scenario = {

    val c = verifyRequiredConfig(config)
    val smrtLinkClient = new SmrtLinkServiceClient(getHost(c), getPort(c))
    val testResources = verifyConfiguredWithTestResources(c)
    new StressTestScenario(smrtLinkClient,
                           testResources,
                           getInt(c, "smrtflow.test.njobs"),
                           getInt(c, "smrtflow.test.max-time").seconds)
  }
}

class StressTestScenario(client: SmrtLinkServiceClient,
                         testResources: PacBioTestResources,
                         nJobs: Int,
                         maxTime: FiniteDuration)
    extends Scenario
    with VarSteps
    with ConditionalSteps
    with IOSteps
    with SmrtLinkSteps
    with ClientUtils {

  import CommonModelImplicits._

  override val name = "StressTestScenario"
  override val requirements = Seq("SL-41", "SL-1295")

  override val smrtLinkClient = client

  val TIMEOUT_ERR = s"Job did not complete within $maxTime seconds"
  val EXIT_SUCCESS: Var[Int] = Var(0)
  val EXIT_FAILURE: Var[Int] = Var(1)

  val reference = Var(
    testResources.getFile("lambdaNEB").get.getTempDataSetFile().path)
  val ftReference: Var[DataSetMetaTypes.DataSetMetaType] = Var(
    DataSetMetaTypes.Reference)
  val refUuid = Var(getDataSetMiniMeta(reference.get).uuid)
  val pipelineOpts: Var[PbsmrtpipeJobOptions] = Var(
    PbsmrtpipeJobOptions(
      Some("stress-test"),
      Some("scenario-runner StressTestScenario"),
      "pbsmrtpipe.pipelines.dev_diagnostic_stress",
      Seq(
        BoundServiceEntryPoint("eid_ref_dataset",
                               "PacBio.DataSet.ReferenceSet",
                               refUuid.get)),
      Seq[ServiceTaskOptionBase](),
      Seq[ServiceTaskOptionBase]()
    ))
  val jobId: Var[UUID] = Var()
  val jobIds: Seq[Var[UUID]] = (0 to nJobs).map(_ => Var(UUID.randomUUID()))
  val jobStatus: Var[Int] = Var()

  val setupSteps = Seq(
    jobStatus := GetStatus,
    fail("Can't get SMRT server status") IF jobStatus !=? EXIT_SUCCESS,
    jobId := ImportDataSet(reference, ftReference),
    jobStatus := WaitForJob(jobId),
    fail("Import job failed") IF jobStatus !=? EXIT_SUCCESS
  )
  // submit multiple jobs in quick succession, and make sure they all finish.
  val pbsmrtpipeJobTests = (0 to nJobs)
    .map(
      i =>
        Seq(
          jobIds(i) := RunAnalysisPipeline(pipelineOpts)
      ))
    .flatMap(s => s) ++ (0 to nJobs)
    .map(
      i =>
        Seq(
          jobStatus := WaitForJob(jobIds(i), Var(maxTime)),
          fail(TIMEOUT_ERR) IF jobStatus !=? EXIT_SUCCESS
      ))
    .flatMap(s => s)
  override val steps = setupSteps ++ pbsmrtpipeJobTests
}
