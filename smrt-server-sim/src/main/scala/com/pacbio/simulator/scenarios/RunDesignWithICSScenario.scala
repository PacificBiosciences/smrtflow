package com.pacbio.simulator.scenarios

import java.net.URL
import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.actor.ActorSystem
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.externaltools.PacBioTestData
import com.pacbio.secondary.analysis.jobs.JobModels.{ServiceTaskOptionBase, _}
import com.pacbio.secondary.analysis.jobs.OptionTypes.{CHOICE, CHOICE_FLOAT, _}
import com.pacbio.secondary.smrtlink.client.SmrtLinkServiceAccessLayer
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.simulator.clients.InstrumentControlClient
import com.pacbio.simulator.steps._
import com.pacbio.simulator.{RunDesignTemplateInfo, Scenario, ScenarioLoader}
import com.typesafe.config.{Config, ConfigException}
import com.pacbio.simulator.steps.IcsClientSteps
import com.pacbio.secondary.analysis.reports.ReportModels.Report
import com.pacbio.secondary.analysis.jobs.{AnalysisJobStates, JobModels, OptionTypes}

import scala.collection.Seq
import spray.httpx.UnsuccessfulResponseException
import com.pacbio.simulator.util._

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
    require(config.isDefined, "Path to config file must be specified for RunDesignWithICSScenario")
    val c: Config = config.get

    // Resolve overrides with String
    def getInt(key: String): Int =
      try {
        c.getInt(key)
      } catch {
        case e: ConfigException.WrongType => c.getString(key).trim.toInt
      }

    new RunDesignWithICSScenario(
      c.getString("sim.smrt-link-host"),
      getInt("sim.smrt-link-port"),
      c.getString("sim.ics-host"),
      getInt("sim.ics-port"),
      Paths.get(c.getString("sim.run-xml-path")))
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
    with IcsClientSteps
    with ClientUtils {

  import scala.concurrent.duration._
  import com.pacbio.simulator.clients.ICSState
  import ICSState._

  override val name = "RunDesignWithICSScenario"

  override val smrtLinkClient = new SmrtLinkServiceAccessLayer(host, port)
  override val icsClient = new InstrumentControlClient(new URL("http",icsHost, icsPort,""))

  val runXmlPath: Var[String] = Var(runXmlFile.toString)
  val runXml: Var[String] = Var()
  val runInfo : Var[RunDesignTemplateInfo] = Var()
  val runId: Var[UUID] = Var()
  val runDesign: Var[Run] = Var()
  val runDesigns: Var[Seq[RunSummary]] = Var()

  val EXIT_SUCCESS: Var[Int] = Var(0)
  val EXIT_FAILURE: Var[Int] = Var(1)

  // for SAT - hacking from pbSmrtPipeScenario
  val testdata = PacBioTestData()
  val reference = Var(testdata.getFile("lambdaNEB"))
  def refUuid = Var(dsUuidFromPath(reference.get))
  val subreads = Var(testdata.getFile("subreads-xml"))
  def subreadsUuid = Var(dsUuidFromPath(subreads.get))

  val jobId: Var[UUID] = Var()
  val jobStatus: Var[Int] = Var()
  val childJobs: Var[Seq[EngineJob]] = Var()
  val referenceSets: Var[Seq[ReferenceServiceDataSet]] = Var()

  println(s"subreads : ${subreads.get}")
 /* def satOpts: Var[PbSmrtPipeServiceOptions] = Var(
    PbSmrtPipeServiceOptions(
      "site-acceptance-test",
      "pbsmrtpipe.pipelines.sa3_sat",
      Seq(BoundServiceEntryPoint("eid_ref_dataset", "PacBio.DataSet.ReferenceSet", Right(refUuid.get)),
          BoundServiceEntryPoint("eid_subread", "PacBio.DataSet.SubreadSet", Right(subreadsUuid.get))),
      Seq[ServiceTaskOptionBase](),
      Seq(ServiceTaskBooleanOption("pbsmrtpipe.options.chunk_mode", true, BOOL.optionTypeId),
          ServiceTaskIntOption("pbsmrtpipe.options.max_nchunks", 2, INT.optionTypeId))))*/


  val icsEndToEndsteps = Seq(

    runDesigns := GetRuns,

    runInfo := ReadFile(runXmlPath),

    runXml := ReadXml(runInfo),

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


  val setupSteps = Seq(

    jobStatus := GetStatus,

    jobId := ImportDataSet(reference, Var(FileTypes.DS_REFERENCE.fileTypeId)),

    jobStatus := WaitForJob(jobId),

    fail("Import job failed") IF jobStatus !=? EXIT_SUCCESS,

    UpdateSubreadsetXml(subreads, runInfo),

    CheckIfUUIDUpdated(subreads, runInfo),

    jobId := ImportDataSet(subreads, Var(FileTypes.DS_SUBREADS.fileTypeId)),

    jobStatus := WaitForJob(jobId),

    fail("Import job failed") IF jobStatus !=? EXIT_SUCCESS
  )

  val satSteps = Seq(

    jobId := RunSAT(refUuid, subreadsUuid),//RunAnalysisPipeline(satOpts),

    jobStatus := WaitForJob(jobId),

    fail("Pipeline job failed") IF jobStatus !=? EXIT_SUCCESS
  )

  override val steps =  icsEndToEndsteps ++ setupSteps ++ satSteps
}
