
package com.pacbio.simulator.scenarios

import java.net.URL
import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigException}

import com.pacbio.secondary.smrtserver.client.AnalysisServiceAccessLayer
import com.pacbio.secondary.analysis.externaltools.PacBioTestData
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.pacbio.simulator.steps._

/**
 * Example config:
 *
 * {{{
 *   smrt-link-host = "smrtlink-bihourly"
 *   smrt-link-port = 8081
 *   run-xml-path = "/path/to/testdata/runDataModel.xml"
 * }}}
 */
object DataSetImportScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(implicit system: ActorSystem): Scenario = {
    require(config.isDefined, "Path to config file must be specified for DataSetImportScenario")
    require(PacBioTestData.isAvailable, "PacBioTestData must be configured for DataSetImportScenario")
    val c: Config = config.get

    // Resolve overrides with String
    def getInt(key: String): Int =
      try {
        c.getInt(key)
      } catch {
        case e: ConfigException.WrongType => c.getString(key).trim.toInt
      }

    new DataSetImportScenario(
      c.getString("smrt-link-host"),
      getInt("smrt-link-port"))
  }
}

class DataSetImportScenario(host: String, port: Int)
  extends Scenario with VarSteps with ConditionalSteps with IOSteps with SmrtLinkSteps with SmrtAnalysisSteps {

  override val name = "DataSetImportScenario"

  override val smrtLinkClient = new AnalysisServiceAccessLayer(new URL("http", host, port, ""))

  val testdata = PacBioTestData()

  val subreadSets: Var[Seq[SubreadServiceDataSet]] = Var()
  val jobId: Var[UUID] = Var()
  val jobStatus: Var[Int] = Var()
  val exitSuccess: Var[Int] = Var(0)

  override val steps = Seq(
    subreadSets := GetSubreadSets,
    fail("DataSet database should be initially empty") IF subreadSets ? (_.nonEmpty),
    // FIXME this should pass the Path directly
    jobId := ImportDataSet(Var(testdata.getFile("subreads-xml")), Var(FileTypes.DS_SUBREADS.fileTypeId)),
    jobStatus := WaitForJob(jobId),
    fail("Import job failed") IF jobStatus !=? exitSuccess
  )
}
