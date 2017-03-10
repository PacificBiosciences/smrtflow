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

import scala.collection.Seq


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

  // for SAT - hacking from pbSmrtPipeScenario

  val testdata = PacBioTestData()
  val reference = Var(testdata.getFile("lambdaNEB"))
  val refUuid = Var(dsUuidFromPath(reference.get))
  val subreads = Var(testdata.getFile("subreads-xml"))
  val subreadsUuid = Var(dsUuidFromPath(subreads.get))
  val jobStatus: Var[Int] = Var()
  val jobId: Var[UUID] = Var()
  val EXIT_SUCCESS: Var[Int] = Var(0)
  val EXIT_FAILURE: Var[Int] = Var(1)
  val childJobs: Var[Seq[EngineJob]] = Var()
  val referenceSets: Var[Seq[ReferenceServiceDataSet]] = Var()
  val dataStore: Var[Seq[DataStoreServiceFile]] = Var()

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

  val setupSteps = Seq(
    /*jobStatus := GetStatus,
    fail("Can't get SMRT server status") IF jobStatus !=? EXIT_SUCCESS,
    jobId := ImportDataSet(reference, Var(FileTypes.DS_REFERENCE.fileTypeId)),
    jobStatus := WaitForJob(jobId),
    fail("Import job failed") IF jobStatus !=? EXIT_SUCCESS,*/
    jobId := ImportDataSet(subreads, Var(FileTypes.DS_SUBREADS.fileTypeId)),
    jobStatus := WaitForJob(jobId),
    fail("Import job failed") IF jobStatus !=? EXIT_SUCCESS,
    childJobs := GetJobChildren(jobId),
    fail("There should not be any child jobs") IF childJobs.mapWith(_.size) !=? 0,
    referenceSets := GetReferenceSets,
    fail("Expected one reference set") IF referenceSets.mapWith(_.size) !=? 1
  )

  val satSteps = Seq(
      jobId := RunAnalysisPipeline(diagnosticOpts),
      jobStatus := WaitForJob(jobId),
      fail("Pipeline job failed") IF jobStatus !=? EXIT_SUCCESS,
      dataStore := GetAnalysisJobDataStore(jobId)
  )
/*
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
  )*/
//icsEndToEndsteps ++
  override val steps =  setupSteps ++ satSteps
}
