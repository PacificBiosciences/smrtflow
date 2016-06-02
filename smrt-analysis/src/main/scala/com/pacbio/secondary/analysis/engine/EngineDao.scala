package com.pacbio.secondary.analysis.engine

import java.util.UUID
import org.joda.time.{DateTime => JodaDateTime}
import collection.mutable

import com.typesafe.scalalogging.LazyLogging

import com.pacbio.secondary.analysis.engine.CommonMessages.{SuccessMessage, FailedMessage}
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobs.{JobResourceResolver, AnalysisJobStates}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future


/**
 *
 * Created by mkocher on 6/10/15.
 */
object EngineDao {

  trait JobEngineDaoComponent {

    def addRunnableJob(job: RunnableJob): Future[EngineJob]

    /**
     * Gets the next Runnable Job (job that is not in a state of 'Created'
     */
    def getNextRunnableJobWithId: Future[Either[NoAvailableWorkError, RunnableJobWithId]]

    /**
     * Update the State of Job By Job UUID
     */
    def updateJobStateByUUID(uuid: UUID, state: AnalysisJobStates.JobStates): Future[String]

    /**
     * Get the last N jobs
     * @param limit Max number of jobs to return
     */
    def getJobs(limit: Int = 1000): Future[Seq[EngineJob]]

    /**
     * Get Job by Int (primary key)
     * The support for both
     */
    def getJobById(i: Int): Future[Option[EngineJob]]

    /**
     * Get Job by UUID
     * @param uuid Job UUID
     */
    def getJobByUUID(uuid: UUID): Future[Option[EngineJob]]

    /**
     * Get all the Job Events associated with a Job (by job id)
     */
    def getJobEventsByJobId(i: Int): Future[Seq[JobEvent]]

  }

  trait InMemoryJobEngineDaoComponent extends JobEngineDaoComponent with LazyLogging {

    // Used locally to Resolve the Job directory path
    val jobResolver: JobResourceResolver
    // local cache of jobs to run. Keep the CoreJob instance around until the job is completed, then deleted it
    var _runnableJobs: mutable.Map[UUID, RunnableJobWithId]
    // Persistence layer for all jobs
    var _engineJobs: mutable.Map[UUID, EngineJob]
    // JobUUID -> Events
    var _jobEvents: mutable.Map[UUID, mutable.MutableList[JobEvent]]

    /**
     * There's a globally unique job id (UUID) and locally unique more human friendly Int primary key.
     *
     * Adding a job here will generate the local primary key and create an EngineJob
     */
    private def runnableJobToEngineJob(runnableJob: RunnableJob, jobId: Int, state: AnalysisJobStates.JobStates): EngineJob = {
      val jobId = _engineJobs.size + 1
      val rjob = RunnableJobWithId(jobId, runnableJob.job, state)
      runnableJobWithIdToEngineJob(rjob, rjob.state)
    }

    private def runnableJobWithIdToEngineJob(runnableJob: RunnableJobWithId, state: AnalysisJobStates.JobStates): EngineJob = {
      val name = s"Job ${runnableJob.id}"
      val description = s"$name job type ${runnableJob.job.jobOptions.toJob.jobTypeId.id}"
      val createdAt = JodaDateTime.now()
      val updatedAt = createdAt
      val path = jobResolver.resolve(runnableJob)
      val jsonSettings = "{}"
      EngineJob(runnableJob.id, runnableJob.job.uuid, name, description, createdAt, updatedAt, state, runnableJob.job.jobOptions.toJob.jobTypeId.id, path.toAbsolutePath.toString, jsonSettings, None)
    }

    override def addRunnableJob(runnableJob: RunnableJob): Future[EngineJob] = Future {
      val jobId = _engineJobs.size + 1
      val state = AnalysisJobStates.CREATED

      println(s"Adding Runnable Job ($jobId) $runnableJob")
      _runnableJobs.update(runnableJob.job.uuid, RunnableJobWithId(jobId, runnableJob.job, runnableJob.state))

      val engineJob = runnableJobToEngineJob(runnableJob, jobId, state)

      _engineJobs.update(runnableJob.job.uuid, engineJob)

      // This is where the job event is initialized at.
      //_jobEvents + (runnableJob.job.uuid -> Seq(JobEvent(UUID.randomUUID(), jobId, state, s"Updating state to $state", JodaDateTime.now())))
      engineJob
    }

