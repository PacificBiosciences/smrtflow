
// TODO this should eventually replace stress.py.  need to figure out a way
// to rewrite dataset UUIDs on the fly first.

package com.pacbio.simulator.scenarios

import java.net.URL
import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import java.io.{File,PrintWriter}

import akka.actor.ActorSystem
import scala.collection._
import com.typesafe.config.{Config, ConfigException}
import spray.httpx.UnsuccessfulResponseException

import com.pacbio.secondary.smrtserver.client.AnalysisServiceAccessLayer
import com.pacbio.secondary.analysis.externaltools.{PacBioTestData,PbReports}
import com.pacbio.secondary.smrtlink.client.ClientUtils
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.reports.ReportModels.Report
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.common.models._
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.pacbio.simulator.steps._

object PbsmrtpipeScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(implicit system: ActorSystem): Scenario = {
    require(config.isDefined, "Path to config file must be specified for PbsmrtpipeScenario")
    require(PacBioTestData.isAvailable, "PacBioTestData must be configured for PbsmrtpipeScenario")
    val c: Config = config.get

    // Resolve overrides with String
    def getInt(key: String): Int =
      try {
        c.getInt(key)
      } catch {
        case e: ConfigException.WrongType => c.getString(key).trim.toInt
      }

    new PbsmrtpipeScenario(
      c.getString("smrt-link-host"),
      getInt("smrt-link-port"))
  }
}

class PbsmrtpipeScenario(host: String, port: Int)
    extends Scenario
    with VarSteps
    with ConditionalSteps
    with IOSteps
    with SmrtLinkSteps
    with SmrtAnalysisSteps
    with ClientUtils {

  override val name = "PbsmrtpipeScenario"

  override val smrtLinkClient = new AnalysisServiceAccessLayer(new URL("http", host, port, ""))

  def fileExists(path: String) = Files.exists(Paths.get(path))

  val EXIT_SUCCESS: Var[Int] = Var(0)
  val EXIT_FAILURE: Var[Int] = Var(1)

  val testdata = PacBioTestData()

  val reference = Var(testdata.getFile("lambdaNEB"))
  val refUuid = Var(dsUuidFromPath(reference.get))
  val subreads = Var(testdata.getFile("subreads-xml"))
  val subreadsUuid = Var(dsUuidFromPath(subreads.get))

  val diagnosticOpts: Var[PbSmrtPipeServiceOptions] = Var(
    PbSmrtPipeServiceOptions(
      "diagnostic-test",
      "pbsmrtpipe.pipelines.dev_diagnostic",
      Seq(BoundServiceEntryPoint("eid_ref_dataset",
                                 "PacBio.DataSet.ReferenceSet",
                                 Right(refUuid.get))),
      Seq[ServiceTaskOptionBase](),
      Seq[ServiceTaskOptionBase]()))
  val satOpts: Var[PbSmrtPipeServiceOptions] = Var(
    PbSmrtPipeServiceOptions(
      "site-acceptance-test",
      "pbsmrtpipe.pipelines.sa3_sat",
      Seq(BoundServiceEntryPoint("eid_ref_dataset",
                                 "PacBio.DataSet.ReferenceSet",
                                 Right(refUuid.get)),
          BoundServiceEntryPoint("eid_subread",
                                 "PacBio.DataSet.SubreadSet",
                                 Right(subreadsUuid.get))),
      Seq(
        ServiceTaskBooleanOption("pbsmrtpipe.options.chunk_mode", true,
                                 "pbsmrtpipe.option_types.boolean"),
        ServiceTaskIntOption("pbsmrtpipe.options.max_nchunks", 2,
                             "pbsmrtpipe.option_types.integer")),
      Seq[ServiceTaskOptionBase]()))

  val jobId: Var[UUID] = Var()
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
    referenceSets := GetReferenceSets,
    fail("Expected one reference set") IF referenceSets.mapWith(_.size) !=? 1
  )
  val diagnosticJobTests = Seq(
    jobId := RunAnalysisPipeline(diagnosticOpts),
    jobStatus := WaitForJob(jobId),
    fail("Import job failed") IF jobStatus !=? EXIT_SUCCESS,
    dataStore := GetAnalysisJobDataStore(jobId),
    fail("Expected three datastore files") IF dataStore.mapWith(_.size) !=? 3,
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
    // try and fail to delete ReferenceSet import
    referenceSets := GetReferenceSets,
    importJob := GetJobById(referenceSets.mapWith(_.head.jobId)),
    DeleteJob(importJob.mapWith(_.uuid)) SHOULD_RAISE classOf[UnsuccessfulResponseException],
    // delete pbsmrtpipe job
    jobId := DeleteJob(jobId),
    jobStatus := WaitForJob(jobId),
    fail("Delete job failed") IF jobStatus !=? EXIT_SUCCESS,
    fail("Expected report file to be deleted") IF jobReports.mapWith(_(0).dataStoreFile.fileExists) !=? false,
    dataStore := GetAnalysisJobDataStore(job.mapWith(_.uuid)),
    fail("Expected wasDeleted=true") IF dataStore.mapWith(_.filter(f => !f.wasDeleted).size) !=? 0,
    // now delete the ReferenceSet import job
    jobId := DeleteJob(importJob.mapWith(_.uuid)),
    jobStatus := WaitForJob(jobId),
    fail("Delete job failed") IF jobStatus !=? EXIT_SUCCESS,
    fail("Reference dataset file should not have been deleted") IF referenceSets.mapWith(rs => fileExists(rs.head.path)) !=? true,
    referenceSets := GetReferenceSets,
    fail("There should be zero ReferenceSets") IF referenceSets.mapWith(_.size) !=? 0
  )
  // these are probably overkill...
  val miscTests = Seq(
    dsRules := GetDataStoreViewRules(Var("pbsmrtpipe.pipelines.dev_01")),
    fail("Wrong pipelineId") IF dsRules.mapWith(_.pipelineId) !=? "pbsmrtpipe.pipelines.dev_01",
    pipelineRules := GetPipelineTemplateViewRule(Var("pbsmrtpipe.pipelines.sa3_sat")),
    fail("Wrong id") IF pipelineRules.mapWith(_.id) !=? "pbsmrtpipe.pipelines.sa3_sat"
  )
  // TODO SAT job?  this is problematic because of the added depenendencies;
  // we need to check for pbalign and GenomicConsensus first
  override val steps = setupSteps ++ diagnosticJobTests ++ miscTests
}
