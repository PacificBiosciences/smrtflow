package com.pacbio.secondary.smrtlink.actors

import java.io.File
import java.nio.file.{Files, Paths}
import java.util.UUID

import com.google.common.annotations.VisibleForTesting
import com.pacbio.common.actors.UserDao
import com.pacbio.common.dependency.Singleton
import com.pacbio.common.services.PacBioServiceErrors.ResourceNotFoundError
import com.pacbio.secondary.analysis.constants.FileTypes
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes
import com.pacbio.secondary.analysis.datasets.DataSetMetaTypes.DataSetMetaType
import com.pacbio.secondary.analysis.datasets.io.{DataSetJsonUtils, DataSetLoader}
import com.pacbio.secondary.analysis.engine.CommonMessages
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
import scala.language.postfixOps
import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.jdbc.meta.MTable
import scala.util.{Failure, Success, Try}

import org.flywaydb.core.Flyway


// TODO(smcclellan): Move this class into the c.p.s.s.database package? Or eliminate it?
class Dal(val dbURI: String) {
  val flyway = new Flyway()
  flyway.setDataSource(dbURI, "", "")
  flyway.setBaselineOnMigrate(true)
  flyway.setBaselineVersionAsString("1")

  lazy val db = Database.forURL(dbURI, driver = "org.sqlite.JDBC")
}

trait DalProvider {
  val dal: Singleton[Dal]
}

trait SmrtLinkDalProvider extends DalProvider {
  this: SmrtLinkConfigProvider =>

  override val dal: Singleton[Dal] = Singleton(() => new Dal(dbURI()))
}

@VisibleForTesting
trait TestDalProvider extends DalProvider {
  override val dal: Singleton[Dal] = Singleton(() => {
    val dbFile = File.createTempFile("test_dal_", ".db")
    dbFile.deleteOnExit()

    val dbURI = s"jdbc:sqlite:file:${dbFile.getCanonicalPath}?cache=shared"

    new Dal(dbURI)
  })
}

/**
 * SQL Datastore backend configuration and db connection
 */
trait DalComponent {
  val dal: Dal
}

trait ProjectDataStore extends LazyLogging {
  this: DalComponent with SmrtLinkConstants =>

  def getProjects(limit: Int = 100): Seq[Project] = {
    dal.db.withSession { implicit session =>
      projects.take(limit).list
    }
  }

  def getProjectById(projId: Int): Option[Project] = {
    dal.db.withSession { implicit session =>
      projects.filter(_.id === projId).firstOption
    }
  }

  def createProject(opts: ProjectRequest): Project = {
    dal.db.withSession { implicit session =>
      val now = JodaDateTime.now()
      val proj = Project(-99, opts.name, opts.description, "CREATED", now, now)
      val projId = (projects returning projects.map(_.id)) += proj
      proj.copy(id = projId)
    }
  }

  def updateProject(projId: Int, opts: ProjectRequest): Option[Project] = {
    dal.db.withSession { implicit session =>
      val now = JodaDateTime.now()
      val proj = projects.filter(_.id === projId)
      proj.map(p => (p.name, p.state, p.description, p.updatedAt))
          .update(opts.name, opts.state, opts.description, now)
      proj.firstOption
    }
  }

  def getProjectUsers(projId: Int): Seq[ProjectUser] = {
    dal.db.withSession { implicit session =>
      projectsUsers.filter(_.projectId === projId).run
    }
  }

  def addProjectUser(projId: Int, user: ProjectUserRequest): MessageResponse = {
    dal.db.withTransaction { implicit session =>
      projectsUsers.filter(x => x.projectId === projId && x.login === user.login).delete
      projectsUsers += ProjectUser(projId, user.login, user.role)
      MessageResponse(s"added user ${user.login} with role ${user.role} to project $projId")
    }
  }

  def deleteProjectUser(projId: Int, user: String): MessageResponse = {
    dal.db.withSession { implicit session =>
      projectsUsers.filter(x => x.projectId === projId && x.login === user).delete
      MessageResponse(s"removed user $user from project $projId")
    }
  }

  def getDatasetsByProject(projId: Int): Seq[DataSetMetaDataSet] = {
    dal.db.withSession { implicit session =>
      dsMetaData2.filter(_.projectId === projId).run
    }
  }

  def getUserProjects(login: String): Seq[UserProjectResponse] = {
    dal.db.withSession { implicit session =>
      val userProjectQ = for {
        pu <- projectsUsers if pu.login === login
        proj <- projects if pu.projectId === proj.id
      } yield (proj, pu.role)

      val userProjects = userProjectQ.list.map({
        case (proj, role) => UserProjectResponse(Some(role), proj)
      })

      val generalProject = projects.filter(_.id === GENERAL_PROJECT_ID)
                                   .list.map(UserProjectResponse(None, _))

      (userProjects ++ generalProject)
    }
  }

