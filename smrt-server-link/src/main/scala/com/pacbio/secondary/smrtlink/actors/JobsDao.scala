package com.pacbio.secondary.smrtlink.actors

import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import com.google.common.annotations.VisibleForTesting
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.{
  ResourceNotFoundError,
  UnprocessableEntityError
}
import com.pacbio.common.models.CommonModelImplicits
import com.pacbio.secondary.smrtlink.analysis.constants.FileTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetMetaTypes.DataSetMetaType
import com.pacbio.secondary.smrtlink.analysis.datasets.io.{
  DataSetJsonUtils,
  DataSetLoader
}
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.analysis.jobs._
import com.pacbio.secondary.smrtlink.analysis.reports.ReportUtils
import com.pacbio.secondary.smrtlink.SmrtLinkConstants
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.database.TableModels._
import com.pacbio.secondary.smrtlink.models.{ServiceDataSetMetadata, _}
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime => JodaDateTime}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, blocking}
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.collection.mutable
import slick.sql.FixedSqlAction
import slick.jdbc.PostgresProfile.api._
import java.sql.SQLException

import akka.actor.ActorRef
import com.pacbio.common.models.CommonModels.{IdAble, IntIdAble, UUIDIdAble}
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.analysis.configloaders.ConfigLoader
import com.pacbio.secondary.smrtlink.analysis.datasets.io.ImplicitDataSetLoader.BarcodeSetLoader
import com.pacbio.secondary.smrtlink.database.{
  SmrtLinkDatabaseConfig => SmrtLinkDbConfig
}
import com.pacbio.secondary.smrtlink.jobtypes.{
  MultiAnalysisJobOptions,
  PbsmrtpipeJobOptions
}
import com.pacbio.secondary.smrtlink.jsonprotocols.{
  ServiceJobTypeJsonProtocols,
  SmrtLinkJsonProtocols
}
import com.pacbio.secondary.smrtlink.models.QueryOperators._
import com.pacificbiosciences.pacbiobasedatamodel.SupportedAcquisitionStates
import com.pacificbiosciences.pacbiodatasets._
import org.apache.commons.io.FileUtils
import org.postgresql.util.PSQLException
import spray.json.JsObject
import spray.json._

trait DalProvider {
  val db: Singleton[Database]
  val dbConfig: SmrtLinkDbConfig
  // this is duplicated for the cake vs provider model
  val dbConfigSingleton: Singleton[SmrtLinkDbConfig] = Singleton(
    () => dbConfig)
}

trait DbConfigLoader extends ConfigLoader {

  /**
    * Database config to be used within the specs
    *
    * There's a bit of duplication here because the interface for flyway requires a PG Datasource
    * while slick requires a Database.forConfig model.
    * Provided, the root key doesn't change (e.g., smrtflow.test-db), this should be
    * too much of a problem.
    *
    */
  lazy final val dbConfig: SmrtLinkDbConfig = {
    val dbName = conf.getString("smrtflow.db.properties.databaseName")
    val user = conf.getString("smrtflow.db.properties.user")
    val password = conf.getString("smrtflow.db.properties.password")
    val port = conf.getInt("smrtflow.db.properties.portNumber")
    val server = conf.getString("smrtflow.db.properties.serverName")
    val maxConnections = conf.getInt("smrtflow.db.numThreads")

    SmrtLinkDbConfig(dbName, user, password, server, port, maxConnections)
  }
}

trait SmrtLinkDalProvider extends DalProvider with DbConfigLoader {

  override val db: Singleton[Database] =
    Singleton(() => Database.forConfig("smrtflow.db"))
}

@VisibleForTesting
trait SmrtLinkTestDalProvider extends DalProvider with ConfigLoader {

  /**
    * This Single configuration will be used in all the specs.
    *
    * See comments above about duplication.
    */
  lazy final val dbConfig: SmrtLinkDbConfig = {
    val dbName = conf.getString("smrtflow.test-db.properties.databaseName")
    val user = conf.getString("smrtflow.test-db.properties.user")
    val password = conf.getString("smrtflow.test-db.properties.password")
    val port = conf.getInt("smrtflow.test-db.properties.portNumber")
    val server = conf.getString("smrtflow.test-db.properties.serverName")
    val maxConnections = conf.getInt("smrtflow.test-db.numThreads")

    SmrtLinkDbConfig(dbName, user, password, server, port, maxConnections)
  }

  override val db: Singleton[Database] = Singleton(() => {
    dbConfig.toDatabase
  })
}

/**
  * SQL Datastore backend configuration and db connection
  */
trait DalComponent extends LazyLogging {
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
    case Some(value) => Future.successful(value)
    case _ => Future.failed(ResourceNotFoundError(message))
  }

  /**
    * Run futures in Batches.
    *
    * This needs to be configurable with an explicit ExectionContext.
    *
    * @param maxItems Max number of items in block to be run
    * @param items    Items to process
    * @param f        Function to compute
    * @param results  Results of Future
    * @return
    */
  def runBatch[A, B](maxItems: Int,
                     items: Seq[A],
                     f: A => Future[B],
                     results: Seq[B] = Seq.empty[B]): Future[Seq[B]] = {
    items match {
      case Nil => Future.successful(results)
      case values if values.size > maxItems =>
        for {
          r1 <- Future.traverse(items.take(maxItems))(f)
          r2 <- runBatch(maxItems, items.drop(maxItems), f, results ++ r1)
        } yield r2
      case values =>
        Future.traverse(items)(f).map(r => results ++ r)
    }
  }
}

trait ProjectDataStore extends LazyLogging {
  this: DalComponent with SmrtLinkConstants with DaoFutureUtils =>

  def getProjects(limit: Int = 1000): Future[Seq[Project]] =
    db.run(projects.filter(_.isActive).take(limit).result)

  def getProjectByName(name: String): Future[Project] = {
    val q = projects.filter(_.name === name)

    db.run(q.result.headOption)
      .flatMap(failIfNone(s"Unable to find project '$name'"))
  }

  def getProjectById(projId: Int): Future[Option[Project]] =
    db.run(projects.filter(_.id === projId).result.headOption)

  def createProject(projReq: ProjectRequest): Future[Project] = {
    val now = JodaDateTime.now()
    val proj = Project(-99,
                       projReq.name,
                       projReq.description,
                       ProjectState.CREATED,
                       now,
                       now,
                       isActive = true,
                       grantRoleToAll = projReq.grantRoleToAll.flatMap(_.role))
    val insert = projects returning projects.map(_.id) into (
        (p,
         i) => p.copy(id = i)) += proj
    val fullAction =
      insert.flatMap(proj => setMembersAndDatasets(proj, projReq))
    db.run(fullAction.transactionally)
  }

  def setMembersAndDatasets(proj: Project,
                            projReq: ProjectRequest): DBIO[Project] = {
    // skip updating member/dataset lists if the request doesn't include those
    val updates = List(
      projReq.members.map(setProjectMembers(proj.id, _)),
      projReq.datasets.map(setProjectDatasets(proj.id, _))
    ).flatten

    DBIO.sequence(updates).andThen(DBIO.successful(proj))
  }

  def setProjectMembers(projId: Int,
                        members: Seq[ProjectRequestUser]): DBIO[Unit] =
    DBIO.seq(
      projectsUsers.filter(_.projectId === projId).delete,
      projectsUsers ++= members.map(m => ProjectUser(projId, m.login, m.role))
    )

  def setProjectDatasets(projId: Int, ids: Seq[RequestId]): DBIO[Unit] = {
    val now = JodaDateTime.now()
    val dsIds = ids.map(_.id)
    val jobIdsFromDatasets = for {
      (ejds, ds) <- engineJobsDataSets join dsMetaData2 on (_.datasetUUID === _.uuid)
      if ds.id inSet dsIds
    } yield ejds.jobId

    DBIO.seq(
      // move datasets not in the list of ids back to the general project
      dsMetaData2
        .filter(_.projectId === projId)
        .filterNot(_.id inSet dsIds)
        .map(ds => (ds.projectId, ds.updatedAt))
        .update((GENERAL_PROJECT_ID, now)),
      // move datasets that *are* in the list of IDs into this project
      dsMetaData2
        .filter(_.id inSet dsIds)
        .map(ds => (ds.projectId, ds.updatedAt))
        .update((projId, now)),
      // move analyses that use one of the given input datasets into this project
      engineJobs
        .filter(_.id in jobIdsFromDatasets)
        .map(ej => (ej.projectId, ej.updatedAt))
        .update((projId, now))
    )
  }

  def updateProject(projId: Int,
                    projReq: ProjectRequest): Future[Option[Project]] = {
    val now = JodaDateTime.now()

    // TODO(smcclellan): Lots of duplication here. Is there a better way to do this?
    val update = (projReq.state, projReq.grantRoleToAll) match {
      case (Some(state), Some(role)) =>
        projects
          .filter(_.id === projId)
          .map(p =>
            (p.name, p.state, p.grantRoleToAll, p.description, p.updatedAt))
          .update((projReq.name, state, role.role, projReq.description, now))
      case (None, Some(role)) =>
        projects
          .filter(_.id === projId)
          .map(p => (p.name, p.description, p.grantRoleToAll, p.updatedAt))
          .update((projReq.name, projReq.description, role.role, now))
      case (Some(state), None) =>
        projects
          .filter(_.id === projId)
          .map(p => (p.name, p.state, p.description, p.updatedAt))
          .update((projReq.name, state, projReq.description, now))
      case (None, None) =>
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
    db.run(
      DBIO
        .seq(
          projects
            .filter(_.id === projId)
            .map(j => (j.isActive, j.updatedAt))
            .update(false, now),
          // move the datasets from this project into the general project
          dsMetaData2
            .filter(_.projectId === projId)
            .map(ds => (ds.isActive, ds.projectId, ds.updatedAt))
            .update((false, GENERAL_PROJECT_ID, now)),
          engineJobs
            .filter(_.projectId === projId)
            .map(j => (j.isActive, j.updatedAt))
            .update(false, now)
        )
        .andThen(
          projects.filter(_.id === projId).result.headOption
        ))
  }

  def getProjectUsers(projId: Int): Future[Seq[ProjectUser]] =
    db.run(projectsUsers.filter(_.projectId === projId).result)

  def getDatasetsByProject(projId: Int): Future[Seq[DataSetMetaDataSet]] =
    db.run(dsMetaData2.filter(_.projectId === projId).result)

  /**
    * Returns a query that will only contain active projects for which the given user is granted a role.
    *
    * The query is for ordered pairs of (Project, Option[ProjectUser])
    *
    * The user may be granted a role individually by the projectsUsers table, by the project.grantRoleToAll field, or
    * both
    */
  private def userProjectsQuery(
      login: String): Query[(ProjectsT, Rep[Option[ProjectsUsersT]]),
                            (Project, Option[ProjectUser]),
                            Seq] =
    for {
      (p, pu) <- projects.filter(_.isActive) joinLeft projectsUsers.filter(
        _.login === login) on (_.id === _.projectId)
      if pu.isDefined || p.grantRoleToAll.isDefined
    } yield (p, pu)

  /**
    * Given a project and optional projectUser (as might be returned from the query produced by {{{userProjectsQuery}}}),
    * return the granted role with the highest permissions. E.g., if the project grants CAN_VIEW to all users, and the
    * specific user is granted CAN_EDIT, this will return CAN_EDIT.
    *
    * Throws an {{{UnsupportedOperationException}}} if no role is found.
    */
  private def maxRole(
      project: Project,
      projectUser: Option[ProjectUser]): ProjectUserRole.ProjectUserRole =
    try {
      (project.grantRoleToAll.toSet ++ projectUser.map(_.role).toSet).max
    } catch {
      case e: UnsupportedOperationException =>
        throw new IllegalArgumentException(
          s"Project ${project.id} does not grant a role to all users, and no user-specific role found")
    }

  def getUserProjects(login: String): Future[Seq[UserProjectResponse]] = {
    val userProjects = userProjectsQuery(login).result
      .map(_.map(j => UserProjectResponse(maxRole(j._1, j._2), j._1)))

    db.run(userProjects)
  }

  def getUserProjectsDatasets(
      login: String): Future[Seq[ProjectDatasetResponse]] = {
    val userJoin = for {
      (p, pu) <- userProjectsQuery(login)
      d <- dsMetaData2 if p.id === d.projectId
    } yield (p, d, pu)

    val userDatasets = userJoin.result
      .map(_.map(j => ProjectDatasetResponse(j._1, j._2, maxRole(j._1, j._3))))

    db.run(userDatasets)
  }

  def userHasProjectRole(
      login: String,
      projectId: Int,
      roles: Set[ProjectUserRole.ProjectUserRole]): Future[Boolean] = {
    val hasRole = projects
      .filter(_.id === projectId)
      .result
      .flatMap(_.headOption match {
        // If project exists and grants required role to all users, return true
        case Some(p) if p.grantRoleToAll.exists(roles.contains) =>
          DBIO.successful(true)
        // If project exists, but does not grant required role to all users, check user-specific roles
        case Some(p) =>
          projectsUsers
            .filter(pu => pu.login === login && pu.projectId === projectId)
            .result
            .map(_.headOption match {
              // If user has required role, return true
              case Some(pu) if roles.contains(pu.role) => true
              // User does not have required role, return false
              case _ => false
            })
        // Project does not exist, throw ResourceNotFoundError
        case _ =>
          DBIO.failed(
            new ResourceNotFoundError(
              s"No project with found with id $projectId"))
      })

    db.run(hasRole)
  }
}

/**
  * SQL Driven JobEngine datastore Backend
  */
trait JobDataStore extends LazyLogging with DaoFutureUtils {
  this: DalComponent with EventComponent =>

  import CommonModelImplicits._

  private val NO_WORK = NoAvailableWorkError("No Available work to run.")

  val DEFAULT_MAX_DATASET_LIMIT = 5000

  val resolver: JobResourceResolver

  /**
    * Raw Insert of an Engine Job into the system. If the job is in the CREATED state it will be
    * eligible to be run.
    *
    *
    * @param job Engine Job instance
    * @return
    */
  def insertJob(job: EngineJob): Future[EngineJob] = {
    val action = (engineJobs returning engineJobs.map(_.id) into (
        (j,
         i) => j.copy(id = i))) += job
    db.run(action.transactionally)
  }

  def insertEntryPoint(ep: EngineJobEntryPoint): Future[EngineJobEntryPoint] =
    db.run(engineJobsDataSets += ep).map(_ => ep)

  def getJobById(ix: IdAble): Future[EngineJob] =
    db.run(qEngineJobById(ix).result.headOption)
      .flatMap(failIfNone(s"Failed to find Job ${ix.toIdString}"))

  val qEngineMultiJobs = engineJobs.filter(_.isMultiJob === true)

  def getMultiJobById(ix: IdAble): Future[EngineJob] =
    db.run(qGetEngineMultiJobById(ix).result.headOption)
      .flatMap(failIfNone(s"Failed to find MultiJob ${ix.toIdString}"))

  def getMultiJobChildren(multiJobId: IdAble): Future[Seq[EngineJob]] = {

    def qGetBy(ix: Rep[Int]) =
      engineJobs.filter(_.parentMultiJobId === ix).sortBy(_.id)

    val q = multiJobId match {
      case IntIdAble(ix) => qGetBy(ix)
      case UUIDIdAble(_) =>
        for {
          job <- qGetEngineMultiJobById(multiJobId)
          jobs <- qGetBy(job.id)
        } yield jobs
    }
    db.run(q.result)
  }

