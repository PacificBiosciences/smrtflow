package com.pacbio.simulator.scenarios

import java.net.URL
import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.actor.ActorSystem
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.externaltools.PacBioTestData
import com.pacbio.secondary.analysis.jobs.JobModels.{ServiceTaskOptionBase, _}
import com.pacbio.secondary.analysis.jobs.OptionTypes.{CHOICE, CHOICE_FLOAT, _}
import com.pacbio.secondary.smrtlink.client.{AnalysisServiceAccessLayer, SmrtLinkServiceAccessLayer}
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.simulator.clients.InstrumentControlClient
import com.pacbio.simulator.steps._
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.typesafe.config.{Config, ConfigException}
import com.pacbio.simulator.steps.IcsClientSteps
import com.pacbio.secondary.analysis.reports.ReportModels.Report
import com.pacbio.secondary.analysis.jobs.{AnalysisJobStates, JobModels, OptionTypes}

import scala.collection.Seq
import spray.httpx.UnsuccessfulResponseException

// for SAT
import com.pacbio.secondary.analysis.externaltools.{PacBioTestData,PbReports}
import com.pacbio.secondary.smrtlink.client.ClientUtils
/**
  * Example config:
  *
  * {{{
  *   smrt-link-host = "smrtlink-bihourly"
  *   smrt-link-port = 8081
  *   run-xml-path = "/path/to/testdata/runDataModel.xml"
  * }}}
  */
object RunDesignWithICSScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(implicit system: ActorSystem): Scenario = {
    require(config.isDefined, "Path to config file must be specified for RunDesignScenario")
    val c: Config = config.get

    // Resolve overrides with String
    def getInt(key: String): Int =
      try {
        c.getInt(key)
      } catch {
        case e: ConfigException.WrongType => c.getString(key).trim.toInt
      }

    new RunDesignWithICSScenario(
      c.getString("smrt-link-host"),
      getInt("smrt-link-port"),
      c.getString("ics-host"),
      getInt("ics-port"),
      Paths.get(c.getString("run-xml-path")))
  }
}

