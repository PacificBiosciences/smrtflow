package com.pacbio.secondary.smrtlink.services

import java.util.UUID
import java.nio.file.{Files, Path, Paths}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

import akka.actor.ActorSystem
import akka.http.scaladsl.server._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.MediaType.NotCompressible
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{
  ContentDispositionTypes,
  `Content-Disposition`
}
import akka.http.scaladsl.server.directives.FileAndResourceDirectives
import akka.http.scaladsl.settings.RoutingSettings
import akka.http.scaladsl.unmarshalling.{FromRequestUnmarshaller, Unmarshaller}
import akka.stream.scaladsl.FileIO
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.{FileUtils, FilenameUtils}
import spray.json._

import com.pacbio.common.models.CommonModels._
import com.pacbio.common.models.CommonModelImplicits
import CommonModelImplicits._
import com.pacbio.common.models.CommonModelSpraySupport
import com.pacbio.common.models.CommonModelSpraySupport.IdAbleMatcher
import com.pacbio.secondary.smrtlink.actors.{
  ActorRefFactoryProvider,
  ActorSystemProvider,
  JobsDao,
  JobsDaoProvider
}
import com.pacbio.secondary.smrtlink.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.smrtlink.analysis.jobs.JobModels._
import com.pacbio.secondary.smrtlink.services.PacBioServiceErrors.{
  MethodNotImplementedError,
  ResourceNotFoundError,
  UnprocessableEntityError
}
import com.pacbio.secondary.smrtlink.JobServiceConstants
import com.pacbio.secondary.smrtlink.actors.CommonMessages.MessageResponse
import com.pacbio.secondary.smrtlink.analysis.datasets.DataSetFileUtils
import com.pacbio.secondary.smrtlink.analysis.jobs.AnalysisJobStates
import com.pacbio.secondary.smrtlink.jobtypes.PbsmrtpipeJobUtils
import com.pacbio.secondary.smrtlink.app.SmrtLinkConfigProvider
import com.pacbio.secondary.smrtlink.dependency.Singleton
import com.pacbio.secondary.smrtlink.jobtypes._
import com.pacbio.secondary.smrtlink.models._
import com.pacbio.secondary.smrtlink.models.QueryOperators._
import com.pacbio.secondary.smrtlink.jsonprotocols.SmrtLinkJsonProtocols
import com.pacbio.secondary.smrtlink.models.ConfigModels.SystemJobConfig
import com.pacbio.secondary.smrtlink.services.utils.SmrtDirectives

trait JobServiceRoutes {
  def jobTypeId: JobTypeIds.JobType
  def routes: Route
}

trait DownloadFileUtils {

  def failIfNotFound(path: Path)(implicit ec: ExecutionContext): Future[Path] = {
    if (path.toFile.exists()) Future.successful(path)
    else Future.failed(ResourceNotFoundError(s"File $path is not found"))
  }

  def downloadFile(path: Path,
                   customFileName: String,
                   chunkSize: Int = 8192): HttpResponse = {

    //FIXME. this will yield an illegal argument exception, it should be a NotFound
    require(path.toFile.exists(), s"File does not exist $path")

    val params: Map[String, String] = Map("filename" -> customFileName)
    val customHeader: HttpHeader =
      `Content-Disposition`(ContentDispositionTypes.attachment, params)
    val customHeaders: collection.immutable.Seq[HttpHeader] =
      collection.immutable.Seq(customHeader)

    val f = path.toFile

    val responseEntity = HttpEntity(
      MediaTypes.`application/octet-stream`,
      f.length,
      FileIO.fromPath(path, chunkSize = chunkSize))
    HttpResponse(entity = responseEntity, headers = customHeaders)
  }
}