  /**
    * Get next runnable job. Look for any SUBMITTED jobs and update the
    * state to RUNNING.
    *
    * @param isQuick Only select quick job types.
    * @return
    */
  def getNextRunnableEngineCoreJob(isQuick: Boolean = false)
    : Future[Either[NoAvailableWorkError, EngineJob]] = {

    //logger.debug(s"Checking for next runnable isQuick? $isQuick EngineJobs")

    val quickJobTypeIds = JobTypeIds.ALL
      .filter(_.isQuick)
      .map(_.id)
      .toSet

    val q0 = qGetEngineJobByState(AnalysisJobStates.SUBMITTED)
      .filter(_.isMultiJob === false)
    val q = if (isQuick) q0.filter(_.jobTypeId inSet quickJobTypeIds) else q0
    val q1 = q.sortBy(_.id).take(1)

    // This needs to be thought out a bit more. The entire engine job table needs to be locked in a worker-queue model
    // This is using a head call that fail in the caught and recover block
    def fx =
      for {
        job <- q1.result.head
        _ <- qEngineJobById(job.id)
          .map(j => (j.state, j.updatedAt, j.jobUpdatedAt, j.jobStartedAt))
          .update(
            (AnalysisJobStates.RUNNING,
             JodaDateTime.now(),
             JodaDateTime.now(),
             Some(JodaDateTime.now())))
        _ <- jobEvents += JobEvent(
          UUID.randomUUID(),
          job.id,
          AnalysisJobStates.RUNNING,
          s"Updating state to ${AnalysisJobStates.RUNNING} (from get-next-job)",
          JodaDateTime.now())
        job <- qEngineJobById(job.id).result.head
      } yield job

    db.run(fx.transactionally)
      .map { engineJob =>
        logger.info(
          s"Found runnable job id:${engineJob.id} type:${engineJob.jobTypeId} in state ${engineJob.state} isQuick:$isQuick")
        Right(engineJob)
      }
      .recover {
        case NonFatal(_) =>
          //logger.debug(s"No available work")
          Left(NO_WORK)
      }
  }

  /**
    * Get all the Job Events associated with a specific job
    */
  def getJobEventsByJobId(jobId: Int): Future[Seq[JobEvent]] =
    db.run(jobEvents.filter(_.jobId === jobId).result)

  def qUpdateJobState(jobId: IdAble,
                      state: AnalysisJobStates.JobStates,
                      message: String,
                      errorMessage: Option[String] = None)
    : DBIOAction[EngineJob, NoStream, Effect.Read with Effect.Write] = {

    logger.info(s"Updating job state of JobId:${jobId.toIdString} to $state")
    val now = JodaDateTime.now()

    val qUpdate = if (state.isCompleted) {
      qEngineJobById(jobId)
        .map(
          j =>
            (j.state,
             j.updatedAt,
             j.jobUpdatedAt,
             j.jobCompletedAt,
             j.errorMessage))
        .update(state, now, now, Some(now), errorMessage)
    } else {
      qEngineJobById(jobId)
        .map(j => (j.state, j.updatedAt, j.jobUpdatedAt, j.errorMessage))
        .update(state, now, now, errorMessage)
    }

    // The error handling of this .head call needs to be improved
    for {
      job <- qEngineJobById(jobId).result.head
      _ <- DBIO.seq(
        qUpdate,
        jobEvents += JobEvent(UUID.randomUUID(), job.id, state, message, now)
      )
      updatedJob <- qEngineJobById(jobId).result.head
    } yield updatedJob
  }

  /**
    * Update the State of a Job
    *
    * @param jobId Job Id
    * @param state Job State to be updated to
    * @param message Job progress message message (will be used in the JobEvent)
    * @param errorMessage Optional Error Message. If the state is FAILED, this should be explicitly set to propagate the
    *                     error message.
    * @return
    */
  def updateJobState(
      jobId: IdAble,
      state: AnalysisJobStates.JobStates,
      message: String,
      errorMessage: Option[String] = None): Future[EngineJob] = {

    val q = qUpdateJobState(jobId, state, message, errorMessage)

    val f: Future[EngineJob] = db.run(q.transactionally)

    f.foreach { job: EngineJob =>
      sendEventToManager[JobChangeStateMessage](JobChangeStateMessage(job))
    }

    f
  }

  /**
    *
    * Note, you can't write this as an inner join using the monadic join model to use in a DELETE statement
    *
    * This will create a runtime error with a SQL error.
    * "Invalid query for DELETE statement: A single source table is required, found: Join Inner"
    *
    * https://github.com/slick/slick/issues/684
    *
    * @param parentJobId
    * @return
    */
  private def getAllChildJobEvents(parentJobId: Rep[Int]) = {
    jobEvents filter { jEvent =>
      jEvent.jobId in (
        // Explicitly filter by state to avoid mutate running jobs. If the state gets borked this is a
        // fundamental problem.
        qGetEngineJobByState(AnalysisJobStates.CREATED)
          .filter(_.parentMultiJobId === parentJobId)
          .map(_.id)
        )
    }
  }

  // See comments above
  //  def getAllChildJobEvents(parentJobId: Rep[Int]) = {
  //    for {
  //      jobs <- engineJobs
  //        .filter(_.parentMultiJobId === parentJobId)
  //        .filter(_.isMultiJob === false)
  //      childJobEvents <- jobEvents if jobs.id === childJobEvents.jobId
  //    } yield childJobEvents
  //  }

  /**
    * Update Job metadata
    *
    * @param jobId   Job Id
    * @param name    Name of the Job
    * @param comment Description of the Job
    * @return
    */
  def updateJob(jobId: IdAble,
                name: Option[String],
                comment: Option[String],
                tags: Option[String]): Future[EngineJob] = {
    val q = for {
      job <- qEngineJobById(jobId).result.head
      _ <- DBIO.seq(
        qEngineJobById(jobId)
          .map(j => (j.name, j.comment, j.tags, j.updatedAt))
          .update(name.getOrElse(job.name),
                  comment.getOrElse(job.comment),
                  tags.getOrElse(job.tags),
                  JodaDateTime.now())
      )
      updatedJob <- qEngineJobById(jobId).result.headOption
    } yield updatedJob
    db.run(q.transactionally)
      .flatMap(failIfNone(s"Failed to find Job ${jobId.toIdString}"))
  }

  // This can only be called when the Job is in the CREATED state
  def updateMultiAnalysisJob(
      jobId: Int,
      opts: MultiAnalysisJobOptions,
      jsonSetting: JsObject,
      createdBy: Option[String],
      createdByEmail: Option[String],
      smrtlinkVersion: Option[String]): Future[EngineJob] = {
    val childJobs = opts.jobs
    val projectId = opts.getProjectId()

    val now = JodaDateTime.now()
    logger.info(
      s"Attempting to update MultiJob ${jobId.toIdString} job with ${childJobs.length} child jobs. Settings ${jsonSetting.prettyPrint.toString}")

    val updatedEntryPoints: Set[EngineJobEntryPoint] = childJobs
      .flatten(_.entryPoints.map(e =>
        EngineJobEntryPoint(jobId, e.uuid, e.fileTypeId)))
      .toSet

    def aToChildrenJobs(parentJobId: Int): DBIO[Seq[EngineJob]] =
      DBIO.sequence(
        deferredJobToChildrenJobs(childJobs,
                                  projectId,
                                  createdBy,
                                  smrtlinkVersion)(parentJobId).map(out =>
          qInsertEngineJob(out._1, out._2, submitJob = false)))

    val states: Set[AnalysisJobStates.JobStates] = Set(
      AnalysisJobStates.CREATED)

    val action = for {
      job <- qGetEngineMultiJobsByIdAndStates(jobId, states).result.head
      _ <- DBIO.seq(
        qGetEngineMultiJobById(jobId)
          .map(
            j =>
              (j.updatedAt,
               j.jobUpdatedAt,
               j.jsonSettings,
               j.name,
               j.comment,
               j.projectId))
          .update(now,
                  now,
                  jsonSetting.toString(),
                  opts.name.getOrElse(job.name),
                  opts.description.getOrElse(job.comment),
                  projectId),
        engineJobsDataSets.filter(_.jobId === job.id).delete,
        engineJobsDataSets ++= updatedEntryPoints.toList,
        // Delete an Children Jobs from previous
        getAllChildJobEvents(job.id).delete,
        qGetEngineJobByState(AnalysisJobStates.CREATED)
          .filter(_.parentMultiJobId === jobId)
          .filter(_.isMultiJob === false)
          .delete,
        // Insert the "updated" Deferred Jobs
        aToChildrenJobs(job.id)
      )
      updatedJob <- qGetEngineMultiJobById(jobId).result.head
    } yield updatedJob

    db.run(action.transactionally)
  }

  // Note, this should only be called when the MultiJob is in the CREATED state
  def deleteMultiJob(jobId: IdAble): Future[MessageResponse] = {
    logger.info(s"Attempting to delete job ${jobId.toIdString}")

    val states: Set[AnalysisJobStates.JobStates] = Set(
      AnalysisJobStates.CREATED)

    val q = for {
      job <- qGetEngineMultiJobsByIdAndStates(jobId, states).result.head
      _ <- jobEvents.filter(_.jobId === job.id).delete
      _ <- engineJobsDataSets.filter(_.jobId === job.id).delete
      _ <- qEngineJobById(jobId).delete
      // Delete all Children and companion JobEvents
      _ <- getAllChildJobEvents(job.id).delete
      _ <- qGetEngineJobByState(AnalysisJobStates.CREATED)
        .filter(_.parentMultiJobId === job.id)
        .filter(_.isMultiJob === false)
        .delete
    } yield
      MessageResponse(s"Successfully deleted MultiJob ${jobId.toIdString}")

    db.run(q.transactionally)
  }

  private def extractJobState(childJobs: Seq[EngineJob],
                              multiJobState: AnalysisJobStates.JobStates)
    : (AnalysisJobStates.JobStates, Option[String]) = {
    def toS(j: EngineJob) =
      s"${j.id} ${j.state} ${j.errorMessage.getOrElse("")}"

    val errorMessage = childJobs
      .filter(j => AnalysisJobStates.hasFailed(j.state))
      .map(toS)
      .reduceLeftOption(_ + _)

    val finalState =
      JobUtils.determineMultiJobState(childJobs.map(_.state), multiJobState)

    (finalState, errorMessage)
  }

  /**
    *
    * Trigger a check to update the MultiJob state from states of
    * children jobs. Only MultiJobs in the SUBMITTED, or RUNNING state
    * will be updated.
    */
  def triggerUpdateOfMultiJobState(jobId: IdAble): Future[EngineJob] = {

    val multiJobStates: Set[AnalysisJobStates.JobStates] =
      Set(AnalysisJobStates.SUBMITTED, AnalysisJobStates.RUNNING)

    val a1: DBIO[Option[EngineJob]] = for {
      job <- qGetEngineMultiJobsByIdAndStates(jobId, multiJobStates).result.headOption
    } yield job

    val action = a1.flatMap {
      case Some(parentJob) =>
        for {
          childJobs <- engineJobs
            .filter(_.parentMultiJobId === parentJob.id)
            .result
          updatedStateMessage <- DBIO.successful(
            extractJobState(childJobs, parentJob.state))
          updatedJob <- qUpdateJobState(
            parentJob.id,
            updatedStateMessage._1,
            s"MultiJob ${parentJob.id} state:${parentJob.state} to ${updatedStateMessage._1}",
            updatedStateMessage._2)
        } yield updatedJob
      case _ =>
        DBIO.failed(ResourceNotFoundError(
          s"Failed to Find MultiJob ${jobId.toIdString}, or job state is not in $multiJobStates"))
    }

    db.run(action.transactionally)
  }

  private def qInsertEngineJob(engineJob: EngineJob,
                               entryPoints: Set[EngineJobEntryPointRecord],
                               submitJob: Boolean = false)
    : DBIOAction[EngineJob, NoStream, Effect.Read with Effect.Write] = {
    val jobState =
      if (submitJob) AnalysisJobStates.SUBMITTED else AnalysisJobStates.CREATED

    // The original Job must be in the CREATED state.
    val cEngineJob =
      if (submitJob) engineJob.copy(state = jobState) else engineJob

    val updates = (engineJobs returning engineJobs.map(_.id) into (
        (j,
         i) => j.copy(id = i)) += cEngineJob) flatMap { job =>
      val jobId = job.id

      // Using the RunnableJobWithId is a bit clumsy and heavy for such a simple task
      // Resolving Path so we can update the state in the DB
      // Note, this will raise if the path can't be created
      val resolvedPath = resolver.resolve(jobId).toAbsolutePath.toString

      val jobCreateEvent = JobEvent(
        UUID.randomUUID(),
        jobId,
        AnalysisJobStates.CREATED,
        s"Created job $jobId type ${cEngineJob.jobTypeId} with ${cEngineJob.uuid.toString}",
        JodaDateTime.now()
      )

      val jobSubmitEvent = JobEvent(
        UUID.randomUUID(),
        jobId,
        AnalysisJobStates.SUBMITTED,
        s"Submitted job $jobId type ${cEngineJob.jobTypeId} with ${cEngineJob.uuid.toString}",
        JodaDateTime.now()
      )

      val allEvents =
        if (submitJob) Seq(jobCreateEvent, jobSubmitEvent)
        else Seq(jobCreateEvent)

      DBIO
        .seq(
          engineJobs
            .filter(_.id === jobId)
            .map(_.path)
            .update(resolvedPath.toString),
          jobEvents ++= allEvents,
          engineJobsDataSets ++= entryPoints.toList.map(e =>
            EngineJobEntryPoint(jobId, e.datasetUUID, e.datasetType))
        )
        .map(_ => cEngineJob.copy(id = jobId, path = resolvedPath.toString))
    }

    val action =
      projects.filter(_.id === engineJob.projectId).exists.result.flatMap {
        case true => updates
        case false =>
          DBIO.failed(
            UnprocessableEntityError(
              s"Project id ${engineJob.projectId} does not exist"))
      }

    action
  }

  private def insertEngineJob(
      engineJob: EngineJob,
      entryPoints: Set[EngineJobEntryPointRecord],
      submitJob: Boolean = false): Future[EngineJob] = {

    val f = db.run(
      qInsertEngineJob(engineJob, entryPoints, submitJob).transactionally)

    // Need to send an event to EngineManager to Check for work
    f.foreach { job: EngineJob =>
      sendEventToManager[JobChangeStateMessage](JobChangeStateMessage(job))
    }

    f
  }

  private def deferredJobToEngineJob(job: DeferredJob,
                                     defaultName: String,
                                     parentJobId: Int,
                                     projectId: Int,
                                     createdBy: Option[String] = None,
                                     createdByEmail: Option[String],
                                     smrtLinkVersion: Option[String],
                                     submitJob: Boolean = false): EngineJob = {

    val path = ""
    val createdAt = JodaDateTime.now()
    val uuid = UUID.randomUUID()
    val name = job.name.getOrElse(defaultName)
    val entryPoints: Seq[BoundServiceEntryPoint] =
      job.entryPoints.map(ep =>
        BoundServiceEntryPoint(ep.entryId, ep.fileTypeId, ep.uuid))

    val pbsmrtpipeJobOptions = PbsmrtpipeJobOptions(job.name,
                                                    job.description,
                                                    job.pipelineId,
                                                    entryPoints,
                                                    job.taskOptions,
                                                    Nil,
                                                    Some(projectId),
                                                    submit = Some(submitJob))

    // hack, this will be removed from the data model after the old Auto running code is deleted
    val workflow = "{}"

    // This is kinda brutal to drag this in
    val jsonSettings: JsObject =
      ServiceJobTypeJsonProtocols.pbsmrtpipeJobOptionsJsonFormat
        .write(pbsmrtpipeJobOptions)
        .asJsObject

    EngineJob(
      -1,
      uuid,
      name,
      job.description.getOrElse(s"Deferred Job $name"),
      createdAt,
      createdAt,
      createdAt,
      AnalysisJobStates.CREATED,
      JobTypeIds.PBSMRTPIPE.id,
      path,
      jsonSettings.toString(),
      createdBy,
      createdByEmail,
      smrtLinkVersion,
      projectId = projectId,
      parentMultiJobId = Some(parentJobId),
      isMultiJob = false,
      workflow = workflow
    )
  }

  private def deferredJobToChildrenJobs(childJobs: Seq[DeferredJob],
                                        projectId: Int,
                                        createdBy: Option[String],
                                        smrtlinkVersion: Option[String])
    : Int => Seq[(EngineJob, Set[EngineJobEntryPointRecord])] = {
    parentJobId =>
      val numJobs = childJobs.length

      childJobs.zipWithIndex.map { xs =>
        val defaultName = s"Job ${xs._2 + 1}/$numJobs"
        val eJob = deferredJobToEngineJob(
          xs._1,
          defaultName,
          parentJobId,
          projectId,
          createdBy,
          None, // Explicitly set the Child Job to not send an email
          smrtlinkVersion)

        val ePoints = xs._1.entryPoints.map(e =>
          EngineJobEntryPointRecord(e.uuid, e.fileTypeId))
        (eJob, ePoints.toSet)
      }
  }

