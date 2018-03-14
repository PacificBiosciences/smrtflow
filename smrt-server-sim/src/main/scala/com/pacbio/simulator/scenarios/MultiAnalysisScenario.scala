package com.pacbio.simulator.scenarios

import java.nio.file.Path
import java.util.UUID

import akka.actor.ActorSystem
import com.pacbio.secondary.smrtlink.actors.DaoFutureUtils

import scala.collection._
import com.typesafe.config.Config
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.externaltools._
import com.pacbio.secondary.smrtlink.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.io.PacBioDataBundleIOUtils
import com.pacbio.secondary.smrtlink.client.{
  ClientUtils,
  SmrtLinkServiceClient
}
import com.pacbio.secondary.smrtlink.jobtypes.MultiAnalysisJobOptions
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.pacbio.simulator.steps._

import scala.concurrent.Future
import scala.util.Try

object MultiAnalysisScenarioLoader extends ScenarioLoader {

  // There are two parameter usecases here
  // 1. N "standard" analysis jobs where each analysis job emits ~ 25 datastore
  // files. N is restricted to 32? (this should probably be 16)
  // 2. N barcoding analysis where N is <= 8 and the barcoded datastore outputs
  // will 8,16,96,384 (+ some delta of ~5 datastore files)
  val DEFAULT_NUM_SUBREADSETS = 25
  val DEFAULT_MAX_2N_NUM_JOBS = 4

  def getOrDefault(key: String, c: Config, default: Int): Int =
    Try(c.getInt(key)).getOrElse(default)

  override def load(config: Option[Config])(
      implicit system: ActorSystem): Scenario = {

    val name = "MultiAnalysisScenario"

    require(config.isDefined,
            s"Path to config file must be specified for $name")
    require(
      PacBioTestResourcesLoader.isAvailable(),
      s"PacBioTestData must be configured for $name ${PacBioTestResourcesLoader.ERROR_MESSAGE}")

    val c: Config = config.get

    val numSubreadSets: Int = getOrDefault(
      "smrtflow.test.multiJob.numSubreadSets",
      c,
      DEFAULT_NUM_SUBREADSETS)
    val max2nNumJobs: Int = getOrDefault("smrtflow.test.multiJob.max2nNumJobs",
                                         c,
                                         DEFAULT_MAX_2N_NUM_JOBS)

    val testData = PacBioTestResourcesLoader.loadFromConfig()
    val smrtLinkClient = new SmrtLinkServiceClient(getHost(c), getPort(c))

    new MultiAnalysisScenario(smrtLinkClient,
                              testData,
                              numSubreadSets,
                              max2nNumJobs)
  }
}

/**
  * Sanity Test for MultiJob
  *
  * 1. create Multi-job
  * 2. change state to submit
  * 3. import dataset
  * 4. poll for multi-job to complete
  * 5. check children jobs created from multi-job to be successful
  *
  */
