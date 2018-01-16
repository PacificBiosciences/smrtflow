package com.pacbio.simulator.scenarios

import java.net.URL
import java.nio.file.{Path, Paths}
import java.util.UUID
import java.io.{File, PrintWriter}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri

import scala.collection._
import com.typesafe.config.Config
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.externaltools.{
  PacBioTestData,
  PbReports
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.reports.ReportModels.Report
import com.pacbio.secondary.smrtlink.io.PacBioDataBundleIOUtils
import com.pacbio.secondary.smrtlink.client.{
  ClientUtils,
  SmrtLinkServiceClient
}
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.simulator.{Scenario, ScenarioLoader, StepResult}
import com.pacbio.simulator.steps._

import scala.concurrent.{ExecutionContext, Future}

object RegistryScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(
      implicit system: ActorSystem): Scenario = {
    require(config.isDefined,
            "Path to config file must be specified for RegistryScenarioLoader")

    val c: Config = config.get
    val client = new SmrtLinkServiceClient(getHost(c), getPort(c))
    new RegistryScenario(client)
  }
}

class RegistryScenario(client: SmrtLinkServiceClient)
    extends Scenario
    with VarSteps
    with SmrtLinkSteps {

  override val name: String = this.getClass.getSimpleName
  override val smrtLinkClient = client

  case class RegistrySanityStep(host: String,
                                port: Int,
                                resourceId: String,
                                path: Uri.Path)
      extends VarStep[String] {

    override val name: String = this.getClass.getSimpleName

    override def runWith: Future[String] = {
      for {
        resource <- client.addRegistryService(host, port, resourceId)
        _ <- andLog(s"Created Resource $resource")
        response <- client.getRegistryProxy(resource.uuid, path)
      } yield s"Successfully accessed  ${response.status}"
    }
  }

  // This could be set to any external service that is reliable. If this isn't
  // reliable for testing, it could be changed to google.com or similar.
  private val proxyHost = "jsonplaceholder.typicode.com"
  private val proxyPort = 80
  private val resourceId = s"test-resource-${UUID.randomUUID()}"
  private val path = Uri.Path("/users")
  override val steps: Seq[Step] = Seq(
    RegistrySanityStep(proxyHost, proxyPort, resourceId, path))

}