  def createMultiJob(
      uuid: UUID,
      name: String,
      description: String,
      jobTypeId: JobTypeIds.JobType,
      entryPoints: Set[EngineJobEntryPointRecord] =
        Set.empty[EngineJobEntryPointRecord],
      jsonSetting: JsObject,
      createdBy: Option[String] = None,
      createdByEmail: Option[String] = None,
      smrtLinkVersion: Option[String] = None,
      projectId: Int = JobConstants.GENERAL_PROJECT_ID,
      workflow: JsObject = JsObject.empty,
      subJobTypeId: Option[String] = None,
      submitJob: Boolean = false,
      childJobs: Seq[DeferredJob],
      tags: Set[String] = Set.empty[String]): Future[EngineJob] = {

    // FIXME. This should have Option[Path]
    val path = ""
    val createdAt = JodaDateTime.now()
    val tagString = tags.toList.reduceLeftOption(_ ++ "," ++ _).getOrElse("")

    val engineJob = EngineJob(
      -1,
      uuid,
      name,
      description,
      createdAt,
      createdAt,
      createdAt,
      AnalysisJobStates.CREATED,
      jobTypeId.id,
      path,
      jsonSetting.toString(),
      createdBy,
      createdByEmail,
      smrtLinkVersion,
      projectId = projectId,
      parentMultiJobId = None,
      isMultiJob = true,
      workflow = workflow.toString(),
      tags = tagString
    )

    // All Children Jobs have submit = false because after a
    // MultiJob is changed state to submitted, that will trigger a
    // check to see if the inputs are resolved for each child job.
    def aToChildrenJobs(parentJobId: Int): DBIO[Seq[EngineJob]] =
      DBIO.sequence(
        deferredJobToChildrenJobs(childJobs,
                                  projectId,
                                  createdBy,
                                  smrtLinkVersion)(parentJobId).map(out =>
          qInsertEngineJob(out._1, out._2, submitJob = false)))

    // For the Parent Job we remove duplicate EntryPoints
    val qAction =
      qInsertEngineJob(engineJob, entryPoints.toSet, submitJob)
        .flatMap { createdMultiJob =>
          aToChildrenJobs(createdMultiJob.id).map(_ => createdMultiJob)
        }

    val f = db.run(qAction.transactionally)

    // Need to send an event to EngineManager to Check for work
    f.foreach { job: EngineJob =>
      sendEventToManager[JobChangeStateMessage](JobChangeStateMessage(job))
    }
    f
  }

  /** New Actor-less model **/
  def createCoreJob(
      uuid: UUID,
      name: String,
      description: String,
      jobTypeId: JobTypeIds.JobType,
      entryPoints: Set[EngineJobEntryPointRecord] =
        Set.empty[EngineJobEntryPointRecord],
      jsonSetting: JsObject,
      createdBy: Option[String] = None,
      createdByEmail: Option[String] = None,
      smrtLinkVersion: Option[String] = None,
      projectId: Int = JobConstants.GENERAL_PROJECT_ID,
      parentMultiJobId: Option[Int] = None,
      importedAt: Option[JodaDateTime] = None,
      subJobTypeId: Option[String] = None,
      submitJob: Boolean = false,
      tags: Set[String] = Set.empty[String]): Future[EngineJob] = {

    // This is wrong.
    val path = ""
    val createdAt = JodaDateTime.now()
    val tagString = tags.toList.reduceLeftOption(_ ++ "," ++ _).getOrElse("")

    val engineJob = EngineJob(
      -1,
      uuid,
      name,
      description,
      createdAt,
      createdAt,
      createdAt,
      AnalysisJobStates.CREATED,
      jobTypeId.id,
      path,
      jsonSetting.toString(),
      createdBy,
      createdByEmail,
      smrtLinkVersion,
      projectId = projectId,
      parentMultiJobId = parentMultiJobId,
      importedAt = importedAt,
      subJobTypeId = subJobTypeId,
      tags = tagString
    )

    insertEngineJob(engineJob, entryPoints, submitJob)
  }

  /**
    * Import a job from another SMRT Link system.
    * @param job EngineJob from exported manifest
    * @param entryPoints entry point records
    * @return new EngineJob object for the imported job
    */
  def importRawEngineJob(
      job: EngineJob,
      entryPoints: Set[EngineJobEntryPointRecord] =
        Set.empty[EngineJobEntryPointRecord]): Future[EngineJob] = {

    val importedJob =
      job.copy(id = -1, path = "", importedAt = Some(JodaDateTime.now()))

    // Submit must be false, otherwise the state will be mutated
    insertEngineJob(importedJob, entryPoints, submitJob = false)
  }

  def updateJsonSettings(jobId: IdAble,
                         jsonSettings: JsObject): Future[EngineJob] = {

    val q0 = qEngineJobById(jobId)

    val q1: DBIO[Int] = q0.map(_.jsonSettings).update(jsonSettings.toString())

    // This is to get around slick not supporting Returning an object
    val fx = DBIO
      .seq(q1)
      .andThen(q0.result.headOption)

    db.run(fx.transactionally)
      .flatMap(failIfNone(s"Unable to find Job ${jobId.toIdString}"))

  }

  def addJobEvent(jobEvent: JobEvent): Future[JobEvent] =
    db.run(jobEvents += jobEvent).map(_ => jobEvent)

  def addJobEvents(events: Seq[JobEvent]): Future[Seq[JobEvent]] =
    db.run(jobEvents ++= events).map(_ => events)

  def getJobEvents: Future[Seq[JobEvent]] = db.run(jobEvents.result)

  def addJobTask(jobTask: JobTask): Future[JobTask] = {
    // when pbsmrtpipe has parity with the AnalysisJobStates, JobTask should have state:AnalysisJobState
    val state = AnalysisJobStates
      .toState(jobTask.state)
      .getOrElse(AnalysisJobStates.UNKNOWN)
    val errorMessage = s"Failed to insert JobEvent from JobTask $jobTask"
    val message =
      s"Creating task ${jobTask.name} id:${jobTask.taskId} type:${jobTask.taskTypeId} State:${jobTask.state}"

    val jobEvent = JobEvent(jobTask.uuid,
                            jobTask.jobId,
                            state,
                            message,
                            jobTask.createdAt,
                            eventTypeId =
                              JobConstants.EVENT_TYPE_JOB_TASK_STATUS)

    def fx =
      for {
        _ <- jobTasks += jobTask
        _ <- jobEvents += jobEvent
        task <- jobTasks.filter(_.uuid === jobTask.uuid).result
      } yield task

    db.run(fx.transactionally)
      .map(_.headOption)
      .flatMap(failIfNone(errorMessage))
  }

  /**
    * Check a Child Job in the CREATED state for Resolved Entry Points and change the
    * job state from CREATED to SUBMITTED if all Resolved Entry Points are found.
    *
    * @param childJobId Child Job Id
    * @return
    */
  def qCheckCreatedChildJobForResolvedEntryPoints(
      childJobId: Int): DBIO[String] = {

    implicit val jobStateMapper = jobStateType

    val submitMessage =
      s"All Entry points are resolved for child job $childJobId. Submitting Child Job $childJobId"

    val notSubmittedMessage =
      s"Cannot Submit. Child Job $childJobId is not in the CREATED state or ALL Entry Points are not resolved."

    // Get the Child Job
    val q0 = qGetEngineJobByState(AnalysisJobStates.CREATED)
    val q1 = q0.filter(_.parentMultiJobId.isDefined)
    val q2 = q1.filter(_.id === childJobId)

    val qNumJobEntryPoints = for {
      childJob <- q2
      foundEpoints <- engineJobsDataSets.filter(_.jobId === childJob.id)
    } yield foundEpoints

    val qNumResolvedDataSets = for {
      childJob <- q2
      // Entry Points of the Job
      childJobEntryPoints <- engineJobsDataSets.filter(_.jobId === childJob.id)
      // Should this only include Active datasets?
      resolvedDataSets <- dsMetaData2
        .filter(_.uuid === childJobEntryPoints.datasetUUID)
    } yield resolvedDataSets

    // Simple comparison that the Job EntryPoints are all resolved to already imported DataSets
    val q4 = for {
      numEntryPoints <- qNumJobEntryPoints.result
      numResolvedDataSets <- qNumResolvedDataSets.result
    } yield
      (numEntryPoints.length == numResolvedDataSets.length) && numEntryPoints.nonEmpty

    val qSubmit = qUpdateJobState(childJobId,
                                  AnalysisJobStates.SUBMITTED,
                                  submitMessage,
                                  None)
      .map(updatedJob => submitMessage)

    // If found All the EntryPoints are Resolved, then update the Child Job state to
    // Submitted.
    val totalAction = q4.flatMap {
      case false =>
        // logger.info(notSubmittedMessage)
        DBIO.successful(notSubmittedMessage)
      case true =>
        // logger.info(submitMessage)
        qSubmit
    }

    totalAction
  }

  def checkCreatedChildJobForResolvedEntryPoints(
      childJobId: Int): Future[String] =
    db.run(
      qCheckCreatedChildJobForResolvedEntryPoints(childJobId).transactionally)

  /**
    * When a new DataSet is added to the System, see if any Children
    * Jobs (in CREATED state) have Entry Points that are now resolved.
    *
    * The Parent MultiJob must not be in a FAILED state.
    *
    * @param datasetUUID Imported DataSet UUID
    */
  def checkCreatedChildrenJobsForNewlyEnteredDataSet(
      datasetUUID: UUID): Future[String] = {

    // Get all Valid Child Job Jobs
    val q0 = qGetEngineJobByState(AnalysisJobStates.CREATED)
    val q1 = q0.filter(_.parentMultiJobId.isDefined)

    val q3 = for {
      validChildJobs <- q1
      eJobIds <- engineJobsDataSets
        .filter(_.datasetUUID === datasetUUID)
        .filter(_.jobId === validChildJobs.id)
        .map(_.jobId)
    } yield eJobIds

    val total: DBIO[Seq[String]] = q3.result.flatMap { jobIds =>
      DBIO.sequence(jobIds.map(qCheckCreatedChildJobForResolvedEntryPoints))
    }

    db.run(total.transactionally)
      .map { xs =>
        xs.reduceLeftOption(_ ++ _)
          .getOrElse(s"No Updates for DataSet:$datasetUUID")
      }
  }

  /**
    * Update the state of a Job Task and create an JobEvent
    *
    * @param update Task Update record
    * @return
    */
  def updateJobTask(update: UpdateJobTask): Future[JobTask] = {
    // Need to sync the pbsmrtpipe task states with the allowed JobStates
    val taskState = AnalysisJobStates
      .toState(update.state)
      .getOrElse(AnalysisJobStates.UNKNOWN)
    val now = JodaDateTime.now()

    val futureFailMessage =
      s"Unable to find JobTask uuid:${update.uuid} for Job id ${update.jobId}"

    val fx = for {
      _ <- jobTasks
        .filter(_.uuid === update.uuid)
        .filter(_.jobId === update.jobId)
        .map((x) => (x.state, x.errorMessage, x.updatedAt))
        .update((update.state, update.errorMessage, now))
      _ <- jobEvents += JobEvent(UUID.randomUUID(),
                                 update.jobId,
                                 taskState,
                                 update.message,
                                 now,
                                 JobConstants.EVENT_TYPE_JOB_TASK_STATUS)
      jobTask <- jobTasks.filter(_.uuid === update.uuid).result
    } yield jobTask

    db.run(fx.transactionally)
      .map(_.headOption)
      .flatMap(failIfNone(futureFailMessage))
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
        getJobById(uuid).flatMap(job =>
          db.run(jobTasks.filter(_.jobId === job.id).result))
    }
  }

  def getJobTask(taskId: UUID): Future[JobTask] = {
    val errorMessage = s"Can't find job task $taskId"
    db.run(jobTasks.filter(_.uuid === taskId).result)
      .map(_.headOption)
      .flatMap(failIfNone(errorMessage))
  }

  // TODO(smcclellan): limit is never used. add `.take(limit)`?
  def getEngineCoreJobs(
      limit: Int = 100,
      includeInactive: Boolean = false): Future[Seq[EngineJob]] = {
    if (!includeInactive)
      db.run(engineJobs.filter(_.isActive).sortBy(_.id.desc).result)
    else db.run(engineJobs.sortBy(_.id.desc).result)
  }

  def getEngineMultiJobs(
      limit: Int = 100,
      includeInactive: Boolean = false): Future[Seq[EngineJob]] = {
    val q0 = qEngineMultiJobs
    val q1 = if (!includeInactive) q0.filter(_.isActive) else q0
    db.run(q1.sortBy(_.id.desc).result)
  }