class MultiAnalysisScenario(client: SmrtLinkServiceClient,
                            testData: PacBioTestResources,
                            numSubreadSets: Int,
                            max2nNumJobs: Int,
                            testSubreadSetId: String = "subreads-sequel")
    extends Scenario
    with VarSteps
    with ConditionalSteps
    with IOSteps
    with SmrtLinkSteps
    with ClientUtils
    with PacBioDataBundleIOUtils {

  import com.pacbio.common.models.CommonModelImplicits._

  override val name = "MultiAnalysisScenario"
  override val smrtLinkClient = client

  def toJobOptions(subreadset: TestDataResource,
                   uuid: UUID,
                   numJobs: Int,
                   jobName: Option[String] = Some("Scenario Multi-job"))
    : MultiAnalysisJobOptions = {
    val entryPoint = DeferredEntryPoint(
      DataSetMetaTypes.Subread.fileType.fileTypeId,
      uuid,
      "eid_subread")

    val numSubreadsetOpt = ServiceTaskIntOption(
      "pbsmrtpipe.task_options.num_subreadsets",
      numSubreadSets)
    val taskOptions: Seq[ServiceTaskOptionBase] = Seq(numSubreadsetOpt)
    val workflowOptions = Seq.empty[ServiceTaskOptionBase]

    val jobs = (1 to numJobs).map { i =>
      DeferredJob(Seq(entryPoint),
                  "pbsmrtpipe.pipelines.dev_01_ds",
                  taskOptions,
                  workflowOptions,
                  jobName.map(n => s"$n-MultiJob-$i"),
                  None,
                  None)

    }

    MultiAnalysisJobOptions(jobs,
                            jobName,
                            None,
                            Some(JobConstants.GENERAL_PROJECT_ID))
  }

  def getFileOrFail(testFileId: String): Future[TestDataResource] = {
    testData.files
      .find(_.id == testFileId)
      .map(f => Future.successful(f))
      .getOrElse(Future.failed(
        new Exception(s"Unable to find TestFile id $testFileId")))
  }

  def validateJobWasSuccessful(job: EngineJob): Future[EngineJob] = {
    if (AnalysisJobStates.isSuccessful(job.state)) Future.successful(job)
    else
      Future.failed(
        new Exception(
          s"Job ${job.id} was NOT successful (state:${job.state})"))
  }

  def jobSummary(jobs: Seq[EngineJob]): String = {
    def toSummary(j: EngineJob): String =
      s"Job id:${j.id} state:${j.state} parent:${j.parentMultiJobId} path:${j.path}"

    jobs
      .map(toSummary)
      .reduceLeftOption(_ + "\n" + _)
      .getOrElse("No Jobs")
  }

  // Giving up on the Step approach. It's easier to write a single future and avoid the
  // Var mechanism. Futures also compose, Steps do not.
  def runSanityTest(subreadsetTestFileId: String,
                    numJobs: Int,
                    jobName: Option[String]): Future[Seq[EngineJob]] = {
    for {
      _ <- andLog(
        s"Starting to Run MultiJob ScenarioStep with numJobs:$numJobs")
      subreadset <- getFileOrFail(subreadsetTestFileId)
      _ <- andLog(s"Loaded TestDataFile $subreadset")
      msg <- client.getStatus
      _ <- andLog(
        s"Successfully connected to SMRT Link Server: ${client.RootUri} ${msg.message}")
      dst <- Future.successful(getDataSetMiniMeta(subreadset.path))
      importJob <- client.importDataSet(subreadset.path, dst.metatype)
      _ <- andLog(s"Created import-dataset job ${importJob.id}")
      successfulImportJob <- Future.fromTry(
        client.pollForSuccessfulJob(importJob.id))
      _ <- andLog(s"Successfully imported dataset ${successfulImportJob.id}")
      multiJob <- client.createMultiAnalysisJob(
        toJobOptions(subreadset, dst.uuid, numJobs, jobName))
      _ <- andLog(
        s"Successfully Created MultiJob ${multiJob.id} in state:${multiJob.state}")
      updateMsg <- client.updateMultiAnalysisJobToSubmit(multiJob.id)
      _ <- andLog(updateMsg.message)
      successfulMultiJob <- Future.fromTry(
        client.pollForSuccessfulJob(multiJob.id))
      _ <- andLog(s"Successfully ran MultiJob ${successfulMultiJob.id}")
      jobs <- client.getMultiAnalysisChildrenJobs(multiJob.id)
      _ <- andLog(s"Found ${jobs.length} children jobs ids:${jobs
        .map(_.id)} from MultiJob ${successfulMultiJob.id}")
      _ <- andLog(jobSummary(jobs))
      _ <- Future.sequence(jobs.map(validateJobWasSuccessful))
      _ <- andLog(
        s"Got children jobs ${jobs.map(_.id)} from Multi-Job ${successfulMultiJob.id}")
    } yield jobs
  }

  case class RunMultiJobAnalysisSanityStep(subreadsetTestFileId: String,
                                           numJobs: Int,
                                           jobName: String)
      extends VarStep[Seq[EngineJob]] {
    override val name: String = "RunMultiJobAnalysisSanity"
    override def runWith =
      runSanityTest(subreadsetTestFileId, numJobs, Some(jobName))
  }

  case class ImportTestData(subreadSetTestId: String)
      extends VarStep[String]
      with DaoFutureUtils {
    override val name: String = "ImportTestData"

    def getFile(fileId: String): Future[Path] =
      failIfNone(s"Unable to find testdata $fileId")(
        testData.getFile(fileId).map(_.path))

    override val runWith = for {
      path <- getFile(subreadSetTestId)
      job <- client.importDataSet(path, DataSetMetaTypes.Subread)
      _ <- Future.fromTry(client.pollForSuccessfulJob(job.id))
      msg <- Future.successful(
        s"Successful imported $subreadSetTestId with Job ${job.id}")
      _ <- andLog(msg)
    } yield msg
  }

  val numJobsPerMultiJob: Seq[Int] =
    (0 until max2nNumJobs).map(x => math.pow(2, x).toInt)

  val multiJobSteps: Seq[Step] = numJobsPerMultiJob.zipWithIndex.map {
    case (n, i) =>
      RunMultiJobAnalysisSanityStep(testSubreadSetId, n, s"Multi-job-${i + 1}")
  }

  // When only running this Scenario, Add a centalizing importing of the TestData
  // make the test run quicker.
  override val steps = Seq(ImportTestData(testSubreadSetId)) ++ multiJobSteps

}
