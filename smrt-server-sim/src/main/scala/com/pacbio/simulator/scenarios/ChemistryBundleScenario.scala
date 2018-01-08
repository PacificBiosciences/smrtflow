package com.pacbio.simulator.scenarios

import java.net.URL
import java.nio.file.{Path, Paths}
import java.util.UUID
import java.io.{File, PrintWriter}

import akka.actor.ActorSystem

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
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.pacbio.simulator.steps._

object ChemistryBundleScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(
      implicit system: ActorSystem): Scenario = {
    require(
      config.isDefined,
      "Path to config file must be specified for ChemistryBundleScenario")
    require(PacBioTestData.isAvailable,
            "PacBioTestData must be configured for ChemistryBundleScenario")
    val c: Config = config.get

    new ChemistryBundleScenario(getHost(c), getPort(c))
  }
}

class ChemistryBundleScenario(host: String, port: Int)
    extends Scenario
    with VarSteps
    with ConditionalSteps
    with IOSteps
    with SmrtLinkSteps
    with ClientUtils
    with PacBioDataBundleIOUtils {

  import com.pacbio.common.models.CommonModelImplicits._

  override val name = "ChemistryBundleScenario"
  override val requirements = Seq("SEQ-306", "SL-458", "SL-998")

  override val smrtLinkClient = new SmrtLinkServiceClient(host, port)

  val EXIT_SUCCESS: Var[Int] = Var(0)
  val EXIT_FAILURE: Var[Int] = Var(1)

  val testdata = PacBioTestData()

  val subreads = Var(testdata.getTempDataSet("subreads-sequel"))
  val subreadsUuid = Var(getDataSetMiniMeta(subreads.get).uuid)
  val ftSubreads: Var[DataSetMetaTypes.DataSetMetaType] = Var(
    DataSetMetaTypes.Subread)
  val bundle: Var[PacBioDataBundle] = Var()
  val jobId: Var[UUID] = Var()
  val jobStatus: Var[Int] = Var()

  def getPipelineOpts(version: String): PbSmrtPipeServiceOptions = {
    println(s"Chemistry bundle version is ${version}")
    PbSmrtPipeServiceOptions(
      "chemistry-bundle-test",
      "pbsmrtpipe.pipelines.dev_verify_chemistry",
      Seq(
        BoundServiceEntryPoint("eid_subread",
                               FileTypes.DS_SUBREADS.fileTypeId,
                               subreadsUuid.get)),
      Seq(
        ServiceTaskStrOption("pbsmrtpipe.task_options.chemistry_version",
                             version)),
      Seq[ServiceTaskOptionBase]()
    )
  }

  val setupSteps = Seq(
    jobStatus := GetStatus,
    fail("Can't get SMRT server status") IF jobStatus !=? EXIT_SUCCESS,
    jobId := ImportDataSet(subreads, ftSubreads),
    jobStatus := WaitForJob(jobId),
    fail("Import job failed") IF jobStatus !=? EXIT_SUCCESS
  )
  val pbsmrtpipeSteps = Seq(
    bundle := GetBundle(Var("chemistry-pb")),
    jobId := RunAnalysisPipeline(
      bundle.mapWith(b => getPipelineOpts(b.version))),
    jobStatus := WaitForJob(jobId),
    fail("pbsmrtpipe job failed") IF jobStatus !=? EXIT_SUCCESS
  )
  override val steps = setupSteps ++ pbsmrtpipeSteps
}
