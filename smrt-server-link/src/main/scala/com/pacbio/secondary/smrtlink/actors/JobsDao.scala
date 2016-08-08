package com.pacbio.secondary.smrtlink.actors

import java.nio.file.{Files, Paths}
import java.util.UUID

import com.google.common.annotations.VisibleForTesting
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.services.PacBioServiceErrors.ResourceNotFoundError
import com.pacbio.database.Database
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

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}
import scala.util.control.NonFatal
import slick.driver.SQLiteDriver.api._

import scala.concurrent.duration._


trait DalProvider {
  val db: Singleton[Database]
}

trait SmrtLinkDalProvider extends DalProvider {
  this: SmrtLinkConfigProvider =>

  override val db: Singleton[Database] = Singleton(() => new Database(dbURI()))
}

@VisibleForTesting
trait TestDalProvider extends DalProvider {
  override val db: Singleton[Database] = Singleton(() => {
    // in-memory DB for tests
    new Database(dbURI = "jdbc:sqlite::memory:")
  })
}

/**
 * SQL Datastore backend configuration and db connection
 */
trait DalComponent {
  val db: Database
}

trait ProjectDataStore extends LazyLogging {
  this: DalComponent with SmrtLinkConstants =>

  def getProjects(limit: Int = 100): Future[Seq[Project]] =
    db.run(projects.take(limit).result)

  def getProjectById(projId: Int): Future[Option[Project]] =
    db.run(projects.filter(_.id === projId).result.headOption)

  def createProject(projReq: ProjectRequest, ownerLogin: String): Future[Project] = {
    val owner = ProjectRequestUser(RequestUser(ownerLogin), "Owner")
    val members = projReq.members.getOrElse(List())
    val withOwner = members.filter(_.user.login != ownerLogin) ++ List(owner)
    val requestWithOwner = projReq.copy(members = Some(withOwner))

    val now = JodaDateTime.now()
    val proj = Project(-99, projReq.name, projReq.description, "CREATED", now, now)
    val insert = projects returning projects.map(_.id) into((p, i) => p.copy(id = i)) += proj
    val fullAction = insert.flatMap(proj => setMembersAndDatasets(proj, requestWithOwner))
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
      projectsUsers ++= members.map(m => ProjectUser(projId, m.user.login, m.role))
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
      projects.filter(_.id === projId).result.headOption.flatMap { maybeProj =>
        maybeProj match {
          case Some(proj) => setMembersAndDatasets(proj, projReq).map(Some(_))
          case None => DBIO.successful(None)
        }
      }
    )

