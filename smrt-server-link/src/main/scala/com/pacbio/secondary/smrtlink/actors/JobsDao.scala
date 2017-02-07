package com.pacbio.secondary.smrtlink.actors

import java.nio.file.{Files, Paths}
import java.util.UUID

import com.google.common.annotations.VisibleForTesting
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.services.PacBioServiceErrors.ResourceNotFoundError
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes.DataSetMetaType
import com.pacbio.secondary.analysis.datasets.io.{DataSetJsonUtils, DataSetLoader}
import com.pacbio.secondary.analysis.engine.CommonMessages.MessageResponse
import com.pacbio.secondary.analysis.engine.{CommonMessages, EngineConfig}
import com.pacbio.secondary.analysis.engine.EngineDao.{DataStoreComponent, JobEngineDaoComponent, JobEngineDataStore}
import com.pacbio.secondary.analysis.jobs.JobModels._
import com.pacbio.secondary.analysis.jobs._
import com.pacbio.secondary.smrtlink.SmrtLinkConstants
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.database.TableModels._
import com.pacbio.secondary.smrtlink.models._
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}
import org.apache.commons.lang.SystemUtils

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}
import scala.util.control.NonFatal
import slick.driver.PostgresDriver.api._
import java.sql.SQLException

import com.pacbio.common.models.CommonModels.{IdAble, IntIdAble, UUIDIdAble}
import com.pacbio.secondary.analysis.configloaders.ConfigLoader
import com.pacbio.secondary.smrtlink.database.DatabaseConfig
import org.postgresql.util.PSQLException


trait DalProvider {
  val db: Singleton[Database]
}

trait SmrtLinkDalProvider extends DalProvider with ConfigLoader{
  this: SmrtLinkConfigProvider =>

  /**
    * Database config to be used within the specs
    *
    * There's a bit of duplication here because the interface for flyway requires a PG Datasource
    * while slick requires a Database.forConfig model.
    * Provided, the root key doesn't change (e.g., smrtflow.test-db), this should be
    * too much of a problem.
    *
    */
  lazy final val dbConfig: DatabaseConfig = {
    val dbName = conf.getString("smrtflow.db.properties.databaseName")
    val user = conf.getString("smrtflow.db.properties.user")
    val password = conf.getString("smrtflow.db.properties.password")
    val port = conf.getInt("smrtflow.db.properties.portNumber")
    val server = conf.getString("smrtflow.db.properties.serverName")
    val maxConnections = conf.getInt("smrtflow.db.numThreads")

    DatabaseConfig(dbName, user, password, server, port, maxConnections)
  }

  override val db: Singleton[Database] =
    Singleton(() => Database.forConfig("smrtflow.db"))
}

@VisibleForTesting
trait TestDalProvider extends DalProvider with ConfigLoader{


  /**
    * This Single configuration will be used in all the specs.
    *
    * See comments above about duplication.
    */
  lazy final val dbConfig: DatabaseConfig = {
    val dbName = conf.getString("smrtflow.test-db.properties.databaseName")
    val user = conf.getString("smrtflow.test-db.properties.user")
    val password = conf.getString("smrtflow.test-db.properties.password")
    val port = conf.getInt("smrtflow.test-db.properties.portNumber")
    val server = conf.getString("smrtflow.test-db.properties.serverName")
    val maxConnections = conf.getInt("smrtflow.test-db.numThreads")

    DatabaseConfig(dbName, user, password, server, port, maxConnections)
  }

  override val db: Singleton[Database] = Singleton(() => { dbConfig.toDatabase })
}

/**
 * SQL Datastore backend configuration and db connection
 */
trait DalComponent extends LazyLogging{
  val db: Database

  // https://www.postgresql.org/docs/9.6/static/errcodes-appendix.html
  final private val integrityConstraintViolationSqlStateCodes =
    Set("23000", "230001", "23502", "23503", "23505", "23514", "23P01")

  def isConstraintViolation(t: Throwable): Boolean = {
    t match {
      case se: SQLException =>
        //logger.debug(s"Is Violation constraint error-code:'${se.getErrorCode}' state:'${se.getSQLState}' ${se.getMessage}")
        integrityConstraintViolationSqlStateCodes contains se.getSQLState
      case _ => false
    }
  }
}

// Need to find a central home for these util funcs
trait DaoFutureUtils {
  def failIfNone[T](message: String): (Option[T] => Future[T]) = {
    case Some(value) => Future { value}
    case _ => Future.failed(new ResourceNotFoundError(message))
  }
}

trait ProjectDataStore extends LazyLogging {
  this: DalComponent with SmrtLinkConstants =>

  def getProjects(limit: Int = 1000): Future[Seq[Project]] =
    db.run(projects.filter(_.isActive).take(limit).result)

  def getProjectById(projId: Int): Future[Option[Project]] =
    db.run(projects.filter(_.id === projId).result.headOption)

  def createProject(projReq: ProjectRequest): Future[Project] = {
    val now = JodaDateTime.now()
    val proj = Project(-99, projReq.name, projReq.description, ProjectState.CREATED, now, now, isActive = true)
    val insert = projects returning projects.map(_.id) into((p, i) => p.copy(id = i)) += proj
    val fullAction = insert.flatMap(proj => setMembersAndDatasets(proj, projReq))
    db.run(fullAction.transactionally)
  }

  def setMembersAndDatasets(proj: Project, projReq: ProjectRequest): DBIO[Project] = {
    // skip updating member/dataset lists if the request doesn't include those
    val updates = List(
      projReq.members.map(setProjectMembers(proj.id, _)),
      projReq.datasets.map(setProjectDatasets(proj.id, _))
    ).flatten

    DBIO.sequence(updates).andThen(DBIO.successful(proj))
  }

  def setProjectMembers(projId: Int, members: Seq[ProjectRequestUser]): DBIO[Unit] =
    DBIO.seq(
      projectsUsers.filter(_.projectId === projId).delete,
      projectsUsers ++= members.map(m => ProjectUser(projId, m.login, m.role))
    )

  def setProjectDatasets(projId: Int, ids: Seq[RequestId]): DBIO[Unit] = {
    val now = JodaDateTime.now()
    DBIO.seq(
      // move datasets not in the list of ids back to the general project
      dsMetaData2
        .filter(_.projectId === projId)
        .filterNot(_.id inSet ids.map(_.id))
        .map(ds => (ds.projectId, ds.updatedAt))
        .update((GENERAL_PROJECT_ID, now)),
      // move datasets that *are* in the list of IDs into this project
      dsMetaData2
        .filter(_.id inSet ids.map(_.id))
        .map(ds => (ds.projectId, ds.updatedAt))
        .update((projId, now))
    )
  }

  def updateProject(projId: Int, projReq: ProjectRequest): Future[Option[Project]] = {
    val now = JodaDateTime.now()
    val update = projReq.state match {
      case Some(state) =>
        projects
          .filter(_.id === projId)
          .map(p => (p.name, p.state, p.description, p.updatedAt))
          .update((projReq.name, state, projReq.description, now))
      case None =>
        projects
          .filter(_.id === projId)
          .map(p => (p.name, p.description, p.updatedAt))
          .update((projReq.name, projReq.description, now))
    }

    val fullAction = update.andThen(
      projects.filter(_.id === projId).result.headOption.flatMap {
        case Some(proj) => setMembersAndDatasets(proj, projReq).map(Some(_))
        case None => DBIO.successful(None)
      }
    )

    db.run(fullAction.transactionally)
  }

  def deleteProjectById(projId: Int): Future[Option[Project]] = {
    logger.info(s"Setting isActive=false for project-id $projId")
    val now = JodaDateTime.now()
    db.run(DBIO.seq(
      projects
        .filter(_.id === projId)
        .map(j => (j.isActive, j.updatedAt))
        .update(false, now),
      // move the datasets from this project into the general project
      dsMetaData2
        .filter(_.projectId === projId)
        .map(ds => (ds.projectId, ds.updatedAt))
        .update((GENERAL_PROJECT_ID, now))
    ).andThen(
      projects.filter(_.id === projId).result.headOption
    ))
  }

  def getProjectUsers(projId: Int): Future[Seq[ProjectUser]] =
    db.run(projectsUsers.filter(_.projectId === projId).result)

  def getDatasetsByProject(projId: Int): Future[Seq[DataSetMetaDataSet]] =
    db.run(dsMetaData2.filter(_.projectId === projId).result)

