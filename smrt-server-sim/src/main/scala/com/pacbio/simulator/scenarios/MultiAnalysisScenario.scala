package com.pacbio.simulator.scenarios

import java.nio.file.Path
import java.util.UUID

import akka.actor.ActorSystem
import com.pacbio.secondary.smrtlink.actors.DaoFutureUtils
import com.typesafe.config.Config
import com.pacbio.secondary.smrtlink.analysis.datasets.{
  DataSetMetaTypes,
  DataSetMiniMeta
}
import com.pacbio.secondary.smrtlink.analysis.externaltools._
import com.pacbio.secondary.smrtlink.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.io.{
  PacBioDataBundleIOUtils,
  XmlTemplateReader
}
import com.pacbio.secondary.smrtlink.client.{
  ClientUtils,
  SmrtLinkServiceClient
}
import com.pacbio.secondary.smrtlink.jobtypes.MultiAnalysisJobOptions
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.simulator.{Scenario, ScenarioLoader}
import com.pacbio.simulator.steps._
import com.pacificbiosciences.pacbiobasedatamodel.{
  SupportedAcquisitionStates,
  SupportedRunStates
}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import scala.util.Try
import scala.xml.Node

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

  private def toDefEntryPoint(uuid: UUID) =
    DeferredEntryPoint(DataSetMetaTypes.Subread.fileType.fileTypeId,
                       uuid,
                       "eid_subread")

  def toJobOptions(uuid: UUID,
                   numJobs: Int,
                   jobName: Option[String] = Some("Scenario Multi-job"))
    : MultiAnalysisJobOptions = {
    val entryPoint = toDefEntryPoint(uuid)

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

  def getOrImportSuccessfully(path: Path): Future[DataSetMiniMeta] = {
    for {
      dst <- Future.successful(getDataSetMiniMeta(path))
      importJob <- client.importDataSet(path, dst.metatype)
      _ <- andLog(s"Created import-dataset Job:${importJob.id}")
      successfulImportJob <- Future.fromTry(
        client.pollForSuccessfulJob(importJob.id))
      _ <- andLog(
        s"Successfully imported dataset from Job:${successfulImportJob.id}")
    } yield dst
  }

  def loadAndCopyTestFile(subreadSetTestFileId: String)
    : Future[(DataSetMiniMeta, TestDataResource)] = {
    for {
      subreadset <- getFileOrFail(subreadSetTestFileId)
      copiedSubreadSet <- Future.successful(
        subreadset.getTempDataSetFile(setNewUuid = true))
      _ <- andLog(s"Loaded and Copied TestDataFile $copiedSubreadSet")
      dst <- Future.successful(getDataSetMiniMeta(copiedSubreadSet.path))
    } yield (dst, copiedSubreadSet)
  }

  def getTestDataOrRunImportTestFile(subreadSetTestFileId: String)
    : Future[(DataSetMiniMeta, TestDataResource)] = {
    for {
      xs <- loadAndCopyTestFile(subreadSetTestFileId)
      dst <- getOrImportSuccessfully(xs._2.path)
    } yield (dst, xs._2)
  }

  // Giving up on the Step approach. It's easier to write a single future and avoid the
  // Var mechanism. Futures also compose, Steps do not.

  /**
    * Runs a Simple MultiJob MultiAnalysis Job with N child jobs created from a single
    * SubreadSet.
    *
    * @param subreadsetTestFileId Test File Id
    * @param numJobs              Number of Child Jobs to Create
    * @param jobName              Name of the MultiJob
    * @return
    */
  def runMultiJobSanityTest(
      subreadsetTestFileId: String,
      numJobs: Int,
      jobName: Option[String]): Future[Seq[EngineJob]] = {
    for {
      _ <- andLog(
        s"Starting to Run MultiJob ScenarioStep with numJobs:$numJobs")
      dstAndsubreadset <- getTestDataOrRunImportTestFile(subreadsetTestFileId)
      multiJob <- client.createMultiAnalysisJob(
        toJobOptions(dstAndsubreadset._1.uuid, numJobs, jobName))
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

  private def toMultiJobWithTwoChildren(
      multiJobName: String,
      ssetA: UUID,
      jobNameA: String,
      ssetB: UUID,
      jobNameB: String): MultiAnalysisJobOptions = {
    val numSubreadsetOpt = ServiceTaskIntOption(
      "pbsmrtpipe.task_options.num_subreadsets",
      numSubreadSets)
    val taskOptions: Seq[ServiceTaskOptionBase] = Seq(numSubreadsetOpt)
    val workflowOptions = Seq.empty[ServiceTaskOptionBase]

    def toDefJob(entryPoint: DeferredEntryPoint, name: String) = {
      DeferredJob(Seq(entryPoint),
                  "pbsmrtpipe.pipelines.dev_01_ds",
                  taskOptions,
                  workflowOptions,
                  Some(name),
                  None,
                  None)
    }

    val alpha = toDefJob(toDefEntryPoint(ssetA), jobNameA)
    val beta = toDefJob(toDefEntryPoint(ssetB), jobNameB)

    val jobs = Seq(alpha, beta)
    MultiAnalysisJobOptions(jobs, Some(multiJobName), None, None, Some(true))
  }

  private def getJobByNameOrFail(jobs: Seq[EngineJob],
                                 name: String): Future[EngineJob] = {
    jobs
      .find(_.name == name)
      .map(Future.successful)
      .getOrElse(Future.failed(new Exception(
        s"Unable to find job $name. Job names ${jobs.map(_.name)}")))
  }

  /**
    * Start a MultiJob with one job that has entry point resolved and one that will be
    * imported after the first job completes. Will poll for each child job and parent
    * multijob to complete.
    *
    */
  def runMultiJobDeferredSanityTest(
      subreadsetTestFileId: String,
      jobName: String): Future[Seq[EngineJob]] = {

    val jobAlpha = s"job-alpha-${UUID.randomUUID()}"
    val jobBeta = s"job-beta-${UUID.randomUUID()}"

    for {
      dstAndsubreadset <- getTestDataOrRunImportTestFile(subreadsetTestFileId)
      copiedTestFile <- getFileOrFail(subreadsetTestFileId).map(
        _.getTempDataSetFile(setNewUuid = true))
      copiedDst <- Future.successful(getDataSetMiniMeta(copiedTestFile.path))
      multiJobOpts <- Future.successful(
        toMultiJobWithTwoChildren(jobName,
                                  dstAndsubreadset._1.uuid,
                                  jobAlpha,
                                  copiedDst.uuid,
                                  jobBeta))
      createdMultiJob <- client.createMultiAnalysisJob(multiJobOpts)
      childJobs <- client.getMultiAnalysisChildrenJobs(createdMultiJob.id)
      childJobAlpha <- getJobByNameOrFail(childJobs, jobAlpha)
      successfulChildAlpha <- Future.fromTry(
        client.pollForSuccessfulJob(childJobAlpha.id))
      _ <- andLog(
        s"Successful ran Child job ${successfulChildAlpha.id} ${successfulChildAlpha.name}")
      _ <- getOrImportSuccessfully(copiedTestFile.path)
      childJobBeta <- getJobByNameOrFail(childJobs, jobBeta)
      successfulChildBeta <- Future.fromTry(
        client.pollForSuccessfulJob(childJobBeta.id))
      _ <- andLog(
        s"Successful ran Child job ${successfulChildBeta.id} ${successfulChildBeta.name}")
      successfulMultiJob <- Future.fromTry(
        client.pollForSuccessfulJob(createdMultiJob.id))
      _ <- andLog(
        s"Successful ran Child job ${successfulMultiJob.id} ${successfulMultiJob.name}")
      successfulChildJobs <- client.getMultiAnalysisChildrenJobs(
        createdMultiJob.id)
    } yield successfulChildJobs
  }

  private val RUN_XML_TEMPLATE = "/multi-job-run-xml-template.xml"

  private case class TemplateInput(runId: UUID,
                                   state: SupportedRunStates,
                                   sset1: UUID,
                                   sset1State: SupportedAcquisitionStates,
                                   sset2: UUID,
                                   sset2State: SupportedAcquisitionStates,
                                   multiJobId: Int) {

    def toSubMap(): XmlTemplateReader.Subs = {

      val map: XmlTemplateReader.Subs = Map(
        "{RUN_UUID}" -> (() => runId.toString),
        "{RUN_STATE}" -> (() => state.value()),
        "{SSET_1_UUID}" -> (() => sset1.toString),
        "{SSET_1_STATE}" -> (() => sset1State.value()),
        "{SSET_2_UUID}" -> (() => sset2.toString),
        "{SSET_2_STATE}" -> (() => sset2State.value()),
        "{MULTI_JOB_ID}" -> (() => multiJobId.toString)
      )
      map
    }
  }

  private def generateRunXml(resourceId: String, input: TemplateInput): Node = {

    XmlTemplateReader
      .fromStream(getClass.getResourceAsStream(resourceId))
      .globally()
      .substituteMap(input.toSubMap())
      .result()
  }

  /**
    *
    * @param subreadsetTestFileId Id of the SubreadSet in PacBioTestData
    * @param jobName MultiJob name
    *
    *  1. Create a MultiJob with 2 deferred entry points
    *  2. Create/Import a Run XML with Run in Ready State
    *  3. Verify MultiJob is still in CREATED state
    *  4. Generate new run with Run State in non-Ready State
    *  5. Verify MultiJob is in SUBMITTED state
    *  6. Verify Children jobs are in CREATED state
    *  6. Import Both SubreadSets
    *  7. Poll For both Children jobs to successfully complete
    *  8. Poll for parent MultiJob to successfully complete
    */
  def runMutiJobFromRunXml(subreadsetTestFileId: String,
                           jobName: String): Future[Seq[EngineJob]] = {

    val acqReady = SupportedAcquisitionStates.READY
    val runId = UUID.randomUUID()
    val jobCreatedStates: Set[AnalysisJobStates.JobStates] = Set(
      AnalysisJobStates.CREATED)
    val jobSubmittedStates: Set[AnalysisJobStates.JobStates] = Set(
      AnalysisJobStates.SUBMITTED)

    for {
      dst1 <- loadAndCopyTestFile(subreadsetTestFileId)
      dst2 <- loadAndCopyTestFile(subreadsetTestFileId)
      multiJobOpts <- Future.successful(
        toMultiJobWithTwoChildren(jobName,
                                  dst1._1.uuid,
                                  s"$jobName-JobA",
                                  dst2._1.uuid,
                                  s"$jobName-JobB"))
      createdMultiJob <- client.createMultiAnalysisJob(
        multiJobOpts.copy(submit = Some(false)))
      templateReadyInput <- Future.successful(
        TemplateInput(runId,
                      SupportedRunStates.READY,
                      dst1._1.uuid,
                      acqReady,
                      dst2._1.uuid,
                      acqReady,
                      createdMultiJob.id))
      runReadyXml <- Future.successful(
        generateRunXml(RUN_XML_TEMPLATE, templateReadyInput))
      runRunningXml <- Future.successful(
        generateRunXml(
          RUN_XML_TEMPLATE,
          templateReadyInput.copy(state = SupportedRunStates.RUNNING)))
      createdRun <- client.createRun(runReadyXml.mkString)
      _ <- andLog(s"Created Run ${runReadyXml.mkString}")
      _ <- andLog(s"Created Run $runId state:${createdRun.status}")
      _ <- client.pollForJobInState(createdMultiJob.id, jobCreatedStates)
      updatedRun <- client.updateRun(createdRun.uniqueId,
                                     dataModel = Some(runRunningXml.mkString),
                                     reserved = Some(true))
      _ <- andLog(s"Updated Run $runId state:${updatedRun.status}")
      _ <- client.pollForJobInState(createdMultiJob.id, jobSubmittedStates)
      // Import SubreadSets
      _ <- getOrImportSuccessfully(dst1._2.path)
      _ <- getOrImportSuccessfully(dst2._2.path)
      // Poll for Children Jobs to Complete
      childrenJobs <- client.getMultiAnalysisChildrenJobs(createdMultiJob.id)
      // Poll for MultiJob to Complete
      _ <- Future.fromTry(client.pollForSuccessfulJob(createdMultiJob.id))
    } yield childrenJobs
  }

  private def collectionSummary(
      run: UUID,
      collection: UUID,
      message: Option[String] = None): Future[String] = {
    for {
      c1 <- client.getCollection(run, collection)
      msg <- andLog(
        s"Found Collection ${c1.uniqueId} state:${c1.status} ${message.getOrElse("")}")
    } yield msg
  }

  def runMutiJobFromRunXmlWithFailedAcq(
      subreadSetTestFileId: String,
      jobName: String): Future[Seq[EngineJob]] = {

    val acqReady = SupportedAcquisitionStates.READY
    val runId = UUID.randomUUID()
    val jobCreatedStates: Set[AnalysisJobStates.JobStates] = Set(
      AnalysisJobStates.CREATED)
    val jobSubmittedStates: Set[AnalysisJobStates.JobStates] = Set(
      AnalysisJobStates.SUBMITTED)

    val jobSuccessStates: Set[AnalysisJobStates.JobStates] = Set(
      AnalysisJobStates.SUCCESSFUL)

    // Use a globally unique name to look up the Job
    val jobSuffix = UUID.randomUUID()
    // Subreadset/Job 1 will be the successful, SubreadSet/Acq/Job2 will Fail
    val jobName1 = s"$jobName-Job1-$jobSuffix"
    val jobName2 = s"$jobName-Job2-EXPECTED-TO-FAIL-$jobSuffix"

    // numRetries * retryDelay will the max time that the job will
    // poll. This will be system/load dependent.
    def pollForState(
        jobId: Int,
        states: Set[AnalysisJobStates.JobStates]): Future[EngineJob] =
      client.pollForJobInState(jobId,
                               states,
                               numRetries = 40,
                               retryDelay = 2.seconds)

    for {
      dst1 <- loadAndCopyTestFile(subreadSetTestFileId)
      dst2 <- loadAndCopyTestFile(subreadSetTestFileId)
      multiJobOpts <- Future.successful(
        toMultiJobWithTwoChildren(jobName,
                                  dst1._1.uuid,
                                  jobName1,
                                  dst2._1.uuid,
                                  jobName2))
      createdMultiJob <- client.createMultiAnalysisJob(
        multiJobOpts.copy(submit = Some(false)))
      templateReadyInput <- Future.successful(
        TemplateInput(runId,
                      SupportedRunStates.READY,
                      dst1._1.uuid,
                      acqReady,
                      dst2._1.uuid,
                      acqReady,
                      createdMultiJob.id))
      runReadyXml <- Future.successful(
        generateRunXml(RUN_XML_TEMPLATE, templateReadyInput))
      runRunningXml <- Future.successful(
        generateRunXml(RUN_XML_TEMPLATE,
                       templateReadyInput.copy(
                         state = SupportedRunStates.RUNNING,
                         sset1State = SupportedAcquisitionStates.COMPLETE)))
      runFailedXml <- Future.successful(
        generateRunXml(RUN_XML_TEMPLATE,
                       templateReadyInput.copy(
                         state = SupportedRunStates.TERMINATED,
                         sset2State = SupportedAcquisitionStates.ERROR)))
      createdRun <- client.createRun(runReadyXml.mkString)
      _ <- andLog(s"Created Run ${runReadyXml.mkString}")
      _ <- andLog(s"Created Run $runId state:${createdRun.status}")
      _ <- client.pollForJobInState(createdMultiJob.id, jobCreatedStates)
      // This will Trigger the MultiJob to Submit status
      updatedRun <- client.updateRun(createdRun.uniqueId,
                                     dataModel = Some(runRunningXml.mkString),
                                     reserved = Some(true))
      _ <- andLog(s"Updated Run $runId state:${updatedRun.status}")

      // Log Collections
      _ <- collectionSummary(createdRun.uniqueId, dst1._1.uuid)
      _ <- collectionSummary(createdRun.uniqueId, dst2._1.uuid)

      // Make sure Children are found
      childrenJobs <- client.getMultiAnalysisChildrenJobs(createdMultiJob.id)
      childJob1 <- getJobByNameOrFail(childrenJobs, jobName1)
      childJob2 <- getJobByNameOrFail(childrenJobs, jobName2)
      // Verify the MultiJob is in a Submitted state
      _ <- client.pollForJobInState(createdMultiJob.id, jobSubmittedStates)

      // This is an error case where there's two collections, one of them
      // imports successfully, then the second one is assumed to be "FAILED"
      // from the Run state.
      _ <- getOrImportSuccessfully(dst1._2.path)
      // The import should trigger the child job to successfully run
      successfulChildJob1 <- pollForState(childJob1.id, jobSuccessStates)
      // Now trigger a "FAILED" Run and Collection level error
      updatedFailedRun <- client.updateRun(createdRun.uniqueId,
                                           Some(runFailedXml.mkString),
                                           Some(true))

      // Verify the Collections for the Run are in the correct state
      // Log Collections
      _ <- collectionSummary(createdRun.uniqueId, dst1._1.uuid)
      _ <- collectionSummary(
        createdRun.uniqueId,
        dst2._1.uuid,
        Some(s"expected ${SupportedAcquisitionStates.ERROR}"))

      // The remaining non-running jobs should be marked as FAILED
      expectedFailedChildJob2 <- pollForState(
        childJob2.id,
        AnalysisJobStates.FAILURE_STATES.toSet)

      _ <- andLog(
        s"Found expected failed Child Job ${expectedFailedChildJob2.id} ${expectedFailedChildJob2.errorMessage
          .getOrElse("")}")

      // Verify the MultiJob from the Run is marked as Failed
      expectedFailedMultiJob <- pollForState(
        createdMultiJob.id,
        AnalysisJobStates.FAILURE_STATES.toSet)

      _ <- andLog(
        s"Found expected failed MultiJob ${expectedFailedMultiJob.id} ${expectedFailedMultiJob.errorMessage
          .getOrElse("")}")
    } yield Seq(expectedFailedChildJob2)
  }

  case class RunMultiJobAnalysisSanityStep(subreadsetTestFileId: String,
                                           numJobs: Int,
                                           jobName: String)
      extends VarStep[Seq[EngineJob]] {
    override val name: String = "RunMultiJobAnalysisSanity"
    override def runWith =
      runMultiJobSanityTest(subreadsetTestFileId, numJobs, Some(jobName))
  }

  case class ImportTestData(subreadSetTestId: String)
      extends VarStep[String]
      with DaoFutureUtils {
    override val name: String = "ImportTestData"

    def getFile(fileId: String): Future[Path] =
      failIfNone(s"Unable to find testdata $fileId")(
        testData.findById(fileId).map(_.path))

    override val runWith = for {
      path <- getFile(subreadSetTestId)
      job <- client.importDataSet(path, DataSetMetaTypes.Subread)
      _ <- Future.fromTry(client.pollForSuccessfulJob(job.id))
      msg <- Future.successful(
        s"Successful imported $subreadSetTestId with Job ${job.id}")
      _ <- andLog(msg)
    } yield msg
  }

  case class RunMultiJobDeferredStep(subreadSetTestFileId: String,
                                     jobName: String)
      extends VarStep[Seq[EngineJob]] {
    override val name: String = "RunMultiJobDeferredStep"
    override def runWith: Future[Seq[EngineJob]] =
      runMultiJobDeferredSanityTest(subreadSetTestFileId, jobName)
  }

  case class RunMultiJobRunXmlStep(subreadSetTestFileId: String,
                                   jobName: String)
      extends VarStep[Seq[EngineJob]] {
    override val name: String = "RunMultiJobRunXmlStep"
    override def runWith: Future[Seq[EngineJob]] =
      runMutiJobFromRunXml(subreadSetTestFileId, jobName)
  }

  case class RunMultiJobRunXmlWithFailedAcq(subreadSetTestFiledId: String,
                                            jobName: String)
      extends VarStep[Seq[EngineJob]] {
    override val name: String = "RunMultiJobRunXmlWithFailedAcq"

    override def runWith: Future[Seq[EngineJob]] =
      runMutiJobFromRunXmlWithFailedAcq(subreadSetTestFiledId, jobName)
  }

  val numJobsPerMultiJob: Seq[Int] =
    (0 until max2nNumJobs).map(x => math.pow(2, x).toInt)

  val multiJobSteps: Seq[Step] = numJobsPerMultiJob.zipWithIndex.map {
    case (n, i) =>
      RunMultiJobAnalysisSanityStep(testSubreadSetId, n, s"Multi-job-${i + 1}")
  }

  lazy val multiDeferredSteps: Seq[Step] = Seq(
    RunMultiJobDeferredStep(testSubreadSetId, "Deferred JobTest"))

  lazy val multiJobRunXmlSteps: Seq[Step] = Seq(
    RunMultiJobRunXmlStep(testSubreadSetId, "MultiJob with Run XML"))

  lazy val multiJobRunXmlExpectFailSteps: Seq[Step] = Seq(
    RunMultiJobRunXmlWithFailedAcq(testSubreadSetId, "MultiJobExpectFail"))

  // When only running this Scenario, Add a centalizing importing of the TestData
  // make the test run quicker.
  override val steps = Seq(ImportTestData(testSubreadSetId)) ++ multiJobSteps ++ multiDeferredSteps ++ multiJobRunXmlSteps ++ multiJobRunXmlExpectFailSteps

}
