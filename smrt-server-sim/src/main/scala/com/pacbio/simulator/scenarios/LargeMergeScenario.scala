package com.pacbio.simulator.scenarios

import java.net.URL
import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import java.io.{File, FileNotFoundException, PrintWriter}

import scala.collection._
import akka.actor.ActorSystem
import com.pacbio.common.models.CommonModels.UUIDIdAble
import com.typesafe.config.Config
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.reports.ReportModels.Report
import com.pacbio.secondary.smrtlink.client.{
  ClientUtils,
  SmrtLinkServiceClient
}
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.pacbio.simulator.steps._

object LargeMergeScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(
      implicit system: ActorSystem): Scenario = {
    require(config.isDefined,
            "Path to config file must be specified for LargeMergeScenario")
    val c: Config = config.get

    val dsRootPath = Paths.get(c.getString("datasetsPath"))

    if (!Files.exists(dsRootPath)) {
      throw new FileNotFoundException(s"Unable to find $dsRootPath")
    }

    new LargeMergeScenario(getHost(c), getPort(c), dsRootPath)
  }
}

class LargeMergeScenario(host: String, port: Int, datasetsPath: Path)
    extends Scenario
    with VarSteps
    with ConditionalSteps
    with IOSteps
    with SmrtLinkSteps
    with ClientUtils {

  override val name = "LargeMergeScenario"

  override val smrtLinkClient = new SmrtLinkServiceClient(host, port)

  val SLEEP_TIME = 2000 // polling interval for checking import job status
  val EXIT_SUCCESS: Var[Int] = Var(0)
  val EXIT_FAILURE: Var[Int] = Var(1)

  val ftSubreads: Var[DataSetMetaTypes.DataSetMetaType] = Var(
    DataSetMetaTypes.Subread)
  val dsFiles = listFilesByExtension(datasetsPath.toFile, ".subreadset.xml")
  val nFiles = dsFiles.size
  println(s"$nFiles SubreadSets found")
  val jobIds: Seq[Var[UUID]] =
    (0 until nFiles).map(_ => Var(UUID.randomUUID()))
  val dsUUIDs: Seq[UUID] =
    (0 until nFiles).map(i => getDataSetMiniMeta(dsFiles(i).toPath).uuid)
  val jobId: Var[UUID] = Var()
  val jobStatus: Var[Int] = Var()

  val setupSteps = Seq(
    jobStatus := GetStatus,
    fail("Can't get SMRT server status") IF jobStatus !=? EXIT_SUCCESS
  )
  val importSteps = (0 until nFiles)
    .map(
      i =>
        Seq(
          jobIds(i) := ImportDataSet(Var(dsFiles(i).toPath), ftSubreads)
      ))
    .flatMap(s => s) ++ (0 until nFiles)
    .map(i =>
      Seq(jobStatus := WaitForJob(jobIds(i), sleepTime = Var(SLEEP_TIME)),
          fail(s"Import job $i failed") IF jobStatus !=? EXIT_SUCCESS))
    .flatMap(s => s)
  val mergeSteps = Seq(
    jobId := MergeDataSetsMany(ftSubreads,
                               Var(dsUUIDs.map(UUIDIdAble)),
                               Var("merge-subreads")),
    jobStatus := WaitForJob(jobId),
    fail("Merge job failed") IF jobStatus !=? EXIT_SUCCESS
  )

  override val steps = setupSteps ++ importSteps ++ mergeSteps
}