  def getUserProjects(login: String): Future[Seq[UserProjectResponse]] = {
    val join = for {
      (pu, p) <- projectsUsers join projects on (_.projectId === _.id)
      if pu.login === login && p.isActive
    } yield (pu.role, p)

    val userProjects = join
      .result
      .map(_.map(j => UserProjectResponse(Some(j._1), j._2)))

    val generalProject = projects
      .filter(_.id === GENERAL_PROJECT_ID)
      .result
      .headOption
      .map(_.map(UserProjectResponse(None, _)).toSeq)

    db.run(userProjects.zip(generalProject).map(p => p._1 ++ p._2))
  }

  def getUserProjectsDatasets(login: String): Future[Seq[ProjectDatasetResponse]] = {
    val userJoin = for {
      pu <- projectsUsers if pu.login === login
      p <- projects if pu.projectId === p.id && p.isActive
      d <- dsMetaData2 if pu.projectId === d.projectId
    } yield (p, d, pu.role)

    val userProjects = userJoin
      .result
      .map(_.map(j => ProjectDatasetResponse(j._1, j._2, Some(j._3))))

    val genJoin = for {
      p <- projects if p.id === GENERAL_PROJECT_ID
      d <- dsMetaData2 if p.id === d.projectId
    } yield (p, d)

    val genProjects = genJoin
      .result
      .map(_.map(j => ProjectDatasetResponse(j._1, j._2, None)))

    db.run(userProjects.zip(genProjects).map(p => p._1 ++ p._2))
  }

  def userHasProjectRole(login: String, projectId: Int, roles: Set[ProjectUserRole.ProjectUserRole]): Future[Boolean] = {
    val pUser = for {
      pu <- projectsUsers if pu.login === login && pu.projectId === projectId
    } yield pu

    val hasRole = pUser.result.map(_.headOption match {
      case Some(pu) if roles.contains(pu.role) => true
      case _ =>  false
    })

    db.run(hasRole)
  }
}

/**
 * SQL Driven JobEngine datastore Backend
 */
trait JobDataStore extends JobEngineDaoComponent with LazyLogging with DaoFutureUtils{
  this: DalComponent =>

  import CommonModelImplicits._

  final val QUICK_TASK_IDS = Set(JobTypeId("import_dataset"), JobTypeId("merge_dataset"))

  val DEFAULT_MAX_DATASET_LIMIT = 5000

  val resolver: JobResourceResolver
  // This is local queue of the Runnable Job instances. Once they're turned into an EngineJob, and submitted, it
  // should be deleted. This should probably just be stored as a json blob in the database.
  var _runnableJobs: mutable.LinkedHashMap[UUID, RunnableJobWithId]

  /**
   * This is the pbscala engine required interface. The `createJob` method is the preferred method to add new jobs
   * to the job manager queue.
   */
  override def addRunnableJob(runnableJob: RunnableJob): Future[EngineJob] = {
    // this should probably default to the system temp dir
    val path = ""

    val createdAt = JodaDateTime.now()
    val name = s"Runnable Job $runnableJob"
    val comment = s"Job comment for $runnableJob"
    val jobTypeId = runnableJob.job.jobOptions.toJob.jobTypeId.id
    val jsonSettings = "{}"

    val job = EngineJob(-1, runnableJob.job.uuid, name, comment, createdAt, createdAt, AnalysisJobStates.CREATED, jobTypeId, path, jsonSettings, None, None, None)

    val update = (engineJobs returning engineJobs.map(_.id) into ((j, i) => j.copy(id = i)) += job).flatMap { j =>
      val runnableJobWithId = RunnableJobWithId(j.id, runnableJob.job, runnableJob.state)
      _runnableJobs.update(runnableJob.job.uuid, runnableJobWithId)

      val resolvedPath = resolver.resolve(runnableJobWithId).toAbsolutePath.toString
      val jobEvent = JobEvent(
        UUID.randomUUID(),
        j.id,
        AnalysisJobStates.CREATED,
        s"Created job ${j.id} type $jobTypeId with ${runnableJob.job.uuid.toString}",
        JodaDateTime.now())

      DBIO.seq(
        jobEvents += jobEvent,
        engineJobs.filter(_.id === j.id).map(_.path).update(resolvedPath)
      ).map { _ =>
        job.copy(id = j.id, path = resolvedPath)
      }
    }

    db.run(update.transactionally)
  }

  //FIXME(mpkocher)(1-25-2017) Remove this Future[Option[T]] usage
  // it introduces a lot of duplication on the caller side.
  override def getJobByUUID(jobId: UUID): Future[Option[EngineJob]] =
    db.run(engineJobs.filter(_.uuid === jobId).result.headOption)

  override def getJobById(jobId: Int): Future[Option[EngineJob]] =
    db.run(engineJobs.filter(_.id === jobId).result.headOption)

  def getJobByIdAble(ix: IdAble): Future[EngineJob] = {
    val fx = ix match {
      case IntIdAble(i) => getJobById(i)
      case UUIDIdAble(uuid) => getJobByUUID(uuid)
    }
    fx.flatMap(failIfNone(s"Failed to find Job ${ix.toIdString}"))
  }

  def getNextRunnableJob: Future[Either[NoAvailableWorkError, RunnableJob]] = {
    val noWork = NoAvailableWorkError("No Available work to run.")
    _runnableJobs.values.find(_.state == AnalysisJobStates.CREATED) match {
      case Some(job) => {
        _runnableJobs.remove(job.job.uuid)
        getJobById(job.id).map {
          case Some(j) =>
            Right(RunnableJob(job.job, j.state))
          case None => Left(noWork)
        }
      }
      case None => Future(Left(noWork))
    }
  }

  private def getNextRunnableJobByType(jobTypeFilter: JobTypeId => Boolean): Future[Either[NoAvailableWorkError, RunnableJobWithId]] = {
    val noWork = NoAvailableWorkError("No Available work to run.")
    _runnableJobs.values.find((rj) =>
      rj.state == AnalysisJobStates.CREATED &&
      jobTypeFilter(rj.job.jobOptions.toJob.jobTypeId)) match {
      case Some(job) => {
        _runnableJobs.remove(job.job.uuid)
        getJobById(job.id).map {
          case Some(j) =>
            Right(RunnableJobWithId(job.id, job.job, j.state))
          case None => Left(noWork)
        }
      }
      case None => Future(Left(noWork))
    }
  }

  private def filterByQuickJobType(j: JobTypeId): Boolean = QUICK_TASK_IDS contains j
  override def getNextRunnableJobWithId = getNextRunnableJobByType((j: JobTypeId) => !filterByQuickJobType(j))
  def getNextRunnableQuickJobWithId = getNextRunnableJobByType(filterByQuickJobType)

  /**
   * Get all the Job Events associated with a specific job
   */
  override def getJobEventsByJobId(jobId: Int): Future[Seq[JobEvent]] =
    db.run(jobEvents.filter(_.jobId === jobId).result)

  //FIXME(mpkocher)(1-29-2017) This should return an updated EngineJob and be parameterized by an IdAble to avoid duplication
  def updateJobState(
      jobId: Int,
      state: AnalysisJobStates.JobStates,
      message: String): Future[MessageResponse] = {
    logger.info(s"Updating job state of job-id $jobId to $state")
    val now = JodaDateTime.now()
    db.run {
      DBIO.seq(
        engineJobs.filter(_.id === jobId).map(j => (j.state, j.updatedAt)).update(state, now),
        jobEvents += JobEvent(UUID.randomUUID(), jobId, state, message, now)
      ).transactionally
    }.map(_ => MessageResponse(s"Successfully updated job $jobId to $state"))
  }

  //FIXME(mpkocher)(1-29-2017) This should return an updated EngineJob
  override def updateJobStateByUUID(uuid: UUID, state: AnalysisJobStates.JobStates): Future[String] = {
    logger.info(s"attempting db update of job $uuid state to $state")
    val f = db.run(engineJobs
      .filter(_.uuid === uuid)
      .map(j => (j.state, j.updatedAt))
      .update(state, JodaDateTime.now()))
      .map(_ => s"Successfully updated job $uuid to $state")
    f.onComplete {
      case Success(_) => logger.debug(s"Successfully updated job ${uuid.toString} to $state")
      case Failure(ex) => logger.error(s"Unable to update state of job id ${uuid.toString} to state $state Error ${ex.getMessage}")
    }
    f
  }

