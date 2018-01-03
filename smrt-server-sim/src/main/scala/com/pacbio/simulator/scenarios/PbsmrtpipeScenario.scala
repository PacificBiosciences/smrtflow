// TODO this should eventually replace stress.py.  need to figure out a way
// to rewrite dataset UUIDs on the fly first.

package com.pacbio.simulator.scenarios

import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import java.io.{File, PrintWriter}

import scala.collection._
import spray.json._
import akka.actor.ActorSystem
import com.typesafe.config.Config
import org.apache.commons.io.FileUtils

import com.pacbio.common.models._
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.externaltools.{
  PacBioTestData,
  PbReports
}
import com.pacbio.secondary.smrtlink.analysis.jobs.{
  AnalysisJobStates,
  JobModels,
  OptionTypes
}
import com.pacbio.secondary.smrtlink.analysis.reports.ReportModels.Report
import com.pacbio.secondary.smrtlink.client.{
  ClientUtils,
  SmrtLinkServiceAccessLayer
}
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.pacbio.simulator.steps._

object PbsmrtpipeScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(
      implicit system: ActorSystem): Scenario = {
    require(config.isDefined,
            "Path to config file must be specified for PbsmrtpipeScenario")
    require(PacBioTestData.isAvailable,
            "PacBioTestData must be configured for PbsmrtpipeScenario")
    val c: Config = config.get

    new PbsmrtpipeScenario(getHost(c), getPort(c))
  }
}