trait CommonJobsRoutes[T <: ServiceJobOptions]
    extends SmrtLinkBaseMicroService
    with JobServiceConstants
    with JobServiceRoutes
    with DownloadFileUtils
    with SearchQueryUtils {
  val dao: JobsDao
  val config: SystemJobConfig

  implicit val um: FromRequestUnmarshaller[T]
  implicit val sm: ToEntityMarshaller[T]
  implicit val jwriter: JsonWriter[T]

  import SmrtLinkJsonProtocols._
  import CommonModelSpraySupport._
  import CommonModelImplicits._

  override val manifest = PacBioComponentManifest(
    toServiceId(s"job_service_${jobTypeId.id.replace("-", "_")}"),
    s"JobService ${jobTypeId.id} Service",
    "0.2.0",
    s"Job Service ${jobTypeId.id} ${jobTypeId.description}"
  )

  /**
    * Logging util
    *
    * @param sx String
    * @return
    */
  def andLog(sx: String): String = {
    logger.info(sx)
    sx
  }

  /**
    * Validation of the Opts returns Option[Error], This will fail
    * the Future if any validation errors occur.
    *
    * @param opts
    * @param user
    * @return
    */
  def validator(opts: T, user: Option[UserRecord]): Future[T] = {
    opts.validate(dao, config) match {
      case Some(ex) =>
        Future.failed(UnprocessableEntityError(ex.msg))
      case _ => Future.successful(opts)
    }
  }

  def validateStateIsCreated(job: EngineJob, msg: String): Future[EngineJob] = {
    if (job.state == AnalysisJobStates.CREATED) Future.successful(job)
    else Future.failed(UnprocessableEntityError(msg))
  }

  /**
    * Optional Termination implementation of a Job. By default this method is not supported.
    *
    * @param jobId Job id to terminateb
    * @return
    */
  def terminateJob(jobId: IdAble): Future[MessageResponse] =
    Future.failed(
      MethodNotImplementedError(
        s"Job type ${jobTypeId.id} does NOT support termination"))

  /**
    * Central interface for creating Jobs using the ServiceJobOptions.
    *
    * This interface will handle the POST (and unmarshalling) and
    * validation and creating a new EngineJob from the ServiceJobOptions
    * provided.
    *
    * If there are customized needs, you should override this and
    * always call super.createJob.
    *
    * @param opts
    * @param user
    * @return
    */
  def createJob(opts: T, user: Option[UserRecord]): Future[EngineJob] = {
    val uuid = UUID.randomUUID()

    val name = opts.name.getOrElse(opts.jobTypeId.id)
    val comment =
      opts.description.getOrElse(s"Description for job ${opts.jobTypeId.name}")

    // This will require an implicit JsonWriter in scope
    val jsettings = opts.toJson.asJsObject

    val projectId = opts.projectId.getOrElse(JobConstants.GENERAL_PROJECT_ID)

    def creator(epoints: Seq[EngineJobEntryPointRecord]): Future[EngineJob] = {
      // For the MultiJob case, the createJob method will often have to completely be overridden
      if (opts.jobTypeId.isMultiJob) {
        dao.createMultiJob(
          uuid,
          name,
          comment,
          opts.jobTypeId,
          epoints.toSet,
          jsettings,
          user.map(_.userId),
          user.flatMap(_.userEmail),
          config.smrtLinkVersion,
          projectId,
          subJobTypeId = opts.subJobTypeId,
          submitJob = opts.getSubmit(),
          childJobs = Nil,
          tags = opts.getTags()
        )
      } else {
        dao.createCoreJob(
          uuid,
          name,
          comment,
          opts.jobTypeId,
          epoints.toSet,
          jsettings,
          user.map(_.userId),
          user.flatMap(_.userEmail),
          config.smrtLinkVersion,
          projectId,
          subJobTypeId = opts.subJobTypeId,
          submitJob = opts.getSubmit(),
          tags = opts.getTags()
        )
      }
    }

    for {
      vopts <- validator(opts, user) // This will fail the Future if any validation errors occur.
      entryPoints <- Future(vopts.resolveEntryPoints(dao))
      engineJob <- creator(entryPoints)
    } yield engineJob
  }

  protected def parseJobSearchCriteria(
      projectIds: Set[Int],
      isActive: Option[Boolean],
      limit: Int,
      marker: Option[Int],
      id: Option[String],
      uuid: Option[String],
      name: Option[String],
      comment: Option[String],
      createdAt: Option[String],
      updatedAt: Option[String],
      jobUpdatedAt: Option[String],
      state: Option[String],
      path: Option[String],
      createdBy: Option[String],
      createdByEmail: Option[String],
      smrtlinkVersion: Option[String],
      errorMessage: Option[String],
      projectId: Option[String],
      parentMultiJobId: Option[String],
      importedAt: Option[String],
      tags: Option[String],
      subJobTypeId: Option[String]): Future[JobSearchCriteria] = {
    val search = JobSearchCriteria(projectIds = projectIds,
                                   limit = limit,
                                   marker = marker,
                                   isActive = isActive,
                                   jobTypeId =
                                     Some(StringEqQueryOperator(jobTypeId.id)))

    for {
      qId <- parseQueryOperator[IntQueryOperator](id,
                                                  IntQueryOperator.fromString)
      qUUID <- parseQueryOperator[UUIDQueryOperator](
        uuid,
        UUIDQueryOperator.fromString)
      qName <- parseQueryOperator[StringQueryOperator](
        name,
        StringQueryOperator.fromString)
      qComment <- parseQueryOperator[StringQueryOperator](
        comment,
        StringQueryOperator.fromString)
      qCreatedAt <- parseQueryOperator[DateTimeQueryOperator](
        createdAt,
        DateTimeQueryOperator.fromString)
      qUpdatedAt <- parseQueryOperator[DateTimeQueryOperator](
        updatedAt,
        DateTimeQueryOperator.fromString)
      qJobUpdatedAt <- parseQueryOperator[DateTimeQueryOperator](
        jobUpdatedAt,
        DateTimeQueryOperator.fromString)
      qState <- parseQueryOperator[JobStateQueryOperator](
        state,
        JobStateQueryOperator.fromString)
      qPath <- parseQueryOperator[StringQueryOperator](
        path,
        StringQueryOperator.fromString)
      qCreatedBy <- parseQueryOperator[StringQueryOperator](
        createdBy,
        StringQueryOperator.fromString)
      qCreatedByEmail <- parseQueryOperator[StringQueryOperator](
        createdByEmail,
        StringQueryOperator.fromString)
      qSmrtlinkVersion <- parseQueryOperator[StringQueryOperator](
        smrtlinkVersion,
        StringQueryOperator.fromString)
      qErrorMessage <- parseQueryOperator[StringQueryOperator](
        errorMessage,
        StringQueryOperator.fromString)
      qProjectId <- parseQueryOperator[IntQueryOperator](
        projectId,
        IntQueryOperator.fromString)
      // qIsMultiJob FIXME how do i handle booleans?
      qParentMultiJobId <- parseQueryOperator[IntQueryOperator](
        parentMultiJobId,
        IntQueryOperator.fromString)
      qImportedAt <- parseQueryOperator[DateTimeQueryOperator](
        importedAt,
        DateTimeQueryOperator.fromString)
      qTags <- parseQueryOperator[StringQueryOperator](
        tags,
        StringQueryOperator.fromString)
      qSubJobTypeId <- parseQueryOperator[StringQueryOperator](
        subJobTypeId,
        StringQueryOperator.fromString)
    } yield
      search.copy(
        id = qId,
        uuid = qUUID,
        name = qName,
        comment = qComment,
        createdAt = qCreatedAt,
        updatedAt = qUpdatedAt,
        jobUpdatedAt = qJobUpdatedAt,
        state = qState,
        path = qPath,
        createdBy = qCreatedBy,
        createdByEmail = qCreatedByEmail,
        smrtlinkVersion = qSmrtlinkVersion,
        errorMessage = qErrorMessage,
        projectId = qProjectId,
        parentMultiJobId = qParentMultiJobId,
        importedAt = qImportedAt,
        tags = qTags,
        subJobTypeId = qSubJobTypeId
      )
  }

  // Means a project wasn't provided
  val DEFAULT_PROJECT: Option[Int] = None

  val allRootJobRoutes: Route =
    SmrtDirectives.extractOptionalUserRecord { user =>
      pathEndOrSingleSlash {
        post {
          entity(as[T]) { opts =>
            complete(StatusCodes.Created -> createJob(opts, user))
          }
        } ~
          get {
            parameters(
              'isActive.as[Boolean].?,
              'limit.as[Int].?,
              'marker.as[Int].?,
              'id.?,
              'uuid.?,
              'name.?,
              'comment.?,
              'createdAt.?,
              'updatedAt.?,
              'jobUpdatedAt.?,
              'state.?,
              'path.?,
              'createdBy.?,
              'createdByEmail.?,
              'smrtlinkVersion.?,
              'errorMessage.?,
              'parentMultiJobId.?,
              'importedAt.?,
              'tags.?,
              'subJobTypeId.?,
              'projectId.?
            ) {
              (isActive,
               limit,
               marker,
               id,
               uuid,
               name,
               comment,
               createdAt,
               updatedAt,
               jobUpdatedAt,
               state,
               path,
               createdBy,
               createdByEmail,
               smrtlinkVersion,
               errorMessage,
               parentMultiJobId,
               importedAt,
               tags,
               subJobTypeId,
               projectId) =>
                encodeResponse {
                  complete {
                    for {
                      ids <- getProjectIds(dao, projectId.map(_.toInt), user)
                      searchCriteria <- parseJobSearchCriteria(
                        ids.toSet,
                        // default to isActive=true
                        Some(isActive.getOrElse(true)),
                        limit.getOrElse(JobSearchCriteria.DEFAULT_MAX_JOBS),
                        marker,
                        id,
                        uuid,
                        name,
                        comment,
                        createdAt,
                        updatedAt,
                        jobUpdatedAt,
                        state,
                        path,
                        createdBy,
                        createdByEmail,
                        smrtlinkVersion,
                        errorMessage,
                        projectId,
                        parentMultiJobId,
                        importedAt,
                        tags,
                        subJobTypeId
                      )
                      jobs <- dao.getJobs(searchCriteria)
                    } yield jobs
                  }
                }
            }
          }
      }
    }

  def updateJobStateToSubmitted(
      jobId: IdAble,
      customExecutionContext: ExecutionContext): Future[MessageResponse] = {
    // implicit val customEc = customExecutionContext
    (for {
      job <- dao.getJobById(jobId)
      _ <- validateStateIsCreated(
        job,
        s"ONLY Jobs in the CREATED state can be submitted. Job ${job.id} is in state:${job.state}")
      msg <- Future.successful(
        s"Updating job ${job.id} state ${job.state} to SUBMITTED")
      updatedJob <- dao.updateJobState(job.id,
                                       AnalysisJobStates.SUBMITTED,
                                       msg,
                                       None)
    } yield
      MessageResponse(
        s"Updated Job ${jobId.toIdString} to state ${updatedJob.state}"))(
      customExecutionContext)
  }

  def updateJobRoute(jobId: IdAble) = {
    put {
      entity(as[UpdateJobRecord]) { update =>
        complete {
          dao.updateJob(jobId, update.name, update.comment, update.tags)
        }
      }
    }
  }

  def deleteJobRoute(jobId: IdAble) = {
    delete {
      complete {
        for {
          job <- dao.getJobById(jobId)
          _ <- validateStateIsCreated(
            job,
            s"ONLY Jobs in the CREATED state can be DELETED. Job is in state: ${job.state}")
          msg <- dao.deleteMultiJob(job.id)
        } yield msg
      }
    }
  }

  def allIdAbleJobRoutes(implicit ec: ExecutionContext): Route =
    pathPrefix(IdAbleMatcher) { jobId =>
      pathEndOrSingleSlash {
        get {
          complete {
            dao.getJobById(jobId)
          }
        } ~ updateJobRoute(jobId) ~
          deleteJobRoute(jobId)
      } ~
        path(LOG_PREFIX) {
          pathEndOrSingleSlash {
            post {
              entity(as[LogMessageRecord]) { m =>
                complete {
                  val f = jobId match {
                    case IntIdAble(n) => Future.successful(n)
                    case UUIDIdAble(_) => dao.getJobById(jobId).map(_.id)(ec)
                  }
                  f.map { intId =>
                    val message =
                      s"$LOG_PB_SMRTPIPE_RESOURCE_ID::job::$intId::${m.sourceId} ${m.message}"
                    // FIXME. Need to map this to the proper log level
                    logger.info(message)
                    StatusCodes.Created -> MessageResponse(
                      s"Successfully logged. $message")
                  }(ec)
                }
              }
            }
          }
        } ~
        path("terminate") {
          pathEndOrSingleSlash {
            post {
              complete {
                terminateJob(jobId)
              }
            }
          }
        } ~
        path(JOB_SUBMIT_PREFIX) {
          pathEndOrSingleSlash {
            post {
              complete {
                updateJobStateToSubmitted(jobId, ec)
              }
            }
          }
        } ~
        path(JOB_TASK_PREFIX) {
          get {
            complete {
              dao.getJobTasks(jobId)
            }
          } ~
            post {
              entity(as[CreateJobTaskRecord]) { r =>
                complete {
                  StatusCodes.Created -> {
                    dao
                      .getJobById(jobId)
                      .flatMap { job =>
                        dao.addJobTask(
                          JobTask(r.uuid,
                                  job.id,
                                  r.taskId,
                                  r.taskTypeId,
                                  r.name,
                                  AnalysisJobStates.CREATED.toString,
                                  r.createdAt,
                                  r.createdAt,
                                  None))
                      }(ec)
                  }
                }
              }
            }
        } ~
        path(JOB_TASK_PREFIX / JavaUUID) { taskUUID =>
          get {
            complete {
              dao.getJobTask(taskUUID)
            }
          } ~
            put {
              entity(as[UpdateJobTaskRecord]) { r =>
                complete {
                  StatusCodes.Created -> {
                    dao
                      .getJobById(jobId)
                      .flatMap { engineJob =>
                        dao.updateJobTask(UpdateJobTask(engineJob.id,
                                                        taskUUID,
                                                        r.state,
                                                        r.message,
                                                        r.errorMessage))
                      }(ec)
                  }
                }
              }
            }
        } ~
        path(JOB_REPORT_PREFIX / JavaUUID) { reportUUID =>
          pathEndOrSingleSlash {
            get {
              complete {
                dao
                  .getDataStoreReportByUUID(reportUUID)
                  .map(_.parseJson)(ec) // To get the mime type correct
              }
            }
          }
        } ~
        path(JOB_REPORT_PREFIX / JavaUUID / "resources") { reportUUID =>
          pathEndOrSingleSlash {
            parameter("relpath") { relpath =>
              onSuccess(dao.getDataStoreFileByUUID(reportUUID)) { file =>
                val resourcePath = Paths.get(file.path).resolveSibling(relpath)
                getFromFile(resourcePath.toFile)
              }
            }
          }
        } ~
        path(JOB_REPORT_PREFIX) {
          get {
            complete {
              dao
                .getJobById(jobId)
                .flatMap { engineJob =>
                  dao.getDataStoreReportFilesByJobId(engineJob.id)
                }(ec)
            }
          }
        } ~
        path(JOB_EVENT_PREFIX) {
          get {
            complete {
              dao
                .getJobById(jobId)
                .flatMap { engineJob =>
                  dao.getJobEventsByJobId(engineJob.id)
                }(ec)
            }
          }
        } ~
        path(JOB_DATASTORE_PREFIX) {
          get {
            complete {
              dao
                .getJobById(jobId)
                .flatMap { engineJob =>
                  dao.getDataStoreServiceFilesByJobId(engineJob.id)
                }(ec)
            }
          }
        } ~
        path(JOB_DATASTORE_PREFIX / JavaUUID) { datastoreFileUUID =>
          get {
            complete {
              dao.getDataStoreFileByUUID(datastoreFileUUID)
            }
          } ~
            put {
              entity(as[DataStoreFileUpdateRequest]) { sopts =>
                complete {
                  dao.updateDataStoreFile(datastoreFileUUID,
                                          sopts.path,
                                          sopts.fileSize,
                                          sopts.isActive)
                }
              }
            }
        } ~
        path(JOB_OPTIONS) {
          get {
            complete {
              dao.getJobById(jobId).map(_.jsonSettings.parseJson)(ec)
            }
          }
        } ~
        path(ENTRY_POINTS_PREFIX) {
          get {
            complete {
              dao
                .getJobById(jobId)
                .flatMap { engineJob =>
                  dao.getJobEntryPoints(engineJob.id)
                }(ec)
            }
          }
        } ~
        path(JOB_DATASTORE_PREFIX) {
          post {
            entity(as[DataStoreFile]) { dsf =>
              complete {
                StatusCodes.Created -> {
                  if (dsf.isChunked) {
                    Future.successful(MessageResponse(
                      s"Chunked Files are not importable. Skipping Importing of DataStoreFile uuid:${dsf.uniqueId} path:${dsf.path}"))
                  } else {
                    dao
                      .getJobById(jobId)
                      .flatMap { engineJob =>
                        dao.importDataStoreFile(dsf, engineJob.uuid)
                      }(ec)
                  }
                }
              }
            }
          }
        } ~
        path(JOB_DATASTORE_PREFIX / JavaUUID / "download") {
          datastoreFileUUID =>
            get {
              complete {
                dao
                  .getDataStoreFileByUUID(datastoreFileUUID)
                  .map { dsf =>
                    val fn =
                      s"job-${jobId.toIdString}-${dsf.uuid.toString}-${Paths.get(dsf.path).toAbsolutePath.getFileName}"
                    downloadFile(Paths.get(dsf.path), fn)
                  }(ec)
              }
            }
        } ~
        path("children") {
          complete {
            dao.getJobChildrenByJobId(jobId)
          }
        }
    }

  override def routes: Route = allIdAbleJobRoutes ~ allRootJobRoutes
}

