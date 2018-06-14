package com.pacbio.simulator.scenarios

import scala.collection._
import akka.actor.ActorSystem
import com.typesafe.config.Config
import com.pacbio.secondary.smrtlink.analysis.externaltools.PacBioTestResources

import com.pacbio.secondary.smrtlink.client.SmrtLinkServiceClient
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  JobModels,
  OptionTypes
}
import com.pacbio.common.models._
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.pacbio.simulator.steps._

object UpgradeScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(
      implicit system: ActorSystem): Scenario = {

    val c = verifyRequiredConfig(config)
    val testResources = verifyConfiguredWithTestResources(c)
    val smrtLinkClient = new SmrtLinkServiceClient(getHost(c), getPort(c))

    // FIXME I'd rather pass this as a cmdline arg but it's difficult
    val preUpgrade: Boolean = c.getString("preUpgrade").toBoolean
    new UpgradeScenario(smrtLinkClient, testResources, preUpgrade)
  }
}

class UpgradeScenario(client: SmrtLinkServiceClient,
                      val testResources: PacBioTestResources,
                      preUpgrade: Boolean)
    extends PbsmrtpipeScenarioCore {

  import OptionTypes._
  import JobModels._

  override val name = "UpgradeScenario"
  override val smrtLinkClient = client

  // options need to be empty because JSON format changed since 4.0
  private val cleanOpts = Var(
    diagnosticOptsCore.copy(
      taskOptions = Seq.empty[ServiceTaskOptionBase],
      workflowOptions = Seq.empty[ServiceTaskOptionBase]))
  val preUpgradeSteps = setupSteps ++ Seq(
    jobId := RunAnalysisPipeline(cleanOpts),
    jobStatus := WaitForJob(jobId),
    fail("Pipeline job failed") IF jobStatus !=? EXIT_SUCCESS
  )
  val diagnosticJobTests = Seq(
    jobId := GetLastAnalysisJobId,
    job := GetJob(jobId),
    dataStore := GetJobDataStore(jobId),
    fail(s"Expected at least one datastore file") IF dataStore
      .mapWith(_.size) ==? 0,
    jobReports := GetJobReports(jobId),
    fail("Expected at least one report") IF jobReports.mapWith(_.size) ==? 0,
    report := GetJobReport(job.mapWith(_.id),
                           jobReports.mapWith(_(0).dataStoreFile.uuid)),
    fail("Wrong report UUID in datastore") IF jobReports.mapWith(
      _(0).dataStoreFile.uuid) !=? report.mapWith(_.uuid),
    job := GetJob(jobId),
    fail("Expected non-blank smrtlinkVersion") IF job.mapWith(
      _.smrtlinkVersion) ==? None,
    entryPoints := GetJobEntryPoints(job.mapWith(_.id)),
    fail("Expected one entry point") IF entryPoints.mapWith(_.size) !=? 1,
    //fail("Wrong entry point UUID") IF entryPoints
    //  .mapWith(_(0).datasetUUID) !=? subreadsUuid
    fail(
      s"Expected Analysis Job subJobTypeId to be ${diagnosticOptsCore.pipelineId}")
      IF job.mapWith(_.subJobTypeId) !=? Some(diagnosticOptsCore.pipelineId)
  )

  // XXX unused, unnecessary?
  val satJobTests = Seq(
    jobId := RunAnalysisPipeline(satOpts),
    jobStatus := WaitForJob(jobId),
    fail("Pipeline job failed") IF jobStatus !=? EXIT_SUCCESS,
    dataStore := GetJobDataStore(jobId),
    fail("Expected four datastore files") IF dataStore.mapWith(_.size) !=? 3,
    jobReports := GetJobReports(jobId),
    fail("Expected four reports") IF jobReports.mapWith(_.size) !=? 4,
    job := GetJob(jobId),
    jobTasks := GetJobTasks(job.mapWith(_.id)),
    fail("Expected at least one job task") IF jobTasks.mapWith(_.size) ==? 0,
    jobEvents := GetJobEvents(job.mapWith(_.id)),
    fail("Expected at least one job event") IF jobEvents.mapWith(_.size) ==? 0
  )

  // This scenario is designed to be run twice: once with a smrtlink 4.0.0
  // install (SQLite), and a second time after upgrading to 4.x (Postgres)
  override val steps = if (preUpgrade) {
    preUpgradeSteps ++ diagnosticJobTests
  } else {
    diagnosticJobTests //++ satJobTests
  }
}