  //FIXME(mpkocher)(1-29-2017) This should return an updated EngineJob
  def updateJobStateByUUID(
      jobId: UUID,
      state: AnalysisJobStates.JobStates,
      message: String, errorMessage: Option[String] = None): Future[MessageResponse] =
    db.run {
      val now = JodaDateTime.now()
      engineJobs.filter(_.uuid === jobId).result.headOption.flatMap {
        case Some(job) =>
          DBIO.seq(
            engineJobs.filter(_.uuid === jobId).map(j => (j.state, j.updatedAt, j.errorMessage)).update(state, now, errorMessage),
            jobEvents += JobEvent(UUID.randomUUID(), job.id, state, message, now)
          )
        case None =>
          throw new ResourceNotFoundError(s"Unable to find job $jobId. Failed to update job state to $state")
      }.transactionally
    }.map { _ =>
      logger.info(s"Updated job ${jobId.toString} state to $state")
      MessageResponse(s"Successfully updated job $jobId to $state")
    }

  /**
   * This is the new interface will replace the original createJob
   *
   * @param uuid        UUID
   * @param name        Name of job
   * @param description This is really a comment. FIXME
   * @param jobTypeId   String of the job type identifier. This should be consistent with the
   *                    jobTypeId defined in CoreJob
   * @return
   */
  def createJob(
      uuid: UUID,
      name: String,
      description: String,
      jobTypeId: String,
      coreJob: CoreJob,
      entryPoints: Option[Seq[EngineJobEntryPointRecord]] = None,
      jsonSetting: String,
      createdBy: Option[String],
      smrtLinkVersion: Option[String],
      smrtLinkToolsVersion: Option[String]): Future[EngineJob] = {

    // This should really be Option[String]
    val path = ""
    // TODO(smcclellan): Use dependency-injected Clock instance
    val createdAt = JodaDateTime.now()

    val engineJob = EngineJob(-1, uuid, name, description, createdAt, createdAt, AnalysisJobStates.CREATED, jobTypeId, path, jsonSetting, createdBy, smrtLinkVersion, smrtLinkToolsVersion)

    logger.info(s"Creating Job $engineJob")

    val updates = (engineJobs returning engineJobs.map(_.id) into ((j, i) => j.copy(id = i)) += engineJob) flatMap { job =>
      val jobId = job.id
      val rJob = RunnableJobWithId(jobId, coreJob, AnalysisJobStates.CREATED)
      _runnableJobs.update(uuid, rJob)

      val resolvedPath = resolver.resolve(rJob).toAbsolutePath.toString
      val jobEvent = JobEvent(
        UUID.randomUUID(),
        jobId,
        AnalysisJobStates.CREATED,
        s"Created job $jobId type $jobTypeId with ${uuid.toString}",
        JodaDateTime.now())

      DBIO.seq(
        engineJobs.filter(_.id === jobId).map(_.path).update(resolvedPath),
        jobEvents += jobEvent,
        engineJobsDataSets ++= entryPoints.getOrElse(Nil).map(e => EngineJobEntryPoint(jobId, e.datasetUUID, e.datasetType))
      ).map(_ => engineJob.copy(id = jobId, path = resolvedPath))
    }

    db.run(updates.transactionally)
  }

  def addJobEvent(jobEvent: JobEvent): Future[JobEvent] =
    db.run(jobEvents += jobEvent).map(_ => jobEvent)

  def addJobEvents(events: Seq[JobEvent]): Future[Seq[JobEvent]] =
    db.run(jobEvents ++= events).map(_ => events)

  def getJobEvents: Future[Seq[JobEvent]] = db.run(jobEvents.result)

  def addJobTask(jobTask: JobTask): Future[JobTask] = {
    // when pbsmrtpipe has parity with the AnalysisJobStates, JobTask should have state:AnalysisJobState
    val state = AnalysisJobStates.toState(jobTask.state).getOrElse(AnalysisJobStates.UNKNOWN)
    val errorMessage = s"Failed to insert JobEvent from JobTask $jobTask"
    val message = s"Creating task ${jobTask.name} id:${jobTask.taskId} type:${jobTask.taskTypeId} State:${jobTask.state}"

    val jobEvent = JobEvent(
      jobTask.uuid,
      jobTask.jobId,
      state,
      message,
      jobTask.createdAt,
      eventTypeId = JobConstants.EVENT_TYPE_JOB_TASK_STATUS)

    val fx = for {
      _ <- jobTasks += jobTask
      _ <- jobEvents += jobEvent
      task <- jobTasks.filter(_.uuid === jobTask.uuid).result
    } yield task

    db.run(fx.transactionally).map(_.headOption).flatMap(failIfNone(errorMessage))
  }

  /**
    * Update the state of a Job Task and create an JobEvent
    *
    * @param update
    * @return
    */
  def updateJobTask(update: UpdateJobTask): Future[JobTask] = {
    // Need to sync the pbsmrtpipe task states with the allowed JobStates
    val taskState = AnalysisJobStates.toState(update.state).getOrElse(AnalysisJobStates.UNKNOWN)
    val now = JodaDateTime.now()

    val futureFailMessage = s"Unable to find JobTask uuid:${update.uuid} for Job id ${update.jobId}"

    val fx = for {
      _ <- jobTasks.filter(_.uuid === update.uuid).filter(_.jobId === update.jobId ).map((x) => (x.state, x.errorMessage, x.updatedAt)).update((update.state, update.errorMessage, now))
      _ <- jobEvents += JobEvent(UUID.randomUUID(), update.jobId, taskState, update.message, now, JobConstants.EVENT_TYPE_JOB_TASK_STATUS)
      jobTask <- jobTasks.filter(_.uuid === update.uuid).result
    } yield jobTask

    db.run(fx.transactionally).map(_.headOption).flatMap(failIfNone(futureFailMessage))
  }

  /**
    * Get all tasks associated with a specific EngineJob
    *
    * Will fail if the job is not found, or will return an empty
    * list of Tasks if none are found.
    *
    * @param ix Int or UUID of Engine Job
    * @return
    */
  def getJobTasks(ix: IdAble): Future[Seq[JobTask]] = {
    ix match {
      case IntIdAble(i) =>
        db.run(jobTasks.filter(_.jobId === i).result)
      case UUIDIdAble(uuid) =>
        getJobByIdAble(uuid).flatMap(job => db.run(jobTasks.filter(_.jobId === job.id).result))
    }
  }

  def getJobTask(taskId: UUID): Future[JobTask] = {
    val errorMessage = s"Can't find job task $taskId"
    db.run(jobTasks.filter(_.uuid === taskId).result).map(_.headOption).flatMap(failIfNone(errorMessage))
  }

  // TODO(smcclellan): limit is never used. add `.take(limit)`?
  override def getJobs(limit: Int = 100, includeInactive: Boolean = false): Future[Seq[EngineJob]] = {
    if (!includeInactive) db.run(engineJobs.filter(_.isActive).sortBy(_.id.desc).result)
    else db.run(engineJobs.sortBy(_.id.desc).result)
  }

  def getJobsByTypeId(jobTypeId: String, includeInactive: Boolean = false): Future[Seq[EngineJob]] = {
    if (!includeInactive) db.run(engineJobs.filter(j => j.isActive && (j.jobTypeId === jobTypeId)).result)
    else db.run(engineJobs.filter(_.jobTypeId === jobTypeId).result)
  }

  def getJobEntryPoints(jobId: Int): Future[Seq[EngineJobEntryPoint]] =
    db.run(engineJobsDataSets.filter(_.jobId === jobId).result)

  //FIXME(mpkocher)(1-25-2017) Convert to use IdAble to remove code duplication
  def deleteJobById(jobId: Int): Future[EngineJob] = {
    logger.info(s"Setting isActive=false for job-id $jobId")
    val now = JodaDateTime.now()
    db.run(
      for {
        _ <- engineJobs.filter(_.id === jobId).map(j => (j.isActive, j.updatedAt)).update(false, now)
        job <- engineJobs.filter(_.id === jobId).result.headOption
      } yield job).flatMap(failIfNone(s"Unable to Delete job. Unable to find job id $jobId"))
  }

  def deleteJobByUUID(jobId: UUID): Future[EngineJob] = {
    logger.info(s"Attempting to set isActive=false for job-id $jobId")
    val now = JodaDateTime.now()
    db.run(
      for {
        _ <- engineJobs.filter(_.uuid === jobId).map(j => (j.isActive, j.updatedAt)).update(false, now)
        job <- engineJobs.filter(_.uuid === jobId).result.headOption
      } yield job).flatMap(failIfNone(s"Unable to Delete job. Unable to find job $jobId"))
  }

}

/**
 * Model extend DataSet Component to have 'extended' importing of datastore files by file type
 * (e.g., DataSet, Report)
 *
 * Mixin the Job Component because the files depended on the job
 */
trait DataSetStore extends DataStoreComponent with DaoFutureUtils with LazyLogging {
  this: JobDataStore with DalComponent =>

  val DEFAULT_PROJECT_ID = 1
  val DEFAULT_USER_ID = 1