class RunDesignWithICSScenario(host: String,
                               port: Int,
                               icsHost : String,
                               icsPort : Int,
                               runXmlFile: Path)
  extends Scenario
    with VarSteps
    with ConditionalSteps
    with IOSteps
    with SmrtLinkSteps
    with SmrtAnalysisSteps
    with IcsClientSteps
    with ClientUtils{

  import scala.concurrent.duration._
  import com.pacbio.simulator.clients.ICSState
  import ICSState._

  override val name = "RunDesignScenario"

  override val smrtLinkClient = new AnalysisServiceAccessLayer(new URL("http", host, port, ""))
  override val icsClient = new InstrumentControlClient(new URL("http",icsHost, icsPort,""))

  val runXmlPath: Var[String] = Var(runXmlFile.toString)
  val runXml: Var[String] = Var()
  val runId: Var[UUID] = Var()
  val runDesign: Var[Run] = Var()
  val runDesigns: Var[Seq[RunSummary]] = Var()

  val EXIT_SUCCESS: Var[Int] = Var(0)
  val EXIT_FAILURE: Var[Int] = Var(1)

  // for SAT - hacking from pbSmrtPipeScenario

  val testdata = PacBioTestData()
  val reference = Var(testdata.getFile("lambdaNEB"))
  val refUuid = Var(dsUuidFromPath(reference.get))
  val subreads = Var(testdata.getFile("subreads-xml"))
  val subreadsUuid = Var(dsUuidFromPath(subreads.get))


  val jobId: Var[UUID] = Var()
  val jobId2: Var[UUID] = Var()
  val jobStatus: Var[Int] = Var()
  val job: Var[EngineJob] = Var()
  val importJob: Var[EngineJob] = Var()
  val jobReports: Var[Seq[DataStoreReportFile]] = Var()
  val report: Var[Report] = Var()
  val dataStore: Var[Seq[DataStoreServiceFile]] = Var()
  val entryPoints: Var[Seq[EngineJobEntryPoint]] = Var()
  val childJobs: Var[Seq[EngineJob]] = Var()
  val referenceSets: Var[Seq[ReferenceServiceDataSet]] = Var()
  val dsRules: Var[PipelineDataStoreViewRules] = Var()
  val pipelineRules: Var[PipelineTemplateViewRule] = Var()
  val jobOptions: Var[PipelineTemplatePreset] = Var()
  val jobEvents: Var[Seq[JobEvent]] = Var()
  val jobTasks: Var[Seq[JobTask]] = Var()

  def toI(name: String) = s"pbsmrtpipe.task_options.$name"

 val diagnosticOpts: Var[PbSmrtPipeServiceOptions] = Var(
    PbSmrtPipeServiceOptions(
      "diagnostic-test",
      "pbsmrtpipe.pipelines.dev_diagnostic",
      Seq(BoundServiceEntryPoint("eid_ref_dataset",
                                 "PacBio.DataSet.ReferenceSet",
                                 Right(refUuid.get))),
      Seq(
        ServiceTaskBooleanOption(toI("dev_diagnostic_strict"), true,
                                 BOOL.optionTypeId),
        ServiceTaskIntOption(toI("test_int"), 2, INT.optionTypeId),
        ServiceTaskDoubleOption(toI("test_float"), 1.234, FLOAT.optionTypeId),
        ServiceTaskStrOption(toI("test_str"), "Hello, world", STR.optionTypeId),
        ServiceTaskIntOption(toI("test_choice_int"), 3, CHOICE_INT.optionTypeId),
        ServiceTaskDoubleOption(toI("test_choice_float"), 1.0, CHOICE_FLOAT.optionTypeId),
        ServiceTaskStrOption(toI("test_choice_str"), "B", CHOICE.optionTypeId)
      ),
      Seq[ServiceTaskOptionBase]()))
      
  val failOpts = diagnosticOpts.mapWith(_.copy(
    taskOptions=Seq(ServiceTaskBooleanOption(toI("raise_exception"), true,
                                             BOOL.optionTypeId))))

  val setupSteps = Seq(
    jobStatus := GetStatus,
    fail("Can't get SMRT server status") IF jobStatus !=? EXIT_SUCCESS,
    jobId := ImportDataSet(reference, Var(FileTypes.DS_REFERENCE.fileTypeId)),
    jobStatus := WaitForJob(jobId),
    fail("Import job failed") IF jobStatus !=? EXIT_SUCCESS,
    jobId := ImportDataSet(subreads, Var(FileTypes.DS_SUBREADS.fileTypeId)),
    jobStatus := WaitForJob(jobId),
    fail("Import job failed") IF jobStatus !=? EXIT_SUCCESS,
    childJobs := GetJobChildren(jobId),
    fail("There should not be any child jobs") IF childJobs.mapWith(_.size) !=? 0,
    referenceSets := GetReferenceSets
    //fail("Expected one reference set") IF referenceSets.mapWith(_.size) !=? 1
  )

  val satSteps = Seq(
      jobId := RunAnalysisPipeline(diagnosticOpts),
      jobStatus := WaitForJob(jobId),
      fail("Pipeline job failed") IF jobStatus !=? EXIT_SUCCESS,
      dataStore := GetAnalysisJobDataStore(jobId)
  )

  val satTests = Seq(
    jobId := RunAnalysisPipeline(diagnosticOpts),
    jobStatus := WaitForJob(jobId),
    fail("Pipeline job failed") IF jobStatus !=? EXIT_SUCCESS,
    dataStore := GetAnalysisJobDataStore(jobId),
    fail("Expected four datastore files") IF dataStore.mapWith(_.size) !=? 4,
    jobReports := GetAnalysisJobReports(jobId),
    fail("Expected one report") IF jobReports.mapWith(_.size) !=? 1,
    report := GetReport(jobReports.mapWith(_(0).dataStoreFile.uuid)),
    fail("Wrong report UUID in datastore") IF jobReports.mapWith(_(0).dataStoreFile.uuid) !=? report.mapWith(_.uuid),
    job := GetJob(jobId),
    fail("Expected non-blank smrtlinkVersion") IF job.mapWith(_.smrtlinkVersion) ==? None,
    fail("Expected non-blank smrtlinkToolsVersion") IF job.mapWith(_.smrtlinkToolsVersion) ==? None,
    entryPoints := GetAnalysisJobEntryPoints(job.mapWith(_.id)),
    fail("Expected one entry point") IF entryPoints.mapWith(_.size) !=? 1,
    fail("Wrong entry point UUID") IF entryPoints.mapWith(_(0).datasetUUID) !=? refUuid,
    job := GetJob(jobId),
    jobTasks := GetAnalysisJobTasks(job.mapWith(_.id)),
    //fail("Expected two job tasks") IF jobTasks.mapWith(_.size) !=? 3,
    //fail("Expected both tasks to succeed") IF jobTasks.mapWith(_.filter(t => t.state == "successful").size) !=? 3,
    jobEvents := GetAnalysisJobEvents(job.mapWith(_.id)),
    fail("Expected at least one job event") IF jobEvents.mapWith(_.size) ==? 0,
    // there are two tasks, each one has CREATED and SUCCESSFUL events
    fail("Expected four task_status events") IF jobEvents.mapWith(_.filter(e => e.eventTypeId == JobConstants.EVENT_TYPE_JOB_TASK_STATUS).size) !=? 4,
    fail("Expected three SUCCESSFUL events") IF jobEvents.mapWith(_.filter(e => e.state == AnalysisJobStates.SUCCESSFUL).size) !=? 3,
    // Failure mode
    jobId2 := RunAnalysisPipeline(failOpts),
    jobStatus := WaitForJob(jobId2),
    fail("Expected job to fail when raise_exception=true") IF jobStatus !=? EXIT_FAILURE,
    job := GetJob(jobId2),
    jobTasks := GetAnalysisJobTasks(job.mapWith(_.id)),
    fail("Expected two job tasks") IF jobTasks.mapWith(_.size) !=? 2,
    jobEvents := GetAnalysisJobEvents(job.mapWith(_.id)),
    fail("Expected at least one job event") IF jobEvents.mapWith(_.size) ==? 0,
    // FIXME the task status events never leave CREATED state...
    //fail("Expected two task_status events") IF jobEvents.mapWith(_.filter(e => e.eventTypeId == JobConstants.EVENT_TYPE_JOB_TASK_STATUS).size) !=? 2,
    //fail("Expected FAILED task_status event") IF jobEvents.mapWith(_.filter(e => (e.eventTypeId == JobConstants.EVENT_TYPE_JOB_TASK_STATUS) && (e.state == AnalysisJobStates.FAILED)).size) !=? 1,
    fail("Expected FAILED job_status event") IF jobEvents.mapWith(_.filter(e => (e.eventTypeId == JobConstants.EVENT_TYPE_JOB_STATUS) && (e.state == AnalysisJobStates.FAILED)).size) !=? 1,
    // FIXME this is broken because of wrong Content-Type
    //jobOptions := GetAnalysisJobOptions(job.mapWith(_.id)),
    //fail("Expected a single task option") IF jobOptions.mapWith(_.taskOptions.size) !=? 1,
    // try and fail to delete ReferenceSet import
    referenceSets := GetReferenceSets,
    importJob := GetJobById(referenceSets.mapWith(_.head.jobId)),
    //DeleteJob(importJob.mapWith(_.uuid), Var(true)) SHOULD_RAISE classOf[UnsuccessfulResponseException],
    //DeleteJob(importJob.mapWith(_.uuid), Var(false)) SHOULD_RAISE classOf[UnsuccessfulResponseException],
    // delete pbsmrtpipe jobs
    jobId2 := DeleteJob(jobId2, Var(false)),
    jobStatus := WaitForJob(jobId2),
    fail("Delete job failed") IF jobStatus !=? EXIT_SUCCESS,
    job := GetJob(jobId),
    jobId2 := DeleteJob(jobId, Var(true)),
    fail("Expected original job to be returned") IF jobId2 !=? jobId,
    jobId := DeleteJob(jobId, Var(false)),
    jobStatus := WaitForJob(jobId),
    fail("Delete job failed") IF jobStatus !=? EXIT_SUCCESS,
    fail("Expected report file to be deleted") IF jobReports.mapWith(_(0).dataStoreFile.fileExists) !=? false,
    dataStore := GetAnalysisJobDataStore(job.mapWith(_.uuid)),
    fail("Expected isActive=false") IF dataStore.mapWith(_.filter(f => f.isActive).size) !=? 0,
    // now delete the ReferenceSet import job
    jobId := DeleteJob(importJob.mapWith(_.uuid), Var(false)),
    jobStatus := WaitForJob(jobId),
    fail("Delete job failed") IF jobStatus !=? EXIT_SUCCESS,
    //fail("Reference dataset file should not have been deleted") IF referenceSets.mapWith(rs => fileExists(rs.head.path)) !=? true,
    referenceSets := GetReferenceSets
    //fail("There should be zero ReferenceSets") IF referenceSets.mapWith(_.size) !=? 0
  )

  val icsEndToEndsteps = Seq(
    runDesigns := GetRuns,

    //fail("Run database should be initially empty") IF runDesigns ? (_.nonEmpty),

    runXml := ReadFileFromTemplate(runXmlPath),

    runId := CreateRun(runXml),

    runDesign := GetRun(runId),

    fail("Wrong uniqueId found") IF runDesign.mapWith(_.uniqueId) !=? runId,

    fail("Expected reserved to be false") IF runDesign.mapWith(_.reserved) !=? false,

    PostLoadInventory,

    PostRunDesignToICS(runDesign),

    PostRunRqmtsToICS,

    //GetRunRqmts(),

    GetRunStatus(runDesign, Seq(Idle,Ready)),

    // WAIT FOR FEW SECS, FOR ICS TO LOAD THE RUN
    SleepStep(1.minutes),

    PostRunStartToICS,

    GetRunStatus(runDesign, Seq(Running,Starting)),

    SleepStep(15.minutes),

    GetRunStatus(runDesign, Seq(Complete))
  )

  override val steps =  icsEndToEndsteps ++ setupSteps ++ satTests 
}