  def getUserProjectsDatasets(login: String): Seq[ProjectDatasetResponse] = {
    dal.db.withSession { implicit session =>
      val userProjectQ = for {
        pu <- projectsUsers if pu.login === login
        proj <- projects if pu.projectId === proj.id
        dsMeta <- dsMetaData2 if pu.projectId === dsMeta.projectId
      } yield (proj, dsMeta, pu.role)

      val userProjects = userProjectQ.list.map({
        case (proj, dsMeta, role) => ProjectDatasetResponse(proj, dsMeta, Some(role))
      })

      val genProjectQ = for {
        proj <- projects if proj.id === GENERAL_PROJECT_ID
        dsMeta <- dsMetaData2 if proj.id === dsMeta.projectId
      } yield (proj, dsMeta)

      val genProjects = genProjectQ.list.map({
        case (proj, dsMeta) => ProjectDatasetResponse(proj, dsMeta, None)
      })

      userProjects ++ genProjects
    }
  }

  def setProjectForDatasetId(dsId: Int, projId: Int): MessageResponse = {
    dal.db.withSession { implicit session =>
      val now = JodaDateTime.now()
      dsMetaData2.filter(_.id === dsId)
                 .map(ds => (ds.projectId, ds.updatedAt))
                 .update(projId, now)
      MessageResponse(s"moved dataset with ID $dsId to project $projId")
    }
  }