  /**
   * Importing of DataStore File by Job Int Id
   */
  def insertDataStoreFileById(ds: DataStoreFile, jobId: Int): Future[MessageResponse] =
    getJobById(jobId)
        .flatMap(failIfNone(s"Failed to find Job id $jobId for DataStore File ${ds.uniqueId}"))
        .flatMap(job => insertDataStoreByJob(job, ds))


  def insertDataStoreFileByUUID(ds: DataStoreFile, jobId: UUID): Future[MessageResponse] =
    getJobByUUID(jobId)
        .flatMap(failIfNone(s"Failed to find Job id $jobId for DataStore File ${ds.uniqueId}"))
        .flatMap(job => insertDataStoreByJob(job, ds))

  override def addDataStoreFile(ds: DataStoreJobFile): Future[Either[CommonMessages.FailedMessage, CommonMessages.SuccessMessage]] = {
    logger.info(s"adding datastore file for $ds")
    getJobByUUID(ds.jobId).flatMap {
      case Some(engineJob) => insertDataStoreByJob(engineJob, ds.dataStoreFile)
        .map(m => Right(CommonMessages.SuccessMessage(m.message)))
        .recover {
          case NonFatal(e) => Left(CommonMessages.FailedMessage(s"Failed to add datastore file file $ds Error ${e.getMessage}"))
        }
      case None => Future(Left(CommonMessages.FailedMessage(s"Failed to find jobId ${ds.jobId}")))
    }
  }

  /**
   * Generic Importing of DataSet by type and Path to dataset file
   */
  protected def insertDataSet(dataSetMetaType: DataSetMetaType, spath: String, jobId: Int, userId: Int, projectId: Int): Future[MessageResponse] = {
    val path = Paths.get(spath)
    dataSetMetaType match {
      case DataSetMetaTypes.Subread =>
        val dataset = DataSetLoader.loadSubreadSet(path)
        val sds = Converters.convert(dataset, path.toAbsolutePath, userId, jobId, projectId)
        insertSubreadDataSet(sds)
      case DataSetMetaTypes.Reference =>
        val dataset = DataSetLoader.loadReferenceSet(path)
        val sds = Converters.convert(dataset, path.toAbsolutePath, userId, jobId, projectId)
        insertReferenceDataSet(sds)
      case DataSetMetaTypes.GmapReference =>
        val dataset = DataSetLoader.loadGmapReferenceSet(path)
        val sds = Converters.convert(dataset, path.toAbsolutePath, userId, jobId, projectId)
        insertGmapReferenceDataSet(sds)
      case DataSetMetaTypes.HdfSubread =>
        val dataset = DataSetLoader.loadHdfSubreadSet(path)
        val sds = Converters.convert(dataset, path.toAbsolutePath, userId, jobId, projectId)
        insertHdfSubreadDataSet(sds)
      case DataSetMetaTypes.Alignment =>
        val dataset = DataSetLoader.loadAlignmentSet(path)
        val sds = Converters.convert(dataset, path.toAbsolutePath, userId, jobId, projectId)
        insertAlignmentDataSet(sds)
      case DataSetMetaTypes.Barcode =>
        val dataset = DataSetLoader.loadBarcodeSet(path)
        val sds = Converters.convert(dataset, path.toAbsolutePath, userId, jobId, projectId)
        insertBarcodeDataSet(sds)
      case DataSetMetaTypes.CCS =>
        val dataset = DataSetLoader.loadConsensusReadSet(path)
        val sds = Converters.convert(dataset, path.toAbsolutePath, userId, jobId, projectId)
        insertConsensusReadDataSet(sds)
      case DataSetMetaTypes.AlignmentCCS =>
        val dataset = DataSetLoader.loadConsensusAlignmentSet(path)
        val sds = Converters.convert(dataset, path.toAbsolutePath, userId, jobId, projectId)
        insertConsensusAlignmentDataSet(sds)
      case DataSetMetaTypes.Contig =>
        val dataset = DataSetLoader.loadContigSet(path)
        val sds = Converters.convert(dataset, path.toAbsolutePath, userId, jobId, projectId)
        insertContigDataSet(sds)
      case x =>
        val msg = s"Unsupported DataSet type $x. Skipping DataSet Import of $path"
        logger.warn(msg)
        Future(MessageResponse(msg))
    }
  }

  protected def insertDataStoreByJob(engineJob: EngineJob, ds: DataStoreFile): Future[MessageResponse] = {
    logger.info(s"Inserting DataStore File $ds with job id ${engineJob.id}")

    // TODO(smcclellan): Use dependency-injected Clock instance
    val createdAt = JodaDateTime.now()
    val modifiedAt = createdAt
    val importedAt = createdAt

    if (ds.isChunked) {
      Future(MessageResponse(s"Skipping inserting of Chunked DataStoreFile $ds"))
    } else {

      /**
       *  Transaction with conditional (add, insert) so that data isn't duplicated in the database
       */
      def addOptionalInsert(existing: Option[Any]): Future[MessageResponse] = {
        // 1 of 3: add the DataStoreServiceFile, if it isn't already in the DB
        val addDataStoreServiceFile = existing match {
          case Some(_) =>
            DBIO.from(Future(s"Already imported. Skipping inserting of datastore file $ds"))
          case None =>
            logger.info(s"importing datastore file into db $ds")
            val dss = DataStoreServiceFile(ds.uniqueId, ds.fileTypeId, ds.sourceId, ds.fileSize, createdAt, modifiedAt, importedAt, ds.path, engineJob.id, engineJob.uuid, ds.name, ds.description)
            datastoreServiceFiles += dss
        }
        // 2 of 3: insert of the data set, if it is a known/supported file type
        val optionalInsert = DataSetMetaTypes.toDataSetType(ds.fileTypeId) match {
          case Some(typ) =>
            DBIO.from(insertDataSet(typ, ds.path, engineJob.id, DEFAULT_USER_ID, DEFAULT_PROJECT_ID))
          case None =>
            existing match {
              case Some(_) =>
                DBIO.from(Future(MessageResponse(s"Previously somehow imported unsupported DataSet type ${ds.fileTypeId}.")))
              case None =>
                DBIO.from(Future(MessageResponse(s"Unsupported DataSet type ${ds.fileTypeId}. Imported $ds. Skipping extended/detailed importing")))
            }
        }
        // 3 of 3: run the appropriate actions in a transaction
        val fin = for {
          _ <- addDataStoreServiceFile
          oi <- optionalInsert
        } yield oi
        db.run(fin.transactionally)
      }
      
      // This needed queries un-nested due to SQLite limitations -- see #197
      db.run(datastoreServiceFiles.filter(_.uuid === ds.uniqueId).result.headOption).flatMap{
        addOptionalInsert
      }
    }
  }

  def getDataStoreFiles2(ignoreInactive: Boolean = true): Future[Seq[DataStoreServiceFile]] = {
    if (ignoreInactive) db.run(datastoreServiceFiles.filter(_.isActive).result)
    else db.run(datastoreServiceFiles.result)
  }

  def getDataStoreFileByUUID2(uuid: UUID): Future[DataStoreServiceFile] =
    db.run(datastoreServiceFiles.filter(_.uuid === uuid).result.headOption)
        .flatMap(failIfNone(s"Unable to find DataStore File with uuid `$uuid`"))

  def getDataStoreServiceFilesByJobId(i: Int): Future[Seq[DataStoreServiceFile]] =
    db.run(datastoreServiceFiles.filter(_.jobId === i).result)

  def getDataStoreServiceFilesByJobUuid(uuid: UUID): Future[Seq[DataStoreServiceFile]] =
    db.run(datastoreServiceFiles.filter(_.jobUUID === uuid).result)

  def getDataStoreReportFilesByJobId(jobId: Int): Future[Seq[DataStoreReportFile]] =
    db.run {
      datastoreServiceFiles
        .filter(_.jobId === jobId)
        .filter(_.fileTypeId === FileTypes.REPORT.fileTypeId)
        .result
    }.map(_.map((d: DataStoreServiceFile) => DataStoreReportFile(d, d.sourceId.split("-").head)))

  def getDataStoreReportFilesByJobUuid(jobUuid: UUID): Future[Seq[DataStoreReportFile]] =
    db.run {
      datastoreServiceFiles
        .filter(_.jobUUID === jobUuid)
        .filter(_.fileTypeId === FileTypes.REPORT.fileTypeId)
        .result
    }.map(_.map((d: DataStoreServiceFile) => DataStoreReportFile(d, d.sourceId.split("-").head)))