// All Service Jobs should be defined here. This a bit boilerplate, but it's very simple and there aren't a large number of job types
class HelloWorldJobsService(override val dao: JobsDao,
                            override val config: SystemJobConfig)(
    implicit val um: FromRequestUnmarshaller[HelloWorldJobOptions],
    implicit val sm: ToEntityMarshaller[HelloWorldJobOptions],
    implicit val jwriter: JsonWriter[HelloWorldJobOptions])
    extends CommonJobsRoutes[HelloWorldJobOptions] {
  override def jobTypeId = JobTypeIds.HELLO_WORLD
}

class DbBackupJobsService(override val dao: JobsDao,
                          override val config: SystemJobConfig)(
    implicit val um: FromRequestUnmarshaller[DbBackUpJobOptions],
    implicit val sm: ToEntityMarshaller[DbBackUpJobOptions],
    implicit val jwriter: JsonWriter[DbBackUpJobOptions])
    extends CommonJobsRoutes[DbBackUpJobOptions] {
  override def jobTypeId = JobTypeIds.DB_BACKUP
}

class DeleteDataSetJobsService(override val dao: JobsDao,
                               override val config: SystemJobConfig)(
    implicit val um: FromRequestUnmarshaller[DeleteDataSetJobOptions],
    implicit val sm: ToEntityMarshaller[DeleteDataSetJobOptions],
    implicit val jwriter: JsonWriter[DeleteDataSetJobOptions])
    extends CommonJobsRoutes[DeleteDataSetJobOptions] {
  override def jobTypeId = JobTypeIds.DELETE_DATASETS
}