  private def qJobsBySearch(c: JobSearchCriteria) = {

    type Q = Query[EngineJobsT, EngineJobsT#TableElementType, Seq]
    type QF = (Q => Q)
    type QOF = (Q => Option[Q])

    val qIsActive: QOF = { q =>
      c.isActive.map { value =>
        value match {
          case true => q.filter(_.isActive)
          case false => q // there's no filter for isActive=false
        }
      }
    }

    // This needs to be clarified and collapsed back into the SearchCriteria API
    // in the new standard way.
    val qOldProjectIds: QOF = { q =>
      if (c.projectIds.nonEmpty) Some(q.filter(_.projectId inSet c.projectIds))
      else None
    }

    val qById: QOF = { q =>
      c.id
        .map {
          case IntEqQueryOperator(value) => q.filter(_.id === value)
          case IntInQueryOperator(values) => q.filter(_.id inSet values)
          case IntGteQueryOperator(value) => q.filter(_.id >= value)
          case IntGtQueryOperator(value) => q.filter(_.id > value)
          case IntLteQueryOperator(value) => q.filter(_.id <= value)
          case IntLtQueryOperator(value) => q.filter(_.id < value)
        }
    }
    val qByUUID: QOF = { q =>
      c.uuid
        .map {
          case UUIDEqOperator(value) => q.filter(_.uuid === value)
          case UUIDInOperator(values) => q.filter(_.uuid inSet values)
        }
    }
    val qByName: QOF = { q =>
      c.name
        .map {
          case StringEqQueryOperator(value) => q.filter(_.name === value)
          case StringMatchQueryOperator(value) =>
            q.filter(_.name like s"%${value}%")
          case StringInQueryOperator(values) => q.filter(_.name inSet values)
        }
    }
    val qByComment: QOF = { q =>
      c.comment
        .map {
          case StringEqQueryOperator(value) => q.filter(_.comment === value)
          case StringMatchQueryOperator(value) =>
            q.filter(_.comment like s"%${value}%")
          case StringInQueryOperator(values) =>
            q.filter(_.comment inSet values)
        }
    }
    val qByCreatedAt: QOF = { q =>
      c.createdAt
        .map {
          case DateTimeEqOperator(value) => q.filter(_.createdAt === value)
          case DateTimeGtOperator(value) => q.filter(_.createdAt > value)
          case DateTimeGteOperator(value) => q.filter(_.createdAt >= value)
          case DateTimeLtOperator(value) => q.filter(_.createdAt < value)
          case DateTimeLteOperator(value) => q.filter(_.createdAt <= value)
        }
    }
    val qByUpdatedAt: QOF = { q =>
      c.updatedAt
        .map {
          case DateTimeEqOperator(value) => q.filter(_.updatedAt === value)
          case DateTimeGtOperator(value) => q.filter(_.updatedAt > value)
          case DateTimeGteOperator(value) => q.filter(_.updatedAt >= value)
          case DateTimeLtOperator(value) => q.filter(_.updatedAt < value)
          case DateTimeLteOperator(value) => q.filter(_.updatedAt <= value)
        }
    }
    val qByJobUpdatedAt: QOF = { q =>
      c.jobUpdatedAt
        .map {
          case DateTimeEqOperator(value) => q.filter(_.jobUpdatedAt === value)
          case DateTimeGtOperator(value) => q.filter(_.jobUpdatedAt > value)
          case DateTimeGteOperator(value) => q.filter(_.jobUpdatedAt >= value)
          case DateTimeLtOperator(value) => q.filter(_.jobUpdatedAt < value)
          case DateTimeLteOperator(value) => q.filter(_.jobUpdatedAt <= value)
        }
    }
    val qByState: QOF = { q =>
      c.state
        .map {
          case JobStateEqOperator(value) => q.filter(_.state === value)
          case JobStateInOperator(values) => q.filter(_.state inSet values)
        }
    }
    val qByJobTypeId: QOF = { q =>
      c.jobTypeId
        .map {
          case StringEqQueryOperator(value) => q.filter(_.jobTypeId === value)
          case StringMatchQueryOperator(value) =>
            q.filter(_.jobTypeId like s"%${value}%")
          case StringInQueryOperator(values) =>
            q.filter(_.jobTypeId inSet values)
        }
    }
    val qByPath: QOF = { q =>
      c.path
        .map {
          case StringEqQueryOperator(value) => q.filter(_.path === value)
          case StringMatchQueryOperator(value) =>
            q.filter(_.path like s"%${value}%")
          case StringInQueryOperator(values) => q.filter(_.path inSet values)
        }
    }
    val qByCreatedBy: QOF = { q =>
      c.createdBy
        .map {
          case StringEqQueryOperator(value) => q.filter(_.createdBy === value)
          case StringMatchQueryOperator(value) =>
            q.filter(_.createdBy like s"%${value}%")
          case StringInQueryOperator(values) =>
            q.filter(_.createdBy inSet values)
        }
    }
    val qByCreatedByEmail: QOF = { q =>
      c.createdByEmail
        .map {
          case StringEqQueryOperator(value) =>
            q.filter(_.createdByEmail === value)
          case StringMatchQueryOperator(value) =>
            q.filter(_.createdByEmail like s"%${value}%")
          case StringInQueryOperator(values) =>
            q.filter(_.createdByEmail inSet values)
        }
    }
    val qBySmrtlinkVersion: QOF = { q =>
      c.smrtlinkVersion
        .map {
          case StringEqQueryOperator(value) =>
            q.filter(_.smrtLinkVersion === value)
          case StringMatchQueryOperator(value) =>
            q.filter(_.smrtLinkVersion like s"%${value}%")
          case StringInQueryOperator(values) =>
            q.filter(_.smrtLinkVersion inSet values)
        }
    }
    val qByErrorMessage: QOF = { q =>
      c.errorMessage
        .map {
          case StringEqQueryOperator(value) =>
            q.filter(_.errorMessage === value)
          case StringMatchQueryOperator(value) =>
            q.filter(_.errorMessage like s"%${value}%")
          case StringInQueryOperator(values) =>
            q.filter(_.errorMessage inSet values)
        }
    }
    val qByIsMultiJob: QOF = { q =>
      c.isMultiJob.map { value =>
        value match {
          case true => q.filter(_.isMultiJob === true)
          case false => q.filter(_.isMultiJob === false)
        }
      }
    }
    val qByParentMultiJobId: QOF = { q =>
      c.parentMultiJobId
        .map {
          case IntEqQueryOperator(value) =>
            q.filter(_.parentMultiJobId === value)
          case IntInQueryOperator(values) =>
            q.filter(_.parentMultiJobId inSet values)
          case IntGteQueryOperator(value) =>
            q.filter(_.parentMultiJobId >= value)
          case IntGtQueryOperator(value) =>
            q.filter(_.parentMultiJobId > value)
          case IntLteQueryOperator(value) =>
            q.filter(_.parentMultiJobId <= value)
          case IntLtQueryOperator(value) =>
            q.filter(_.parentMultiJobId < value)
        }
    }
    val qByImportedAt: QOF = { q =>
      c.importedAt
        .map {
          case DateTimeEqOperator(value) => q.filter(_.importedAt === value)
          case DateTimeGtOperator(value) => q.filter(_.importedAt > value)
          case DateTimeGteOperator(value) => q.filter(_.importedAt >= value)
          case DateTimeLtOperator(value) => q.filter(_.importedAt < value)
          case DateTimeLteOperator(value) => q.filter(_.importedAt <= value)
        }
    }
    val qByTags: QOF = { q =>
      c.tags
        .map {
          case StringEqQueryOperator(value) => q.filter(_.tags === value)
          case StringMatchQueryOperator(value) =>
            q.filter(_.tags like s"%${value}%")
          case StringInQueryOperator(values) => q.filter(_.tags inSet values)
        }
    }
    val qBySubJobTypeId: QOF = { q =>
      c.subJobTypeId
        .map {
          case StringEqQueryOperator(value) =>
            q.filter(_.subJobTypeId === value)
          case StringMatchQueryOperator(value) =>
            q.filter(_.subJobTypeId like s"%${value}%")
          case StringInQueryOperator(values) =>
            q.filter(_.subJobTypeId inSet values)
        }
    }

    val queries: Seq[QOF] = Seq(
      qIsActive,
      qByJobTypeId,
      qOldProjectIds,
      qById,
      qByName,
      qByUUID,
      qByComment,
      qByCreatedAt,
      qByUpdatedAt,
      qByJobUpdatedAt,
      qByState,
      qByPath,
      qByCreatedBy,
      qByCreatedByEmail,
      qBySmrtlinkVersion,
      qByErrorMessage,
      qByIsMultiJob,
      qByParentMultiJobId,
      qByImportedAt,
      qByTags,
      qBySubJobTypeId
    )

    val qTotal: QF = { q =>
      queries.foldLeft(q) { case (acc, qf) => qf(acc).getOrElse(acc) }
    }

    qTotal(engineJobs)
  }

  def getJobs(c: JobSearchCriteria): Future[Seq[EngineJob]] = {
    val q1 = qJobsBySearch(c).sortBy(_.id.desc)
    val q2 = c.marker.map(i => q1.drop(i)).getOrElse(q1)
    db.run(q2.take(c.limit).result)
  }

  def getJobEntryPoints(jobId: Int): Future[Seq[EngineJobEntryPoint]] =
    db.run(engineJobsDataSets.filter(_.jobId === jobId).result)

  def deleteJobById(jobId: IdAble): Future[EngineJob] = {
    logger.info(s"Setting isActive=false for Job id:${jobId.toIdString}")
    val now = JodaDateTime.now()
    db.run(for {
        _ <- qEngineJobById(jobId)
          .map(j => (j.isActive, j.updatedAt))
          .update(false, now)
        job <- qEngineJobById(jobId).result.headOption
      } yield job)
      .flatMap(failIfNone(
        s"Unable to Delete job. Unable to find Job id:${jobId.toIdString}"))
  }
}

trait DataSetTypesDao extends DaoFutureUtils {
  private def toServiceMetaType(t: DataSetMetaTypes.DataSetMetaType) = {
    val now = JodaDateTime.now()
    ServiceDataSetMetaType(t.fileType.fileTypeId,
                           s"Display name for ${t.shortName}",
                           s"Description for ${t.fileType.fileTypeId}",
                           now,
                           now,
                           t.shortName)
  }

  def getDataSetTypeById(typeId: String): Future[ServiceDataSetMetaType] =
    Future
      .successful(DataSetMetaTypes.fromAnyName(typeId))
      .flatMap(failIfNone(s"Can't find dataset type $typeId"))
      .map(toServiceMetaType)

  def getDataSetTypes: Future[Seq[ServiceDataSetMetaType]] =
    Future.successful(DataSetMetaTypes.ALL.toSeq.map(toServiceMetaType))
}

/**
  * Model extend DataSet Component to have 'extended' importing of datastore files by file type
  * (e.g., DataSet, Report)
  *
  * Mixin the Job Component because the files depended on the job
  */
trait DataSetStore extends DaoFutureUtils with LazyLogging {
  this: EventComponent with JobDataStore with DalComponent =>

  val DEFAULT_PROJECT_ID = 1
  val DEFAULT_USER_ID = 1
  import CommonModelImplicits._

  /**
    * Import a DataStoreJob File
    *
    * All of these insert/add methods should return the entity that was inserted.
    *
    * @param ds DataStore Job file
    * @return
    */
  def addDataStoreFile(ds: DataStoreJobFile): Future[MessageResponse] = {
    logger.info(s"adding datastore file for $ds")
    getJobById(ds.jobId).flatMap(engineJob =>
      importDataStoreFile(ds.dataStoreFile, engineJob.uuid))
  }

  /**
    * Get DataStoreService Files
    *
    * In practice, this would return a list that is very large
    *
    * @param ignoreInactive Ignore Inactive files
    * @return
    */
  def getDataStoreFiles(
      ignoreInactive: Boolean = true): Future[Seq[DataStoreServiceFile]] = {
    val q = if (ignoreInactive) {
      datastoreServiceFiles.filter(_.isActive)
    } else {
      datastoreServiceFiles
    }
    db.run(q.result)
  }

  def getDataStoreFileByUUID(uuid: UUID): Future[DataStoreServiceFile] =
    db.run(datastoreServiceFiles.filter(_.uuid === uuid).result.headOption)
      .flatMap(failIfNone(s"Unable to find DataStore File with uuid `$uuid`"))

  def qDatastoreServiceFilesByJobId(id: IdAble) = {
    id match {
      case IntIdAble(i) => datastoreServiceFiles.filter(_.jobId === i)
      case UUIDIdAble(uuid) => datastoreServiceFiles.filter(_.jobUUID === uuid)
    }
  }

  def getDataStoreServiceFilesByJobId(
      i: IdAble): Future[Seq[DataStoreServiceFile]] =
    db.run(qDatastoreServiceFilesByJobId(i).result)

  def getDataStoreReportFilesByJobId(
      jobId: IdAble): Future[Seq[DataStoreReportFile]] =
    db.run {
        qDatastoreServiceFilesByJobId(jobId)
          .filter(_.fileTypeId === FileTypes.REPORT.fileTypeId)
          .result
      }
      .map(_.map((d: DataStoreServiceFile) =>
        DataStoreReportFile(d, d.sourceId.split("-").head)))

  // THere needs to be special care when loading blocking
  // operations within Future
  private def loadFile(path: Path): Future[Option[String]] = {
    if (Files.exists(path)) Future {
      blocking {
        Option(FileUtils.readFileToString(path.toFile))
      }
    } else {
      Future.successful(None)
    }
  }

  // Return the contents of the Report. This should really return Future[JsObject]
  def getDataStoreReportByUUID(reportUUID: UUID): Future[String] = {

    val action = datastoreServiceFiles
      .filter(_.uuid === reportUUID)
      .map(_.path)
      .result
      .headOption

    db.run(action)
      .flatMap(
        optPath =>
          optPath
            .map(f => loadFile(Paths.get(f)))
            .getOrElse(Future.successful(None)))
      .flatMap(failIfNone(s"Unable to find report with id $reportUUID"))
  }

  def getDataSetReports(dsId: IdAble): Future[Seq[DataStoreReportFile]] = {

    val q1 = for {
      ds <- qDsMetaDataById(dsId)
      dsReportIds <- datasetReports
        .filter(_.datasetId === ds.uuid)
      reportFiles <- datastoreServiceFiles
        .filter(_.jobId === ds.jobId)
        .filter(_.fileTypeId === FileTypes.REPORT.fileTypeId) // This isn't really necessary.
        .filter(_.uuid === dsReportIds.reportId)
    } yield reportFiles

    db.run(q1.result)
      .map(_.map((d: DataStoreServiceFile) =>
        DataStoreReportFile(d, d.sourceId.split("-").head)))
  }

  def qDsMetaDataById(id: IdAble) = {
    id match {
      case IntIdAble(i) => dsMetaData2.filter(_.id === i)
      case UUIDIdAble(uuid) => dsMetaData2.filter(_.uuid === uuid)
    }
  }

  val qDsMetaDataIsActive = dsMetaData2.filter(_.isActive)

  /**
    * Get the Base PacBioDataSet data for DataSets imported into the system
    * @param limit Maximum number of returned results
    * @return
    */
  def getDataSetMetas(
      limit: Option[Int] = None,
      activity: Option[Boolean]): Future[Seq[DataSetMetaDataSet]] = {
    val qActive = activity
      .map(activity => dsMetaData2.filter(_.isActive === activity))
      .getOrElse(dsMetaData2)
    val q = limit.map(x => qActive.take(x)).getOrElse(qActive)
    db.run(q.sortBy(_.id).result)
  }

  def getDataSetMetasByJobId(ix: IdAble): Future[Seq[DataSetMetaDataSet]] = {
    val q1 = for {
      jobs <- qEngineJobById(ix)
      dsMetas <- dsMetaData2.filter(_.jobId === jobs.id)
    } yield dsMetas

    db.run(q1.result)
  }

  /**
    * Update the
    * @param ids DataSetMetaSets that are to marked as InActive.
    * @return
    */
  def updatedDataSetMetasAsInActive(ids: Set[Int]): Future[MessageResponse] = {
    val q = qDsMetaDataIsActive
      .filter(_.id inSet ids)
      .map(d => (d.isActive, d.updatedAt))
      .update((false, JodaDateTime.now()))
    // Is there a better way to do this?
    db.run(q.map(_ =>
      MessageResponse(s"Marked ${ids.size} MetaDataSet as inActive")))
  }

  private def getDataSetMetaDataSet(
      id: IdAble): Future[Option[DataSetMetaDataSet]] =
    db.run(qDsMetaDataById(id).result.headOption)

  def getDataSetMetaData(id: IdAble): Future[DataSetMetaDataSet] =
    getDataSetMetaDataSet(id).flatMap(
      failIfNone(s"Unable to find dataset with ID ${id.toIdString}"))

  private def insertMetaData(ds: ServiceDataSetMetadata)
    : DBIOAction[Int, NoStream, Effect.Read with Effect.Write] = {

    dsMetaData2 returning dsMetaData2.map(_.id) += DataSetMetaDataSet(
      -999,
      ds.uuid,
      ds.name,
      ds.path,
      ds.createdAt,
      ds.updatedAt,
      ds.importedAt,
      ds.numRecords,
      ds.totalLength,
      ds.tags,
      ds.version,
      ds.comments,
      ds.md5,
      ds.createdBy,
      ds.jobId,
      ds.projectId,
      isActive = ds.isActive,
      parentUuid = ds.parentUuid
    )
  }
  // Util func for composing the composition of loading the dataset and translation to
  // a necessary file formats.
  // Note, DsServiceJobFile has projectId, jobId is used to populated
  // the ServiceDataSetMetadata
  private def loadImportAbleServiceFileLoader[T <: DataSetType,
                                              X <: ServiceDataSetMetadata,
                                              Y <: ImportAbleServiceFile](
      loader: (Path => T),
      converter: ((T, Path, Option[String], Int, Int) => X),
      g: ((DsServiceJobFile, X) => Y))(dsj: DsServiceJobFile): Y = {
    val path = Paths.get(dsj.file.path)
    val dataset = loader(path)

    logger.debug(
      s"Converting DataSet${dataset.getUniqueId} with projectId ${dsj.projectId}")
    val sds = converter(dataset,
                        path.toAbsolutePath,
                        dsj.createdBy,
                        dsj.file.jobId,
                        dsj.projectId)
    logger.debug(
      s"Converted DataSet id:${sds.id} uuid:${sds.uuid} jobId:${sds.jobId} projectId:${sds.projectId} createdAt:${sds.createdAt}")

    g(dsj, sds)
  }

  def loadImportAbleSubreadSet(dsj: DsServiceJobFile): ImportAbleSubreadSet =
    loadImportAbleServiceFileLoader[SubreadSet,
                                    SubreadServiceDataSet,
                                    ImportAbleSubreadSet](
      DataSetLoader.loadSubreadSet,
      Converters.convertSubreadSet,
      ImportAbleSubreadSet.apply)(dsj)

  def loadImportAbleHdfSubreadSet(
      dsj: DsServiceJobFile): ImportAbleHdfSubreadSet =
    loadImportAbleServiceFileLoader[HdfSubreadSet,
                                    HdfSubreadServiceDataSet,
                                    ImportAbleHdfSubreadSet](
      DataSetLoader.loadHdfSubreadSet,
      Converters.convertHdfSubreadSet,
      ImportAbleHdfSubreadSet.apply)(dsj)

  def loadImportAbleAlignmentSet(
      dsj: DsServiceJobFile): ImportAbleAlignmentSet =
    loadImportAbleServiceFileLoader[AlignmentSet,
                                    AlignmentServiceDataSet,
                                    ImportAbleAlignmentSet](
      DataSetLoader.loadAlignmentSet,
      Converters.convertAlignmentSet,
      ImportAbleAlignmentSet.apply)(dsj)