  // Return the contents of the Report
  def getDataStoreReportByUUID(reportUUID: UUID): Future[Option[String]] = {
    val action = datastoreServiceFiles.filter(_.uuid === reportUUID).result.headOption.map {
      case Some(x) =>
        if (Files.exists(Paths.get(x.path))) {
          Option(scala.io.Source.fromFile(x.path).mkString)
        } else {
          logger.error(s"Unable to find report ${x.uuid} path ${x.path}")
          None
        }
      case None => None
    }
    db.run(action)
  }

  private def getDataSetMetaDataSet(uuid: UUID): Future[Option[DataSetMetaDataSet]] =
    db.run(dsMetaData2.filter(_.uuid === uuid).result.headOption)

  // removes a query that seemed like it was potentially nested based on race condition with executor
  private def getDataSetMetaDataSetBlocking(uuid: UUID): Option[DataSetMetaDataSet] =
    Await.result(getDataSetMetaDataSet(uuid), 23456 milliseconds)

  private def insertMetaData(ds: ServiceDataSetMetadata): DBIOAction[Int, NoStream, Effect.Read with Effect.Write] = {
    val createdAt = JodaDateTime.now()
    val modifiedAt = createdAt
    dsMetaData2 returning dsMetaData2.map(_.id) += DataSetMetaDataSet(
      -999, ds.uuid, ds.name, ds.path, createdAt, modifiedAt, ds.numRecords, ds.totalLength, ds.tags, ds.version,
      ds.comments, ds.md5, ds.userId, ds.jobId, ds.projectId, isActive = true)
  }

  type U = slick.profile.FixedSqlAction[Int,slick.dbio.NoStream,slick.dbio.Effect.Write]

  def insertDataSetSafe[T <: ServiceDataSetMetadata](ds: T, dsTypeStr: String,
                         insertFxn: (Int) => U): Future[MessageResponse] = {
    getDataSetMetaDataSetBlocking(ds.uuid) match {
      case Some(_) =>
        val msg = s"$dsTypeStr ${ds.uuid} already imported. Skipping importing of $ds"
        logger.debug(msg)
        Future(MessageResponse(msg))
      case None => db.run {
        insertMetaData(ds).flatMap {
          id => insertFxn(id)
        }.map(_ => {
          val m = s"Successfully entered $dsTypeStr $ds"
          logger.info(m)
          MessageResponse(m)
        })
      }
    }
  }

  def insertReferenceDataSet(ds: ReferenceServiceDataSet): Future[MessageResponse] =
    insertDataSetSafe[ReferenceServiceDataSet](ds, "ReferenceSet",
      (id) => { dsReference2 forceInsert ReferenceServiceSet(id, ds.uuid, ds.ploidy, ds.organism) })

  def insertGmapReferenceDataSet(ds: GmapReferenceServiceDataSet): Future[MessageResponse] = 
    insertDataSetSafe[GmapReferenceServiceDataSet](ds, "GmapReferenceSet",
      (id) => { dsGmapReference2 forceInsert GmapReferenceServiceSet(id, ds.uuid, ds.ploidy, ds.organism) })

  def insertSubreadDataSet(ds: SubreadServiceDataSet): Future[MessageResponse] =
    insertDataSetSafe[SubreadServiceDataSet](ds, "SubreadSet",
      (id) => { dsSubread2 forceInsert SubreadServiceSet(id, ds.uuid,
          "cell-id", ds.metadataContextId, ds.wellSampleName, ds.wellName,
          ds.bioSampleName, ds.cellIndex, ds.instrumentName, ds.instrumentName,
          ds.runName, "instrument-ctr-version") })

  def insertHdfSubreadDataSet(ds: HdfSubreadServiceDataSet): Future[MessageResponse] =
    insertDataSetSafe[HdfSubreadServiceDataSet](ds, "HdfSubreadSet",
      (id) => {
        dsHdfSubread2 forceInsert HdfSubreadServiceSet(id, ds.uuid,
          "cell-id", ds.metadataContextId, ds.wellSampleName, ds.wellName,
          ds.bioSampleName, ds.cellIndex, ds.instrumentName, ds.instrumentName,
          ds.runName, "instrument-ctr-version") })

  def insertAlignmentDataSet(ds: AlignmentServiceDataSet): Future[MessageResponse] =
    insertDataSetSafe[AlignmentServiceDataSet](ds, "AlignmentSet",
      (id) => { dsAlignment2 forceInsert AlignmentServiceSet(id, ds.uuid) })

  def insertConsensusReadDataSet(ds: ConsensusReadServiceDataSet): Future[MessageResponse] =
    insertDataSetSafe[ConsensusReadServiceDataSet](ds, "ConsensusReadSet",
      (id) => { dsCCSread2 forceInsert ConsensusReadServiceSet(id, ds.uuid) })

  def insertConsensusAlignmentDataSet(ds: ConsensusAlignmentServiceDataSet): Future[MessageResponse] =
    insertDataSetSafe[ConsensusAlignmentServiceDataSet](ds, "ConsensusAlignmentSet",
      (id) => { dsCCSAlignment2 forceInsert ConsensusAlignmentServiceSet(id, ds.uuid) })

  def insertBarcodeDataSet(ds: BarcodeServiceDataSet): Future[MessageResponse] =
    insertDataSetSafe[BarcodeServiceDataSet](ds, "BarcodeSet",
      (id) => { dsBarcode2 forceInsert BarcodeServiceSet(id, ds.uuid) })

  def insertContigDataSet(ds: ContigServiceDataSet): Future[MessageResponse] =
    insertDataSetSafe[ContigServiceDataSet](ds, "ContigSet",
      (id) => { dsContig2 forceInsert ContigServiceSet(id, ds.uuid) })

  def getDataSetTypeById(typeId: String): Future[ServiceDataSetMetaType] =
    db.run(datasetMetaTypes.filter(_.id === typeId).result.headOption)
        .flatMap(failIfNone(s"Unable to find dataSet type `$typeId`"))

  def getDataSetTypes: Future[Seq[ServiceDataSetMetaType]] = db.run(datasetMetaTypes.result)

  // Get All DataSets mixed in type. Only metadata
  def getDataSetByUUID(id: UUID): Future[DataSetMetaDataSet] =
    db.run(datasetMetaTypeByUUID(id).result.headOption).
        flatMap(failIfNone(s"Unable to find dataSet with UUID `$id`"))

  def getDataSetById(id: Int): Future[DataSetMetaDataSet] =
    db.run(datasetMetaTypeById(id).result.headOption)
        .flatMap(failIfNone(s"Unable to find dataSet with id `$id`"))

  def deleteDataSetById(id: Int, setIsActive: Boolean = false): Future[MessageResponse] = {
    val now = JodaDateTime.now()
    db.run(dsMetaData2.filter(_.id === id).map(d => (d.isActive, d.updatedAt)).update(setIsActive, now)).map(_ => MessageResponse(s"Successfully set isActive=$setIsActive for dataset $id"))
  }

  def deleteDataSetByUUID(id: UUID, setIsActive: Boolean = false): Future[MessageResponse] = {
    val now = JodaDateTime.now()
    db.run(dsMetaData2.filter(_.uuid === id).map(d => (d.isActive, d.updatedAt)).update(setIsActive, now)).map(_ => MessageResponse(s"Successfully set isActive=$setIsActive for dataset $id"))
  }

  def datasetMetaTypeById(id: Int) = dsMetaData2.filter(_.id === id)

  def datasetMetaTypeByUUID(id: UUID) = dsMetaData2.filter(_.uuid === id)

  def getDataSetJobsByUUID(id: UUID): Future[Seq[EngineJob]] = {
    db.run {
      val q = engineJobsDataSets.filter(_.datasetUUID === id) join engineJobs.filter(_.isActive) on (_.jobId === _.id)
      q.result.map(_.map(x => x._2))
    }
  }

  // util for converting to the old model
  def toSds(t1: DataSetMetaDataSet, t2: SubreadServiceSet): SubreadServiceDataSet =
    SubreadServiceDataSet(t1.id, t1.uuid, t1.name, t1.path, t1.createdAt, t1.updatedAt, t1.numRecords, t1.totalLength,
      t1.version, t1.comments, t1.tags, t1.md5, t2.instrumentName, t2.metadataContextId, t2.wellSampleName, t2.wellName, t2.bioSampleName, t2.cellIndex, t2.runName, t1.userId, t1.jobId, t1.projectId)