class DeleteSmrtLinkJobsService(override val dao: JobsDao,
                                override val config: SystemJobConfig)(
    implicit val um: FromRequestUnmarshaller[DeleteSmrtLinkJobOptions],
    implicit val sm: ToEntityMarshaller[DeleteSmrtLinkJobOptions],
    implicit val jwriter: JsonWriter[DeleteSmrtLinkJobOptions])
    extends CommonJobsRoutes[DeleteSmrtLinkJobOptions] {
  override def jobTypeId = JobTypeIds.DELETE_JOB
}

class ExportDataSetsJobsService(override val dao: JobsDao,
                                override val config: SystemJobConfig)(
    implicit val um: FromRequestUnmarshaller[ExportDataSetsJobOptions],
    implicit val sm: ToEntityMarshaller[ExportDataSetsJobOptions],
    implicit val jwriter: JsonWriter[ExportDataSetsJobOptions])
    extends CommonJobsRoutes[ExportDataSetsJobOptions] {
  override def jobTypeId = JobTypeIds.EXPORT_DATASETS
}

class ExportJobsService(override val dao: JobsDao,
                        override val config: SystemJobConfig)(
    implicit val um: FromRequestUnmarshaller[ExportSmrtLinkJobOptions],
    implicit val sm: ToEntityMarshaller[ExportSmrtLinkJobOptions],
    implicit val jwriter: JsonWriter[ExportSmrtLinkJobOptions])
    extends CommonJobsRoutes[ExportSmrtLinkJobOptions] {
  override def jobTypeId = JobTypeIds.EXPORT_JOBS
}

