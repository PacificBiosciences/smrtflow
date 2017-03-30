
// TODO this should eventually replace stress.py.  need to figure out a way
// to rewrite dataset UUIDs on the fly first.

package com.pacbio.simulator.scenarios

import java.net.URL
import java.nio.file.{Path, Paths}
import java.util.UUID
import java.io.{File, PrintWriter}

import akka.actor.ActorSystem

import scala.collection._
import com.typesafe.config.{Config, ConfigException}
import com.pacbio.secondary.analysis.externaltools.{PacBioTestData, PbReports}
import com.pacbio.secondary.smrtlink.client.{SmrtLinkServiceAccessLayer, ClientUtils}
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.reports.ReportModels.Report
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.pacbio.simulator.steps._

object StressTestScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(implicit system: ActorSystem): Scenario = {
    require(config.isDefined, "Path to config file must be specified for StressTestScenario")
    require(PacBioTestData.isAvailable, "PacBioTestData must be configured for StressTestScenario")
    val c: Config = config.get

    // Resolve overrides with String
    def getInt(key: String): Int =
      try {
        c.getInt(key)
      } catch {
        case e: ConfigException.WrongType => c.getString(key).trim.toInt
      }

    new StressTestScenario(
      c.getString("smrtflow.server.host"),
      getInt("smrtflow.server.port"),
      getInt("smrtflow.test.njobs"),
      getInt("smrtflow.test.max-time"))
  }
}

class StressTestScenario(host: String, port: Int, nJobs: Int, maxTime: Int)
    extends Scenario
    with VarSteps
    with ConditionalSteps
    with IOSteps
    with SmrtLinkSteps
    with ClientUtils {

  override val name = "StressTestScenario"

  override val smrtLinkClient = new SmrtLinkServiceAccessLayer(host, port)

  val TIMEOUT_ERR = s"Job did not complete within $maxTime seconds"
  val EXIT_SUCCESS: Var[Int] = Var(0)
  val EXIT_FAILURE: Var[Int] = Var(1)

  val testdata = PacBioTestData()

  val reference = Var(testdata.getFile("lambdaNEB"))
  val refUuid = Var(dsUuidFromPath(reference.get))
  val pipelineOpts: Var[PbSmrtPipeServiceOptions] = Var(
    PbSmrtPipeServiceOptions(
      "stress-test",
      "pbsmrtpipe.pipelines.dev_diagnostic_stress",
      Seq(BoundServiceEntryPoint("eid_ref_dataset",
                                 "PacBio.DataSet.ReferenceSet",
                                 Right(refUuid.get))),
      Seq[ServiceTaskOptionBase](),
      Seq[ServiceTaskOptionBase]()))
  val jobId: Var[UUID] = Var()
  val jobIds: Seq[Var[UUID]] = (0 to nJobs).map(_ => Var(UUID.randomUUID()))
  val jobStatus: Var[Int] = Var()

  val setupSteps = Seq(
    jobStatus := GetStatus,
    fail("Can't get SMRT server status") IF jobStatus !=? EXIT_SUCCESS,
    jobId := ImportDataSet(reference, Var(FileTypes.DS_REFERENCE.fileTypeId)),
    jobStatus := WaitForJob(jobId),
    fail("Import job failed") IF jobStatus !=? EXIT_SUCCESS
  )
  // submit multiple jobs in quick succession, and make sure they all finish.
  val pbsmrtpipeJobTests = (0 to nJobs).map(i => Seq(
      jobIds(i) := RunAnalysisPipeline(pipelineOpts)
    )).flatMap(s => s) ++ (0 to nJobs).map(i => Seq(
      jobStatus := WaitForJob(jobIds(i), Var(maxTime)),
      fail(TIMEOUT_ERR) IF jobStatus !=? EXIT_SUCCESS
    )).flatMap(s => s)
  override val steps = setupSteps ++ pbsmrtpipeJobTests
}