    /**
     * This 'checks' out the next runnable job and updates the state to "SUBMITTED" if there
     * is work available to run.
     */
    override def getNextRunnableJobWithId: Future[Either[NoAvailableWorkError, RunnableJobWithId]] = Future {
      _runnableJobs.values.find(_.state == AnalysisJobStates.CREATED) match {
        case Some(runnableJobWithId) =>
          val engineJob = runnableJobWithIdToEngineJob(runnableJobWithId, AnalysisJobStates.SUBMITTED)
          _engineJobs.update(engineJob.uuid, engineJob)

          _runnableJobs.remove(runnableJobWithId.job.uuid)
          //_jobEvents(engineJob.uuid) += JobEvent(UUID.randomUUID(), engineJob.id, engineJob.state, s"Updating state to ${engineJob.state}", JodaDateTime.now())
          Right(runnableJobWithId)
        case _ => Left(NoAvailableWorkError("No Available work to run."))
      }
    }

    override def updateJobStateByUUID(uuid: UUID, state: AnalysisJobStates.JobStates): Future[String] = Future {
      _engineJobs.get(uuid) match {
        case Some(x) =>
          val updatedAt = JodaDateTime.now()
          val engineJob = EngineJob(x.id, x.uuid, x.name, x.comment, x.createdAt, updatedAt, state, x.jobTypeId, x.path, x.jsonSettings, x.createdBy)
          logger.info(s"Updating job ${uuid.toString} from ${x.state} to $state")

          _engineJobs.update(engineJob.uuid, engineJob)

          s"Updated job ${uuid.toString} from ${x.state} to $state"
        case _ =>
          logger.error(s"Unknown job id ${uuid.toString}. Failed to job state to $state")
          s"Unknown job id ${uuid.toString}. Failed to job state to $state"
      }
    }

    override def getJobs(limit: Int = 1000): Future[Seq[EngineJob]] = Future(_engineJobs.values.toSeq)

    override def getJobById(i: Int): Future[Option[EngineJob]] = Future(_engineJobs.values.find(_.id == i))

    override def getJobByUUID(uuid: UUID): Future[Option[EngineJob]] = Future(_engineJobs.get(uuid))

    def getJobEventsByJobUUID(uuid: UUID): Seq[JobEvent] = _jobEvents.getOrElse(uuid, Seq.empty[JobEvent])

    override def getJobEventsByJobId(i: Int): Future[Seq[JobEvent]] =
      // this would compose easier if getJobEventsByJobUUID returned Option.
      getJobById(i).map(_.map(job => getJobEventsByJobUUID(job.uuid)) getOrElse Seq.empty[JobEvent])
  }


  /**
   * General Data file store interface.
   */
  trait DataStoreComponent {

    /**
     * Add a DataStore file to the datastore
     *
     * @param dstoreJobFile DataStoreJobFile with UUID of job
     * @return
     */
    def addDataStoreFile(dstoreJobFile: DataStoreJobFile): Future[Either[FailedMessage, SuccessMessage]]

    /**
     * Get All DataStore files
     * @return
     */
    def getDataStoreFiles: Future[Seq[DataStoreJobFile]]

    /**
     * Get DataStore file by UUID
     * @param uuid UUID of datastore file
     * @return
     */
    def getDataStoreFileByUUID(uuid: UUID): Future[Option[DataStoreJobFile]]

    /**
     * Get All DataStore files assiociated with a Job (by job UUID)
     * @param uuid UUID of the job
     * @return
     */
    def getDataStoreFilesByJobUUID(uuid: UUID): Future[Seq[DataStoreJobFile]]

  }

  trait InMemoryDataStore extends DataStoreComponent with LazyLogging {

    /**
     * In memory Storage
     */
    var dataStoreJobFiles: mutable.Map[UUID, DataStoreJobFile]

    def addDataStoreFile(dstoreJobFile: DataStoreJobFile) = Future {
      val uuid = dstoreJobFile.dataStoreFile.uniqueId
      dataStoreJobFiles.put(uuid, dstoreJobFile)
      Right(SuccessMessage(s"Successfully added $dstoreJobFile"))
    }

    def getDataStoreFiles = Future(dataStoreJobFiles.values.toSeq)

    def getDataStoreFileByUUID(uuid: UUID): Future[Option[DataStoreJobFile]] = Future(dataStoreJobFiles.get(uuid))

    def getDataStoreFilesByJobUUID(uuid: UUID): Future[Seq[DataStoreJobFile]] =
      Future(dataStoreJobFiles.values.filter(_.jobId == uuid).toSeq)

  }

  /**
   * Minimal Interface required to run the Job Engine
   */
  abstract class JobEngineDataStore extends JobEngineDaoComponent with DataStoreComponent

  /**
   * In memory job engine DAO
   */
  class JobEngineDao(val jobResolver: JobResourceResolver) extends JobEngineDataStore with InMemoryJobEngineDaoComponent with InMemoryDataStore {

    var dataStoreJobFiles = mutable.Map[UUID, DataStoreJobFile]()
    var _runnableJobs = mutable.Map[UUID, RunnableJobWithId]()
    var _engineJobs = mutable.Map[UUID, EngineJob]()
    var _jobEvents = mutable.Map[UUID, mutable.MutableList[JobEvent]]()
  }

}