class ImportJobService(override val dao: JobsDao,
                       override val config: SystemJobConfig)(
    implicit val um: FromRequestUnmarshaller[ImportSmrtLinkJobOptions],
    implicit val sm: ToEntityMarshaller[ImportSmrtLinkJobOptions],
    implicit val jwriter: JsonWriter[ImportSmrtLinkJobOptions])
    extends CommonJobsRoutes[ImportSmrtLinkJobOptions] {
  override def jobTypeId = JobTypeIds.IMPORT_JOB
}

class ImportBarcodeFastaJobsService(override val dao: JobsDao,
                                    override val config: SystemJobConfig)(
    implicit val um: FromRequestUnmarshaller[ImportBarcodeFastaJobOptions],
    implicit val sm: ToEntityMarshaller[ImportBarcodeFastaJobOptions],
    implicit val jwriter: JsonWriter[ImportBarcodeFastaJobOptions])
    extends CommonJobsRoutes[ImportBarcodeFastaJobOptions] {
  override def jobTypeId = JobTypeIds.CONVERT_FASTA_BARCODES
}

class ImportDataSetJobsService(override val dao: JobsDao,
                               override val config: SystemJobConfig)(
    implicit val um: FromRequestUnmarshaller[ImportDataSetJobOptions],
    implicit val sm: ToEntityMarshaller[ImportDataSetJobOptions],
    implicit val jwriter: JsonWriter[ImportDataSetJobOptions])
    extends CommonJobsRoutes[ImportDataSetJobOptions]
    with DataSetFileUtils {
  //type T = ImportDataSetJobOptions
  import CommonModelImplicits._

  override def jobTypeId = JobTypeIds.IMPORT_DATASET

  private def updatePathIfNecessary(
      opts: ImportDataSetJobOptions): Future[EngineJob] = {
    for {
      dsMini <- Future.fromTry(Try(getDataSetMiniMeta(opts.path)))
      ds <- dao.getDataSetById(dsMini.uuid)
      engineJob <- dao.getJobById(ds.jobId)
      m1 <- dao.updateDataStoreFile(ds.uuid,
                                    Some(opts.path.toString),
                                    None,
                                    true)
      m2 <- dao.updateDataSetById(ds.uuid, opts.path.toString, true)
      _ <- Future.successful(andLog(s"$m1 $m2"))

    } yield engineJob
  }

  /**
    * Need a custom create job method to return the companion dataset job IF the
    * dataset has already been imported.
    *
    * If the path of dataset in the database is different than the provided path, the
    * path is assumed to be updated to the path provided.
    *
    * @param opts
    * @param user
    * @return
    */
  override def createJob(opts: ImportDataSetJobOptions,
                         user: Option[UserRecord]): Future[EngineJob] = {

    val f1 = for {
      _ <- validator(opts, user)
      engineJob <- updatePathIfNecessary(opts)
    } yield engineJob

    f1.recoverWith {
      case _: ResourceNotFoundError => super.createJob(opts, user)
    }
  }

}

class ImportDataSetsZipJobService(override val dao: JobsDao,
                                  override val config: SystemJobConfig)(
    implicit val um: FromRequestUnmarshaller[ImportDataSetsZipJobOptions],
    implicit val sm: ToEntityMarshaller[ImportDataSetsZipJobOptions],
    implicit val jwriter: JsonWriter[ImportDataSetsZipJobOptions])
    extends CommonJobsRoutes[ImportDataSetsZipJobOptions] {
  override def jobTypeId = JobTypeIds.IMPORT_DATASETS_ZIP
}

class ImportFastaJobsService(override val dao: JobsDao,
                             override val config: SystemJobConfig)(
    implicit val um: FromRequestUnmarshaller[ImportFastaJobOptions],
    implicit val sm: ToEntityMarshaller[ImportFastaJobOptions],
    implicit val jwriter: JsonWriter[ImportFastaJobOptions])
    extends CommonJobsRoutes[ImportFastaJobOptions] {
  override def jobTypeId = JobTypeIds.CONVERT_FASTA_REFERENCE

}

class ImportFastaGmapJobsService(override val dao: JobsDao,
                                 override val config: SystemJobConfig)(
    implicit val um: FromRequestUnmarshaller[ImportFastaGmapJobOptions],
    implicit val sm: ToEntityMarshaller[ImportFastaGmapJobOptions],
    implicit val jwriter: JsonWriter[ImportFastaGmapJobOptions])
    extends CommonJobsRoutes[ImportFastaGmapJobOptions] {
  override def jobTypeId = JobTypeIds.CONVERT_FASTA_GMAPREFERENCE

}

class MergeDataSetJobsService(override val dao: JobsDao,
                              override val config: SystemJobConfig)(
    implicit val um: FromRequestUnmarshaller[MergeDataSetJobOptions],
    implicit val sm: ToEntityMarshaller[MergeDataSetJobOptions],
    implicit val jwriter: JsonWriter[MergeDataSetJobOptions])
    extends CommonJobsRoutes[MergeDataSetJobOptions] {
  override def jobTypeId = JobTypeIds.MERGE_DATASETS
}

