package com.pacbio.simulator.scenarios

import java.nio.file.Path

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigException}
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.externaltools.{
  PacBioTestData,
  PacBioTestResources
}
import com.pacbio.secondary.smrtlink.client._
import com.pacbio.simulator.steps._
import com.pacbio.simulator.{Scenario, ScenarioLoader}

trait SmrtLinkScenario extends Scenario with VarSteps {
  private val TIMEOUT = 30 seconds
  protected val DEFAULT_USER_NAME = "smrtlinktest"

  protected val EXIT_SUCCESS: Var[Int] = Var(0)
  protected val EXIT_FAILURE: Var[Int] = Var(1)
  protected val FILETYPE_SUBREADS: Var[DataSetMetaTypes.DataSetMetaType] = Var(
    DataSetMetaTypes.Subread)
  protected val FILETYPE_REFERENCE: Var[DataSetMetaTypes.DataSetMetaType] =
    Var(DataSetMetaTypes.Reference)

  val testResources: PacBioTestResources

  protected def getSubreads: Path =
    testResources
      .findById("subreads-xml")
      .get
      .getTempDataSetFile(copyFiles = true, tmpDirBase = "dataset contents")
      .path

  protected def getClient(host: String,
                          port: Int,
                          user: Option[String] = None,
                          password: Option[String] = None)(
      implicit actorSystem: ActorSystem): SmrtLinkServiceClient =
    (user, password) match {
      case (Some(u), Some(p)) => {
        Await.result( // FIXME this is awful
          AuthenticatedServiceAccessLayer.getClient(host, port, u, p)(
            actorSystem),
          TIMEOUT)
      }
      case _ =>
        new SmrtLinkServiceClient(host, port, Some(DEFAULT_USER_NAME))(
          actorSystem)
    }
}

trait SmrtLinkScenarioLoader extends ScenarioLoader {
  protected val REQUIRE_AUTH = false

  protected def toScenario(host: String,
                           port: Int,
                           user: Option[String],
                           password: Option[String],
                           testResources: PacBioTestResources): Scenario

  private def requireAuth(c: Config) = if (REQUIRE_AUTH) {
    require(getUser(c).isDefined && getPassword(c).isDefined,
            "Authentication required to run this scenario")
  }

  override def load(config: Option[Config])(
      implicit system: ActorSystem): Scenario = {
    val c = verifyRequiredConfig(config)
    requireAuth(c)

    val testResources = verifyConfiguredWithTestResources(c)
    toScenario(getHost(c),
               getPort(c),
               getUser(c),
               getPassword(c),
               testResources)
  }
}
