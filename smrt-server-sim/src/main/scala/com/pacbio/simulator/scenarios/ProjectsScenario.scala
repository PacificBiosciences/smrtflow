package com.pacbio.simulator.scenarios

import java.net.URL
import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.actor.ActorSystem
import com.typesafe.config.Config
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.externaltools.{
  PacBioTestData,
  PacBioTestResources,
  PbReports
}
import com.pacbio.secondary.smrtlink.analysis.reports.ReportModels.Report
import com.pacbio.secondary.smrtlink.client.{
  ClientUtils,
  SmrtLinkServiceClient
}
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.pacbio.simulator.steps._

object ProjectsScenarioLoader extends SmrtLinkScenarioLoader {
  def toScenario(host: String,
                 port: Int,
                 user: Option[String],
                 password: Option[String],
                 testResources: PacBioTestResources): Scenario =
    new ProjectsScenario(host, port, user, password, testResources)
}

class ProjectsScenario(host: String,
                       port: Int,
                       user: Option[String],
                       password: Option[String],
                       val testResources: PacBioTestResources)
    extends SmrtLinkScenario
    with VarSteps
    with ConditionalSteps
    with IOSteps
    with SmrtLinkSteps
    with ClientUtils {

  override val name = "ProjectsScenario"

  private val effectiveUserName = user.getOrElse(DEFAULT_USER_NAME)
  override val smrtLinkClient =
    getClient(host, port, user, password)(system)

  protected val projectName = Var(s"Project-${UUID.randomUUID()}")
  protected val projectDesc = Var("Project Description")
  protected val jobStatus: Var[Int] = Var()
  protected val projId: Var[Int] = Var()
  protected val projects: Var[Seq[Project]] = Var()
  protected val project: Var[FullProject] = Var()
  protected val subreads = Var(getSubreads)
  protected val subreadsUuid = Var(getDataSetMiniMeta(subreads.get).uuid)
  protected val subreadDs: Var[DataSetMetaDataSet] = Var()
  protected val jobId: Var[UUID] = Var()
  protected val dsId: Var[Int] = Var()

  private def datasetInProject(p: FullProject) =
    p.datasets.map(_.uuid).toSet contains subreadsUuid.get

  val setupSteps = Seq(
    jobStatus := GetStatus,
    fail("Can't get SMRT server status") IF jobStatus !=? EXIT_SUCCESS
  )
  val projectTests = Seq(
    projId := CreateProject(projectName, projectDesc, Var(effectiveUserName)),
    project := GetProject(projId),
    jobId := ImportDataSet(subreads, FILETYPE_SUBREADS),
    jobStatus := WaitForJob(jobId),
    fail("Import job failed") IF jobStatus !=? EXIT_SUCCESS,
    project := GetProject(Var(1)),
    fail("Expected dataset in General Project") IF project.mapWith(
      datasetInProject) !=? true,
    project := GetProject(projId),
    subreadDs := GetDataSet(subreadsUuid),
    project := UpdateProject(
      projId,
      project.mapWith(_.asRequest.appendDataSet(subreadDs.mapWith(_.id).get))),
    fail("Expected one dataset in project") IF project
      .mapWith(_.datasets.size) !=? 1,
    project := GetProject(Var(1)),
    fail("Expected no dataset in General Project") IF project.mapWith(
      datasetInProject) !=? false
  )
  override val steps = setupSteps ++ projectTests
}