class MockPbsmrtpipeJobsService(override val dao: JobsDao,
                                override val config: SystemJobConfig)(
    implicit val um: FromRequestUnmarshaller[MockPbsmrtpipeJobOptions],
    implicit val sm: ToEntityMarshaller[MockPbsmrtpipeJobOptions],
    implicit val jwriter: JsonWriter[MockPbsmrtpipeJobOptions])
    extends CommonJobsRoutes[MockPbsmrtpipeJobOptions] {
  override def jobTypeId = JobTypeIds.MOCK_PBSMRTPIPE
}

class PbsmrtpipeJobsService(override val dao: JobsDao,
                            override val config: SystemJobConfig)(
    implicit val um: FromRequestUnmarshaller[PbsmrtpipeJobOptions],
    implicit val sm: ToEntityMarshaller[PbsmrtpipeJobOptions],
    implicit val jwriter: JsonWriter[PbsmrtpipeJobOptions])
    extends CommonJobsRoutes[PbsmrtpipeJobOptions] {
  override def jobTypeId = JobTypeIds.PBSMRTPIPE

  override def terminateJob(jobId: IdAble): Future[MessageResponse] = {
    for {
      engineJob <- dao.getJobById(jobId)
      message <- terminatePbsmrtpipeJob(engineJob)
    } yield message
  }

  private def failIfNotRunning(engineJob: EngineJob): Future[EngineJob] = {
    if (engineJob.isRunning) Future.successful(engineJob)
    else
      Future.failed(
        UnprocessableEntityError(
          s"Only terminating ${AnalysisJobStates.RUNNING} is supported"))
  }

  private def terminatePbsmrtpipeJob(
      engineJob: EngineJob): Future[MessageResponse] = {
    for {
      runningJob <- failIfNotRunning(engineJob)
      _ <- Future {
        PbsmrtpipeJobUtils.terminateJobFromDir(Paths.get(runningJob.path))
      } // FIXME Handle failure in better well defined model
    } yield
      MessageResponse(
        s"Attempting to terminate analysis job ${runningJob.id} in ${runningJob.path}")
  }
}

class RsConvertMovieToDataSetJobsService(override val dao: JobsDao,
                                         override val config: SystemJobConfig)(
    implicit val um: FromRequestUnmarshaller[
      RsConvertMovieToDataSetJobOptions],
    implicit val sm: ToEntityMarshaller[RsConvertMovieToDataSetJobOptions],
    implicit val jwriter: JsonWriter[RsConvertMovieToDataSetJobOptions])
    extends CommonJobsRoutes[RsConvertMovieToDataSetJobOptions] {
  override def jobTypeId = JobTypeIds.CONVERT_RS_MOVIE
}

class SimpleJobsService(override val dao: JobsDao,
                        override val config: SystemJobConfig)(
    implicit val um: FromRequestUnmarshaller[SimpleJobOptions],
    implicit val sm: ToEntityMarshaller[SimpleJobOptions],
    implicit val jwriter: JsonWriter[SimpleJobOptions])
    extends CommonJobsRoutes[SimpleJobOptions] {
  override def jobTypeId = JobTypeIds.SIMPLE
}

class TsJobBundleJobsService(override val dao: JobsDao,
                             override val config: SystemJobConfig)(
    implicit val um: FromRequestUnmarshaller[TsJobBundleJobOptions],
    implicit val sm: ToEntityMarshaller[TsJobBundleJobOptions],
    implicit val jwriter: JsonWriter[TsJobBundleJobOptions])
    extends CommonJobsRoutes[TsJobBundleJobOptions] {
  override def jobTypeId = JobTypeIds.TS_JOB
}

class TsSystemStatusBundleJobsService(override val dao: JobsDao,
                                      override val config: SystemJobConfig)(
    implicit val um: FromRequestUnmarshaller[TsSystemStatusBundleJobOptions],
    implicit val sm: ToEntityMarshaller[TsSystemStatusBundleJobOptions],
    implicit val jwriter: JsonWriter[TsSystemStatusBundleJobOptions])
    extends CommonJobsRoutes[TsSystemStatusBundleJobOptions] {
  override def jobTypeId = JobTypeIds.TS_SYSTEM_STATUS
}

class TsJobHarvesterJobsService(override val dao: JobsDao,
                                override val config: SystemJobConfig)(
    implicit val um: FromRequestUnmarshaller[TsJobHarvesterJobOptions],
    implicit val sm: ToEntityMarshaller[TsJobHarvesterJobOptions],
    implicit val jwriter: JsonWriter[TsJobHarvesterJobOptions])
    extends CommonJobsRoutes[TsJobHarvesterJobOptions] {
  override def jobTypeId = JobTypeIds.TS_JOB_HARVESTER_JOB
}

class CopyDataSetJobService(override val dao: JobsDao,
                            override val config: SystemJobConfig)(
    implicit val um: FromRequestUnmarshaller[CopyDataSetJobOptions],
    implicit val sm: ToEntityMarshaller[CopyDataSetJobOptions],
    implicit val jwriter: JsonWriter[CopyDataSetJobOptions])
    extends CommonJobsRoutes[CopyDataSetJobOptions] {
  override def jobTypeId = JobTypeIds.DS_COPY
}

// This is the used for the "Naked" untyped job route service <smrt-link>/<job-manager>/jobs
class NakedNoTypeJobsService(override val dao: JobsDao,
                             override val config: SystemJobConfig)(
    implicit val um: FromRequestUnmarshaller[SimpleJobOptions],
    implicit val sm: ToEntityMarshaller[SimpleJobOptions],
    implicit val jwriter: JsonWriter[SimpleJobOptions])
    extends CommonJobsRoutes[SimpleJobOptions] {
  override def jobTypeId = JobTypeIds.SIMPLE
  override def createJob(opts: SimpleJobOptions,
                         user: Option[UserRecord]): Future[EngineJob] =
    Future.failed(throw new UnprocessableEntityError(
      "Unsupported Request for Job creation. Using specific job type endpoint"))

}

