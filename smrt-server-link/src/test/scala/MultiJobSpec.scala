import java.util.UUID

import com.pacbio.secondary.smrtlink.analysis.jobs.AnalysisJobStates
import com.typesafe.scalalogging.LazyLogging
import org.specs2.mutable.Specification
import org.joda.time.{DateTime => JodaDateTime}

// Take a test driven development approach here to sketch out the basic idea.

/**
  * Take a test driven development-ish approach here to sketch out the basic idea. Specifically, these three items.
  *
  * 1. create a engine data model that has a "workflow" that will contain the data/state of the multi-job (in the dat
  * 2. create a simple example to demonstrate the model
  * 3. Add an example Multi-Job runner/executor
  *
  * Real todo items:
  *
  * - Add workflow:JsObject and isMultiJob: Boolean to Engine table in db
  * - Create new MultiJob model, add filters on isMultiJob to make sure existing "EngineJob" usage isn't changing
  * - Add new job-manager/multi-jobs/ endpoint for Multi-Job job types
  * - Add hello world multi-job (migrated/borrowed from the model described in the test below)
  * - Add EngineMultiJobManager. This will periodicially search for MultiJobs in a non-terminal state and "run" them and update the state in the db
  * - Add Multi-Job case for the barcoding usecase (e.g., Wait for SubreadSets alpha, beta, gamma, then create N analysis/pbsmrtpipe "core" jobs
  *
  */
object Utils extends LazyLogging {

  // Testing data
  val EXAMPLE_PIPELINE_ID = "pbsmrtpipe.pipelines.dev_diagnostic_subreads"

  val DS1_UUID = UUID.fromString("eb3e9420-934f-11e7-aa72-3c15c2cc8f88")
  val DS2_UUID = UUID.fromString("f59bb826-934f-11e7-9353-3c15c2cc8f88")

  val JOB_UUID = UUID.fromString("083d72f8-9350-11e7-a120-3c15c2cc8f88")

  val MJOB_UUID = UUID.fromString("90ea4c88-935b-11e7-b901-3c15c2cc8f88")

  def getTestMultiJob(): MultiEngineJob = {
    val now = JodaDateTime.now()
    val dsj1 =
      DataSetJob(DS1_UUID, datasetIsResolved = false, None, None, None)
    val dsj2 =
      DataSetJob(DS2_UUID, datasetIsResolved = false, None, None, None)
    val datasetJobs = Seq(dsj1, dsj2)

    // The state isn't really correct
    val workflow = SimpleWorkflow(datasetJobs,
                                  EXAMPLE_PIPELINE_ID,
                                  AnalysisJobStates.CREATED)

    MultiEngineJob(MJOB_UUID, now, now, AnalysisJobStates.CREATED, workflow)
  }

  // This really should be an entry point? Not a dataset UUID?
  case class DataSetJob(dataset: UUID,
                        datasetIsResolved: Boolean = false,
                        jobId: Option[UUID],
                        jobState: Option[AnalysisJobStates.JobStates],
                        message: Option[String]) {
    def summary: String = {
      s"""
        |DataSet    : $dataset
        |is Resolved: $datasetIsResolved
        |job Id     : ${jobId.getOrElse("")}
        |job state  : ${jobState.getOrElse(AnalysisJobStates.UNKNOWN)}
      """.stripMargin
    }
  }

  // Runs an analysis job after each dataset is resolved
  case class SimpleWorkflow(datasetJobs: Seq[DataSetJob],
                            pipelineId: String,
                            state: AnalysisJobStates.JobStates) {
    def summary: String = {

      s"""
        |state: $state
        |pipeline:$pipelineId
        |${datasetJobs.map(_.summary).reduce(_ + "\n" + _)}
      """.stripMargin
    }
  }

  case class MultiEngineJob(uuid: UUID,
                            createdAt: JodaDateTime,
                            updatedAt: JodaDateTime,
                            state: AnalysisJobStates.JobStates,
                            workflow: SimpleWorkflow) {
    def summary: String = {
      s"""
        |MultiJob : $uuid
        |state    : $state
        |Workflow:
        |${workflow.summary}
      """.stripMargin
    }
  }

  case class SimpleEngineJob(uuid: UUID, state: AnalysisJobStates.JobStates)

  trait Dao {
    def getDataSet(uuid: UUID): Option[UUID]
    def getJob(uuid: UUID): Option[SimpleEngineJob]
    def createCoreJob(job: SimpleEngineJob): SimpleEngineJob
  }

  /**
    * Resolve every entity on the first try.
    */
  class TestDao extends Dao {
    override def getDataSet(uuid: UUID): Option[UUID] = {
      println(s"Found Resolved dataset $uuid")
      Some(uuid)
    }

    // Getting a job will return a SUCCESSFUL job
    override def getJob(uuid: UUID): Option[SimpleEngineJob] = {
      val job = SimpleEngineJob(uuid, AnalysisJobStates.SUCCESSFUL)
      println(s"Found successful job $job")
      Some(job)
    }

    override def createCoreJob(job: SimpleEngineJob) = job
  }