  def loadImportAbleReferenceSet(
      dsj: DsServiceJobFile): ImportAbleReferenceSet =
    loadImportAbleServiceFileLoader[ReferenceSet,
                                    ReferenceServiceDataSet,
                                    ImportAbleReferenceSet](
      DataSetLoader.loadReferenceSet,
      Converters.convertReferenceSet,
      ImportAbleReferenceSet.apply)(dsj)

  def loadImportAbleBarcodeSet(dsj: DsServiceJobFile): ImportAbleBarcodeSet =
    loadImportAbleServiceFileLoader[BarcodeSet,
                                    BarcodeServiceDataSet,
                                    ImportAbleBarcodeSet](
      DataSetLoader.loadBarcodeSet,
      Converters.convertBarcodeSet,
      ImportAbleBarcodeSet.apply)(dsj)

  def loadImportAbleConsensusReadSet(
      dsj: DsServiceJobFile): ImportAbleConsensusReadSet =
    loadImportAbleServiceFileLoader[ConsensusReadSet,
                                    ConsensusReadServiceDataSet,
                                    ImportAbleConsensusReadSet](
      DataSetLoader.loadConsensusReadSet,
      Converters.convertConsensusReadSet,
      ImportAbleConsensusReadSet.apply)(dsj)

  def loadImportAbleConsensusAlignmentSet(
      dsj: DsServiceJobFile): ImportAbleConsensusAlignmentSet =
    loadImportAbleServiceFileLoader[ConsensusAlignmentSet,
                                    ConsensusAlignmentServiceDataSet,
                                    ImportAbleConsensusAlignmentSet](
      DataSetLoader.loadConsensusAlignmentSet,
      Converters.convertConsensusAlignmentSet,
      ImportAbleConsensusAlignmentSet.apply)(dsj)

  def loadImportAbleTranscriptSet(
      dsj: DsServiceJobFile): ImportAbleTranscriptSet =
    loadImportAbleServiceFileLoader[TranscriptSet,
                                    TranscriptServiceDataSet,
                                    ImportAbleTranscriptSet](
      DataSetLoader.loadTranscriptSet,
      Converters.convertTranscriptSet,
      ImportAbleTranscriptSet.apply)(dsj)

  def loadImportAbleContigSet(dsj: DsServiceJobFile): ImportAbleContigSet =
    loadImportAbleServiceFileLoader[ContigSet,
                                    ContigServiceDataSet,
                                    ImportAbleContigSet](
      DataSetLoader.loadContigSet,
      Converters.convertContigSet,
      ImportAbleContigSet.apply)(dsj)

  def loadImportAbleGmapReferenceSet(
      dsj: DsServiceJobFile): ImportAbleGmapReferenceSet =
    loadImportAbleServiceFileLoader[GmapReferenceSet,
                                    GmapReferenceServiceDataSet,
                                    ImportAbleGmapReferenceSet](
      DataSetLoader.loadGmapReferenceSet,
      Converters.convertGmapReferenceSet,
      ImportAbleGmapReferenceSet.apply)(dsj)

  def loadImportAbleReport(dsj: DsServiceJobFile): ImportAbleReport =
    ImportAbleReport(dsj, ReportUtils.loadReport(Paths.get(dsj.file.path)))

  /**
    * def loadImportAbleFile(file: DataStoreFile): ImportAbleFile
    * def importImportAbleFile(file: ImportAbleFile): Future[MessageResponse]]
    *
    * 1. val files = Seq[DataStoreFile]
    * 2. val serviceFiles = Seq[DataStoreServiceFile] (turn into DataStore ServiceFile)
    * 3. val importAbleFiles: Seq[ImportAbleFile] = files.map(loadImportAbleFile) // Load from filesystem
    * 4. val xs = Future.sequence(importableFiles.map(file => importImportAbleFile(file))) // import into DB
    */
  def loadImportAbleFile[T >: ImportAbleServiceFile](
      dsj: DsServiceJobFile): T = {
    DataSetMetaTypes
      .fromString(dsj.file.fileTypeId)
      .map {
        case DataSetMetaTypes.Subread => loadImportAbleSubreadSet(dsj)
        case DataSetMetaTypes.HdfSubread =>
          loadImportAbleHdfSubreadSet(dsj)
        case DataSetMetaTypes.Alignment => loadImportAbleAlignmentSet(dsj)
        case DataSetMetaTypes.Barcode => loadImportAbleBarcodeSet(dsj)
        case DataSetMetaTypes.CCS => loadImportAbleConsensusReadSet(dsj)
        case DataSetMetaTypes.AlignmentCCS =>
          loadImportAbleConsensusAlignmentSet(dsj)
        case DataSetMetaTypes.Contig => loadImportAbleContigSet(dsj)
        case DataSetMetaTypes.Reference => loadImportAbleReferenceSet(dsj)
        case DataSetMetaTypes.GmapReference =>
          loadImportAbleGmapReferenceSet(dsj)
        case DataSetMetaTypes.Transcript => loadImportAbleTranscriptSet(dsj)
      }
      .getOrElse {
        dsj.file.fileTypeId match {
          case FileTypes.REPORT.fileTypeId => loadImportAbleReport(dsj)
          case _ => ImportAbleDataStoreFile(dsj)
        }
      }
  }

  def actionImportSimpleDataStoreFile(
      f: ImportAbleDataStoreFile): DBIO[MessageResponse] = {
    val ds = f.ds.file
    val action0 = datastoreServiceFiles += ds

    val ax =
      datastoreServiceFiles
        .filter(_.uuid === ds.uuid)
        .exists
        .result
        .flatMap {
          case true =>
            DBIO.successful(MessageResponse(
              s"DataStoreFile ${ds.uuid} ${ds.fileTypeId} already exists. File:${ds.path}"))
          case false =>
            action0.map(_ =>
              MessageResponse(
                s"Successfully imported DSF ${ds.uuid} type:${ds.fileTypeId}"))
        }
    ax
  }

  def importSimpleDataStoreFile(
      f: ImportAbleDataStoreFile): Future[MessageResponse] =
    db.run(actionImportSimpleDataStoreFile(f).transactionally)

  type U = FixedSqlAction[Int, slick.dbio.NoStream, slick.dbio.Effect.Write]

  private def insertSubreadSetRecord(dsId: Int, ds: SubreadServiceDataSet)
    : DBIOAction[Int, NoStream, Effect.Read with Effect.Write] = {

    dsSubread2 returning dsSubread2.map(_.id) forceInsert SubreadServiceSet(
      dsId,
      ds.uuid,
      ds.cellId,
      ds.metadataContextId,
      ds.wellSampleName,
      ds.wellName,
      ds.bioSampleName,
      ds.cellIndex,
      ds.instrumentName,
      ds.instrumentName,
      ds.runName,
      ds.instrumentControlVersion,
      ds.dnaBarcodeName
    )
  }

  private def insertHdfSubreadSetRecord(dsId: Int,
                                        ds: HdfSubreadServiceDataSet)
    : DBIOAction[Int, NoStream, Effect.Read with Effect.Write] = {
    dsHdfSubread2 forceInsert HdfSubreadServiceSet(
      dsId,
      ds.uuid,
      "cell-id",
      ds.metadataContextId,
      ds.wellSampleName,
      ds.wellName,
      ds.bioSampleName,
      ds.cellIndex,
      ds.instrumentName,
      ds.instrumentName,
      ds.runName,
      "instrument-ctr-version"
    )
  }

  private def checkForServiceMetaData(
      ds: DataStoreServiceFile,
      fileType: String,
      action: DBIO[Unit]): DBIO[MessageResponse] = {
    dsMetaData2.filter(_.uuid === ds.uuid).exists.result.flatMap {
      case false =>
        logger.info(
          s"Job id:${ds.jobId} Attempting to import DataStorefile/$fileType uud:${ds.uuid} path:${ds.path}")
        action.map(
          _ =>
            MessageResponse(
              s"Job id:${ds.jobId} DataStoreFile ${ds.uuid} already exists"))
      case true =>
        logger.info(
          s"Job id:${ds.jobId} DataStore file already imported. $fileType uuid:${ds.uuid} path:${ds.path}")
        DBIO.successful(
          MessageResponse(
            s"Job id:${ds.jobId} DataStoreFile ${ds.uuid} already exists"))
    }
  }

  /**
    * Update the numChildren (active+inactive) from dataset with a specific UUID.
    *
    * If the parent UUID does NOT exist, the update will be ignored.
    */
  private def actionUpdateNumChildren(parentUUID: UUID): DBIO[Int] = {
    def action0 =
      for {
        numChildren <- dsMetaData2
          .filter(_.parentUuid === parentUUID)
          .length
          .result
        _ <- dsMetaData2
          .filter(_.uuid === parentUUID)
          .map(d => (d.numChildren, d.updatedAt))
          .update((numChildren, JodaDateTime.now()))
      } yield numChildren

    dsMetaData2.filter(_.uuid === parentUUID).exists.result.flatMap {
      case true => action0
      case false =>
        logger.warn(
          s"Parent DataSet $parentUUID does not exist. Unable to update num children")
        DBIO.successful(0)
    }
  }

  /**
    * Util for the common usecase
    */
  private def actionUpdateNumChildrenOpt(parentUUID: Option[UUID]): DBIO[Int] =
    parentUUID
      .map(actionUpdateNumChildren)
      .getOrElse(DBIO.successful(0))

  private def actionImportSubreadSet(
      i: ImportAbleSubreadSet): DBIO[MessageResponse] = {

    val action0 = insertMetaData(i.file)
      .flatMap(x => insertSubreadSetRecord(x, i.file))

    val action: DBIO[Unit] = DBIO.seq(
      action0,
      datastoreServiceFiles += i.ds.file,
      actionUpdateNumChildrenOpt(i.file.parentUuid)
    )

    checkForServiceMetaData(i.ds.file, i.ds.file.fileTypeId, action)
  }

  /**
    *
    * The motivation for this is very unclear. This means a datastore file will potentially have the
    * the wrong job id. If the concurrency issues are fixed, I believe this should fail, not skip
    * the importing.
    *
    * This has three steps:
    *
    * 0  Precheck. Check if dataset meta exists and datastore file exists (see comments above)
    * 1. Import DataSetMeta
    * 2. Import Specific DataSet
    * 3. Import DataStoreFile
    */
  def importSubreadSet(i: ImportAbleSubreadSet): Future[MessageResponse] =
    db.run(actionImportSubreadSet(i).transactionally)

  private def actionImportHdfSubreadSet(
      i: ImportAbleHdfSubreadSet): DBIO[MessageResponse] = {

    val action0 = insertMetaData(i.file)
      .flatMap(x => insertHdfSubreadSetRecord(x, i.file))

    val action = DBIO.seq(
      action0,
      datastoreServiceFiles += i.ds.file,
      actionUpdateNumChildrenOpt(i.file.parentUuid)
    )

    checkForServiceMetaData(i.ds.file, i.ds.file.fileTypeId, action)
  }

  def importHdfSubreadSet(
      i: ImportAbleHdfSubreadSet): Future[MessageResponse] =
    db.run(actionImportHdfSubreadSet(i).transactionally)

  private def actionImportAlignmentSet(
      i: ImportAbleAlignmentSet): DBIO[MessageResponse] = {

    val action0 = insertMetaData(i.file).flatMap { id: Int =>
      dsAlignment2 forceInsert AlignmentServiceSet(id, i.file.uuid)
    }

    val action = DBIO.seq(
      action0,
      datastoreServiceFiles += i.ds.file,
      actionUpdateNumChildrenOpt(i.file.parentUuid)
    )

    checkForServiceMetaData(i.ds.file, i.ds.file.fileTypeId, action)
  }

  def importAlignmentSet(i: ImportAbleAlignmentSet): Future[MessageResponse] =
    db.run(actionImportAlignmentSet(i).transactionally)

  private def actionImportBarcodeSet(
      i: ImportAbleBarcodeSet): DBIO[MessageResponse] = {

    val action0 = insertMetaData(i.file).flatMap { id: Int =>
      dsBarcode2 forceInsert BarcodeServiceSet(id, i.file.uuid)
    }

    val action = DBIO.seq(
      action0,
      datastoreServiceFiles += i.ds.file,
      actionUpdateNumChildrenOpt(i.file.parentUuid)
    )

    checkForServiceMetaData(i.ds.file, i.ds.file.fileTypeId, action)
  }

  def importImportAbleBarcodeSet(
      i: ImportAbleBarcodeSet): Future[MessageResponse] =
    db.run(actionImportBarcodeSet(i).transactionally)

  private def actionImportConsensusReadSet(
      i: ImportAbleConsensusReadSet): DBIO[MessageResponse] = {

    val action0 = insertMetaData(i.file).flatMap { id: Int =>
      dsCCSread2 forceInsert ConsensusReadServiceSet(id, i.file.uuid)
    }

    val action = DBIO.seq(action0,
                          datastoreServiceFiles += i.ds.file,
                          actionUpdateNumChildrenOpt(i.file.parentUuid))

    checkForServiceMetaData(i.ds.file, i.ds.file.fileTypeId, action)
  }

  def importImportAbleConsensusReadSet(
      i: ImportAbleConsensusReadSet): Future[MessageResponse] =
    db.run(actionImportConsensusReadSet(i).transactionally)

  private def actionImportConsensusAlignmentSet(
      i: ImportAbleConsensusAlignmentSet): DBIO[MessageResponse] = {

    val action0 = insertMetaData(i.file).flatMap { id: Int =>
      dsCCSAlignment2 forceInsert ConsensusAlignmentServiceSet(id, i.file.uuid)
    }

    val action = DBIO.seq(action0,
                          datastoreServiceFiles += i.ds.file,
                          actionUpdateNumChildrenOpt(i.file.parentUuid))

    checkForServiceMetaData(i.ds.file, i.ds.file.fileTypeId, action)
  }

  def importImportAbleConsensusAlignmentSet(
      i: ImportAbleConsensusAlignmentSet): Future[MessageResponse] =
    db.run(actionImportConsensusAlignmentSet(i).transactionally)

  private def actionImportTranscriptSet(
      i: ImportAbleTranscriptSet): DBIO[MessageResponse] = {

    val action0 = insertMetaData(i.file).flatMap { id: Int =>
      dsTranscript2 forceInsert TranscriptServiceSet(id, i.file.uuid)
    }

    val action = DBIO.seq(action0,
                          datastoreServiceFiles += i.ds.file,
                          actionUpdateNumChildrenOpt(i.file.parentUuid))

    checkForServiceMetaData(i.ds.file, i.ds.file.fileTypeId, action)
  }

  def importImportAbleTranscriptSet(
      i: ImportAbleTranscriptSet): Future[MessageResponse] =
    db.run(actionImportTranscriptSet(i).transactionally)

  private def actionImportContigSet(
      i: ImportAbleContigSet): DBIO[MessageResponse] = {

    val action0 = insertMetaData(i.file).flatMap { id: Int =>
      dsContig2 forceInsert ContigServiceSet(id, i.file.uuid)
    }

    val action = DBIO.seq(action0,
                          datastoreServiceFiles += i.ds.file,
                          actionUpdateNumChildrenOpt(i.file.parentUuid))

    checkForServiceMetaData(i.ds.file, i.ds.file.fileTypeId, action)
  }

  def importImportAbleContigSet(
      i: ImportAbleContigSet): Future[MessageResponse] =
    db.run(actionImportContigSet(i).transactionally)

  private def actionImportReferenceSet(
      i: ImportAbleReferenceSet): DBIO[MessageResponse] = {

    val action0 = insertMetaData(i.file).flatMap { id: Int =>
      dsReference2 forceInsert ReferenceServiceSet(id,
                                                   i.file.uuid,
                                                   i.file.ploidy,
                                                   i.file.organism)
    }

    val action = DBIO.seq(action0,
                          datastoreServiceFiles += i.ds.file,
                          actionUpdateNumChildrenOpt(i.file.parentUuid))

    checkForServiceMetaData(i.ds.file, i.ds.file.fileTypeId, action)
  }

