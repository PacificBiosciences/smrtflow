package com.pacbio.simulator.scenarios

import akka.actor.ActorSystem
import com.pacbio.secondary.smrtlink.client.{ClientUtils, SmrtLinkServiceAccessLayer}
import com.pacbio.simulator.steps.{ConditionalSteps, IOSteps, SmrtLinkSteps, VarSteps}
import com.pacbio.simulator.{Scenario, ScenarioLoader, ScenarioResult}
import com.typesafe.config.Config

import scala.concurrent.Future


object TechSupportScenario extends ScenarioLoader {
  override def load(config: Option[Config])(implicit system: ActorSystem):Scenario = {

    val c: Config = config.get
    new TechSupportScenario(getHost(c), getPort(c))
  }
}

class TechSupportScenario(host: String, port: Int) extends Scenario
    with VarSteps
    with ConditionalSteps
    with IOSteps
    with SmrtLinkSteps
    with ClientUtils {

  override val name = "TechSupportScenario"
  override val smrtLinkClient = new SmrtLinkServiceAccessLayer(host, port)

  lazy val createFailedJob = 0

  override val steps = Seq(

  )

}