trait PbsmrtpipeScenarioCore
    extends Scenario
    with VarSteps
    with ConditionalSteps
    with IOSteps
    with SmrtLinkSteps {

  import JobModels._
  import OptionTypes._
  import CommonModels._
  import CommonModelImplicits._

  protected def fileExists(path: String) = Files.exists(Paths.get(path))

  protected val EXIT_SUCCESS: Var[Int] = Var(0)
  protected val EXIT_FAILURE: Var[Int] = Var(1)

  protected val tmpDir = Files.createTempDirectory("export-job")
  protected val testdata = PacBioTestData()
  protected def getSubreads =
    testdata.getTempDataSet("subreads-xml",
                            true,
                            tmpDirBase = "dataset contents")
  protected def getReference = testdata.getTempDataSet("lambdaNEB")

  protected val reference = Var(getReference)
  protected val refUuid = Var(getDataSetMiniMeta(reference.get).uuid)
  protected val subreads = Var(getSubreads)
  protected val subreadsUuid = Var(getDataSetMiniMeta(subreads.get).uuid)
  val ftSubreads: Var[DataSetMetaTypes.DataSetMetaType] = Var(
    DataSetMetaTypes.Subread)
  val ftReference: Var[DataSetMetaTypes.DataSetMetaType] = Var(
    DataSetMetaTypes.Reference)

  // Randomize project name to avoid collisions
  protected val projectName = Var(s"Project-${UUID.randomUUID()}")
  protected val projectDesc = Var("Project Description")
  protected val projectId: Var[Int] = Var()

  private def toI(name: String) = s"pbsmrtpipe.task_options.$name"

  def toDiagnosticOptions(referenceSet: UUID,
                          triggerFailure: Boolean = false,
                          name: String = "diagnostic-test",
                          projectId: Int = JobConstants.GENERAL_PROJECT_ID)
    : PbSmrtPipeServiceOptions = {
    val pipelineId = "pbsmrtpipe.pipelines.dev_diagnostic_subreads"
    val ep = BoundServiceEntryPoint("eid_subread",
                                    FileTypes.DS_SUBREADS.fileTypeId,
                                    subreadsUuid.get)

    val taskOptions = Seq(
      ServiceTaskBooleanOption(toI("dev_diagnostic_strict"),
                               true,
                               BOOL.optionTypeId),
      ServiceTaskBooleanOption(toI("raise_exception"),
                               triggerFailure,
                               BOOL.optionTypeId),
      ServiceTaskIntOption(toI("test_int"), 2, INT.optionTypeId),
      ServiceTaskDoubleOption(toI("test_float"), 1.234, FLOAT.optionTypeId),
      ServiceTaskStrOption(toI("test_str"), "Hello, world", STR.optionTypeId),
      ServiceTaskIntOption(toI("test_choice_int"), 3, CHOICE_INT.optionTypeId),
      ServiceTaskDoubleOption(toI("test_choice_float"),
                              1.0,
                              CHOICE_FLOAT.optionTypeId),
      ServiceTaskStrOption(toI("test_choice_str"), "B", CHOICE.optionTypeId)
    )

    val workflowOptions = Seq.empty[ServiceTaskOptionBase]

    PbSmrtPipeServiceOptions(name,
                             pipelineId,
                             Seq(ep),
                             taskOptions,
                             workflowOptions,
                             projectId)
  }

  protected val diagnosticOptsCore = toDiagnosticOptions(subreadsUuid.get)
  protected val diagnosticOpts: Var[PbSmrtPipeServiceOptions] =
    projectId.mapWith { pid =>
      diagnosticOptsCore.copy(projectId = pid)
    }
  protected val failOpts = diagnosticOpts.mapWith(
    _.copy(
      taskOptions = Seq(
        ServiceTaskBooleanOption(toI("raise_exception"),
                                 true,
                                 BOOL.optionTypeId))))
  protected val satOpts: Var[PbSmrtPipeServiceOptions] = Var(
    PbSmrtPipeServiceOptions(
      "site-acceptance-test",
      "pbsmrtpipe.pipelines.sa3_sat",
      Seq(
        BoundServiceEntryPoint("eid_ref_dataset",
                               "PacBio.DataSet.ReferenceSet",
                               refUuid.get),
        BoundServiceEntryPoint("eid_subread",
                               "PacBio.DataSet.SubreadSet",
                               subreadsUuid.get)
      ),
      Seq[ServiceTaskOptionBase](),
      Seq(
        ServiceTaskBooleanOption("pbsmrtpipe.options.chunk_mode",
                                 true,
                                 BOOL.optionTypeId),
        ServiceTaskIntOption("pbsmrtpipe.options.max_nchunks",
                             2,
                             INT.optionTypeId)
      )
    ))
  protected val chunkOpts = Var(
    PbSmrtPipeServiceOptions(
      "subreads-chunk-test",
      "pbsmrtpipe.pipelines.dev_subreads_chunk",
      Seq(
        BoundServiceEntryPoint("eid_subread",
                               "PacBio.DataSet.SubreadSet",
                               subreadsUuid.get)),
      Seq.empty[ServiceTaskOptionBase],
      Seq.empty[ServiceTaskOptionBase]
    ))

  protected val jobId: Var[UUID] = Var()
  protected val jobId2: Var[UUID] = Var()
  protected val jobStatus: Var[Int] = Var()
  protected val job: Var[EngineJob] = Var()
  protected val jobs: Var[Seq[EngineJob]] = Var()
  protected val importJob: Var[EngineJob] = Var()
  protected val jobReports: Var[Seq[DataStoreReportFile]] = Var()
  protected val report: Var[Report] = Var()
  protected val dataStore: Var[Seq[DataStoreServiceFile]] = Var()
  protected val entryPoints: Var[Seq[EngineJobEntryPoint]] = Var()
  protected val childJobs: Var[Seq[EngineJob]] = Var()
  protected val subreadSets: Var[Seq[SubreadServiceDataSet]] = Var()
  protected val dsRules: Var[PipelineDataStoreViewRules] = Var()
  protected val pipelineRules: Var[PipelineTemplateViewRule] = Var()
  protected val jobOptions: Var[PipelineTemplatePreset] = Var()
  protected val jobEvents: Var[Seq[JobEvent]] = Var()
  protected val jobTasks: Var[Seq[JobTask]] = Var()

  protected val setupSteps = Seq(
    jobStatus := GetStatus,
    fail("Can't get SMRT server status") IF jobStatus !=? EXIT_SUCCESS,
    jobId := ImportDataSet(subreads, ftSubreads),
    jobStatus := WaitForJob(jobId),
    fail("Import job failed") IF jobStatus !=? EXIT_SUCCESS,
    jobId := ImportDataSet(subreads, ftSubreads),
    jobStatus := WaitForJob(jobId),
    fail("Import job failed") IF jobStatus !=? EXIT_SUCCESS,
    childJobs := GetJobChildren(jobId),
    fail("There should not be any child jobs") IF childJobs
      .mapWith(_.size) !=? 0
  )
}