  def importReferenceSet(i: ImportAbleReferenceSet): Future[MessageResponse] =
    db.run(actionImportReferenceSet(i).transactionally)

  private def actionImportGmapReferenceSet(
      i: ImportAbleGmapReferenceSet): DBIO[MessageResponse] = {
    val ds = i.ds.file
    val action0 = insertMetaData(i.file).flatMap { id: Int =>
      dsGmapReference2 forceInsert GmapReferenceServiceSet(id,
                                                           i.file.uuid,
                                                           i.file.ploidy,
                                                           i.file.organism)
    }

    val action = DBIO.seq(action0, datastoreServiceFiles += ds)

    checkForServiceMetaData(i.ds.file, i.ds.file.fileTypeId, action)
  }

  def importGmapReferenceSet(
      i: ImportAbleGmapReferenceSet): Future[MessageResponse] =
    db.run(actionImportGmapReferenceSet(i).transactionally)

  private def actionImportReport(i: ImportAbleReport): DBIO[MessageResponse] = {
    val rpt = i.file
    val msg = s"Linked report ${rpt.uuid} to ${rpt.datasetUuids.size} datasets"
    val msg2 = s"Already added report ${rpt.uuid} to datastore"
    val toMsg = (m: String) => DBIO.successful(MessageResponse(m))
    val dsr = rpt.datasetUuids.toList.map(u => DataSetReport(u, rpt.uuid))
    def qInsertFiles =
      DBIO.seq(datastoreServiceFiles += i.ds.file, datasetReports ++= dsr)
    datastoreServiceFiles
      .filter(_.uuid === i.ds.file.uuid)
      .exists
      .result
      .flatMap {
        case false => qInsertFiles.andThen(toMsg(msg))
        case true => toMsg(msg2)
      }
  }

  private def importImportAbleReport(
      i: ImportAbleReport): Future[MessageResponse] =
    db.run(actionImportReport(i).transactionally)

  private def importImportAbleFile[T >: ImportAbleServiceFile](
      f: T): Future[MessageResponse] = {
    f match {
      case x: ImportAbleDataStoreFile => importSimpleDataStoreFile(x)
      case x: ImportAbleSubreadSet => importSubreadSet(x)
      case x: ImportAbleHdfSubreadSet => importHdfSubreadSet(x)
      case x: ImportAbleAlignmentSet => importAlignmentSet(x)
      case x: ImportAbleBarcodeSet => importImportAbleBarcodeSet(x)
      case x: ImportAbleConsensusReadSet =>
        importImportAbleConsensusReadSet(x)
      case x: ImportAbleConsensusAlignmentSet =>
        importImportAbleConsensusAlignmentSet(x)
      case x: ImportAbleContigSet => importImportAbleContigSet(x)
      case x: ImportAbleReferenceSet => importReferenceSet(x)
      case x: ImportAbleGmapReferenceSet => importGmapReferenceSet(x)
      case x: ImportAbleTranscriptSet => importImportAbleTranscriptSet(x)
      case x: ImportAbleReport => importImportAbleReport(x)
    }
  }

  private def actionImportAbleFile[T >: ImportAbleServiceFile](
      f: T): DBIO[MessageResponse] = {
    f match {
      case x: ImportAbleDataStoreFile => actionImportSimpleDataStoreFile(x)
      case x: ImportAbleSubreadSet => actionImportSubreadSet(x)
      case x: ImportAbleHdfSubreadSet => actionImportHdfSubreadSet(x)
      case x: ImportAbleAlignmentSet => actionImportAlignmentSet(x)
      case x: ImportAbleBarcodeSet => actionImportBarcodeSet(x)
      case x: ImportAbleConsensusReadSet => actionImportConsensusReadSet(x)
      case x: ImportAbleConsensusAlignmentSet =>
        actionImportConsensusAlignmentSet(x)
      case x: ImportAbleContigSet => actionImportContigSet(x)
      case x: ImportAbleReferenceSet => actionImportReferenceSet(x)
      case x: ImportAbleGmapReferenceSet => actionImportGmapReferenceSet(x)
      case x: ImportAbleTranscriptSet => actionImportTranscriptSet(x)
      case x: ImportAbleReport => actionImportReport(x)
    }
  }

  private def actionImportAbleFiles[T >: ImportAbleServiceFile](
      files: Seq[T]): DBIO[Seq[MessageResponse]] =
    DBIO.sequence(files.map(actionImportAbleFile))

  private def importImportAbleFiles[T >: ImportAbleServiceFile](
      files: Seq[T]): Future[Seq[MessageResponse]] =
    db.run(actionImportAbleFiles(files).transactionally)

  private def toDataStoreServiceFile(f: DataStoreFile,
                                     jobId: Int,
                                     jobUUID: UUID,
                                     isActive: Boolean) = {
    val now = JodaDateTime.now()
    DataStoreServiceFile(f.uniqueId,
                         f.fileTypeId,
                         f.sourceId,
                         f.fileSize,
                         f.createdAt,
                         f.modifiedAt,
                         now,
                         f.path,
                         jobId,
                         jobUUID,
                         f.name,
                         f.description,
                         isActive)
  }

  def andLog(sx: String): Future[String] = Future {
    logger.info(sx)
    sx
  }

  private def validateBarcodeSetFile(
      path: Path,
      maxNumRecords: Int): Future[MessageResponse] = {
    val barcodeSet = BarcodeSetLoader.load(path)
    val numRecords: Int = barcodeSet.getDataSetMetadata.getNumRecords
    if (numRecords > maxNumRecords)
      Future.failed(UnprocessableEntityError(
        s"Cannot import: Barcode Set with $numRecords barcodes contains more than the maximum of $maxNumRecords barcodes"))
    else
      Future.successful(
        MessageResponse(
          s"Valid barcode set with $numRecords <= $maxNumRecords"))
  }

  /**
    * General Interface to do pre-validation of DataStoreServiceFiles prior to import.
    *
    * @param files
    * @param maxNumRecords
    * @return
    */
  private def validateServiceDataStoreFiles(
      files: Seq[DataStoreServiceFile],
      maxNumRecords: Int): Future[Seq[DataStoreServiceFile]] =
    for {
      barcodeFiles <- Future.successful(
        files.filter(_.fileTypeId == FileTypes.DS_BARCODE.fileTypeId))
      _ <- Future.sequence(barcodeFiles.map(b =>
        validateBarcodeSetFile(Paths.get(b.path), maxNumRecords)))
    } yield files

  /**
    *
    * THIS IS THE NEW PUBLIC INTERFACE THAT SHOULD BE USED from the Job interface to
    * import datastore files.
    *
    * With regards to how the project is is propagated, there's a bit of disconnect
    * in the models. If an explicit project id is not passed in, the project id
    * of the companion job (from the jobUUID) project id will be used.
    *
    */
  def importDataStoreFiles(
      files: Seq[DataStoreFile],
      jobId: UUID,
      projectId: Option[Int] = None): Future[Seq[MessageResponse]] = {

    val importPrefix = "Attempting to import"
    val successPrefix = "Successfully imported"

    def toMessage(prefix: String, ix: Int): String = {
      files match {
        case item :: Nil =>
          s"$prefix datastore for job $ix file type:${item.fileTypeId} uuid:${item.uniqueId} ${item.path}"
        case _ =>
          s"$prefix datastore files for job $ix ${files.length} files"
      }
    }

    // Note, Due to the IO heavy nature, this needs to be wrapped in an explicit blocking
    // operation to be used within a Future
    def loadServiceFiles[T >: ImportAbleServiceFile](
        serviceFiles: Seq[DataStoreServiceFile],
        createdBy: Option[String],
        projectId: Int): Future[Seq[T]] = Future {
      blocking {
        serviceFiles.map(dsf =>
          loadImportAbleFile(DsServiceJobFile(dsf, createdBy, projectId)))
      }
    }

    for {
      job <- getJobById(jobId)
      serviceFiles <- Future.successful(files.map(f =>
        toDataStoreServiceFile(f, job.id, job.uuid, isActive = true)))
      _ <- andLog(toMessage(importPrefix, job.id))
      _ <- validateServiceDataStoreFiles(
        serviceFiles,
        JobConstants.BARCODE_SET_MAX_NUM_RECORDS)
      importAbleFiles <- loadServiceFiles(serviceFiles,
                                          job.createdBy,
                                          projectId.getOrElse(job.projectId))
      messages <- importImportAbleFiles(importAbleFiles)
      _ <- andLog(toMessage(successPrefix, job.id))
    } yield messages
  }

  /**
    * PUBLIC INTERFACE THAT SHOULD BE USED for importing a Single DataStoreFile
    *
    * See comments above with regards to the project id propagation.
    */
  def importDataStoreFile(
      file: DataStoreFile,
      jobId: UUID,
      projectId: Option[Int] = None): Future[MessageResponse] = {
    importDataStoreFiles(Seq(file), jobId, projectId).map(
      _ =>
        MessageResponse(
          s"Successfully imported ${file.uniqueId} ${file.fileTypeId}"))
  }

  def getDataSetById(id: IdAble): Future[DataSetMetaDataSet] =
    db.run(qDsMetaDataById(id).result.headOption)
      .flatMap(failIfNone(s"Unable to find dataSet with id ${id.toIdString}"))

  def deleteDataSetById(
      id: IdAble,
      setIsActive: Boolean = false): Future[MessageResponse] = {
    db.run(
        qDsMetaDataById(id)
          .map(d => (d.isActive, d.updatedAt))
          .update(setIsActive, JodaDateTime.now()))
      .map(_ =>
        MessageResponse(
          s"Successfully set isActive=$setIsActive for dataset ${id.toIdString}"))
  }

  def updateDataSetById(
      id: IdAble,
      path: String,
      setIsActive: Boolean = true): Future[MessageResponse] = {
    val msg =
      s"Successfully set path=$path and isActive=$setIsActive for dataset ${id.toIdString}"
    db.run(
        qDsMetaDataById(id)
          .map(d => (d.isActive, d.path, d.updatedAt))
          .update(setIsActive, path, JodaDateTime.now()))
      .map(_ => MessageResponse(msg))
  }

  def updateSubreadSetDetails(
      id: IdAble,
      bioSampleName: Option[String],
      wellSampleName: Option[String]): Future[MessageResponse] = {
    getSubreadDataSetById(id).flatMap { ds =>
      val newBioSample = bioSampleName.getOrElse(ds.bioSampleName)
      val newWellSample = wellSampleName.getOrElse(ds.wellSampleName)
      db.run {
        DBIO.seq(
          dsSubread2
            .filter(_.id === ds.id)
            .map(s => (s.bioSampleName, s.wellSampleName))
            .update(newBioSample, newWellSample),
          qDsMetaDataById(id)
            .map(d => Tuple1(d.updatedAt))
            .update(Tuple1(JodaDateTime.now()))
        )
      } map { _ =>
        val msg =
          s"Set bioSampleName=$newBioSample and wellSampleName=$newWellSample"
        MessageResponse(msg)
      }
    }
  }

  //FIXME. Make this IdAble
  def getDataSetJobsByUUID(id: UUID): Future[Seq[EngineJob]] = {
    val q = engineJobsDataSets.filter(_.datasetUUID === id) join engineJobs
      .filter(_.isActive) on (_.jobId === _.id)
    db.run(q.result).map(_.map(x => x._2))
  }

  /**
    * These conversion funcs are necessary to take the base dataset metadata model to
    * the flattened out data model that will be returned from the Web Services
    *
    * @param t1 base dataset metadata
    * @param t2 Subread data model (e.g., subreadset specific db table)
    * @return
    */
  private def toSds(t1: DataSetMetaDataSet,
                    t2: SubreadServiceSet): SubreadServiceDataSet =
    SubreadServiceDataSet(
      t1.id,
      t1.uuid,
      t1.name,
      t1.path,
      t1.createdAt,
      t1.updatedAt,
      t1.importedAt,
      t1.numRecords,
      t1.totalLength,
      t1.version,
      t1.comments,
      t1.tags,
      t1.md5,
      t2.instrumentName,
      t2.instrumentControlVersion,
      t2.metadataContextId,
      t2.wellSampleName,
      t2.wellName,
      t2.bioSampleName,
      t2.cellIndex,
      t2.cellId,
      t2.runName,
      t1.createdBy,
      t1.jobId,
      t1.projectId,
      t2.dnaBarcodeName,
      t1.parentUuid,
      isActive = t1.isActive,
      numChildren = t1.numChildren
    )

  /**
    * Get a SubreadServiceDataSet by Id
    *
    * @param id Int or UUID of dataset
    * @return
    */
  def getSubreadDataSetById(id: IdAble): Future[SubreadServiceDataSet] = {
    val q = qDsMetaDataById(id) join dsSubread2 on (_.id === _.id)
    //val qParentCount = dsMetaData2.filter
    db.run(q.result.headOption)
      .map(_.map(x => toSds(x._1, x._2)))
      .flatMap(
        failIfNone(s"Unable to find SubreadSet with id ${id.toIdString}"))
  }

  // This might be wrapped in a Try to fail the future downstream with a better HTTP error code
  private def subreadToDetails(ds: SubreadServiceDataSet): String =
    DataSetJsonUtils.subreadSetToJson(
      DataSetLoader.loadSubreadSet(Paths.get(ds.path)))

  def getSubreadDataSetDetailsById(id: IdAble): Future[String] =
    getSubreadDataSetById(id).flatMap { x =>
      Future(blocking(subreadToDetails(x)))
    }