    db.run(fullAction.transactionally)
  }

  def getProjectUsers(projId: Int): Future[Seq[ProjectUser]] =
    db.run(projectsUsers.filter(_.projectId === projId).result)

  def getDatasetsByProject(projId: Int): Future[Seq[DataSetMetaDataSet]] =
    db.run(dsMetaData2.filter(_.projectId === projId).result)

  def getUserProjects(login: String): Future[Seq[UserProjectResponse]] = {
    val join = for {
      (pu, p) <- projectsUsers join projects on (_.projectId === _.id)
      if pu.login === login
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
      p <- projects if pu.projectId === p.id
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
}

/**
 * SQL Driven JobEngine datastore Backend
 */
trait JobDataStore extends JobEngineDaoComponent with LazyLogging {
  this: DalComponent =>

  val DEFAULT_MAX_DATASET_LIMIT = 5000

  val resolver: JobResourceResolver
  // This is local queue of the Runnable Job instances. Once they're turned into an EngineJob, and submitted, it
  // should be deleted. This should probably just be stored as a json blob in the database.
  var _runnableJobs: mutable.Map[UUID, RunnableJobWithId]

  /**
   * This is the pbscala engine required interface. The `createJob` method is the prefered method to add new jobs
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

    val job = EngineJob(-99, runnableJob.job.uuid, name, comment, createdAt, createdAt, AnalysisJobStates.CREATED, jobTypeId, path, jsonSettings, None)

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

  override def getJobByUUID(jobId: UUID): Future[Option[EngineJob]] =
    db.run(engineJobs.filter(_.uuid === jobId).result.headOption)

  override def getJobById(jobId: Int): Future[Option[EngineJob]] =
    db.run(engineJobs.filter(_.id === jobId).result.headOption)

  def getNextRunnableJob: Future[Either[NoAvailableWorkError, RunnableJob]] = {
    val noWork = NoAvailableWorkError("No Available work to run.")
    _runnableJobs.values.find(_.state == AnalysisJobStates.CREATED) match {
      case Some(job) =>
        getJobById(job.id).map {
          case Some(j) =>
            _runnableJobs.remove(j.uuid)
            Right(RunnableJob(job.job, j.state))
          case None => Left(noWork)
        }
      case None => Future(Left(noWork))
    }
  }

  override def getNextRunnableJobWithId: Future[Either[NoAvailableWorkError, RunnableJobWithId]] = {
    val noWork = NoAvailableWorkError("No Available work to run.")
    _runnableJobs.values.find(_.state == AnalysisJobStates.CREATED) match {
      case Some(job) =>
        getJobById(job.id).map {
          case Some(j) =>
            _runnableJobs.remove(j.uuid)
            Right(RunnableJobWithId(job.id, job.job, j.state))
          case None => Left(noWork)
        }
      case None => Future(Left(noWork))
    }
  }

  /**
   * Get all the Job Events accosciated with a specific job
   */
  override def getJobEventsByJobId(jobId: Int): Future[Seq[JobEvent]] =
    db.run(jobEvents.filter(_.jobId === jobId).result)

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

  def updateJobStateByUUID(
      jobId: UUID,
      state: AnalysisJobStates.JobStates,
      message: String): Future[MessageResponse] =
    db.run {
      val now = JodaDateTime.now()
      engineJobs.filter(_.uuid === jobId).result.headOption.flatMap {
        case Some(job) =>
          DBIO.seq(
            engineJobs.filter(_.uuid === jobId).map(j => (j.state, j.updatedAt)).update(state, now),
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
      createdBy: Option[String]): Future[EngineJob] = {

    // This should really be Option[String]
    val path = ""
    // TODO(smcclellan): Use dependency-injected Clock instance
    val createdAt = JodaDateTime.now()

    val engineJob = EngineJob(-9999, uuid, name, description, createdAt, createdAt, AnalysisJobStates.CREATED, jobTypeId, path, jsonSetting, createdBy)

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


  // TODO(smcclellan): limit is never uesed. add `.take(limit)`?
  override def getJobs(limit: Int = 100): Future[Seq[EngineJob]] = db.run(engineJobs.result)

  def getJobsByTypeId(jobTypeId: String): Future[Seq[EngineJob]] =
    db.run(engineJobs.filter(_.jobTypeId === jobTypeId).result)

  def getJobEntryPoints(jobId: Int): Future[Seq[EngineJobEntryPoint]] =
    db.run(engineJobsDataSets.filter(_.jobId === jobId).result)

}

/**
 * Model extend DataSet Component to have 'extended' importing of datastore files by file type
 * (e.g., DataSet, Report)
 *
 * Mixin the Job Component because the files depended on the job
 */
trait DataSetStore extends DataStoreComponent with LazyLogging {
  this: JobDataStore with DalComponent =>

  val DEFAULT_PROJECT_ID = 1
  val DEFAULT_USER_ID = 1

  /**
   * Importing of DataStore File by Job Int Id
   */
  def insertDataStoreFileById(ds: DataStoreFile, jobId: Int): Future[MessageResponse] = getJobById(jobId).flatMap {
    case Some(job) => insertDataStoreByJob(job, ds)

    case None => throw new ResourceNotFoundError(s"Failed to import $ds Failed to find job id $jobId")
  }

  def insertDataStoreFileByUUID(ds: DataStoreFile, jobId: UUID): Future[MessageResponse] = getJobByUUID(jobId).flatMap {
    case Some(job) => insertDataStoreByJob(job, ds)
    case None => throw new ResourceNotFoundError(s"Failed to import $ds Failed to find job id $jobId")
  }

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

  def getDataStoreFiles2: Future[Seq[DataStoreServiceFile]] = db.run(datastoreServiceFiles.result)

  def getDataStoreFileByUUID2(uuid: UUID): Future[Option[DataStoreServiceFile]] =
    db.run(datastoreServiceFiles.filter(_.uuid === uuid).result.headOption)

  def getDataStoreServiceFilesByJobId(i: Int): Future[Seq[DataStoreServiceFile]] =
    db.run(datastoreServiceFiles.filter(_.jobId === i).result)

  def getDataStoreReportFilesByJobId(jobId: Int): Future[Seq[DataStoreReportFile]] =
    db.run {
      datastoreServiceFiles
        .filter(_.jobId === jobId)
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

  def getDataSetTypeById(typeId: String): Future[Option[ServiceDataSetMetaType]] =
    db.run(datasetTypes.filter(_.id === typeId).result.headOption)

  def getDataSetTypes: Future[Seq[ServiceDataSetMetaType]] = db.run(datasetTypes.result)

  // Get All DataSets mixed in type. Only metadata
  def getDataSetByUUID(id: UUID): Future[Option[DataSetMetaDataSet]] =
    db.run(datasetMetaTypeByUUID(id).result.headOption)

  def getDataSetById(id: Int): Future[Option[DataSetMetaDataSet]] =
    db.run(datasetMetaTypeById(id).result.headOption)

  def datasetMetaTypeById(id: Int) = dsMetaData2.filter(_.id === id)

  def datasetMetaTypeByUUID(id: UUID) = dsMetaData2.filter(_.uuid === id)

  // util for converting to the old model
  def toSds(t1: DataSetMetaDataSet, t2: SubreadServiceSet): SubreadServiceDataSet =
    SubreadServiceDataSet(t1.id, t1.uuid, t1.name, t1.path, t1.createdAt, t1.updatedAt, t1.numRecords, t1.totalLength,
      t1.version, t1.comments, t1.tags, t1.md5, t2.instrumentName, t2.metadataContextId, t2.wellSampleName, t2.wellName, t2.bioSampleName, t2.cellIndex, t2.runName, t1.userId, t1.jobId, t1.projectId)

  // FIXME. REALLY, REALLY need to generalize this.
  def getSubreadDataSetById(id: Int): Future[Option[SubreadServiceDataSet]] =
    db.run {
      val q = datasetMetaTypeById(id) join dsSubread2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toSds(x._1, x._2)))
    }

  private def subreadToDetails(ds: Future[Option[SubreadServiceDataSet]]): Future[Option[String]] =
    ds.map(_.map(x => DataSetJsonUtils.subreadSetToJson(DataSetLoader.loadSubreadSet(Paths.get(x.path)))))


  def getSubreadDataSetDetailsById(id: Int): Future[Option[String]] = subreadToDetails(getSubreadDataSetById(id))

  def getSubreadDataSetDetailsByUUID(uuid: UUID): Future[Option[String]] = subreadToDetails(getSubreadDataSetByUUID(uuid))

  def getSubreadDataSetByUUID(id: UUID): Future[Option[SubreadServiceDataSet]] =
    db.run {
      val q = datasetMetaTypeByUUID(id) join dsSubread2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toSds(x._1, x._2)))
    }

  def getSubreadDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT): Future[Seq[SubreadServiceDataSet]] =
    db.run {
      val q = dsMetaData2 join dsSubread2 on (_.id === _.id)
      q.result.map(_.map(x => toSds(x._1, x._2)))
    }

  // conversion util for keeping the old interface
  def toR(t1: DataSetMetaDataSet, t2: ReferenceServiceSet): ReferenceServiceDataSet =
    ReferenceServiceDataSet(t1.id, t1.uuid, t1.name, t1.path, t1.createdAt, t1.updatedAt, t1.numRecords, t1.totalLength,
      t1.version, t1.comments, t1.tags, t1.md5, t1.userId, t1.jobId, t1.projectId, t2.ploidy, t2.organism)

  def getReferenceDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT): Future[Seq[ReferenceServiceDataSet]] =
    db.run {
      val q = dsMetaData2 join dsReference2 on (_.id === _.id)
      q.result.map(_.map(x => toR(x._1, x._2)))
    }

  def getReferenceDataSetById(id: Int): Future[Option[ReferenceServiceDataSet]] =
    db.run {
      val q = datasetMetaTypeById(id) join dsReference2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toR(x._1, x._2)))
    }

  private def referenceToDetails(ds: Future[Option[ReferenceServiceDataSet]]): Future[Option[String]] =
    ds.map(_.map(x => DataSetJsonUtils.referenceSetToJson(DataSetLoader.loadReferenceSet(Paths.get(x.path)))))

  def getReferenceDataSetDetailsById(id: Int): Future[Option[String]] = referenceToDetails(getReferenceDataSetById(id))

  def getReferenceDataSetDetailsByUUID(uuid: UUID): Future[Option[String]] =
    referenceToDetails(getReferenceDataSetByUUID(uuid))

  def getReferenceDataSetByUUID(id: UUID): Future[Option[ReferenceServiceDataSet]] =
    db.run {
      val q = datasetMetaTypeByUUID(id) join dsReference2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toR(x._1, x._2)))
    }

  def toGmapR(t1: DataSetMetaDataSet, t2: GmapReferenceServiceSet): GmapReferenceServiceDataSet =
    GmapReferenceServiceDataSet(t1.id, t1.uuid, t1.name, t1.path, t1.createdAt, t1.updatedAt, t1.numRecords, t1.totalLength,
      t1.version, t1.comments, t1.tags, t1.md5, t1.userId, t1.jobId, t1.projectId, t2.ploidy, t2.organism)

  def getGmapReferenceDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT): Future[Seq[GmapReferenceServiceDataSet]] =
    db.run {
      val q = dsMetaData2 join dsGmapReference2 on (_.id === _.id)
      q.result.map(_.map(x => toGmapR(x._1, x._2)))
    }

  def getGmapReferenceDataSetById(id: Int): Future[Option[GmapReferenceServiceDataSet]] =
    db.run {
      val q = datasetMetaTypeById(id) join dsGmapReference2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toGmapR(x._1, x._2)))
    }
  private def gmapReferenceToDetails(ds: Future[Option[GmapReferenceServiceDataSet]]): Future[Option[String]] =
    ds.map(_.map(x => DataSetJsonUtils.gmapReferenceSetToJson(DataSetLoader.loadGmapReferenceSet(Paths.get(x.path)))))

  def getGmapReferenceDataSetDetailsById(id: Int): Future[Option[String]] = gmapReferenceToDetails(getGmapReferenceDataSetById(id))

  def getGmapReferenceDataSetDetailsByUUID(uuid: UUID): Future[Option[String]] =
    gmapReferenceToDetails(getGmapReferenceDataSetByUUID(uuid))

  def getGmapReferenceDataSetByUUID(id: UUID): Future[Option[GmapReferenceServiceDataSet]] =
    db.run {
      val q = datasetMetaTypeByUUID(id) join dsGmapReference2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toGmapR(x._1, x._2)))
    }

  def getHdfDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT): Future[Seq[HdfSubreadServiceDataSet]] =
    db.run {
      val q = dsMetaData2 join dsHdfSubread2 on (_.id === _.id)
      q.result.map(_.map(x => toHds(x._1, x._2)))
    }

  def toHds(t1: DataSetMetaDataSet, t2: HdfSubreadServiceSet): HdfSubreadServiceDataSet =
    HdfSubreadServiceDataSet(t1.id, t1.uuid, t1.name, t1.path, t1.createdAt, t1.updatedAt, t1.numRecords, t1.totalLength,
      t1.version, t1.comments, t1.tags, t1.md5, t2.instrumentName, t2.metadataContextId, t2.wellSampleName, t2.wellName, t2.bioSampleName, t2.cellIndex, t2.runName, t1.userId, t1.jobId, t1.projectId)

  def getHdfDataSetById(id: Int): Future[Option[HdfSubreadServiceDataSet]] =
    db.run {
      val q = datasetMetaTypeById(id) join dsHdfSubread2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toHds(x._1, x._2)))
    }

  private def hdfsubreadToDetails(ds: Future[Option[HdfSubreadServiceDataSet]]): Future[Option[String]] =
    ds.map(_.map(x => DataSetJsonUtils.hdfSubreadSetToJson(DataSetLoader.loadHdfSubreadSet(Paths.get(x.path)))))

  def getHdfDataSetDetailsById(id: Int): Future[Option[String]] = hdfsubreadToDetails(getHdfDataSetById(id))

  def getHdfDataSetDetailsByUUID(uuid: UUID): Future[Option[String]] = hdfsubreadToDetails(getHdfDataSetByUUID(uuid))

  def getHdfDataSetByUUID(id: UUID): Future[Option[HdfSubreadServiceDataSet]] =
    db.run {
      val q = datasetMetaTypeByUUID(id) join dsHdfSubread2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toHds(x._1, x._2)))
    }

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

  def getAlignmentDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT): Future[Seq[AlignmentServiceDataSet]] =
    db.run {
      val q = dsMetaData2 join dsAlignment2 on (_.id === _.id)
      q.result.map(_.map(x => toA(x._1)))
    }

  def getAlignmentDataSetById(id: Int): Future[Option[AlignmentServiceDataSet]] =
    db.run {
      val q = datasetMetaTypeById(id) join dsAlignment2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toA(x._1)))
    }

  def getAlignmentDataSetByUUID(id: UUID): Future[Option[AlignmentServiceDataSet]] =
    db.run {
      val q = datasetMetaTypeByUUID(id) join dsAlignment2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toA(x._1)))
    }

  def toCCSread(t1: DataSetMetaDataSet) =
    ConsensusReadServiceDataSet(t1.id, t1.uuid, t1.name, t1.path, t1.createdAt, t1.updatedAt, t1.numRecords, t1.totalLength,
      t1.version, t1.comments, t1.tags, t1.md5, t1.userId, t1.jobId, t1.projectId)

  // TODO(smcclellan): limit is never uesed. add `.take(limit)`?
  def getCCSDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT): Future[Seq[ConsensusReadServiceDataSet]] = {
    val query = dsMetaData2 join dsCCSread2 on (_.id === _.id)
    db.run(query.result.map(_.map(x => toCCSread(x._1))))
  }

  def getCCSDataSetById(id: Int): Future[Option[ConsensusReadServiceDataSet]] =
    db.run {
      val q = datasetMetaTypeById(id) join dsCCSread2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toCCSread(x._1)))
    }

  def getCCSDataSetByUUID(id: UUID): Future[Option[ConsensusReadServiceDataSet]] =
    db.run {
      val q = datasetMetaTypeByUUID(id) join dsCCSread2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toCCSread(x._1)))
    }

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

  def getConsensusAlignmentDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT): Future[Seq[ConsensusAlignmentServiceDataSet]] =
    db.run {
      val q = dsMetaData2 join dsCCSAlignment2 on (_.id === _.id)
      q.result.map(_.map(x => toCCSA(x._1)))
    }

  def getConsensusAlignmentDataSetById(id: Int): Future[Option[ConsensusAlignmentServiceDataSet]] =
    db.run {
      val q = datasetMetaTypeById(id) join dsCCSAlignment2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toCCSA(x._1)))
    }

  def getConsensusAlignmentDataSetByUUID(id: UUID): Future[Option[ConsensusAlignmentServiceDataSet]] =
    db.run {
      val q = datasetMetaTypeByUUID(id) join dsCCSAlignment2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toCCSA(x._1)))
    }

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

  def getBarcodeDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT): Future[Seq[BarcodeServiceDataSet]] = {
    val query = dsMetaData2 join dsBarcode2 on (_.id === _.id)
    db.run(query.result.map(_.map(x => toB(x._1))))
  }

  def getBarcodeDataSetById(id: Int): Future[Option[BarcodeServiceDataSet]] =
    db.run {
      val q = datasetMetaTypeById(id) join dsBarcode2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toB(x._1)))
    }

  def getBarcodeDataSetByUUID(id: UUID): Future[Option[BarcodeServiceDataSet]] =
    db.run {
      val q = datasetMetaTypeByUUID(id) join dsBarcode2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toB(x._1)))
    }

  private def barcodeSetToDetails(ds: Future[Option[BarcodeServiceDataSet]]): Future[Option[String]] = {
    ds.map(_.map(x => DataSetJsonUtils.barcodeSetToJson(DataSetLoader.loadBarcodeSet(Paths.get(x.path)))))
  }

  def getBarcodeDataSetDetailsById(id: Int): Future[Option[String]] = barcodeSetToDetails(getBarcodeDataSetById(id))

  def getBarcodeDataSetDetailsByUUID(uuid: UUID): Future[Option[String]] = barcodeSetToDetails(getBarcodeDataSetByUUID(uuid))

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

  def getContigDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT): Future[Seq[ContigServiceDataSet]] = {
    val query = dsMetaData2 join dsContig2 on (_.id === _.id)
    db.run(query.result.map(_.map(x => toCtg(x._1))))
  }

  def getContigDataSetById(id: Int): Future[Option[ContigServiceDataSet]] =
    db.run {
      val q = datasetMetaTypeById(id) join dsContig2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toCtg(x._1)))
    }

  def getContigDataSetByUUID(id: UUID): Future[Option[ContigServiceDataSet]] =
    db.run {
      val q = datasetMetaTypeByUUID(id) join dsContig2 on (_.id === _.id)
      q.result.headOption.map(_.map(x => toCtg(x._1)))
    }

  def toDataStoreJobFile(x: DataStoreServiceFile) =
    // This is has the wrong job uuid
    DataStoreJobFile(x.uuid, DataStoreFile(x.uuid, x.sourceId, x.fileTypeId, x.fileSize, x.createdAt, x.modifiedAt, x.path, name=x.name, description=x.description))

  def getDataStoreFilesByJobId(i: Int): Future[Seq[DataStoreJobFile]] =
    db.run(datastoreServiceFiles.filter(_.jobId === i).result.map(_.map(toDataStoreJobFile)))

  // Need to clean all this all up. There's inconsistencies all over the place.
  override def getDataStoreFiles: Future[Seq[DataStoreJobFile]] =
    db.run(datastoreServiceFiles.result.map(_.map(toDataStoreJobFile)))

  override def getDataStoreFileByUUID(uuid: UUID): Future[Option[DataStoreJobFile]] =
    db.run(datastoreServiceFiles.filter(_.uuid === uuid).result.headOption.map(_.map(toDataStoreJobFile)))

  override def getDataStoreFilesByJobUUID(uuid: UUID): Future[Seq[DataStoreJobFile]] =
    db.run {
      val q = for {
        engineJob <- engineJobs.filter(_.uuid === uuid)
        dsFiles <- datastoreServiceFiles.filter(_.jobId === engineJob.id)
      } yield dsFiles
      q.result.map(_.map(toDataStoreJobFile))
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
}

class JobsDao(val db: Database, engineConfig: EngineConfig, val resolver: JobResourceResolver) extends JobEngineDataStore
with DalComponent
with SmrtLinkConstants
with ProjectDataStore
with JobDataStore
with DataSetStore {

  import JobModels._

  var _runnableJobs = mutable.Map[UUID, RunnableJobWithId]()
}

trait JobsDaoProvider {
  this: DalProvider with SmrtLinkConfigProvider =>

  val jobsDao: Singleton[JobsDao] = Singleton(() => new JobsDao(db(), jobEngineConfig(), jobResolver()))
}