  def setProjectForDatasetUuid(dsId: UUID, projId: Int): MessageResponse = {
    dal.db.withSession { implicit session =>
      val now = JodaDateTime.now()
      dsMetaData2.filter(_.uuid === dsId)
                 .map(ds => (ds.projectId, ds.updatedAt))
                 .update(projId, now)
      MessageResponse(s"moved dataset with ID $dsId to project $projId")
    }
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
   *
   * @param runnableJob
   * @return
   */
  override def addRunnableJob(runnableJob: RunnableJob): EngineJob = {
    // this should probably default to the system temp dir
    val path = ""

    val createdAt = JodaDateTime.now()
    val name = s"Runnable Job $runnableJob"
    val comment = s"Job comment for $runnableJob"
    val jobTypeId = runnableJob.job.jobOptions.toJob.jobTypeId.id
    val jsonSettings = "{}"

    val jobId = dal.db.withSession { implicit session =>
      val x = EngineJob(-99, runnableJob.job.uuid, name, comment, createdAt, createdAt, AnalysisJobStates.CREATED, jobTypeId, path, jsonSettings)
      engineJobs returning engineJobs.map(_.id) += x
    }
    // Now that the job id is known, we can resolve the directory that the job will be run in
    // and update the engine job in the db
    val rj = RunnableJobWithId(jobId, runnableJob.job, runnableJob.state)
    val resolvedPath = resolver.resolve(rj)

    dal.db.withSession { implicit session =>
      val qx = for {e <- engineJobs if e.id === jobId} yield e.path
      qx.update(resolvedPath.toAbsolutePath.toString)
    }

    // Add creation event
    dal.db.withSession { implicit session =>
      jobEvents += JobEvent(UUID.randomUUID(), jobId, AnalysisJobStates.CREATED, s"Created job $jobId type $jobTypeId with ${runnableJob.job.uuid.toString}", JodaDateTime.now())
    }

    _runnableJobs.update(runnableJob.job.uuid, rj)
    EngineJob(jobId, runnableJob.job.uuid, name, comment, createdAt, createdAt, AnalysisJobStates.CREATED, jobTypeId, path, jsonSettings)
  }

  override def getJobByUUID(jobId: UUID): Option[EngineJob] = {
    dal.db.withSession { implicit session =>
      engineJobs.filter(_.uuid === jobId).firstOption
    }
  }

  override def getJobById(jobId: Int): Option[EngineJob] = {
    dal.db.withSession { implicit session =>
      engineJobs.filter(_.id === jobId).firstOption
    }
  }

  def getNextRunnableJob: Either[NoAvailableWorkError, RunnableJob] = {

    _runnableJobs.values.find(_.state == AnalysisJobStates.CREATED)
      .flatMap { ejx => getJobById(ejx.id)
      .map(x => RunnableJob(ejx.job, x.state))
    } match {
      case Some(rj) =>
        _runnableJobs.remove(rj.job.uuid)
        Right(rj)
      case _ => Left(NoAvailableWorkError("No Available work to run."))
    }
  }

  override def getNextRunnableJobWithId: Either[NoAvailableWorkError, RunnableJobWithId] = {
    _runnableJobs.values.find(_.state == AnalysisJobStates.CREATED)
      .flatMap { ejx => getJobById(ejx.id)
      .map(x => RunnableJobWithId(ejx.id, ejx.job, x.state))
    } match {
      case Some(rj) =>
        _runnableJobs.remove(rj.job.uuid)
        Right(rj)
      case _ => Left(NoAvailableWorkError("No Available work to run."))
    }
  }

  /**
   * Get all the Job Events accosciated with a specific job
   *
   * @param jobId
   * @return
   */
  override def getJobEventsByJobId(jobId: Int): Seq[JobEvent] = {
    dal.db.withSession { implicit session =>
      def toS(sid: Int) = AnalysisJobStates.intToState(sid).getOrElse(AnalysisJobStates.UNKNOWN)
      val q = jobEvents.filter(_.jobId === jobId).map(x => (x.id, x.stateId, x.message, x.createdAt))
      // Clients should create the JobEvent UUID.
      q.run.map(x => JobEvent(UUID.randomUUID(), jobId, toS(x._2), x._3, x._4))
    }
  }

  def updateJobState(jobId: Int,
                     state: AnalysisJobStates.JobStates,
                     message: String): String = {
    logger.info(s"Updating job state of job-id $jobId to $state")
    dal.db.withSession { implicit session =>
      session.withTransaction {
        engineJobs.filter(_.id === jobId).map(_.stateId).update(state.stateId)
        jobEvents += JobEvent(UUID.randomUUID(), jobId, state, message, JodaDateTime.now())
      }
    }
    s"Successfully updated job $jobId to $state"
  }

  override def updateJobStateByUUID(uuid: UUID, state: AnalysisJobStates.JobStates): Unit = {
    dal.db.withSession { implicit session =>
      val jobState = jobStates.filter(_.name === state.toString).firstOption

      val q = for {
        j <- engineJobs if j.uuid === uuid
      } yield j.stateId

      jobState match {
        case Some(s) =>
          logger.info(s"Updating job state to $jobState")
          q.update(s._1).run
          // FIXME.
          // (pid, stateId, jobId, message, JodaDateTime)
          //jobEvents += (-9999, s._1, jobId, message, JodaDateTime.now())
          //jobEvents += (-999, )
          logger.debug(s"Successfully updated job ${uuid.toString} to $jobState")
        case _ =>
          logger.error(s"Unable to update state of job id ${uuid.toString}")
      }
    }

  }

  def updateJobStateByUUID(jobId: UUID,
                           state: AnalysisJobStates.JobStates,
                           message: String): String = {

    dal.db.withSession { implicit session =>

      val job = engineJobs.filter(_.uuid === jobId).firstOption
      val q = for {
        j <- engineJobs if j.uuid === jobId
      } yield j.stateId

      job match {
        case Some(engineJob) =>
          q.update(state.stateId).run
          val jobEvent = JobEvent(UUID.randomUUID(), engineJob.id, state, message, JodaDateTime.now())
          jobEvents += jobEvent
          logger.info(s"Updated job ${jobId.toString} state to $state Event $jobEvent")
          s"Successfully updated job $jobId to $state"
        case None =>
          throw new ResourceNotFoundError(s"Unable to find job $jobId. Failed to update job state to $state")
      }
    }
  }

  /**
   * This is the new interface will replace the original createJob
   * @param uuid UUID
   * @param name Name of job
   * @param description This is really a comment. FIXME
   * @param jobTypeId String of the job type identifier. This should be consistent with the
   *                  jobTypeId defined in CoreJob
   * @return
   */
  def createJob(uuid: UUID,
                name: String,
                description: String,
                jobTypeId: String,
                coreJob: CoreJob,
                entryPoints: Option[Seq[EngineJobEntryPointRecord]] = None,
                jsonSetting: String): EngineJob = {

    // This should really be Option[String]
    val path = ""
    // TODO(smcclellan): Use dependency-injected Clock instance
    val createdAt = JodaDateTime.now()

    val engineJob = dal.db.withSession { implicit session =>
      engineJobs returning engineJobs.map(_.id) into ((engineJob, id) => engineJob.copy(id = id)) +=
        EngineJob(-9999, uuid, name, description, createdAt, createdAt, AnalysisJobStates.CREATED, jobTypeId, path, jsonSetting)
    }

    val jobId = engineJob.id
    val rJob = RunnableJobWithId(jobId, coreJob, AnalysisJobStates.CREATED)
    val resolvedPath = resolver.resolve(rJob)

    dal.db.withSession { implicit session =>
      val qx = for {e <- engineJobs if e.id === jobId} yield e.path
      qx.update(resolvedPath.toAbsolutePath.toString)
      // Add Job creation Event
      jobEvents += JobEvent(UUID.randomUUID(), jobId, AnalysisJobStates.CREATED, s"Created job $jobId type $jobTypeId with ${uuid.toString}", JodaDateTime.now())
    }

    // Add DataSet Entry Points to a Job
    entryPoints match {
      case Some(eps) =>
        dal.db.withSession { implicit session =>
          engineJobsDataSets ++= eps.map(x => EngineJobEntryPoint(jobId, x.datasetUUID, x.datasetType))
        }
      case _ => None
    }

    _runnableJobs.update(uuid, rJob)
    EngineJob(jobId, uuid, name, description, createdAt, createdAt, AnalysisJobStates.CREATED, jobTypeId, path, jsonSetting)
  }

  override def getJobs(limit: Int = 100): Seq[EngineJob] = {
    dal.db.withSession { implicit session =>
      engineJobs.list
    }
  }

  def getJobsByTypeId(jobTypeId: String): Seq[EngineJob] = {
    dal.db.withSession { implicit session =>
      engineJobs.filter(_.jobTypeId === jobTypeId).list
    }
  }

  def getJobEntryPoints(jobId: Int): Seq[EngineJobEntryPoint] = {
    dal.db.withSession { implicit session =>
      engineJobsDataSets.filter(_.jobId === jobId).list
    }
  }

  def getCCSDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT): Seq[CCSreadServiceDataSet] = {
    dal.db.withSession { implicit session =>
      val q = dsMetaData2 join dsCCSread2 on (_.id === _.id)
      q.run.map(x => toCCSread(x._1))
    }
  }