class MultiAnalysisJobService(override val dao: JobsDao,
                              override val config: SystemJobConfig)(
    implicit val um: FromRequestUnmarshaller[MultiAnalysisJobOptions],
    implicit val sm: ToEntityMarshaller[MultiAnalysisJobOptions],
    implicit val jwriter: JsonWriter[MultiAnalysisJobOptions])
    extends CommonJobsRoutes[MultiAnalysisJobOptions] {

  import SmrtLinkJsonProtocols._
  import CommonModelSpraySupport._

  override def jobTypeId = JobTypeIds.MJOB_MULTI_ANALYSIS

  /**
    * Customization for to enable "auto" submit at creation time.
    *
    * For the submit case, this might need to have a more explicit
    * interface to validate that entry points are resolvable
    * when the job is moved to submitted. However, it depends on how the
    * client wants to run the jobs.
    *
    */
  override def createJob(opts: MultiAnalysisJobOptions,
                         user: Option[UserRecord]): Future[EngineJob] = {
    val uuid = UUID.randomUUID()

    val name = opts.name.getOrElse(opts.jobTypeId.id)
    val comment =
      opts.description.getOrElse(s"Description for job ${opts.jobTypeId.name}")

    val jsettings = jwriter.write(opts).asJsObject

    val projectId = opts.projectId.getOrElse(JobConstants.GENERAL_PROJECT_ID)

    def creator(epoints: Seq[EngineJobEntryPointRecord]): Future[EngineJob] =
      dao.createMultiJob(
        uuid,
        name,
        comment,
        opts.jobTypeId,
        epoints.toSet,
        jsettings,
        user.map(_.userId),
        user.flatMap(_.userEmail),
        config.smrtLinkVersion,
        projectId,
        subJobTypeId = opts.subJobTypeId,
        submitJob = opts.getSubmit(),
        childJobs = opts.jobs
      )

    for {
      vopts <- validator(opts, user) // This will fail the Future if any validation errors occur.
      entryPoints <- Future(vopts.resolveEntryPoints(dao))
      engineJob <- creator(entryPoints)
    } yield engineJob
  }

  override def updateJobRoute(jobId: IdAble) = {
    put {
      entity(as[MultiAnalysisJobOptions]) { opts =>
        complete {
          StatusCodes.Created -> {
            for {
              _ <- Future.successful(
                andLog(s"Attempting to update multi-job state with $opts"))
              job <- dao.getJobById(jobId)
              _ <- validateStateIsCreated(
                job,
                s"ONLY Jobs in the CREATED state can be updated. Job is in state: ${job.state}")
              msg <- Future.successful(s"Updating job ${job.id}")
              updatedJob <- dao.updateMultiAnalysisJob(
                job.id,
                opts,
                opts.toJson(jwriter).asJsObject,
                job.createdBy,
                job.createdByEmail,
                job.smrtlinkVersion
              )
            } yield updatedJob
          }
        }
      }
    }
  }

  // List of Core Jobs that the multi-analysis job has created
  val childrenJobsRoute: Route = {
    pathPrefix(IdAbleMatcher / "jobs") { jobId =>
      pathEndOrSingleSlash {
        get {
          complete {
            dao.getMultiJobChildren(jobId)
          }
        }
      }
    }
  }

  // If/When there are more multi-job types, this should be refactored out into it's own base.
  override def allIdAbleJobRoutes(implicit ec: ExecutionContext): Route =
    super.allIdAbleJobRoutes(ec) ~ childrenJobsRoute
}

/**
  * This is factor-ish util to Adhere to the current SL System design.
  *
  * This has all the job related endpoints and a few misc routes.
  *
  * @param dao JobDao
  */