class PbsmrtpipeScenario(host: String, port: Int)
    extends PbsmrtpipeScenarioCore {

  import OptionTypes._
  import JobModels._
  import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols._

  override val name = "PbsmrtpipeScenario"
  override val smrtLinkClient =
    new SmrtLinkServiceAccessLayer(host, port, Some("jsnow"))

  private def getLastJob(jobs: Seq[EngineJob]) =
    jobs.sortWith(_.id > _.id).head

  private def getExportedZip(dataStoreFiles: Var[Seq[DataStoreServiceFile]]) =
    dataStoreFiles.mapWith { ds =>
      Paths.get(ds.filter(_.fileTypeId == FileTypes.ZIP.fileTypeId).head.path)
    }

  private def getJobDataStoreFromFile(job: EngineJob) = {
    val dsPath = Paths.get(job.path).resolve("workflow/datastore.json")
    FileUtils
      .readFileToString(dsPath.toFile)
      .parseJson
      .convertTo[PacBioDataStore]
  }

  private def findChunkedDataStoreFiles(
      job: EngineJob,
      filesServices: Seq[DataStoreServiceFile]): Int = {
    val filesJson = getJobDataStoreFromFile(job).files
    val uuidsServices = filesServices.map(_.uuid).toSet
    filesJson
      .filter(_.isChunked)
      .count(uuidsServices contains _.uniqueId)
  }

  val diagnosticJobTests = Seq(
    projectId := CreateProject(projectName, projectDesc),
    jobId := RunAnalysisPipeline(diagnosticOpts),
    WaitForSuccessfulJob(jobId),
    //fail("Pipeline job failed") IF jobStatus !=? EXIT_SUCCESS,
    dataStore := GetAnalysisJobDataStore(jobId),
    fail(s"job:${jobId} Expected five datastore files") IF dataStore.mapWith(
      _.size) !=? 6,
    fail(s"job:${jobId} Analysis log file size is 0") IF dataStore.mapWith {
      ds =>
        ds.filter(_.sourceId == "pbsmrtpipe::pbsmrtpipe.log").head.fileSize
    } ==? 0,
    fail("Master log file size is 633 bytes") IF dataStore.mapWith { ds =>
      ds.filter(_.sourceId == "pbsmrtpipe::master.log").head.fileSize
    } ==? 633, // for some reason this is the size it starts at
    jobReports := GetAnalysisJobReports(jobId),
    fail("Expected one report") IF jobReports.mapWith(_.size) !=? 1,
    report := GetReport(jobReports.mapWith(_(0).dataStoreFile.uuid)),
    fail("Wrong report UUID in datastore") IF jobReports.mapWith(
      _(0).dataStoreFile.uuid) !=? report.mapWith(_.uuid),
    job := GetJob(jobId),
    fail("Expected non-blank smrtlinkVersion") IF job.mapWith(
      _.smrtlinkVersion) ==? None,
    fail("Wrong project id in job") IF job.mapWith(_.projectId) !=? projectId,
    jobs := GetAnalysisJobsForProject(projectId),
    fail("Expected one job for project") IF jobs.mapWith(_.size) !=? 1,
    fail("Wrong job found for project ") IF jobs.mapWith(_.head) !=? job,
    entryPoints := GetAnalysisJobEntryPoints(job.mapWith(_.id)),
    fail("Expected one entry point") IF entryPoints.mapWith(_.size) !=? 1,
    fail("Wrong entry point UUID") IF entryPoints
      .mapWith(_(0).datasetUUID) !=? subreadsUuid,
    job := GetJob(jobId),
    jobTasks := GetAnalysisJobTasks(job.mapWith(_.id)),
    fail("Expected three job tasks") IF jobTasks.mapWith(_.size) !=? 3,
    fail("Expected all tasks to succeed") IF jobTasks.mapWith(
      _.count(_.state == "successful")) !=? 3,
    jobEvents := GetAnalysisJobEvents(job.mapWith(_.id)),
    fail("Expected at least one job event") IF jobEvents.mapWith(_.size) ==? 0,
    // there are two tasks, each one has CREATED and SUCCESSFUL events
    fail("Expected four task_status events") IF jobEvents.mapWith(
      _.count(_.eventTypeId == JobConstants.EVENT_TYPE_JOB_TASK_STATUS)) !=? 6,
    fail("Expected three SUCCESSFUL events") IF jobEvents.mapWith(
      _.count(_.state == AnalysisJobStates.SUCCESSFUL)) !=? 4,
    // Export job(s)
    jobId2 := ExportJobs(jobs.mapWith(_.map(_.id)), Var(tmpDir)),
    WaitForSuccessfulJob(jobId2),
    dataStore := GetAnalysisJobDataStore(jobId2),
    fail("Expected two files in datastore") IF dataStore.mapWith(_.size) !=? 2,
    fail("Expected one ZIP file in datastore") IF dataStore.mapWith { ds =>
      Paths
        .get(ds.filter(_.fileTypeId == FileTypes.ZIP.fileTypeId).head.path)
        .toFile
        .isFile
    } !=? true,
    // Import the job we just exported
    ImportJob(Var(Paths.get("/path/does/not/exist.zip"))) SHOULD_RAISE classOf[
      Exception],
    ImportJob(getExportedZip(dataStore), Var(false)) SHOULD_RAISE classOf[
      Exception], // duplicate UUID
    jobId2 := ImportJob(getExportedZip(dataStore)),
    WaitForSuccessfulJob(jobId2),
    dataStore := GetAnalysisJobDataStore(jobId2),
    jobs := GetAnalysisJobs,
    fail("Expected latest analysis job to have non-null importedAt") IF
      jobs.mapWith { j =>
        getLastJob(j).importedAt.isDefined
      } !=? true,
    dataStore := GetAnalysisJobDataStore(
      jobs.mapWith(j => getLastJob(j).uuid)),
    fail(s"job:${jobId} Expected four datastore files") IF dataStore.mapWith(
      _.size) !=? 5,
    jobReports := GetAnalysisJobReports(jobId),
    fail("Expected one report") IF jobReports.mapWith(_.size) !=? 1,
    entryPoints := GetAnalysisJobEntryPoints(
      jobs.mapWith(j => getLastJob(j).id)),
    fail("Expected one entry point for imported job") IF entryPoints.mapWith(
      _.size) !=? 1,
    // Failure mode
    jobId2 := RunAnalysisPipeline(failOpts),
    jobStatus := WaitForJob(jobId2),
    fail("Expected job to fail when raise_exception=true") IF jobStatus !=? EXIT_FAILURE,
    job := GetJob(jobId2),
    jobTasks := GetAnalysisJobTasks(job.mapWith(_.id)),
    fail("Expected three job tasks") IF jobTasks.mapWith(_.size) !=? 3,
    jobEvents := GetAnalysisJobEvents(job.mapWith(_.id)),
    fail("Expected at least one job event") IF jobEvents.mapWith(_.size) ==? 0,
    // FIXME the task status events never leave CREATED state...
    fail("Expected at least two task_status events") IF jobEvents
      .mapWith(_.count(e =>
        e.eventTypeId == JobConstants.EVENT_TYPE_JOB_TASK_STATUS)) ==? 0,
    //fail("Expected FAILED task_status event") IF jobEvents.mapWith(_.filter(e => (e.eventTypeId == JobConstants.EVENT_TYPE_JOB_TASK_STATUS) && (e.state == AnalysisJobStates.FAILED)).size) !=? 1,
    fail("Expected FAILED job_status event") IF jobEvents.mapWith(_.count(e =>
      (e.eventTypeId == JobConstants.EVENT_TYPE_JOB_STATUS) && (e.state == AnalysisJobStates.FAILED))) !=? 1,
    // FIXME this is broken because of wrong Content-Type
    //jobOptions := GetAnalysisJobOptions(job.mapWith(_.id)),
    //fail("Expected a single task option") IF jobOptions.mapWith(_.taskOptions.size) !=? 1,
    // try and fail to delete ReferenceSet import
    subreadSets := GetSubreadSets,
    importJob := GetJobById(subreadSets.mapWith { rs =>
      rs.filter(_.uuid == subreadsUuid.get).head.jobId
    }),
    DeleteJob(importJob.mapWith(_.uuid), Var(true)) SHOULD_RAISE classOf[
      Exception],
    DeleteJob(importJob.mapWith(_.uuid), Var(false)) SHOULD_RAISE classOf[
      Exception],
    // delete pbsmrtpipe jobs
    jobId2 := DeleteJob(jobId2, Var(false)),
    jobStatus := WaitForJob(jobId2),
    fail("Delete job failed") IF jobStatus !=? EXIT_SUCCESS,
    job := GetJob(jobId),
    jobId2 := DeleteJob(jobId, Var(true)),
    jobStatus := WaitForJob(jobId2),
    fail(s"Delete job ${jobId2} failed with dryRun=true") IF jobStatus !=? EXIT_SUCCESS,
    jobId := DeleteJob(job.mapWith(_.uuid), Var(false)),
    jobStatus := WaitForJob(jobId),
    fail(s"Delete job ${jobId} failed") IF jobStatus !=? EXIT_SUCCESS,
    jobReports := GetAnalysisJobReports(job.mapWith(_.uuid)),
    fail("Expected report file to be deleted") IF jobReports.mapWith(
      _(0).dataStoreFile.fileExists) !=? false,
    dataStore := GetAnalysisJobDataStore(job.mapWith(_.uuid)),
    fail(s"Datastore files for ${job.mapWith(_.id)} Expected isActive=false") IF dataStore
      .mapWith(_.count(f => f.isActive)) !=? 0,
    // now delete the job we imported
    jobId := DeleteJob(jobs.mapWith { j =>
      getLastJob(j.filter(_.importedAt.isDefined)).uuid
    }, Var(false)),
    jobStatus := WaitForJob(jobId),
    fail("Delete job failed") IF jobStatus !=? EXIT_SUCCESS,
    // now delete the ReferenceSet import job, which should have no children
    // left
    jobId := DeleteJob(importJob.mapWith(_.uuid), Var(false)),
    jobStatus := WaitForJob(jobId),
    fail("Delete job failed") IF jobStatus !=? EXIT_SUCCESS,
    fail("Subreads dataset file should not have been deleted") IF subreadSets
      .mapWith(rs =>
        fileExists(rs.filter(_.uuid == subreadsUuid.get).head.path)) !=? true,
    subreadSets := GetSubreadSets,
    fail("Subreads dataset should not appear in list") IF subreadSets
      .mapWith(_.count(_.uuid == subreadsUuid.get)) !=? 0
  )
  // these are probably overkill...
  val miscTests = Seq(
    dsRules := GetDataStoreViewRules(Var("pbsmrtpipe.pipelines.dev_01")),
    fail("Wrong pipelineId") IF dsRules
      .mapWith(_.pipelineId) !=? "pbsmrtpipe.pipelines.dev_01",
    pipelineRules := GetPipelineTemplateViewRule(
      Var("pbsmrtpipe.pipelines.sa3_sat")),
    fail("Wrong id") IF pipelineRules
      .mapWith(_.id) !=? "pbsmrtpipe.pipelines.sa3_sat"
  )
  val chunkTests = Seq(
    jobId := RunAnalysisPipeline(chunkOpts),
    WaitForSuccessfulJob(jobId),
    job := GetJob(jobId),
    dataStore := GetAnalysisJobDataStore(job.mapWith(_.uuid)),
    fail("Found at least one chunked file in services datastore") IF
      dataStore.mapWith(ds => findChunkedDataStoreFiles(job.get, ds)) !=? 0
  )
  // TODO SAT job?  this is problematic because of the added depenendencies;
  // we need to check for pbalign and GenomicConsensus first
  override val steps = setupSteps ++ diagnosticJobTests ++ miscTests ++ chunkTests
}