  // FIXME. REALLY, REALLY need to generalize this.
  def getSubreadDataSetById(id: Int): Future[SubreadServiceDataSet] = {
    db.run {
      val q = datasetMetaTypeById(id) join dsSubread2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toSds(x._1, x._2)))
    }.flatMap(failIfNone(s"Unable to find SubreadSet with id `$id`"))
  }

  // This might be wrapped in a Try to fail the future downstream with a better HTTP error code
  private def subreadToDetails(ds: SubreadServiceDataSet): String =
    DataSetJsonUtils.subreadSetToJson(DataSetLoader.loadSubreadSet(Paths.get(ds.path)))


  def getSubreadDataSetDetailsById(id: Int): Future[String] =
    getSubreadDataSetById(id).map(subreadToDetails)

  def getSubreadDataSetDetailsByUUID(uuid: UUID): Future[String] =
    getSubreadDataSetByUUID(uuid).map(subreadToDetails)

  def getSubreadDataSetByUUID(id: UUID): Future[SubreadServiceDataSet] =
    db.run {
      val q = datasetMetaTypeByUUID(id) join dsSubread2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toSds(x._1, x._2)))
    }.flatMap(failIfNone(s"Unable to find SubreadSet with UUID `$id`"))

  def getSubreadDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT, includeInactive: Boolean = false, projectId: Option[Int] = None): Future[Seq[SubreadServiceDataSet]] =
    db.run {
      var q = dsMetaData2 join dsSubread2 on (_.id === _.id)
      if (!includeInactive) q = q.filter(_._1.isActive)
      if (projectId.isDefined) q = q.filter(_._1.projectId === projectId.get)
      q.result.map(_.map(x => toSds(x._1, x._2)))
    }

  // conversion util for keeping the old interface
  def toR(t1: DataSetMetaDataSet, t2: ReferenceServiceSet): ReferenceServiceDataSet =
    ReferenceServiceDataSet(t1.id, t1.uuid, t1.name, t1.path, t1.createdAt, t1.updatedAt, t1.numRecords, t1.totalLength,
      t1.version, t1.comments, t1.tags, t1.md5, t1.userId, t1.jobId, t1.projectId, t2.ploidy, t2.organism)

  def getReferenceDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT, includeInactive: Boolean = false, projectId: Option[Int] = None): Future[Seq[ReferenceServiceDataSet]] =
    db.run {
      var q = dsMetaData2 join dsReference2 on (_.id === _.id)
      if (!includeInactive) q = q.filter(_._1.isActive)
      if (projectId.isDefined) q = q.filter(_._1.projectId === projectId.get)
      q.result.map(_.map(x => toR(x._1, x._2)))
    }

  def getReferenceDataSetById(id: Int): Future[ReferenceServiceDataSet] =
    db.run {
      val q = datasetMetaTypeById(id) join dsReference2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toR(x._1, x._2)))
    }.flatMap(failIfNone(s"Unable to find ReferenceSet with id `$id`"))

  private def referenceToDetails(ds: ReferenceServiceDataSet): String =
    DataSetJsonUtils.referenceSetToJson(DataSetLoader.loadReferenceSet(Paths.get(ds.path)))

  def getReferenceDataSetDetailsById(id: Int): Future[String] =
    getReferenceDataSetById(id).map(referenceToDetails)

  def getReferenceDataSetDetailsByUUID(uuid: UUID): Future[String] =
    getReferenceDataSetByUUID(uuid).map(referenceToDetails)

  def getReferenceDataSetByUUID(id: UUID): Future[ReferenceServiceDataSet] =
    db.run {
      val q = datasetMetaTypeByUUID(id) join dsReference2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toR(x._1, x._2)))
    }.flatMap(failIfNone(s"Unable to find ReferenceSet with uuid `$id`"))

  def toGmapR(t1: DataSetMetaDataSet, t2: GmapReferenceServiceSet): GmapReferenceServiceDataSet =
    GmapReferenceServiceDataSet(t1.id, t1.uuid, t1.name, t1.path, t1.createdAt, t1.updatedAt, t1.numRecords, t1.totalLength,
      t1.version, t1.comments, t1.tags, t1.md5, t1.userId, t1.jobId, t1.projectId, t2.ploidy, t2.organism)

  def getGmapReferenceDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT, includeInactive: Boolean = false, projectId: Option[Int] = None): Future[Seq[GmapReferenceServiceDataSet]] =
    db.run {
      var q = dsMetaData2 join dsGmapReference2 on (_.id === _.id)
      if (!includeInactive) q = q.filter(_._1.isActive)
      if (projectId.isDefined) q = q.filter(_._1.projectId === projectId.get)
      q.result.map(_.map(x => toGmapR(x._1, x._2)))
    }

  def getGmapReferenceDataSetById(id: Int): Future[GmapReferenceServiceDataSet] =
    db.run {
      val q = datasetMetaTypeById(id) join dsGmapReference2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toGmapR(x._1, x._2)))
    }.flatMap(failIfNone(s"Unable to find GmapReferenceSet with uuid `$id`"))

  private def gmapReferenceToDetails(ds: GmapReferenceServiceDataSet): String =
    DataSetJsonUtils.gmapReferenceSetToJson(DataSetLoader.loadGmapReferenceSet(Paths.get(ds.path)))

  def getGmapReferenceDataSetDetailsById(id: Int): Future[String] =
    getGmapReferenceDataSetById(id).map(gmapReferenceToDetails)

  def getGmapReferenceDataSetDetailsByUUID(uuid: UUID): Future[String] =
    getGmapReferenceDataSetByUUID(uuid).map(gmapReferenceToDetails)

  def getGmapReferenceDataSetByUUID(id: UUID): Future[GmapReferenceServiceDataSet] =
    db.run {
      val q = datasetMetaTypeByUUID(id) join dsGmapReference2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toGmapR(x._1, x._2)))
    }.flatMap(failIfNone(s"Unable to find GmapReferenceSet with uuid `$id`"))

  def getHdfDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT, includeInactive: Boolean = false, projectId: Option[Int] = None): Future[Seq[HdfSubreadServiceDataSet]] =
    db.run {
      var q = dsMetaData2 join dsHdfSubread2 on (_.id === _.id)
      if (!includeInactive) q = q.filter(_._1.isActive)
      if (projectId.isDefined) q = q.filter(_._1.projectId === projectId.get)
      q.result.map(_.map(x => toHds(x._1, x._2)))
    }

  def toHds(t1: DataSetMetaDataSet, t2: HdfSubreadServiceSet): HdfSubreadServiceDataSet =
    HdfSubreadServiceDataSet(t1.id, t1.uuid, t1.name, t1.path, t1.createdAt, t1.updatedAt, t1.numRecords, t1.totalLength,
      t1.version, t1.comments, t1.tags, t1.md5, t2.instrumentName, t2.metadataContextId, t2.wellSampleName, t2.wellName, t2.bioSampleName, t2.cellIndex, t2.runName, t1.userId, t1.jobId, t1.projectId)

  def getHdfDataSetById(id: Int): Future[HdfSubreadServiceDataSet] =
    db.run {
      val q = datasetMetaTypeById(id) join dsHdfSubread2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toHds(x._1, x._2)))
    }.flatMap(failIfNone(s"Unable to find HdfSubreadSet with id `$id`"))

  private def hdfsubreadToDetails(ds: HdfSubreadServiceDataSet): String =
    DataSetJsonUtils.hdfSubreadSetToJson(DataSetLoader.loadHdfSubreadSet(Paths.get(ds.path)))

  def getHdfDataSetDetailsById(id: Int): Future[String] =
    getHdfDataSetById(id).map(hdfsubreadToDetails)

  def getHdfDataSetDetailsByUUID(uuid: UUID): Future[String] =
    getHdfDataSetByUUID(uuid).map(hdfsubreadToDetails)

  def getHdfDataSetByUUID(id: UUID): Future[HdfSubreadServiceDataSet] =
    db.run {
      val q = datasetMetaTypeByUUID(id) join dsHdfSubread2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toHds(x._1, x._2)))
    }.flatMap(failIfNone(s"Unable to find HdfSubreadSet with uuid `$id`"))

  def toA(t1: DataSetMetaDataSet) = AlignmentServiceDataSet(
      t1.id,
      t1.uuid,
      t1.name,
      t1.path,
      t1.createdAt,
      t1.updatedAt,
      t1.numRecords,
      t1.totalLength,
      t1.version,
      t1.comments,
      t1.tags,
      t1.md5,
      t1.userId,
      t1.jobId,
      t1.projectId)

  def getAlignmentDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT, includeInactive: Boolean = false, projectId: Option[Int] = None): Future[Seq[AlignmentServiceDataSet]] =
    db.run {
      var q = dsMetaData2 join dsAlignment2 on (_.id === _.id)
      if (!includeInactive) q = q.filter(_._1.isActive)
      if (projectId.isDefined) q = q.filter(_._1.projectId === projectId.get)
      q.result.map(_.map(x => toA(x._1)))
    }

  def getAlignmentDataSetById(id: Int): Future[AlignmentServiceDataSet] =
    db.run {
      val q = datasetMetaTypeById(id) join dsAlignment2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toA(x._1)))
    }.flatMap(failIfNone(s"Unable to find AlignmentSet with id `$id`"))

  def getAlignmentDataSetByUUID(id: UUID): Future[AlignmentServiceDataSet] =
    db.run {
      val q = datasetMetaTypeByUUID(id) join dsAlignment2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toA(x._1)))
    }.flatMap(failIfNone(s"Unable to find AlignmentSet with id `$id`"))

  private def alignmentSetToDetails(ds: AlignmentServiceDataSet): String = {
    DataSetJsonUtils.alignmentSetToJson(DataSetLoader.loadAlignmentSet(Paths.get(ds.path)))
  }

  def getAlignmentDataSetDetailsById(id: Int): Future[String] =
    getAlignmentDataSetById(id).map(alignmentSetToDetails)

  def getAlignmentDataSetDetailsByUUID(uuid: UUID): Future[String] =
    getAlignmentDataSetByUUID(uuid).map(alignmentSetToDetails)

  /*--- CONSENSUS READS ---*/

  def toCCSread(t1: DataSetMetaDataSet) =
    ConsensusReadServiceDataSet(t1.id, t1.uuid, t1.name, t1.path, t1.createdAt, t1.updatedAt, t1.numRecords, t1.totalLength,
      t1.version, t1.comments, t1.tags, t1.md5, t1.userId, t1.jobId, t1.projectId)

  // TODO(smcclellan): limit is never uesed. add `.take(limit)`?
  def getConsensusReadDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT, includeInactive: Boolean = false, projectId: Option[Int] = None): Future[Seq[ConsensusReadServiceDataSet]] =
    db.run {
      var q = dsMetaData2 join dsCCSread2 on (_.id === _.id)
      if (!includeInactive) q = q.filter(_._1.isActive)
      if (projectId.isDefined) q = q.filter(_._1.projectId === projectId.get)
      q.result.map(_.map(x => toCCSread(x._1)))
    }

  def getConsensusReadDataSetById(id: Int): Future[ConsensusReadServiceDataSet] =
    db.run {
      val q = datasetMetaTypeById(id) join dsCCSread2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toCCSread(x._1)))
    }.flatMap(failIfNone(s"Unable to find ConsensusReadSet with id `$id`"))

  def getConsensusReadDataSetByUUID(id: UUID): Future[ConsensusReadServiceDataSet] =
    db.run {
      val q = datasetMetaTypeByUUID(id) join dsCCSread2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toCCSread(x._1)))
    }.flatMap(failIfNone(s"Unable to find ConsensusReadSet with uuid `$id`"))

  private def consensusReadSetToDetails(ds: ConsensusReadServiceDataSet): String =
    DataSetJsonUtils.consensusSetToJson(DataSetLoader.loadConsensusReadSet(Paths.get(ds.path)))

  def getConsensusReadDataSetDetailsById(id: Int): Future[String] =
    getConsensusReadDataSetById(id).map(consensusReadSetToDetails)

  def getConsensusReadDataSetDetailsByUUID(uuid: UUID): Future[String] =
    getConsensusReadDataSetByUUID(uuid).map(consensusReadSetToDetails)

  /*--- CONSENSUS ALIGNMENTS ---*/

  def toCCSA(t1: DataSetMetaDataSet) = ConsensusAlignmentServiceDataSet(
      t1.id,
      t1.uuid,
      t1.name,
      t1.path,
      t1.createdAt,
      t1.updatedAt,
      t1.numRecords,
      t1.totalLength,
      t1.version,
      t1.comments,
      t1.tags,
      t1.md5,
      t1.userId,
      t1.jobId,
      t1.projectId)

  def getConsensusAlignmentDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT, includeInactive: Boolean = false, projectId: Option[Int] = None): Future[Seq[ConsensusAlignmentServiceDataSet]] =
    db.run {
      var q = dsMetaData2 join dsCCSAlignment2 on (_.id === _.id)
      if (!includeInactive) q = q.filter(_._1.isActive)
      if (projectId.isDefined) q = q.filter(_._1.projectId === projectId.get)
      q.result.map(_.map(x => toCCSA(x._1)))
    }

  def getConsensusAlignmentDataSetById(id: Int): Future[ConsensusAlignmentServiceDataSet] =
    db.run {
      val q = datasetMetaTypeById(id) join dsCCSAlignment2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toCCSA(x._1)))
    }.flatMap(failIfNone(s"Unable to find ConsensusAlignmentSet with uuid `$id`"))

  def getConsensusAlignmentDataSetByUUID(id: UUID): Future[ConsensusAlignmentServiceDataSet] =
    db.run {
      val q = datasetMetaTypeByUUID(id) join dsCCSAlignment2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toCCSA(x._1)))
    }.flatMap(failIfNone(s"Unable to find ConsensusAlignmentSet with uuid `$id`"))

  private def consensusAlignmentSetToDetails(ds: ConsensusAlignmentServiceDataSet): String =
    DataSetJsonUtils.consensusAlignmentSetToJson(DataSetLoader.loadConsensusAlignmentSet(Paths.get(ds.path)))

  def getConsensusAlignmentDataSetDetailsById(id: Int): Future[String] =
    getConsensusAlignmentDataSetById(id).map(consensusAlignmentSetToDetails)

  def getConsensusAlignmentDataSetDetailsByUUID(uuid: UUID): Future[String] =
    getConsensusAlignmentDataSetByUUID(uuid).map(consensusAlignmentSetToDetails)

  /*--- BARCODES ---*/

  def toB(t1: DataSetMetaDataSet) = BarcodeServiceDataSet(
      t1.id,
      t1.uuid,
      t1.name,
      t1.path,
      t1.createdAt,
      t1.updatedAt,
      t1.numRecords,
      t1.totalLength,
      t1.version,
      t1.comments,
      t1.tags,
      t1.md5,
      t1.userId,
      t1.jobId,
      t1.projectId)

  def getBarcodeDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT, includeInactive: Boolean = false, projectId: Option[Int] = None): Future[Seq[BarcodeServiceDataSet]] =
    db.run {
      var q = dsMetaData2 join dsBarcode2 on (_.id === _.id)
      if (!includeInactive) q = q.filter(_._1.isActive)
      if (projectId.isDefined) q = q.filter(_._1.projectId === projectId.get)
      q.result.map(_.map(x => toB(x._1)))
    }

  def getBarcodeDataSetById(id: Int): Future[BarcodeServiceDataSet] =
    db.run {
      val q = datasetMetaTypeById(id) join dsBarcode2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toB(x._1)))
    }.flatMap(failIfNone(s"Unable to find BarcodeSet with id `$id`"))

  def getBarcodeDataSetByUUID(id: UUID): Future[BarcodeServiceDataSet] =
    db.run {
      val q = datasetMetaTypeByUUID(id) join dsBarcode2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toB(x._1)))
    }.flatMap(failIfNone(s"Unable to find BarcodeSet with uuid `$id`"))

  private def barcodeSetToDetails(ds: BarcodeServiceDataSet): String =
    DataSetJsonUtils.barcodeSetToJson(DataSetLoader.loadBarcodeSet(Paths.get(ds.path)))

  def getBarcodeDataSetDetailsById(id: Int): Future[String] =
    getBarcodeDataSetById(id).map(barcodeSetToDetails)

  def getBarcodeDataSetDetailsByUUID(uuid: UUID): Future[String] =
    getBarcodeDataSetByUUID(uuid).map(barcodeSetToDetails)

  /*--- CONTIGS ---*/

  def toCtg(t1: DataSetMetaDataSet) = ContigServiceDataSet(
      t1.id,
      t1.uuid,
      t1.name,
      t1.path,
      t1.createdAt,
      t1.updatedAt,
      t1.numRecords,
      t1.totalLength,
      t1.version,
      t1.comments,
      t1.tags,
      t1.md5,
      t1.userId,
      t1.jobId,
      t1.projectId)

  def getContigDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT, includeInactive: Boolean = false, projectId: Option[Int] = None): Future[Seq[ContigServiceDataSet]] =
    db.run {
      var q = dsMetaData2 join dsContig2 on (_.id === _.id)
      if (!includeInactive) q = q.filter(_._1.isActive)
      if (projectId.isDefined) q = q.filter(_._1.projectId === projectId.get)
      q.result.map(_.map(x => toCtg(x._1)))
  }

  def getContigDataSetById(id: Int): Future[ContigServiceDataSet] =
    db.run {
      val q = datasetMetaTypeById(id) join dsContig2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toCtg(x._1)))
    }.flatMap(failIfNone(s"Unable to find ContigSet with id `$id`"))

  def getContigDataSetByUUID(id: UUID): Future[ContigServiceDataSet] =
    db.run {
      val q = datasetMetaTypeByUUID(id) join dsContig2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toCtg(x._1)))
    }.flatMap(failIfNone(s"Unable to find ContigSet with uuid `$id`"))

  private def contigSetToDetails(ds: ContigServiceDataSet): String =
    DataSetJsonUtils.contigSetToJson(DataSetLoader.loadContigSet(Paths.get(ds.path)))

  def getContigDataSetDetailsById(id: Int): Future[String] =
    getContigDataSetById(id).map(contigSetToDetails)

  def getContigDataSetDetailsByUUID(uuid: UUID): Future[String] =
    getContigDataSetByUUID(uuid).map(contigSetToDetails)

  /*--- DATASTORE ---*/

  def toDataStoreJobFile(x: DataStoreServiceFile) =
    // This is has the wrong job uuid
    DataStoreJobFile(x.uuid, DataStoreFile(x.uuid, x.sourceId, x.fileTypeId, x.fileSize, x.createdAt, x.modifiedAt, x.path, name=x.name, description=x.description))

  def getDataStoreFilesByJobId(i: Int): Future[Seq[DataStoreJobFile]] =
    db.run(datastoreServiceFiles.filter(_.jobId === i).result.map(_.map(toDataStoreJobFile)))

  // Need to clean all this all up. There's inconsistencies all over the place.
  override def getDataStoreFiles(ignoreInactive: Boolean = true): Future[Seq[DataStoreJobFile]] = {
    if (ignoreInactive) db.run(datastoreServiceFiles.filter(_.isActive).result.map(_.map(toDataStoreJobFile)))
    else db.run(datastoreServiceFiles.result.map(_.map(toDataStoreJobFile)))
  }

  //FIXME(mpkocher)(1-27-2017) This needs to migrated to Future[T]
  override def getDataStoreFileByUUID(uuid: UUID): Future[Option[DataStoreJobFile]] =
    db.run(datastoreServiceFiles.filter(_.uuid === uuid).result.headOption.map(_.map(toDataStoreJobFile)))

  def deleteDataStoreFile(id: UUID, setIsActive: Boolean = false): Future[MessageResponse] = {
    val now = JodaDateTime.now()
    db.run(datastoreServiceFiles.filter(_.uuid === id).map(f => (f.isActive, f.modifiedAt)).update(setIsActive, now)).map(_ => MessageResponse(s"Successfully set datastore file $id to isActive=$setIsActive"))
  }

  def deleteDataStoreJobFile(id: UUID): Future[MessageResponse] = {
    def addOptionalDelete(ds: Option[DataStoreServiceFile]): Future[MessageResponse] = {
      // 1 of 3: delete the DataStoreServiceFile, if it isn't already in the DB
      val deleteDsFile = ds
          .map(dsFile => DBIO.from(deleteDataStoreFile(id)))
          .getOrElse(DBIO.from(Future(MessageResponse(s"No datastore file with ID $id found"))))

      // 2 of 3: insert of the data set, if it is a known/supported file type
      val optionalDelete = ds.map { dsFile =>
          DataSetMetaTypes.toDataSetType(dsFile.fileTypeId)
              .map(_ => DBIO.from(deleteDataSetByUUID(dsFile.uuid)))
              .getOrElse(DBIO.from(Future(MessageResponse(s"File type ${dsFile.fileTypeId} is not a dataset, so no metadata to delete."))))
        }.getOrElse(DBIO.from(Future(MessageResponse(s"No datastore file, so no dataset metadata to delete"))))

      // 3 of 3: run the appropriate actions in a transaction
      val fin = for {
          _ <- deleteDsFile
          od <- optionalDelete
      } yield od
      db.run(fin.transactionally)
    }

    // This needed queries un-nested due to SQLite limitations -- see #197
    db.run(datastoreServiceFiles.filter(_.uuid === id).result.headOption)
        .flatMap(addOptionalDelete)
  }

  override def getDataStoreFilesByJobUUID(uuid: UUID): Future[Seq[DataStoreJobFile]] =
    db.run {
      val q = for {
        engineJob <- engineJobs.filter(_.uuid === uuid)
        dsFiles <- datastoreServiceFiles.filter(_.jobId === engineJob.id)
      } yield dsFiles
      q.result.map(_.map(toDataStoreJobFile))
    }

  def getJobChildrenByUUID(jobId: UUID): Future[Seq[EngineJob]] = {
    val jobDsJoin = for {
      j <- engineJobs if j.uuid === jobId
      d <- datastoreServiceFiles if d.jobId === j.id
      e <- engineJobsDataSets if e.datasetUUID === d.uuid
      c <- engineJobs if ((c.id === e.jobId) && c.isActive)
    } yield (d, c)
    db.run(jobDsJoin.result).map(_.filter(_._1.fileExists).map(_._2))
  }

  def getJobChildrenById(jobId: Int): Future[Seq[EngineJob]] = {
    val jobDsJoin = for {
      d <- datastoreServiceFiles if d.jobId === jobId
      e <- engineJobsDataSets if e.datasetUUID === d.uuid
      c <- engineJobs if ((c.id === e.jobId) && c.isActive)
    } yield (d, c)
    db.run(jobDsJoin.result).map(_.filter(_._1.fileExists).map(_._2))
  }

  def getSystemSummary(header: String = "System Summary"): Future[String] = {
    for {
      ssets <- db.run((dsMetaData2 join dsSubread2 on (_.id === _.id)).length.result)
      rsets <- db.run((dsMetaData2 join dsReference2 on (_.id === _.id)).length.result)
      asets <- db.run((dsMetaData2 join dsAlignment2 on (_.id === _.id)).length.result)
      jobCounts <- db.run(engineJobs.groupBy(x => (x.jobTypeId, x.state)).map({
        case ((jobType, state), list) => (jobType, state, list.length)
      }).result)
      jobEvents <- db.run(jobEvents.length.result)
      dsFiles <- db.run(datastoreServiceFiles.length.result)
      entryPoints <- db.run(engineJobsDataSets.length.result)
    } yield
      s"""
         |$header
         |--------
         |DataSets
         |--------
         |nsubreads            : $ssets
         |alignments           : $asets
         |references           : $rsets
         |--------
         |Jobs
         |--------
         | ${jobCounts.map(x => f"${x._1}%15s  ${x._2}%10s  ${x._3}%6d").mkString("\n         | ")}
         |--------
         |Total JobEvents      : $jobEvents
         |Total entryPoints    : $entryPoints
         |Total DataStoreFiles : $dsFiles
       """.stripMargin
  }

  def addEulaAcceptance(user: String,
                        smrtlinkVersion: String,
                        enableInstallMetrics: Boolean,
                        enableJobMetrics: Boolean): Future[EulaRecord] = {
    val now = JodaDateTime.now()
    // FIXME this should probably move somewhere central
    val osVersion = if (Files.exists(Paths.get("/proc/version"))) {
      scala.io.Source.fromFile("/proc/version").mkString
    } else {
      s"${SystemUtils.OS_NAME}; ${SystemUtils.OS_ARCH}; ${SystemUtils.OS_VERSION}"
    }
    logger.info(s"OS version: $osVersion")
    val rec = EulaRecord(user, now, smrtlinkVersion, osVersion, enableInstallMetrics, enableJobMetrics)
    db.run(eulas += rec).map(_ => rec)
  }

  def getEulas: Future[Seq[EulaRecord]] = db.run(eulas.result)

  def getEulaByVersion(version: String): Future[EulaRecord] =
    db.run(eulas.filter(_.smrtlinkVersion === version).result.headOption)
        .flatMap(failIfNone(s"Unable to find Eula version `$version`"))

  def removeEula(version: String): Future[Int] =
    db.run(eulas.filter(_.smrtlinkVersion === version).delete)
}

class JobsDao(val db: Database, engineConfig: EngineConfig, val resolver: JobResourceResolver) extends JobEngineDataStore
with DalComponent
with SmrtLinkConstants
with ProjectDataStore
with JobDataStore
with DataSetStore {

  import JobModels._

  var _runnableJobs = mutable.LinkedHashMap[UUID, RunnableJobWithId]()
}

trait JobsDaoProvider {
  this: DalProvider with SmrtLinkConfigProvider =>

  val jobsDao: Singleton[JobsDao] = Singleton(() => new JobsDao(db(), jobEngineConfig(), jobResolver()))
}