  // Update the Job state and Job Id (if found)
  def resolveJob(dao: Dao, jobId: UUID, dataSetJob: DataSetJob): DataSetJob = {
    // check job id state
    dao.getJob(jobId) match {
      case Some(job) =>
        // It doesn't really matter what the job state is.
        dataSetJob.copy(jobId = Some(jobId), jobState = Some(job.state))
      case None =>
        // This is an error. The job was created, by is not found
        dataSetJob.copy(jobId = Some(jobId),
                        jobState = Some(AnalysisJobStates.FAILED),
                        message = Some(s"Unable to find $jobId"),
                        datasetIsResolved = true)
    }
  }

  def createJob(dao: Dao,
                dataSetJob: DataSetJob,
                pipelineId: String): DataSetJob = {
    // create Job
    val j = SimpleEngineJob(UUID.randomUUID(), AnalysisJobStates.CREATED)
    // this should use dsUUID
    println(s"Got Dataset ${dataSetJob.dataset}")
    val job = dao.createCoreJob(j)
    dataSetJob.copy(jobId = Some(job.uuid), jobState = Some(job.state))
  }

  def runResolveDataSetJob(dao: Dao,
                           dataSetJob: DataSetJob,
                           pipelineId: String): DataSetJob = {
    // Need to add a filter for dataset job that is in a completed state. There's nothing to do here.
    dao
      .getDataSet(dataSetJob.dataset)
      .map { dsUUID =>
        dataSetJob.jobId
          .map(
            jobId =>
              resolveJob(dao,
                         jobId,
                         dataSetJob.copy(
                           datasetIsResolved = true,
                           jobId = Some(jobId)))) // need to filter on or set if the dataset was resolved
          .getOrElse(createJob(dao,
                               dataSetJob.copy(datasetIsResolved = true),
                               pipelineId))
      }
      .getOrElse(dataSetJob) // Return the original datasetJob, there's nothing to do
  }

  // This needs some more thought
  def reduceJobStates(
      s1: AnalysisJobStates.JobStates,
      s2: AnalysisJobStates.JobStates): AnalysisJobStates.JobStates = {
    (s1, s2) match {
      case (AnalysisJobStates.CREATED, AnalysisJobStates.CREATED) =>
        AnalysisJobStates.CREATED
      case (AnalysisJobStates.RUNNING, AnalysisJobStates.RUNNING) =>
        AnalysisJobStates.RUNNING
      case (AnalysisJobStates.SUCCESSFUL, AnalysisJobStates.SUCCESSFUL) =>
        AnalysisJobStates.SUCCESSFUL
      case (AnalysisJobStates.SUBMITTED, AnalysisJobStates.SUBMITTED) =>
        AnalysisJobStates.SUBMITTED
      case (AnalysisJobStates.UNKNOWN, AnalysisJobStates.UNKNOWN) =>
        AnalysisJobStates.UNKNOWN // This is unclear what this means
      case (AnalysisJobStates.FAILED, _) => AnalysisJobStates.FAILED
      case (_, AnalysisJobStates.FAILED) => AnalysisJobStates.FAILED
      case (AnalysisJobStates.TERMINATED, _) =>
        AnalysisJobStates.FAILED // The core job failed, mark multi job as failed
      case (_, AnalysisJobStates.TERMINATED) => AnalysisJobStates.FAILED //
      case (AnalysisJobStates.RUNNING, _) =>
        AnalysisJobStates.RUNNING // If any job is running, mark the multi as running
      case (_, AnalysisJobStates.RUNNING) => AnalysisJobStates.RUNNING
      case (_, _) =>
        logger.info(
          s"Unclear mapping of multi-job state from $s1 and $s2. Defaulting to ${AnalysisJobStates.UNKNOWN}")
        AnalysisJobStates.UNKNOWN
    }
  }

  /**
    *
    * Run a Multi-Job and return an new (potentially) updated version.
    *
    * If the job is already in a terminal state (e.g, successful, failed), the initial multi job is returned.
    *
    */
  def runMultiJob(dao: Dao, multiEngineJob: MultiEngineJob): MultiEngineJob = {
    if (!AnalysisJobStates.isCompleted(multiEngineJob.state)) {

      // Check if any DataSetJob can be resolved and return updated state
      val updatedDataSetJobs = multiEngineJob.workflow.datasetJobs.map { dsj =>
        runResolveDataSetJob(dao, dsj, multiEngineJob.workflow.pipelineId)
      }

      // Compute the "overall" job state that will be used at the Multi-Job level
      val jobState = updatedDataSetJobs
        .flatMap(_.jobState)
        .reduceLeftOption(reduceJobStates)
        .getOrElse(multiEngineJob.state) // Not sure this is the correct behavior

      // Return an updated copy of the Multi-job
      multiEngineJob.copy(
        state = jobState,
        workflow =
          multiEngineJob.workflow.copy(datasetJobs = updatedDataSetJobs))
    } else {
      // Job is already completed, there's nothing to do here
      multiEngineJob
    }
  }
}

class MultiJobSpec extends Specification {

  import Utils._

  "Sanity multi job test" should {
    "create a model" in {

      val dao = new TestDao

      val mjob = getTestMultiJob()
      println(s"Initial MultiJob")
      println(mjob.summary)

      val r1 = runMultiJob(dao, mjob)
      println(s"After one iteration")
      println(r1.summary)

      val r2 = runMultiJob(dao, r1)
      println(s"After two iterations.")
      println(r2.summary)

      // The test Dao will resolve after the first call to get the status of of job. Hence, 2 calls are necessary
      r2.state must beEqualTo(AnalysisJobStates.SUCCESSFUL)
    }
  }
}