class JobsServiceUtils(dao: JobsDao, config: SystemJobConfig)(
    implicit val actorSystem: ActorSystem)
    extends PacBioService
    with JobServiceConstants
    with FileAndResourceDirectives
    with LazyLogging
    with DownloadFileUtils {

  import SmrtLinkJsonProtocols._

  // For getFile and friends to work correctly
  implicit val routing = RoutingSettings.default

  override val manifest = PacBioComponentManifest(
    toServiceId("new_job_service"),
    "New Job Service",
    "0.2.0",
    "New Job Service")

  def getServiceMultiJobs(): Seq[JobServiceRoutes] = Seq(
    new MultiAnalysisJobService(dao, config)
  )

  def getServiceJobs(): Seq[JobServiceRoutes] = Seq(
    new DbBackupJobsService(dao, config),
    new DeleteDataSetJobsService(dao, config),
    new DeleteSmrtLinkJobsService(dao, config),
    new ExportDataSetsJobsService(dao, config),
    new ExportJobsService(dao, config),
    new ImportJobService(dao, config),
    new HelloWorldJobsService(dao, config),
    new ImportBarcodeFastaJobsService(dao, config),
    new ImportDataSetJobsService(dao, config),
    new ImportDataSetsZipJobService(dao, config),
    new ImportFastaJobsService(dao, config),
    new ImportFastaGmapJobsService(dao, config),
    new MergeDataSetJobsService(dao, config),
    new MockPbsmrtpipeJobsService(dao, config),
    new PbsmrtpipeJobsService(dao, config),
    new RsConvertMovieToDataSetJobsService(dao, config),
    new SimpleJobsService(dao, config),
    new TsJobBundleJobsService(dao, config),
    new TsSystemStatusBundleJobsService(dao, config),
    new TsJobHarvesterJobsService(dao, config),
    new CopyDataSetJobService(dao, config)
  )

  // Note these is duplicated within a Job
  def datastoreRoute(): Route = {
    pathPrefix(DATASTORE_FILES_PREFIX / JavaUUID) { dsFileUUID =>
      pathEndOrSingleSlash {
        get {
          complete {
            dao.getDataStoreFile(dsFileUUID)
          }
        } ~
          put {
            entity(as[DataStoreFileUpdateRequest]) { sopts =>
              complete {
                dao.updateDataStoreFile(dsFileUUID,
                                        sopts.path,
                                        sopts.fileSize,
                                        sopts.isActive)
              }
            }
          }
      } ~
        path("download") {
          get {
            parameter('prefix.?) { prefix =>
              complete {
                dao.getDataStoreFileByUUID(dsFileUUID).map { f =>
                  val fileName = Paths.get(f.path).toAbsolutePath.getFileName
                  val prefixName =
                    prefix.getOrElse(s"job-${f.jobId}-${f.uuid.toString}")
                  val outputFileName = s"$prefixName-$fileName"
                  downloadFile(Paths.get(f.path), outputFileName)
                }
              }
            }
          }
        } ~
        path("resources") {
          get {
            parameter("relpath") { relpath =>
              onSuccess(dao.getDataStoreFileByUUID(dsFileUUID)) { file =>
                val resourcePath = Paths.get(file.path).resolveSibling(relpath)
                getFromFile(resourcePath.toFile)
              }
            }
          }
        }
    }
  }

  def getJobTypesRoute(jobTypes: Seq[JobTypeIds.JobType]): Route = {
    val jobTypeEndPoints = jobTypes.map(x =>
      JobTypeEndPoint(x.id, x.description, x.isQuick, x.isMultiJob))

    pathPrefix(JOB_TYPES_PREFIX) {
      pathEndOrSingleSlash {
        get {
          complete {
            jobTypeEndPoints
          }
        }
      }
    }
  }

  /**
    * This is a bit sloppy and could be cleaned up. The model is to have a single factory-ish func to return
    * a complete list of routes that already prefixed correctly.
    *
    * @return
    */
  def getServiceJobRoutes(): Route = {

    // This will NOT be wrapped in a job-type prefix
    val nakedCoreJob = new NakedNoTypeJobsService(dao, config)

    // These will be wrapped with the a job-type-id specific prefix
    val coreJobs = getServiceJobs()
    val multiJobs = getServiceMultiJobs()

    val allJobTypeIds = coreJobs.map(_.jobTypeId) ++ multiJobs.map(_.jobTypeId)

    /** Job Type Endpoints **/
    // Total List (core+multi jobs) of JobTypeEndPoints
    // Unprefix Job (Meta) Type routes for each registered Job type
    val jobTypeRoutes: Route = getJobTypesRoute(allJobTypeIds)
    val prefixedJobTypeRoutes = pathPrefix(ROOT_SA_PREFIX / JOB_MANAGER_PREFIX) {
      jobTypeRoutes
    } ~ pathPrefix(ROOT_SL_PREFIX / JOB_MANAGER_PREFIX) { jobTypeRoutes }

    /** Core Jobs **/
    // Create all Core Job Routes with <job-type-id> prefix
    val coreJobsPrefixedByType =
      coreJobs.map(j => pathPrefix(j.jobTypeId.id) { j.routes }).reduce(_ ~ _)

    val allCoreJobRoutes
      : Route = nakedCoreJob.allIdAbleJobRoutes ~ coreJobsPrefixedByType

    /** MultiJob **/
    // Create all Multi Job Routes with <job-type-id> prefix
    val multiJobsPrefixedByType =
      multiJobs.map(j => pathPrefix(j.jobTypeId.id) { j.routes }).reduce(_ ~ _)

    //FIXME (mpkocher)(2017-9-11) We really can't reuse these core job route types for the multi-job model
    val allMultiJobRoutes
      : Route = nakedCoreJob.allIdAbleJobRoutes ~ multiJobsPrefixedByType

    /** Utils to Wrap **/
    // These need to be prefixed with secondary-analysis as well
    // Keep the backward compatibility of /smrt-link/ and /secondary-analysis root prefix
    def wrapWithJobPrefix(jobRoutes: Route): Route =
      pathPrefix(ROOT_SA_PREFIX / JOB_MANAGER_PREFIX / JOB_ROOT_PREFIX) {
        jobRoutes
      } ~ pathPrefix(ROOT_SL_PREFIX / JOB_MANAGER_PREFIX / JOB_ROOT_PREFIX) {
        jobRoutes
      }

    def wrapWithMultiJobPrefix(jobRoutes: Route): Route =
      pathPrefix(ROOT_SA_PREFIX / JOB_MANAGER_PREFIX / JOB_MULTI_ROOT_PREFIX) {
        jobRoutes
      } ~ pathPrefix(
        ROOT_SL_PREFIX / JOB_MANAGER_PREFIX / JOB_MULTI_ROOT_PREFIX) {
        jobRoutes
      }

    /** Random datastore routes that are rooted in an odd prefixed location **/
    // Misc DataStore routes. This should probable migrated to a cleaner subroute.
    val prefixedDataStoreFileRoutes = pathPrefix(ROOT_SA_PREFIX) {
      datastoreRoute()
    } ~ pathPrefix(ROOT_SL_PREFIX) { datastoreRoute() }

    /** Final Route list **/
    prefixedJobTypeRoutes ~ wrapWithJobPrefix(allCoreJobRoutes) ~ wrapWithJobPrefix(
      coreJobsPrefixedByType) ~ prefixedDataStoreFileRoutes ~ wrapWithMultiJobPrefix(
      allMultiJobRoutes) ~ wrapWithMultiJobPrefix(multiJobsPrefixedByType)
  }

  override def routes: Route = getServiceJobRoutes()

}

trait JobsServiceProvider {
  this: ActorRefFactoryProvider
    with ActorSystemProvider
    with ServiceComposer
    with JobsDaoProvider
    with SmrtLinkConfigProvider =>

  //FIXME(mpkocher)(8-27-2017) Rename this to something sensible
  val newJobService: Singleton[JobsServiceUtils] = Singleton { () =>
    implicit val system = actorSystem()
    new JobsServiceUtils(jobsDao(), systemJobConfig())
  }

  addService(newJobService)
}
