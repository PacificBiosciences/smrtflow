package com.pacbio.simulator.scenarios

import java.net.URL
import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.actor.ActorSystem
import com.pacbio.secondary.smrtlink.client.SmrtLinkServiceAccessLayer
import com.pacbio.secondary.smrtlink.models.{Run, RunSummary}
import com.pacbio.simulator.clients.InstrumentControlClient
import com.pacbio.simulator.steps._
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.typesafe.config.{Config, ConfigException}
import com.pacbio.simulator.steps.IcsClientSteps
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
    with IcsClientSteps {

  import scala.concurrent.duration._
  import com.pacbio.simulator.clients.ICSState
  import ICSState._

  override val name = "RunDesignScenario"

  override val smrtLinkClient = new SmrtLinkServiceAccessLayer(new URL("http", host, port, ""))
  override val icsClient = new InstrumentControlClient(new URL("http",icsHost, icsPort,""))

  val runXmlPath: Var[String] = Var(runXmlFile.toString)
  val runXml: Var[String] = Var()
  val runId: Var[UUID] = Var()
  val runDesign: Var[Run] = Var()
  val runDesigns: Var[Seq[RunSummary]] = Var()

  override val steps = Seq(
    runDesigns := GetRuns,

    //fail("Run database should be initially empty") IF runDesigns ? (_.nonEmpty),

    runXml := ReadFileFromTemplate(runXmlPath),

    runId := CreateRun(runXml),

    runDesign := GetRun(runId),

    fail("Wrong uniqueId found") IF runDesign.mapWith(_.uniqueId) !=? runId,

    fail("Expected reserved to be false") IF runDesign.mapWith(_.reserved) !=? false,

    PostRunDesignToICS(runDesign),

    PostRunRqmtsToICS,

    GetRunRqmts(),

    // todo : Inject Manny's script to load runs

    GetRunStatus(runDesign, Seq(Idle,Ready)),

    // WAIT FOR FEW SECS, FOR ICS TO LOAD THE RUN
    SleepStep(5.minutes),

    PostRunStartToICS,

    GetRunStatus(runDesign, Seq(Running,Starting)),

    SleepStep(5.minutes),

    GetRunStatus(runDesign, Seq(Complete))
  )
}
