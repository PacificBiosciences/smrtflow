package com.pacbio.simulator.scenarios

import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import java.io.{File, PrintWriter}

import scala.collection._

import akka.actor.ActorSystem
import com.typesafe.config.Config

import com.pacbio.secondary.smrtlink.analysis.externaltools.{
  PacBioTestData,
  PbReports
}
import com.pacbio.secondary.smrtlink.client.{
  SmrtLinkServiceClient,
  ClientUtils
}
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.analysis.reports.ReportModels.Report
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  JobModels,
  OptionTypes
}
import com.pacbio.common.models._
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.pacbio.simulator.steps._

object UpgradeScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(
      implicit system: ActorSystem): Scenario = {
    require(config.isDefined,
            "Path to config file must be specified for UpgradeScenario")
    require(PacBioTestData.isAvailable,
            "PacBioTestData must be configured for UpgradeScenario")
    val c: Config = config.get

    new UpgradeScenario(getHost(c),
                        getPort(c),
                        // FIXME I'd rather pass this as a cmdline arg but it's difficult
                        c.getString("preUpgrade").toBoolean)
  }
}

class UpgradeScenario(host: String, port: Int, preUpgrade: Boolean)
    extends PbsmrtpipeScenarioCore {

  import OptionTypes._
  import JobModels._

  override val name = "UpgradeScenario"
  override val smrtLinkClient = new SmrtLinkServiceClient(host, port)

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
    fail("Expected one report") IF jobReports.mapWith(_.size) !=? 1,
    report := GetJobReport(job.mapWith(_.id),
                           jobReports.mapWith(_(0).dataStoreFile.uuid)),
    fail("Wrong report UUID in datastore") IF jobReports.mapWith(
      _(0).dataStoreFile.uuid) !=? report.mapWith(_.uuid),
    job := GetJob(jobId),
    fail("Expected non-blank smrtlinkVersion") IF job.mapWith(
      _.smrtlinkVersion) ==? None,
    entryPoints := GetJobEntryPoints(job.mapWith(_.id)),
    fail("Expected one entry point") IF entryPoints.mapWith(_.size) !=? 1
    //fail("Wrong entry point UUID") IF entryPoints
    //  .mapWith(_(0).datasetUUID) !=? subreadsUuid
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
