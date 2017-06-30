package com.pacbio.simulator.scenarios

import java.util.UUID

import akka.actor.ActorSystem
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.common.models.CommonModels.IdAble
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.datasets.{DataSetFileUtils, DataSetMetaTypes}
import com.pacbio.secondary.analysis.externaltools.PacBioTestData
import com.pacbio.secondary.analysis.jobs.JobModels.{ServiceTaskBooleanOption, ServiceTaskOptionBase}
import com.pacbio.secondary.analysis.jobs.OptionTypes.BOOL
import com.pacbio.secondary.smrtlink.client.{ClientUtils, SmrtLinkServiceAccessLayer}
import com.pacbio.secondary.smrtlink.models.{BoundServiceEntryPoint, DataStoreServiceFile, PbSmrtPipeServiceOptions}
import com.pacbio.simulator.steps._
import com.pacbio.simulator.{Scenario, ScenarioLoader, ScenarioResult}
import com.typesafe.config.Config



object TechSupportScenarioLoader extends ScenarioLoader {
  override def load(config: Option[Config])(implicit system: ActorSystem):Scenario = {
    require(config.isDefined, "Path to config file must be specified for PbsmrtpipeScenario")
    require(PacBioTestData.isAvailable, "PacBioTestData must be configured for PbsmrtpipeScenario")

    val testData = PacBioTestData()
    val c: Config = config.get
    new TechSupportScenario(getHost(c), getPort(c), testData)
  }
}

class TechSupportScenario(host: String, port: Int, testData: PacBioTestData) extends Scenario
    with VarSteps
    with ConditionalSteps
    with IOSteps
    with SmrtLinkSteps
    with ClientUtils {

  import CommonModelImplicits._

  override val name = "TechSupportScenario"
  override val smrtLinkClient = new SmrtLinkServiceAccessLayer(host, port)

  // Force this job to fail
  val jobFailedAnalysisId = Var.empty[UUID]

  // This pattern needs to excised from the code
  val jobStatus = Var.empty[Int]

  val user = Var("sim-scenario-user")
  val jobFailedId = Var.empty[UUID]

  val jobStatusId = Var.empty[UUID]

  val lambdaNebPath = testData.getFile("lambdaNEB")
  val lambdaNeb = DataSetFileUtils.getDataSetMiniMeta(lambdaNebPath)
  val dsUUID = Var.empty[UUID]

  val dataStore: Var[Seq[DataStoreServiceFile]] = Var()

  def failedDevDiagnosticOpts(uuid: UUID): Var[PbSmrtPipeServiceOptions] = {

    //MK I don't understand why this has issue with dsUUID.get This will yield a NPE
    val ep = BoundServiceEntryPoint("eid_ref_dataset", FileTypes.DS_REFERENCE.fileTypeId, Right(lambdaNeb.uuid))
    val workflowOptions = Seq.empty[ServiceTaskOptionBase]

    val taskOption = ServiceTaskBooleanOption("pbsmrtpipe.task_options.raise_exception", true, BOOL.optionTypeId)

    Var(PbSmrtPipeServiceOptions("dev-triggered-failed", "pbsmrtpipe.pipelines.dev_diagnostic", Seq(ep), Seq(taskOption), workflowOptions))
  }

  // Sanity Test for creating an SL System status TS bundle
  val createTsSystemStatusSteps: Seq[Step] = Seq(
    jobStatusId := CreateTsSystemStatusJob(user, Var("Sim TS Status support comment")),
    WaitForSuccessfulJob(jobStatusId),
    dataStore := GetAnalysisJobDataStore(jobStatusId),
    fail("Expected 3 datastore files. Log, tgz, json manifest") IF dataStore.mapWith(_.size) !=? 3
  )

  // Import ReferenceSet (if necessary) from PacBioTestData
  val getOrImportDataSets: Seq[Step] = Seq(
    dsUUID := GetOrImportDataSet(lambdaNebPath, lambdaNeb.metatype)
  )

  // Create a Failed Analysis job to use in TS bundle creation
  val createFailedSmrtLinkJob: Seq[Step] = Seq(
    jobFailedAnalysisId := RunAnalysisPipeline(failedDevDiagnosticOpts(lambdaNeb.uuid)),
    jobStatus := WaitForJob(jobFailedAnalysisId),
    fail("Expected job to fail when raise_exception=true") IF jobStatus !=? Var(1)
  )

  // Create a TS Failed Job from the previous Analysis Job
  val createFailedJobSteps: Seq[Step] = Seq(
    jobFailedId := CreateTsFailedJob(jobFailedAnalysisId, user, Var("Sim TS Failed Job support comment")),
    WaitForSuccessfulJob(jobFailedId),
    dataStore := GetAnalysisJobDataStore(jobFailedId),
    fail("Expected 3 datastore files. Log, tgz, json manifest") IF dataStore.mapWith(_.size) !=? 3
  )

  override val steps = createTsSystemStatusSteps ++ getOrImportDataSets ++ createFailedSmrtLinkJob ++ createFailedJobSteps

}