  def toCCSread(t1: DataSetMetaDataSet) =
    CCSreadServiceDataSet(t1.id, t1.uuid, t1.name, t1.path, t1.createdAt, t1.updatedAt, t1.numRecords, t1.totalLength,
      t1.version, t1.comments, t1.tags, t1.md5, t1.userId, t1.jobId, t1.projectId)

  def toB(t1: DataSetMetaDataSet) = BarcodeServiceDataSet(t1.id, t1.uuid, t1.name, t1.path, t1.createdAt, t1.updatedAt, t1.numRecords, t1.totalLength,
    t1.version, t1.comments, t1.tags, t1.md5, t1.userId, t1.jobId, t1.projectId)

  def getBarcodeDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT): Seq[BarcodeServiceDataSet] = {
    dal.db.withSession { implicit session =>
      val q = dsMetaData2 join dsBarcode2 on (_.id === _.id)
      q.run.map(x => toB(x._1))
    }
  }
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
   * @param ds
   * @param jobId
   * @return
   */
  def insertDataStoreFileById(ds: DataStoreFile, jobId: Int): String = getJobById(jobId)
    .map(j => insertDataStoreByJob(j, ds))
    .getOrElse(throw new ResourceNotFoundError(s"Failed to import $ds Failed to find job id $jobId"))

  def insertDataStoreFileByUUID(ds: DataStoreFile, jobId: UUID): String = getJobByUUID(jobId)
    .map(j => insertDataStoreByJob(j, ds))
    .getOrElse(throw new ResourceNotFoundError(s"Failed to import $ds Failed to find job id $jobId"))

  override def addDataStoreFile(ds: DataStoreJobFile): Either[CommonMessages.FailedMessage, CommonMessages.SuccessMessage] = {
    getJobByUUID(ds.jobId) match {
      case Some(engineJob) =>
        Try(insertDataStoreByJob(engineJob, ds.dataStoreFile)) match {
          case Success(x) => Right(CommonMessages.SuccessMessage(x))
          case Failure(t) => Left(CommonMessages.FailedMessage(t.getMessage))
        }
      case _ => Left(CommonMessages.FailedMessage(s"Failed to find jobId ${ds.jobId}"))
    }
  }

  /**
   * Generic Importing of DataSet by type and Path to dataset file
   * @param dataSetMetaType
   * @param spath
   * @param jobId
   * @param userId
   * @param projectId
   * @return
   */
  protected def insertDataSet(dataSetMetaType: DataSetMetaType, spath: String, jobId: Int, userId: Int, projectId: Int): String = {
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
      case x =>
        val msg = s"Unsupported DataSet type $x. Skipping DataSet Import of $path"
        logger.warn(msg)
        msg
    }
  }

  protected def insertDataStoreByJob(engineJob: EngineJob, ds: DataStoreFile): String = {
    logger.info(s"Inserting DataStore File $ds with job id ${engineJob.id}")

    // TODO(smcclellan): Use dependency-injected Clock instance
    val createdAt = JodaDateTime.now()
    val modifiedAt = createdAt
    val importedAt = createdAt

    if (ds.isChunked) {
      s"Skipping inserting of Chunked DataStoreFile $ds"
    } else {
      // Extended details import if file type is a DataSet
      DataSetMetaTypes.toDataSetType(ds.fileTypeId) match {
        case Some(x) =>
          val result = insertDataSet(x, ds.path, engineJob.id, DEFAULT_USER_ID, DEFAULT_PROJECT_ID)
          dal.db.withSession { implicit session =>
            if (!datastoreServiceFiles.filter(_.uuid === ds.uniqueId).exists.run) {
              val dss = DataStoreServiceFile(ds.uniqueId, ds.fileTypeId, ds.sourceId, ds.fileSize, createdAt, modifiedAt, importedAt, ds.path, engineJob.id, engineJob.uuid, ds.name, ds.description)
              datastoreServiceFiles += dss
            } else {
              logger.info(s"Already imported. Skipping inserting of datastore file $ds")
            }
          }
          result
        case _ =>
          dal.db.withSession { implicit session =>
            if (!datastoreServiceFiles.filter(_.uuid === ds.uniqueId).exists.run) {
              val dss = DataStoreServiceFile(ds.uniqueId, ds.fileTypeId, ds.sourceId, ds.fileSize, createdAt, modifiedAt, importedAt, ds.path, engineJob.id, engineJob.uuid, ds.name, ds.description)
              datastoreServiceFiles += dss
            } else {
              logger.info(s"Already imported. Skipping inserting of datastore file $ds")
            }
          }
          s"Unsupported DataSet type ${ds.fileTypeId}. Imported $ds. Skipping extended/detailed importing"
      }
    }
  }

  def getDataStoreFiles2: Seq[DataStoreServiceFile] = {
    dal.db.withSession { implicit session =>
      datastoreServiceFiles.run.map(x => x)
    }
  }

  def getDataStoreFileByUUID2(uuid: UUID): Option[DataStoreServiceFile] = {
    dal.db.withSession { implicit session =>
      datastoreServiceFiles.filter(_.uuid === uuid).firstOption
    }
  }

  def getDataStoreServiceFilesByJobId(i: Int): Seq[DataStoreServiceFile] = {
    dal.db.withSession { implicit session =>
      datastoreServiceFiles.filter(_.jobId === i).run
    }
  }

  def getDataStoreReportFilesByJobId(jobId: Int): Seq[DataStoreReportFile] = {
    dal.db.withSession { implicit session =>
      datastoreServiceFiles.filter(_.jobId === jobId).filter(_.fileTypeId === FileTypes.REPORT.fileTypeId).run.map(x => DataStoreReportFile(x, "mock-report-type-id"))
    }
  }

  // Return the contents of the Report
  def getDataStoreReportByUUID(reportUUID: UUID): Option[String] = {
    dal.db.withSession { implicit session =>
      datastoreServiceFiles.filter(_.uuid === reportUUID).firstOption match {
        case Some(x) =>
          if (Files.exists(Paths.get(x.path))) {
            Option(scala.io.Source.fromFile(x.path).mkString)
          } else {
            logger.error(s"Unable to find report ${x.uuid} path ${x.path}")
            None
          }
        case _ => None
      }
    }
  }

  private def getDataSetMetaDataSet(uuid: UUID): Option[DataSetMetaDataSet] = {
    dal.db.withSession { implicit session =>
      dsMetaData2.filter(_.uuid === uuid).firstOption
    }
  }

  private def insertMetaData(ds: ServiceDataSetMetadata)(implicit session: Session): Int = {
    val createdAt = JodaDateTime.now()
    val modifiedAt = createdAt
    dsMetaData2 returning dsMetaData2.map(_.id) += DataSetMetaDataSet(
      -999, ds.uuid, ds.name, ds.path, createdAt, modifiedAt, ds.numRecords, ds.totalLength, ds.tags, ds.version,
      ds.comments, ds.md5, ds.userId, ds.jobId, ds.projectId, isActive = true)
  }

  def insertReferenceDataSet(ds: ReferenceServiceDataSet): String = {
    dal.db.withSession { implicit session =>
      getDataSetMetaDataSet(ds.uuid) match {
        case Some(_) =>
          val msg = s"ReferenceSet ${ds.uuid} already imported. Skipping importing of $ds"
          logger.debug(msg)
          msg
        case None =>
          val idx = insertMetaData(ds)
          // TODO(smcclellan): Here and below, remove use of forceInsert and allow ids to make use of autoinc
          // TODO(smcclellan): Link datasets to metadata with foreign key, rather than forcing the id value
          dsReference2 forceInsert ReferenceServiceSet(idx, ds.uuid, ds.ploidy, ds.organism)
          s"imported ReferencecSet ${ds.uuid} from ${ds.path}"
      }
    }
  }

  def insertSubreadDataSet(ds: SubreadServiceDataSet): String = {
    dal.db.withSession { implicit session =>
      getDataSetMetaDataSet(ds.uuid) match {
        case Some(_) =>
          val msg = s"SubreadSet ${ds.uuid.toString} already imported. Skipping importing of $ds"
          logger.debug(msg)
          msg
        case None =>
          val idx = insertMetaData(ds)
          dsSubread2 forceInsert SubreadServiceSet(idx, ds.uuid, "cell-id", ds.metadataContextId, ds.wellSampleName,
            ds.wellName, ds.bioSampleName, ds.cellIndex, ds.instrumentName, ds.instrumentName, ds.runName,
            "instrument-ctr-version")
          val msg = s"imported SubreadSet ${ds.uuid} from ${ds.path}"
          logger.info(msg)
          msg
      }
    }
  }

  def insertHdfSubreadDataSet(ds: HdfSubreadServiceDataSet): String = {
    dal.db.withSession { implicit session =>
      getDataSetMetaDataSet(ds.uuid) match {
        case Some(_) =>
          val msg = s"HdfSubreadSet ${ds.uuid.toString} already imported skipping $ds"
          logger.debug(msg)
          msg
        case None =>
          val idx = insertMetaData(ds)
          dsHdfSubread2 forceInsert HdfSubreadServiceSet(idx, ds.uuid, "cell-id", ds.metadataContextId,
            ds.wellSampleName, ds.wellName, ds.bioSampleName, ds.cellIndex, ds.instrumentName, ds.instrumentName,
            ds.runName, "instrument-ctr-version")
          val msg = s"imported HdfSubreadSet ${ds.uuid} from ${ds.path}"
          logger.info(msg)
          msg
      }
    }
  }

  def insertAlignmentDataSet(ds: AlignmentServiceDataSet): String = {
    logger.debug(s"Inserting AlignmentSet $ds")
    dal.db.withSession { implicit session =>
      val idx = insertMetaData(ds)
      dsAlignment2 forceInsert AlignmentServiceSet(idx, ds.uuid)
    }
    s"Successfully entered Alignment dataset $ds"
  }

  def insertBarcodeDataSet(ds: BarcodeServiceDataSet): String = {
    logger.debug(s"Inserting BarcodeSet $ds")
    dal.db.withSession { implicit session =>
      val idx = insertMetaData(ds)
      dsBarcode2 forceInsert BarcodeServiceSet(idx, ds.uuid)
    }
    s"Successfully entered Barcode dataset $ds"
  }

  def getDataSetTypeById(typeId: String): Option[ServiceDataSetMetaType] = {
    dal.db.withSession { implicit session =>
      datasetTypes.filter(_.id === typeId).firstOption
    }
  }

  def getDataSetTypes: Seq[ServiceDataSetMetaType] = {
    dal.db.withSession { implicit session =>
      datasetTypes.list
    }
  }

  // Get All DataSets mixed in type. Only metadata
  def getDataSetByUUID(id: UUID): Option[DataSetMetaDataSet] = {
    dal.db.withSession { implicit session =>
      datasetMetaTypeByUUID(id).firstOption
    }
  }

  def getDataSetById(id: Int): Option[DataSetMetaDataSet] = {
    dal.db.withSession { implicit session =>
      datasetMetaTypeById(id).firstOption
    }
  }

  def datasetMetaTypeById(id: Int) = dsMetaData2.filter(_.id === id)

  def datasetMetaTypeByUUID(id: UUID) = dsMetaData2.filter(_.uuid === id)

  // util for converting to the old model
  def toSds(t1: DataSetMetaDataSet, t2: SubreadServiceSet): SubreadServiceDataSet =
    SubreadServiceDataSet(t1.id, t1.uuid, t1.name, t1.path, t1.createdAt, t1.updatedAt, t1.numRecords, t1.totalLength,
      t1.version, t1.comments, t1.tags, t1.md5, t2.instrumentName, t2.metadataContextId, t2.wellSampleName, t2.wellName, t2.bioSampleName, t2.cellIndex, t2.runName, t1.userId, t1.jobId, t1.projectId)

  // FIXME. REALLY, REALLY need to generalize this.
  def getSubreadDataSetById(id: Int): Option[SubreadServiceDataSet] = {
    dal.db.withSession { implicit session =>
      val q = datasetMetaTypeById(id) join dsSubread2 on (_.id === _.id)
      q.firstOption.flatMap(x => Option(toSds(x._1, x._2)))
    }
  }

  def _subreadToDetails(ds: Option[SubreadServiceDataSet]): Option[String] = {
    ds.flatMap(x => Option(DataSetJsonUtils.subreadSetToJson(DataSetLoader.loadSubreadSet(Paths.get(x.path)))))
  }

  def getSubreadDataSetDetailsById(id: Int): Option[String] = _subreadToDetails(getSubreadDataSetById(id))

  def getSubreadDataSetDetailsByUUID(uuid: UUID): Option[String] = _subreadToDetails(getSubreadDataSetByUUID(uuid))

  def getSubreadDataSetByUUID(id: UUID): Option[SubreadServiceDataSet] = {
    dal.db.withSession { implicit session =>
      val q = datasetMetaTypeByUUID(id) join dsSubread2 on (_.id === _.id)
      q.firstOption.flatMap(x => Option(toSds(x._1, x._2)))
    }
  }

  def getSubreadDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT): Seq[SubreadServiceDataSet] = {
    dal.db.withSession { implicit session =>
      val q = dsMetaData2 join dsSubread2 on (_.id === _.id)
      q.run.map(x => toSds(x._1, x._2))
    }
  }

  // conversion util for keeping the old interface
  def toR(t1: DataSetMetaDataSet, t2: ReferenceServiceSet): ReferenceServiceDataSet =
    ReferenceServiceDataSet(t1.id, t1.uuid, t1.name, t1.path, t1.createdAt, t1.updatedAt, t1.numRecords, t1.totalLength,
      t1.version, t1.comments, t1.tags, t1.md5, t1.userId, t1.jobId, t1.projectId, t2.ploidy, t2.organism)

  def getReferenceDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT): Seq[ReferenceServiceDataSet] = {
    dal.db.withSession { implicit session =>
      val q = dsMetaData2 join dsReference2 on (_.id === _.id)
      q.run.map(x => toR(x._1, x._2))
    }
  }

  def getReferenceDataSetById(id: Int): Option[ReferenceServiceDataSet] = {
    dal.db.withSession { implicit session =>
      val q = datasetMetaTypeById(id) join dsReference2 on (_.id === _.id)
      q.firstOption.flatMap(x => Option(toR(x._1, x._2)))
    }
  }

  def _referenceToDetails(ds: Option[ReferenceServiceDataSet]): Option[String] = {
    ds.flatMap(x => Option(DataSetJsonUtils.referenceSetToJson(DataSetLoader.loadReferenceSet(Paths.get(x.path)))))
  }

  def getReferenceDataSetDetailsById(id: Int): Option[String] = _referenceToDetails(getReferenceDataSetById(id))

  def getReferenceDataSetDetailsByUUID(uuid: UUID): Option[String] = _referenceToDetails(getReferenceDataSetByUUID(uuid))

  def getReferenceDataSetByUUID(id: UUID): Option[ReferenceServiceDataSet] = {
    dal.db.withSession { implicit session =>
      val q = datasetMetaTypeByUUID(id) join dsReference2 on (_.id === _.id)
      q.firstOption.flatMap(x => Option(toR(x._1, x._2)))
    }
  }

  def getHdfDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT): Seq[HdfSubreadServiceDataSet] = {
    dal.db.withSession { implicit session =>
      val q = dsMetaData2 join dsHdfSubread2 on (_.id === _.id)
      q.run.map(x => toHds(x._1, x._2))
    }
  }

  def toHds(t1: DataSetMetaDataSet, t2: HdfSubreadServiceSet): HdfSubreadServiceDataSet =
    HdfSubreadServiceDataSet(t1.id, t1.uuid, t1.name, t1.path, t1.createdAt, t1.updatedAt, t1.numRecords, t1.totalLength,
      t1.version, t1.comments, t1.tags, t1.md5, t2.instrumentName, t2.metadataContextId, t2.wellSampleName, t2.wellName, t2.bioSampleName, t2.cellIndex, t2.runName, t1.userId, t1.jobId, t1.projectId)

  def getHdfDataSetById(id: Int): Option[HdfSubreadServiceDataSet] = {
    dal.db.withSession { implicit session =>
      val q = datasetMetaTypeById(id) join dsHdfSubread2 on (_.id === _.id)
      q.firstOption.flatMap(x => Option(toHds(x._1, x._2)))
    }
  }

  def _hdfsubreadToDetails(ds: Option[HdfSubreadServiceDataSet]): Option[String] = {
    ds.flatMap(x => Option(DataSetJsonUtils.hdfSubreadSetToJson(DataSetLoader.loadHdfSubreadSet(Paths.get(x.path)))))
  }

  def getHdfDataSetDetailsById(id: Int): Option[String] = _hdfsubreadToDetails(getHdfDataSetById(id))

  def getHdfDataSetDetailsByUUID(uuid: UUID): Option[String] = _hdfsubreadToDetails(getHdfDataSetByUUID(uuid))

  def getHdfDataSetByUUID(id: UUID): Option[HdfSubreadServiceDataSet] = {
    dal.db.withSession { implicit session =>
      val q = datasetMetaTypeByUUID(id) join dsHdfSubread2 on (_.id === _.id)
      q.firstOption.flatMap(x => Option(toHds(x._1, x._2)))
    }
  }

  def toA(t1: DataSetMetaDataSet) = AlignmentServiceDataSet(t1.id, t1.uuid, t1.name, t1.path, t1.createdAt, t1.updatedAt, t1.numRecords, t1.totalLength,
    t1.version, t1.comments, t1.tags, t1.md5, t1.userId, t1.jobId, t1.projectId)

  def getAlignmentDataSets(limit: Int = DEFAULT_MAX_DATASET_LIMIT): Seq[AlignmentServiceDataSet] = {
    dal.db.withSession { implicit session =>
      val q = dsMetaData2 join dsAlignment2 on (_.id === _.id)
      q.run.map(x => toA(x._1))
    }
  }

  def getAlignmentDataSetById(id: Int): Option[AlignmentServiceDataSet] = {
    dal.db.withSession { implicit session =>
      val q = datasetMetaTypeById(id) join dsAlignment2 on (_.id === _.id)
      q.firstOption.flatMap(x => Option(toA(x._1)))
    }
  }

  def getAlignmentDataSetByUUID(id: UUID): Option[AlignmentServiceDataSet] = {
    dal.db.withSession { implicit session =>
      val q = datasetMetaTypeByUUID(id) join dsAlignment2 on (_.id === _.id)
      q.firstOption.flatMap(x => Option(toA(x._1)))
    }
  }

  def getCCSDataSetById(id: Int): Option[CCSreadServiceDataSet] = {
    dal.db.withSession { implicit session =>
      val q = datasetMetaTypeById(id) join dsCCSread2 on (_.id === _.id)
      q.firstOption.flatMap(x => Option(toCCSread(x._1)))
    }
  }

  def getCCSDataSetByUUID(id: UUID): Option[CCSreadServiceDataSet] = {
    dal.db.withSession { implicit session =>
      val q = datasetMetaTypeByUUID(id) join dsCCSread2 on (_.id === _.id)
      q.firstOption.flatMap(x => Option(toCCSread(x._1)))
    }
  }

  def getBarcodeDataSetById(id: Int): Option[BarcodeServiceDataSet] = {
    dal.db.withSession { implicit session =>
      val q = datasetMetaTypeById(id) join dsBarcode2 on (_.id === _.id)
      q.firstOption.flatMap(x => Option(toB(x._1)))
    }
  }

  def getBarcodeDataSetByUUID(id: UUID): Option[BarcodeServiceDataSet] = {
    dal.db.withSession { implicit session =>
      val q = datasetMetaTypeByUUID(id) join dsBarcode2 on (_.id === _.id)
      q.firstOption.flatMap(x => Option(toB(x._1)))
    }
  }

  def _barcodeSetToDetails(ds: Option[BarcodeServiceDataSet]): Option[String] = {
    ds.flatMap(x => Option(DataSetJsonUtils.barcodeSetToJson(DataSetLoader.loadBarcodeSet(Paths.get(x.path)))))
  }

  def getBarcodeDataSetDetailsById(id: Int): Option[String] = _barcodeSetToDetails(getBarcodeDataSetById(id))

  def getBarcodeDataSetDetailsByUUID(uuid: UUID): Option[String] = _barcodeSetToDetails(getBarcodeDataSetByUUID(uuid))

  def toDataStoreJobFile(x: DataStoreServiceFile) =
    // This is has the wrong job uuid
    DataStoreJobFile(x.uuid, DataStoreFile(x.uuid, x.sourceId, x.fileTypeId, x.fileSize, x.createdAt, x.modifiedAt, x.path, name=x.name, description=x.description))

  def getDataStoreFilesByJobId(i: Int): Seq[DataStoreJobFile] = {
    dal.db.withSession { implicit session =>
      datastoreServiceFiles.filter(_.jobId === i).run.map(toDataStoreJobFile)
    }
  }

  // Need to clean all this all up. There's inconsistencies all over the place.
  override def getDataStoreFiles: Seq[DataStoreJobFile] = {
    dal.db.withSession { implicit session =>
      datastoreServiceFiles.run.map(x => toDataStoreJobFile(x))
    }
  }

  override def getDataStoreFileByUUID(uuid: UUID): Option[DataStoreJobFile] = {
    dal.db.withSession { implicit session =>
      datastoreServiceFiles.filter(_.uuid === uuid).firstOption.flatMap(x => Option(toDataStoreJobFile(x)))
    }
  }

  override def getDataStoreFilesByJobUUID(uuid: UUID): Seq[DataStoreJobFile] = {
    dal.db.withSession { implicit session =>
      val q = for {
        engineJob <- engineJobs.filter(_.uuid === uuid)
        dsFiles <- datastoreServiceFiles.filter(_.jobId === engineJob.id)
      } yield dsFiles
      q.run.map(toDataStoreJobFile)
    }
  }
}

class JobsDao(val dal: Dal, val resolver: JobResourceResolver) extends JobEngineDataStore
with DalComponent
with SmrtLinkConstants
with ProjectDataStore
with JobDataStore
with DataSetStore {

  import JobModels._

  var _runnableJobs = mutable.Map[UUID, RunnableJobWithId]()

  def initializeDb(): Unit = {
    dal.flyway.migrate()
  }
}

trait JobsDaoProvider {
  this: DalProvider with SmrtLinkConfigProvider =>

  val jobsDao: Singleton[JobsDao] = Singleton(() => new JobsDao(dal(), jobResolver()))
}
