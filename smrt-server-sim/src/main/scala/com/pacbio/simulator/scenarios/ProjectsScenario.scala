
package com.pacbio.simulator.scenarios

import java.net.URL
import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigException}

import com.pacbio.secondary.smrtserver.client.AnalysisServiceAccessLayer
import com.pacbio.secondary.analysis.externaltools.{PacBioTestData,PbReports}
import com.pacbio.secondary.smrtlink.client.ClientUtils
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.reports.ReportModels.Report
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.pacbio.simulator.steps._

/**
 * Example config:
 *
 * {{{
 *   smrt-link-host = "smrtlink-bihourly"
 *   smrt-link-port = 8081
 * }}}
 */

// FIXME too much code duplication
object ProjectsScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(implicit system: ActorSystem): Scenario = {
    require(config.isDefined, "Path to config file must be specified for ProjectsScenario")
    require(PacBioTestData.isAvailable, "PacBioTestData must be configured for ProjectsScenario")
    val c: Config = config.get

    // Resolve overrides with String
    def getInt(key: String): Int =
      try {
        c.getInt(key)
      } catch {
        case e: ConfigException.WrongType => c.getString(key).trim.toInt
      }

    new ProjectsScenario(
      c.getString("smrt-link-host"),
      getInt("smrt-link-port"))
  }
}

class ProjectsScenario(host: String, port: Int)
  extends Scenario with VarSteps with ConditionalSteps with IOSteps with SmrtLinkSteps with SmrtAnalysisSteps with ClientUtils {

  override val name = "ProjectsScenario"

  override val smrtLinkClient = new AnalysisServiceAccessLayer(new URL("http", host, port, ""))

  val MSG_PROJ_ERR = "Project database should be initially have just one project"
  val EXIT_SUCCESS: Var[Int] = Var(0)
  val EXIT_FAILURE: Var[Int] = Var(1)

  val projId: Var[Int] = Var()
  val projects: Var[Seq[Project]] = Var()
  val project: Var[FullProject] = Var()
  /*val testdata = PacBioTestData()
  val ftSubreads = Var(FileTypes.DS_SUBREADS.fileTypeId)
  val subreads1 = Var(testdata.getFile("subreads-xml"))
  val subreadsUuid1 = Var(dsUuidFromPath(subreads1.get))*/

  val projectTests = Seq(
    projects := GetProjects,
    fail(MSG_PROJ_ERR) IF projects.mapWith(_.size) !=? 1,
    projId := CreateProject(Var("test-project"), Var("A test project")),
    project := GetProject(projId)
//    fail("Project ID mismatch") IF project.mapWith(_.id) !=? projId.get
  // TODO add dataset to project
  /*  subreadSets := GetSubreadSets,
    fail(MSG_DS_ERR) IF subreadSets ? (_.nonEmpty),
    jobId := ImportDataSet(subreads1, ftSubreads),
    jobStatus := WaitForJob(jobId),
    fail("Import job failed") IF jobStatus !=? EXIT_SUCCESS,*/
  )
  override val steps = projectTests
}