  /**
    * Note, that limit is not imposed here. Consumers should explicitly
    * set this (perhaps after doing a join on another dataset table).
    *
    */
  private def qDsMetaDataBySearch(c: DataSetSearchCriteria) = {

    type Q = Query[DataSetMetaT, DataSetMetaT#TableElementType, Seq]
    type QF = (Q => Q)
    type QOF = (Q => Option[Q])

    val qInActive: QOF = { q =>
      c.isActive.map { value =>
        value match {
          case true => q.filter(_.isActive === true)
          case false => q.filter(_.isActive === false)
        }
      }
    }

    // This needs to be clarified and collapsed back into the SearchCriteria API
    // in the new standard way.
    val qOldProjectIds: QOF = { q =>
      if (c.projectIds.nonEmpty) Some(q.filter(_.projectId inSet c.projectIds))
      else None
    }

    val qById: QOF = { q =>
      c.id
        .map {
          case IntEqQueryOperator(value) => q.filter(_.id === value)
          case IntInQueryOperator(values) => q.filter(_.id inSet values)
          case IntGteQueryOperator(value) => q.filter(_.id >= value)
          case IntGtQueryOperator(value) => q.filter(_.id > value)
          case IntLteQueryOperator(value) => q.filter(_.id <= value)
          case IntLtQueryOperator(value) => q.filter(_.id < value)
        }
    }

    // By Name
    val qByName: QOF = { q =>
      c.name
        .map {
          case StringEqQueryOperator(value) => q.filter(_.name === value)
          case StringMatchQueryOperator(value) =>
            q.filter(_.name like s"%${value}%")
          case StringInQueryOperator(values) => q.filter(_.name inSet values)
        }
    }

    // By DataSet Path
    val qByPath: QOF = { q =>
      c.path
        .map {
          case StringEqQueryOperator(value) => q.filter(_.path === value)
          case StringMatchQueryOperator(value) =>
            q.filter(_.path like s"%${value}%")
          case StringInQueryOperator(values) => q.filter(_.path inSet values)
        }
    }

    // By NumRecords
    val qByNumRecords: QOF = { q =>
      c.numRecords
        .map {
          case LongEqQueryOperator(value) => q.filter(_.numRecords === value)
          case LongInQueryOperator(values) =>
            q.filter(_.numRecords inSet values)
          case LongGteQueryOperator(value) => q.filter(_.numRecords >= value)
          case LongGtQueryOperator(value) => q.filter(_.numRecords > value)
          case LongLteQueryOperator(value) => q.filter(_.numRecords <= value)
          case LongLtQueryOperator(value) => q.filter(_.numRecords < value)
        }
    }

    // By TotalLength
    val qByTotaLength: QOF = { q =>
      c.totalLength
        .map {
          case LongEqQueryOperator(value) => q.filter(_.totalLength === value)
          case LongInQueryOperator(values) =>
            q.filter(_.totalLength inSet values)
          case LongGteQueryOperator(value) => q.filter(_.totalLength >= value)
          case LongGtQueryOperator(value) => q.filter(_.totalLength > value)
          case LongLteQueryOperator(value) => q.filter(_.totalLength <= value)
          case LongLtQueryOperator(value) => q.filter(_.totalLength < value)
        }
    }

    // By Job Id
    val qByJobId: QOF = { q =>
      c.jobId
        .map {
          case IntEqQueryOperator(value) => q.filter(_.jobId === value)
          case IntInQueryOperator(values) => q.filter(_.jobId inSet values)
          case IntGteQueryOperator(value) => q.filter(_.jobId >= value)
          case IntGtQueryOperator(value) => q.filter(_.jobId > value)
          case IntLteQueryOperator(value) => q.filter(_.jobId <= value)
          case IntLtQueryOperator(value) => q.filter(_.jobId < value)
        }
    }
    // By Version
    val qByVersion: QOF = { q =>
      c.version
        .map {
          case StringEqQueryOperator(value) => q.filter(_.version === value)
          case StringMatchQueryOperator(value) =>
            q.filter(_.version like s"%${value}%")
          case StringInQueryOperator(values) =>
            q.filter(_.version inSet values)
        }
    }

    // By Created By
    val qByCreatedBy: QOF = { q =>
      c.createdBy
        .map {
          case StringEqQueryOperator(value) => q.filter(_.createdBy === value)
          case StringMatchQueryOperator(value) =>
            q.filter(_.createdBy like s"%${value}%")
          case StringInQueryOperator(values) =>
            q.filter(_.createdBy inSet values)
        }
    }

    val qByCreatedAt: QOF = { q =>
      c.createdAt
        .map {
          case DateTimeEqOperator(value) => q.filter(_.createdAt === value)
          case DateTimeGtOperator(value) => q.filter(_.createdAt > value)
          case DateTimeGteOperator(value) => q.filter(_.createdAt >= value)
          case DateTimeLtOperator(value) => q.filter(_.createdAt < value)
          case DateTimeLteOperator(value) => q.filter(_.createdAt <= value)
        }
    }

    val qByUpdatedAt: QOF = { q =>
      c.updatedAt
        .map {
          case DateTimeEqOperator(value) => q.filter(_.updatedAt === value)
          case DateTimeGtOperator(value) => q.filter(_.updatedAt > value)
          case DateTimeGteOperator(value) => q.filter(_.updatedAt >= value)
          case DateTimeLtOperator(value) => q.filter(_.updatedAt < value)
          case DateTimeLteOperator(value) => q.filter(_.updatedAt <= value)
        }
    }

    val qByImportedAt: QOF = { q =>
      c.importedAt
        .map {
          case DateTimeEqOperator(value) => q.filter(_.importedAt === value)
          case DateTimeGtOperator(value) => q.filter(_.importedAt > value)
          case DateTimeGteOperator(value) => q.filter(_.importedAt >= value)
          case DateTimeLtOperator(value) => q.filter(_.importedAt < value)
          case DateTimeLteOperator(value) => q.filter(_.importedAt <= value)
        }
    }

    val qByUUID: QOF = { q =>
      c.uuid
        .map {
          case UUIDEqOperator(value) => q.filter(_.uuid === value)
          case UUIDInOperator(values) => q.filter(_.uuid inSet values)
        }
    }

    val qByParentUUID: QOF = { q =>
      c.parentUuid
        .map {
          case UUIDOptionEqOperator(value) => q.filter(_.parentUuid === value)
          case UUIDOptionInOperator(values) =>
            q.filter(_.parentUuid inSet values)
          case UUIDNullQueryOperator() => q.filter(_.parentUuid.isEmpty)
        }
    }

    val qNumChildren: QOF = { q =>
      c.numChildren
        .map {
          case IntEqQueryOperator(value) => q.filter(_.numChildren === value)
          case IntInQueryOperator(values) =>
            q.filter(_.numChildren inSet values)
          case IntGteQueryOperator(value) => q.filter(_.numChildren >= value)
          case IntGtQueryOperator(value) => q.filter(_.numChildren > value)
          case IntLteQueryOperator(value) => q.filter(_.numChildren <= value)
          case IntLtQueryOperator(value) => q.filter(_.numChildren < value)
        }
    }

    val queries: Seq[QOF] = Seq(
      qInActive,
      qOldProjectIds,
      qById,
      qByName,
      qByPath,
      qByNumRecords,
      qByTotaLength,
      qByJobId,
      qByVersion,
      qByCreatedBy,
      qByCreatedAt,
      qByUpdatedAt,
      qByImportedAt,
      qByUUID,
      qByParentUUID,
      qNumChildren
    )

    val qTotal: QF = { q =>
      queries.foldLeft(q) { case (acc, qf) => qf(acc).getOrElse(acc) }
    }

    qTotal(dsMetaData2)
  }

  def getSubreadDataSets(
      c: DataSetSearchCriteria): Future[Seq[SubreadServiceDataSet]] = {
    val q0 = qDsMetaDataBySearch(c)
    val q1 = q0 join dsSubread2 on (_.id === _.id)
    val q2 = c.marker.map(i => q1.drop(i)).getOrElse(q1)
    db.run(q2.sortBy(_._1.id.desc).take(c.limit).result)
      .map(_.map(x => toSds(x._1, x._2)))
  }

  /**
    * Convert to the Service Data model
    *
    * See the SubreadSet toSds for more context.
    */
  private def toR(t1: DataSetMetaDataSet,
                  t2: ReferenceServiceSet): ReferenceServiceDataSet =
    ReferenceServiceDataSet(
      t1.id,
      t1.uuid,
      t1.name,
      t1.path,
      t1.createdAt,
      t1.updatedAt,
      t1.importedAt,
      t1.numRecords,
      t1.totalLength,
      t1.version,
      t1.comments,
      t1.tags,
      t1.md5,
      t1.createdBy,
      t1.jobId,
      t1.projectId,
      t2.ploidy,
      t2.organism,
      isActive = t1.isActive,
      numChildren = t1.numChildren
    )

  def getReferenceDataSets(
      c: DataSetSearchCriteria): Future[Seq[ReferenceServiceDataSet]] = {
    val q0 = qDsMetaDataBySearch(c)
    val q1 = q0 join dsReference2 on (_.id === _.id)
    val q2 = c.marker.map(i => q1.drop(i)).getOrElse(q1)
    db.run(q2.sortBy(_._1.id.desc).take(c.limit).result)
      .map(_.map(x => toR(x._1, x._2)))
  }

  def getReferenceDataSetById(id: IdAble): Future[ReferenceServiceDataSet] = {
    val q = qDsMetaDataById(id) join dsReference2 on (_.id === _.id)
    db.run(q.result.headOption)
      .map(_.map(x => toR(x._1, x._2)))
      .flatMap(
        failIfNone(s"Unable to find ReferenceSet with id ${id.toIdString}"))
  }

  private def referenceToDetails(ds: ReferenceServiceDataSet): String =
    DataSetJsonUtils.referenceSetToJson(
      DataSetLoader.loadReferenceSet(Paths.get(ds.path)))

  def getReferenceDataSetDetailsById(id: IdAble): Future[String] =
    getReferenceDataSetById(id).map(referenceToDetails)

  // See the SubreadSet toDs for context
  private def toGmapR(
      t1: DataSetMetaDataSet,
      t2: GmapReferenceServiceSet): GmapReferenceServiceDataSet =
    GmapReferenceServiceDataSet(
      t1.id,
      t1.uuid,
      t1.name,
      t1.path,
      t1.createdAt,
      t1.updatedAt,
      t1.importedAt,
      t1.numRecords,
      t1.totalLength,
      t1.version,
      t1.comments,
      t1.tags,
      t1.md5,
      t1.createdBy,
      t1.jobId,
      t1.projectId,
      t2.ploidy,
      t2.organism,
      isActive = t1.isActive,
      numChildren = t1.numChildren
    )

  def getGmapReferenceDataSets(
      c: DataSetSearchCriteria): Future[Seq[GmapReferenceServiceDataSet]] = {
    val q0 = qDsMetaDataBySearch(c)
    val q1 = q0 join dsGmapReference2 on (_.id === _.id)
    val q2 = c.marker.map(i => q1.drop(i)).getOrElse(q1)
    db.run(q2.sortBy(_._1.id.desc).take(c.limit).result)
      .map(_.map(x => toGmapR(x._1, x._2)))
  }

  def getGmapReferenceDataSetById(
      id: IdAble): Future[GmapReferenceServiceDataSet] = {
    val q = qDsMetaDataById(id) join dsGmapReference2 on (_.id === _.id)
    db.run(q.result.headOption)
      .map(_.map(x => toGmapR(x._1, x._2)))
      .flatMap(failIfNone(
        s"Unable to find GmapReferenceSet with id ${id.toIdString}"))
  }

  private def gmapReferenceToDetails(ds: GmapReferenceServiceDataSet): String =
    DataSetJsonUtils.gmapReferenceSetToJson(
      DataSetLoader.loadGmapReferenceSet(Paths.get(ds.path)))

  def getGmapReferenceDataSetDetailsById(id: IdAble): Future[String] =
    getGmapReferenceDataSetById(id).map(gmapReferenceToDetails)

  def getHdfDataSets(
      c: DataSetSearchCriteria): Future[Seq[HdfSubreadServiceDataSet]] = {
    val q0 = qDsMetaDataBySearch(c)
    val q1 = q0 join dsHdfSubread2 on (_.id === _.id)
    val q2 = c.marker.map(i => q1.drop(i)).getOrElse(q1)
    db.run(q2.sortBy(_._1.id.desc).take(c.limit).result)
      .map(_.map(x => toHds(x._1, x._2)))
  }

  private def toHds(t1: DataSetMetaDataSet,
                    t2: HdfSubreadServiceSet): HdfSubreadServiceDataSet =
    HdfSubreadServiceDataSet(
      t1.id,
      t1.uuid,
      t1.name,
      t1.path,
      t1.createdAt,
      t1.updatedAt,
      t1.importedAt,
      t1.numRecords,
      t1.totalLength,
      t1.version,
      t1.comments,
      t1.tags,
      t1.md5,
      t2.instrumentName,
      t2.metadataContextId,
      t2.wellSampleName,
      t2.wellName,
      t2.bioSampleName,
      t2.cellIndex,
      t2.runName,
      t1.createdBy,
      t1.jobId,
      t1.projectId,
      isActive = t1.isActive,
      numChildren = t1.numChildren
    )

  def getHdfDataSetById(id: IdAble): Future[HdfSubreadServiceDataSet] = {
    val q = qDsMetaDataById(id) join dsHdfSubread2 on (_.id === _.id)
    db.run(q.result.headOption)
      .map(_.map(x => toHds(x._1, x._2)))
      .flatMap(failIfNone(s"Unable to find HdfSubreadSet with id `$id`"))
  }

  private def hdfsubreadToDetails(ds: HdfSubreadServiceDataSet): String =
    DataSetJsonUtils.hdfSubreadSetToJson(
      DataSetLoader.loadHdfSubreadSet(Paths.get(ds.path)))

  def getHdfDataSetDetailsById(id: IdAble): Future[String] =
    getHdfDataSetById(id).map(hdfsubreadToDetails)

  private def toA(t1: DataSetMetaDataSet) =
    AlignmentServiceDataSet(
      t1.id,
      t1.uuid,
      t1.name,
      t1.path,
      t1.createdAt,
      t1.updatedAt,
      t1.importedAt,
      t1.numRecords,
      t1.totalLength,
      t1.version,
      t1.comments,
      t1.tags,
      t1.md5,
      t1.createdBy,
      t1.jobId,
      t1.projectId,
      isActive = t1.isActive,
      numChildren = t1.numChildren
    )

  def getAlignmentDataSets(
      c: DataSetSearchCriteria): Future[Seq[AlignmentServiceDataSet]] = {
    val q0 = qDsMetaDataBySearch(c)
    // DataSets that don't extend the base model don't really need to do a join.
    val q1 = q0 join dsAlignment2 on (_.id === _.id)
    val q2 = c.marker.map(i => q1.drop(i)).getOrElse(q1)
    db.run(q2.sortBy(_._1.id.desc).take(c.limit).result)
      .map(_.map(x => toA(x._1)))
  }

  def getAlignmentDataSetById(id: IdAble): Future[AlignmentServiceDataSet] = {
    val q = qDsMetaDataById(id) join dsAlignment2 on (_.id === _.id)
    db.run(q.result.headOption)
      .map(_.map(x => toA(x._1)))
      .flatMap(failIfNone(s"Unable to find AlignmentSet with id `$id`"))
  }

  private def alignmentSetToDetails(ds: AlignmentServiceDataSet): String = {
    DataSetJsonUtils.alignmentSetToJson(
      DataSetLoader.loadAlignmentSet(Paths.get(ds.path)))
  }

  def getAlignmentDataSetDetailsById(id: IdAble): Future[String] =
    getAlignmentDataSetById(id).map(alignmentSetToDetails)

  /*--- CONSENSUS READS ---*/

  private def toCCSread(t1: DataSetMetaDataSet) =
    ConsensusReadServiceDataSet(
      t1.id,
      t1.uuid,
      t1.name,
      t1.path,
      t1.createdAt,
      t1.updatedAt,
      t1.importedAt,
      t1.numRecords,
      t1.totalLength,
      t1.version,
      t1.comments,
      t1.tags,
      t1.md5,
      t1.createdBy,
      t1.jobId,
      t1.projectId,
      isActive = t1.isActive,
      numChildren = t1.numChildren
    )

  // TODO(smcclellan): limit is never uesed. add `.take(limit)`?
  def getConsensusReadDataSets(
      c: DataSetSearchCriteria): Future[Seq[ConsensusReadServiceDataSet]] = {
    val q0 = qDsMetaDataBySearch(c)
    val q1 = q0 join dsCCSread2 on (_.id === _.id)
    val q2 = c.marker.map(i => q1.drop(i)).getOrElse(q1)
    db.run(q2.sortBy(_._1.id.desc).take(c.limit).result)
      .map(_.map(x => toCCSread(x._1)))
  }

  def getConsensusReadDataSetById(
      id: IdAble): Future[ConsensusReadServiceDataSet] = {
    val q = qDsMetaDataById(id) join dsCCSread2 on (_.id === _.id)
    db.run(q.result.headOption)
      .map(_.map(x => toCCSread(x._1)))
      .flatMap(failIfNone(s"Unable to find ConsensusReadSet with id `$id`"))
  }

  private def consensusReadSetToDetails(
      ds: ConsensusReadServiceDataSet): String =
    DataSetJsonUtils.consensusSetToJson(
      DataSetLoader.loadConsensusReadSet(Paths.get(ds.path)))

  def getConsensusReadDataSetDetailsById(id: IdAble): Future[String] =
    getConsensusReadDataSetById(id).map(consensusReadSetToDetails)

  /*--- CONSENSUS ALIGNMENTS ---*/

  private def toCCSA(t1: DataSetMetaDataSet) =
    ConsensusAlignmentServiceDataSet(
      t1.id,
      t1.uuid,
      t1.name,
      t1.path,
      t1.createdAt,
      t1.updatedAt,
      t1.importedAt,
      t1.numRecords,
      t1.totalLength,
      t1.version,
      t1.comments,
      t1.tags,
      t1.md5,
      t1.createdBy,
      t1.jobId,
      t1.projectId,
      isActive = t1.isActive,
      numChildren = t1.numChildren
    )

  def getConsensusAlignmentDataSets(c: DataSetSearchCriteria)
    : Future[Seq[ConsensusAlignmentServiceDataSet]] = {
    val q0 = qDsMetaDataBySearch(c)
    val q1 = q0 join dsCCSAlignment2 on (_.id === _.id)
    val q2 = c.marker.map(i => q1.drop(i)).getOrElse(q1)
    db.run(q2.sortBy(_._1.id.desc).take(c.limit).result)
      .map(_.map(x => toCCSA(x._1)))
  }

  def getConsensusAlignmentDataSetById(
      id: IdAble): Future[ConsensusAlignmentServiceDataSet] = {
    val q = qDsMetaDataById(id) join dsCCSAlignment2 on (_.id === _.id)
    db.run(q.result.headOption)
      .map(_.map(x => toCCSA(x._1)))
      .flatMap(failIfNone(
        s"Unable to find ConsensusAlignmentSet with uuid ${id.toIdString}"))
  }

  private def consensusAlignmentSetToDetails(
      ds: ConsensusAlignmentServiceDataSet): String =
    DataSetJsonUtils.consensusAlignmentSetToJson(
      DataSetLoader.loadConsensusAlignmentSet(Paths.get(ds.path)))

  def getConsensusAlignmentDataSetDetailsById(id: IdAble): Future[String] =
    getConsensusAlignmentDataSetById(id).map(consensusAlignmentSetToDetails)

  /*--- TRANSCRIPTS ---*/
  private def toT(t1: DataSetMetaDataSet) =
    TranscriptServiceDataSet(
      t1.id,
      t1.uuid,
      t1.name,
      t1.path,
      t1.createdAt,
      t1.updatedAt,
      t1.importedAt,
      t1.numRecords,
      t1.totalLength,
      t1.version,
      t1.comments,
      t1.tags,
      t1.md5,
      t1.createdBy,
      t1.jobId,
      t1.projectId,
      isActive = t1.isActive,
      numChildren = t1.numChildren
    )

  def getTranscriptDataSets(
      c: DataSetSearchCriteria): Future[Seq[TranscriptServiceDataSet]] = {
    val q0 = qDsMetaDataBySearch(c)
    val q1 = q0 join dsTranscript2 on (_.id === _.id)
    val q2 = c.marker.map(i => q1.drop(i)).getOrElse(q1)
    db.run(q2.sortBy(_._1.id.desc).take(c.limit).result)
      .map(_.map(x => toT(x._1)))
  }

  def getTranscriptDataSetById(id: IdAble): Future[TranscriptServiceDataSet] = {
    val q = qDsMetaDataById(id) join dsTranscript2 on (_.id === _.id)
    db.run(q.result.headOption)
      .map(_.map(x => toT(x._1)))
      .flatMap(
        failIfNone(s"Unable to find TranscriptSet with uuid ${id.toIdString}"))
  }

  private def transcriptSetToDetails(ds: TranscriptServiceDataSet): String =
    DataSetJsonUtils.transcriptSetToJson(
      DataSetLoader.loadTranscriptSet(Paths.get(ds.path)))

  def getTranscriptDataSetDetailsById(id: IdAble): Future[String] =
    getTranscriptDataSetById(id).map(transcriptSetToDetails)

  /*--- BARCODES ---*/

  private def toB(t1: DataSetMetaDataSet) =
    BarcodeServiceDataSet(
      t1.id,
      t1.uuid,
      t1.name,
      t1.path,
      t1.createdAt,
      t1.updatedAt,
      t1.importedAt,
      t1.numRecords,
      t1.totalLength,
      t1.version,
      t1.comments,
      t1.tags,
      t1.md5,
      t1.createdBy,
      t1.jobId,
      t1.projectId,
      isActive = t1.isActive,
      numChildren = t1.numChildren
    )

  def getBarcodeDataSets(
      c: DataSetSearchCriteria): Future[Seq[BarcodeServiceDataSet]] = {
    val q0 = qDsMetaDataBySearch(c)
    val q1 = q0 join dsBarcode2 on (_.id === _.id)
    val q2 = c.marker.map(i => q1.drop(i)).getOrElse(q1)
    db.run(q2.sortBy(_._1.id.desc).take(c.limit).result)
      .map(_.map(x => toB(x._1)))
  }

  def getBarcodeDataSetById(id: IdAble): Future[BarcodeServiceDataSet] = {
    val q = qDsMetaDataById(id) join dsBarcode2 on (_.id === _.id)
    db.run(q.result.headOption)
      .map(_.map(x => toB(x._1)))
      .flatMap(failIfNone(s"Unable to find BarcodeSet with id `$id`"))
  }

  private def barcodeSetToDetails(ds: BarcodeServiceDataSet): String =
    DataSetJsonUtils.barcodeSetToJson(
      DataSetLoader.loadBarcodeSet(Paths.get(ds.path)))

  def getBarcodeDataSetDetailsById(id: IdAble): Future[String] =
    getBarcodeDataSetById(id).map(barcodeSetToDetails)

  /*--- CONTIGS ---*/

  private def toCtg(t1: DataSetMetaDataSet) =
    ContigServiceDataSet(
      t1.id,
      t1.uuid,
      t1.name,
      t1.path,
      t1.createdAt,
      t1.updatedAt,
      t1.importedAt,
      t1.numRecords,
      t1.totalLength,
      t1.version,
      t1.comments,
      t1.tags,
      t1.md5,
      t1.createdBy,
      t1.jobId,
      t1.projectId,
      isActive = t1.isActive,
      numChildren = t1.numChildren
    )

  def getContigDataSets(
      c: DataSetSearchCriteria): Future[Seq[ContigServiceDataSet]] = {
    val q0 = qDsMetaDataBySearch(c)
    val q1 = q0 join dsContig2 on (_.id === _.id)
    val q2 = c.marker.map(i => q1.drop(i)).getOrElse(q1)
    db.run(q2.sortBy(_._1.id.desc).take(c.limit).result)
      .map(_.map(x => toCtg(x._1)))
  }

  def getContigDataSetById(id: IdAble): Future[ContigServiceDataSet] = {
    val q = qDsMetaDataById(id) join dsContig2 on (_.id === _.id)
    db.run(q.result.headOption)
      .map(_.map(x => toCtg(x._1)))
      .flatMap(failIfNone(s"Unable to find ContigSet with id `$id`"))
  }

  private def contigSetToDetails(ds: ContigServiceDataSet): String =
    DataSetJsonUtils.contigSetToJson(
      DataSetLoader.loadContigSet(Paths.get(ds.path)))

  def getContigDataSetDetailsById(id: IdAble): Future[String] =
    getContigDataSetById(id).map(contigSetToDetails)

  /*--- DATASTORE ---*/

  private def toDataStoreJobFile(x: DataStoreServiceFile) =
    DataStoreJobFile(x.jobUUID,
                     DataStoreFile(x.uuid,
                                   x.sourceId,
                                   x.fileTypeId,
                                   x.fileSize,
                                   x.createdAt,
                                   x.modifiedAt,
                                   x.path,
                                   name = x.name,
                                   description = x.description))

  def getDataStoreFilesByJobId(i: IdAble): Future[Seq[DataStoreJobFile]] = {
    db.run {
      val q = for {
        engineJob <- qEngineJobById(i)
        dsFiles <- datastoreServiceFiles.filter(_.jobId === engineJob.id)
      } yield dsFiles
      q.result.map(_.map(toDataStoreJobFile))
    }
  }

  /**
    * Get a DataStore Service File by DataStore file UUID
    *
    * @param id Unique Id of the datastore file
    * @return
    */
  def getDataStoreFile(id: UUID): Future[DataStoreJobFile] =
    db.run(
        datastoreServiceFiles
          .filter(_.uuid === id)
          .result
          .headOption
          .map(_.map(toDataStoreJobFile)))
      .flatMap(failIfNone(s"Unable to find DataStore File with UUID $id"))

  def deleteDataStoreFile(
      id: UUID,
      setIsActive: Boolean = false): Future[MessageResponse] = {
    db.run(
        datastoreServiceFiles
          .filter(_.uuid === id)
          .map(f => (f.isActive, f.modifiedAt))
          .update(setIsActive, JodaDateTime.now()))
      .map(_ =>
        MessageResponse(
          s"Successfully set datastore file $id to isActive=$setIsActive"))
  }

  /**
    * Update the Path and the Activity of a DataStore file
    *
    * FIXME. These should be using Path, not String
    *
    * @param id          Unique id of the datastore file
    * @param path        Absolute path to the file
    * @param setIsActive activity of the file
    * @return
    */
  def updateDataStoreFile(
      id: UUID,
      path: Option[String] = None,
      fileSize: Option[Long] = None,
      setIsActive: Boolean = true): Future[MessageResponse] = {
    val now = JodaDateTime.now()
    val q1 = datastoreServiceFiles.filter(_.uuid === id)
    val q2 = List(
      path.map(
        p =>
          q1.map(f => (f.isActive, f.path, f.modifiedAt))
            .update((setIsActive, p, now))),
      fileSize.map(
        fsize =>
          q1.map(f => (f.isActive, f.fileSize, f.modifiedAt))
            .update((setIsActive, fsize, now)))
    ).flatten
    db.run(DBIO.sequence(q2))
      .map(_ =>
        MessageResponse(
          s"Successfully set datastore file $id to path=$path, fileSize=$fileSize and isActive=$setIsActive"))
  }

  /**
    * Delete a DataStore File by data store file UUID
    *
    * @param id UUID of the datastore file
    * @return
    */
  def deleteDataStoreJobFile(id: UUID): Future[MessageResponse] = {
    def addOptionalDelete(
        ds: Option[DataStoreServiceFile]): Future[MessageResponse] = {
      // 1 of 3: delete the DataStoreServiceFile, if it isn't already in the DB
      val deleteDsFile = ds
        .map(dsFile => DBIO.from(deleteDataStoreFile(id)))
        .getOrElse(DBIO.from(Future.successful(
          MessageResponse(s"No datastore file with ID $id found"))))

      // 2 of 3: insert of the data set, if it is a known/supported file type
      val optionalDelete = ds
        .map { dsFile =>
          DataSetMetaTypes
            .toDataSetType(dsFile.fileTypeId)
            .map(_ => DBIO.from(deleteDataSetById(dsFile.uuid)))
            .getOrElse(DBIO.from(Future.successful(MessageResponse(
              s"File type ${dsFile.fileTypeId} is not a dataset, so no metadata to delete."))))
        }
        .getOrElse(DBIO.from(Future.successful(MessageResponse(
          s"No datastore file, so no dataset metadata to delete"))))

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

  def getJobChildrenByJobId(i: IdAble): Future[Seq[EngineJob]] = {
    val jobDsJoin = for {
      j <- qEngineJobById(i)
      d <- datastoreServiceFiles if d.jobId === j.id
      e <- engineJobsDataSets if e.datasetUUID === d.uuid
      c <- engineJobs if ((c.id === e.jobId) && c.isActive)
    } yield (d, c)
    db.run(jobDsJoin.result).map(_.filter(_._1.fileExists).map(_._2))
  }

  def getSystemSummary(header: String = "System Summary"): Future[String] = {
    for {
      ssets <- db.run(
        (dsMetaData2 join dsSubread2 on (_.id === _.id)).length.result)
      rsets <- db.run(
        (dsMetaData2 join dsReference2 on (_.id === _.id)).length.result)
      asets <- db.run(
        (dsMetaData2 join dsAlignment2 on (_.id === _.id)).length.result)
      jobCounts <- db.run(
        engineJobs
          .groupBy(x => (x.jobTypeId, x.state))
          .map({
            case ((jobType, state), list) => (jobType, state, list.length)
          })
          .result)
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
         | ${jobCounts
           .map(x => f"${x._1}%25s  ${x._2}%10s  ${x._3}%6d")
           .mkString("\n         | ")}
         |--------
         |Total JobEvents      : $jobEvents
         |Total entryPoints    : $entryPoints
         |Total DataStoreFiles : $dsFiles
       """.stripMargin
  }

  private def sendEulaToEventManager(eula: EulaRecord): Unit = {
    sendEventToManager[EulaRecord](eula)
  }

  def addEulaRecord(eulaRecord: EulaRecord): Future[EulaRecord] = {
    val f = db.run(eulas += eulaRecord).map(_ => eulaRecord)
    f.foreach(sendEulaToEventManager)
    f
  }

  def getEulas: Future[Seq[EulaRecord]] = db.run(eulas.result)

  def getEulaByVersion(version: String): Future[EulaRecord] =
    db.run(eulas.filter(_.smrtlinkVersion === version).result.headOption)
      .flatMap(failIfNone(s"Unable to find Eula version `$version`"))

  def updateEula(version: String,
                 update: EulaUpdateRecord): Future[EulaRecord] = {
    val emsg = s"Unable to find Eula for SMRT Link version $version"

    val getEula = eulas.filter(_.smrtlinkVersion === version)

    val updater =
      (update.enableInstallMetrics, update.enableJobMetrics) match {
        case (Some(i), Some(j)) =>
          getEula
            .map(e => (e.enableInstallMetrics, e.enableJobMetrics))
            .update(i, j)
        case (Some(i), None) =>
          getEula.map(e => e.enableInstallMetrics).update(i)
        case (None, Some(j)) =>
          getEula.map(e => e.enableJobMetrics).update(j)
        case (None, None) =>
          DBIO.successful(Unit)
      }

    val f1 = db
      .run(updater.andThen(getEula.result.headOption).transactionally)
      .flatMap(failIfNone(emsg))

    f1.foreach(sendEulaToEventManager)

    f1
  }

  def removeEula(version: String): Future[Int] =
    db.run(eulas.filter(_.smrtlinkVersion === version).delete)

  /**
    * This overlaps with the RunDao this should be fixed.
    *
    * This will check for look for Acq/CollectionMeta(s) status that
    * have Failed and will update the Collection's companion Child Job(s) of a
    * MultiJob to FAILED.
    *
    * NOTE, this does NOT update the MultiJob state to FAILED, this only
    * deals with updating Children Jobs.
    *
    * @param runId Run UniqueId
    */
  def checkForMultiJobsFromRun(runId: UUID): Future[String] = {

    val errorMessage = s"Run $runId Failed. Unable to Run MultiJob"

    val childJobStates: Set[AnalysisJobStates.JobStates] = Set(
      AnalysisJobStates.CREATED)

    val q1 = {
      for {
        collections <- qGetRunCollectionMetadataByRunIdAndState(
          runId,
          SmrtLinkConstants.FAILED_ACQ_STATES)
        failedDataSets <- engineJobsDataSets.filter(
          _.datasetUUID === collections.uniqueId)
        // Can only update Child Jobs that are in the CREATED state.
        childJobs <- qGetEngineJobsByStates(childJobStates)
          .filter(_.isMultiJob === false)
          .filter(_.parentMultiJobId.isDefined)
          .filter(_.id === failedDataSets.jobId)
      } yield childJobs.id
    }

    def updateChildJobsToFailed(jobIds: Seq[Int]) =
      DBIO.sequence(jobIds.map(ix =>
        qUpdateJobState(ix, AnalysisJobStates.FAILED, errorMessage)))

    val q2 = q1.result.flatMap(updateChildJobsToFailed)

    def toS(j: EngineJob) =
      s"ChildJob:${j.id} ${j.state} ${j.parentMultiJobId.map(i => s"(parent $i)").getOrElse("")}"

    def toJ(jobs: Seq[EngineJob]) =
      jobs.map(toS).reduceLeftOption(_ ++ ", " ++ _).getOrElse("")

    db.run(q2.transactionally)
      .map(jobs => s"Checked for MultiJobs from Run $runId. ${toJ(jobs)}")
  }

}

/**
  * Core SMRT Link Data Access Object for interacting with DataSets, Projects and Jobs.
  *
  * @param db Postgres Database Config
  * @param resolver Resolver that will determine where to write jobs to
  * @param listeners Event/Message listeners (e.g., accepted Eula, Job changed state, MultiJob Submitted)
  */
class JobsDao(val db: Database,
              val resolver: JobResourceResolver,
              private val listeners: Seq[ActorRef] = Seq.empty[ActorRef])
    extends DalComponent
    with SmrtLinkConstants
    with EventComponent
    with ProjectDataStore
    with JobDataStore
    with DataSetStore
    with DataSetTypesDao {

  import JobModels._

  private val eventListeners: mutable.MutableList[ActorRef] =
    new mutable.MutableList()

  eventListeners ++= listeners

  // This is added to get around potential circular dependencies of the listener
  def addListener(listener: ActorRef): Unit = {
    logger.info(s"Adding Listener $listener to $this")
    eventListeners += listener
  }

  override def sendEventToManager[T](message: T): Unit = {
    eventListeners.foreach(a => a ! message)
  }

}

trait JobsDaoProvider {
  this: DalProvider
    with SmrtLinkConfigProvider
    with EventManagerActorProvider =>

  val jobsDao: Singleton[JobsDao] =
    Singleton(() => new JobsDao(db(), jobResolver()))
}
