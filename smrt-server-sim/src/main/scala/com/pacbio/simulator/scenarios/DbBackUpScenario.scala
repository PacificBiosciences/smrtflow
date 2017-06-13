package com.pacbio.simulator.scenarios

import java.util.UUID

import akka.actor.ActorSystem
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.smrtlink.client.{ClientUtils, SmrtLinkServiceAccessLayer}
import com.pacbio.secondary.smrtlink.models.DataStoreServiceFile
import com.pacbio.simulator.steps.{ConditionalSteps, IOSteps, SmrtLinkSteps, VarSteps}
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.typesafe.config.Config


object DbBackUpScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(implicit system: ActorSystem):Scenario = {
    require(config.isDefined, "Path to config file must be specified for PbsmrtpipeScenario")
    // Unclear to how add a check that the server is configured with `smrtflow.pacBioSystem.pgDataDir`
    // To run locally via smrtflow (i.e., outside of the SL system build), export PACBIO_SYSTEM_PG_DATA_DIR
    // and launch SL Analysis Server with the pg* exes in PATH and the db needs to be configured with the
    // correct permissions
    val c: Config = config.get

    new DbBackUpScenario(getHost(c), getPort(c))
  }
}

class DbBackUpScenario(host: String, port: Int) extends Scenario
    with VarSteps
    with ConditionalSteps
    with IOSteps
    with SmrtLinkSteps
    with ClientUtils {

  import CommonModelImplicits._

  override val name = "DbBackUpScenario"
  override val smrtLinkClient = new SmrtLinkServiceAccessLayer(host, port)

  val user = "sim-user"
  val comment = "Sim Submitted Database BackUp Request"

  val jobId = Var.empty[UUID]
  val dataStore: Var[Seq[DataStoreServiceFile]] = Var()

  override val steps: Seq[Step] = Seq(
    jobId := CreateDbBackUpJob(user, comment),
    WaitForSuccessfulJob(jobId),
    dataStore := GetAnalysisJobDataStore(jobId),
    fail("Expected 2 datastore files. Log and JSON file") IF dataStore.mapWith(_.size) !=? 2
  )
}
